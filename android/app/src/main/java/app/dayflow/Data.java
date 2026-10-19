package app.dayflow;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Local database is authoritative. No account or network is needed. */
public final class Data extends SQLiteOpenHelper {
    private static Data instance;
    public static synchronized Data get(Context c) {
        if (instance == null) instance = new Data(c.getApplicationContext());
        return instance;
    }
    private Data(Context c) { super(c, "dayflow.db", null, 1); setWriteAheadLoggingEnabled(true); }
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE records(id TEXT PRIMARY KEY,kind TEXT,title TEXT,start_at TEXT,deleted INTEGER,dirty INTEGER,version INTEGER DEFAULT 0,body TEXT)");
        db.execSQL("CREATE INDEX agenda ON records(deleted,kind,start_at)");
        db.execSQL("CREATE TABLE seen(hash TEXT PRIMARY KEY,created INTEGER)");
    }
    public void onUpgrade(SQLiteDatabase db,int oldV,int newV) { throw new IllegalStateException("Unsupported database upgrade"); }
    public static JSONObject make(String kind,String title) throws JSONException {
        return new JSONObject().put("id",UUID.randomUUID().toString()).put("kind",kind).put("title",title)
            .put("content","").put("start_at","").put("end_at","").put("time_hint","")
            .put("completed","").put("deleted",false).put("revision",UUID.randomUUID().toString())
            .put("ancestors",new JSONArray()).put("updated_at",Instant.now().toString());
    }
    public static void validate(JSONObject r) throws Exception {
        UUID.fromString(r.getString("id")); UUID.fromString(r.getString("revision"));
        if (!Arrays.asList("task","event","note","inbox","course","project").contains(r.getString("kind"))) throw new Exception("未知事项类型");
        if (r.getString("title").length()>1000 || r.toString().getBytes(StandardCharsets.UTF_8).length>65536) throw new Exception("事项过大");
        JSONArray a=r.optJSONArray("ancestors"); if(a==null || a.length()>256) throw new Exception("无效历史");
        String start=r.optString("start_at"); if(!start.isEmpty()) LocalDateTime.parse(start);
        String end=r.optString("end_at"); if(!end.isEmpty()) LocalDateTime.parse(end);
    }
    public synchronized JSONObject find(String id) throws JSONException {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM records WHERE id=?",new String[]{id})) {
            return c.moveToFirst()?new JSONObject(c.getString(0)):null;
        }
    }
    private void put(JSONObject r,boolean dirty,long version) throws JSONException {
        ContentValues v=new ContentValues();v.put("id",r.getString("id"));v.put("kind",r.getString("kind"));
        v.put("title",r.getString("title"));v.put("start_at",r.optString("start_at"));v.put("deleted",r.optBoolean("deleted")?1:0);
        v.put("dirty",dirty?1:0);v.put("version",version);v.put("body",r.toString());
        getWritableDatabase().insertWithOnConflict("records",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    private long version(String id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT version FROM records WHERE id=?",new String[]{id})) {return c.moveToFirst()?c.getLong(0):0;}
    }
    public synchronized void save(JSONObject r) throws JSONException {
        JSONObject old=find(r.getString("id"));
        if(old!=null) {
            JSONArray a=old.optJSONArray("ancestors"); if(a==null)a=new JSONArray();
            JSONArray bounded=new JSONArray();for(int i=Math.max(0,a.length()-254);i<a.length();i++)bounded.put(a.optString(i));
            bounded.put(old.optString("revision"));r.put("ancestors",bounded);
            r.put("revision",UUID.randomUUID().toString());
        }
        r.put("updated_at",Instant.now().toString());put(r,true,version(r.getString("id")));
    }
    public synchronized JSONArray list(String kind,int limit) throws JSONException {
        JSONArray rows=new JSONArray();String sql="SELECT body FROM records WHERE deleted=0";
        String[] args=null;
        if(kind!=null){sql+=" AND kind=?";args=new String[]{kind};}
        sql+=" ORDER BY CASE WHEN start_at='' THEN 1 ELSE 0 END,start_at,title LIMIT "+Math.min(500,limit);
        try(Cursor c=getReadableDatabase().rawQuery(sql,args)){while(c.moveToNext())rows.put(new JSONObject(c.getString(0)));}
        return rows;
    }
    public synchronized JSONArray pending() throws JSONException {
        JSONArray rows=new JSONArray();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT body,version FROM records WHERE dirty=1 LIMIT 100",null)) {
            while(c.moveToNext())rows.put(new JSONObject().put("record",new JSONObject(c.getString(0))).put("base_version",c.getLong(1)));
        }return rows;
    }
    public synchronized JSONArray reminders() throws JSONException {
        JSONArray rows=new JSONArray();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM records WHERE deleted=0 AND start_at>? ORDER BY start_at LIMIT 500",new String[]{LocalDateTime.now().toString()})) {
            while(c.moveToNext())rows.put(new JSONObject(c.getString(0)));
        }return rows;
    }
    public synchronized void ack(String id,long v,String revision) throws JSONException {
        JSONObject r=find(id);if(r==null)return;
        ContentValues values=new ContentValues();values.put("version",v);
        if(r.optString("revision").equals(revision))values.put("dirty",0);
        getWritableDatabase().update("records",values,"id=?",new String[]{id});
    }
    private static boolean contains(JSONArray a,String s){if(a!=null)for(int i=0;i<a.length();i++)if(s.equals(a.optString(i)))return true;return false;}
    private void conflictCopy(JSONObject r) throws JSONException {
        JSONObject copy=new JSONObject(r.toString());
        String id=UUID.nameUUIDFromBytes((r.getString("id")+":"+r.getString("revision")).getBytes(StandardCharsets.UTF_8)).toString();
        if(find(id)!=null)return;
        String title=r.optString("title");copy.put("id",id).put("title","[冲突副本] "+title.substring(0,Math.min(title.length(),990))).put("deleted",false)
            .put("conflict_of",r.getString("id"));
        put(copy,true,0);
    }
    public synchronized void remote(JSONObject r,long v) throws JSONException {
        String id=r.getString("id");if(v<=version(id))return;
        JSONObject local=find(id);
        if(local!=null && !local.optString("revision").equals(r.optString("revision"))) {
            try(Cursor c=getReadableDatabase().rawQuery("SELECT dirty FROM records WHERE id=?",new String[]{id})) {
                if(c.moveToFirst()&&c.getInt(0)==1)conflictCopy(local);
            }
        }put(r,false,v);
    }
    public synchronized boolean seen(String hash) {
        SQLiteDatabase db=getWritableDatabase();db.delete("seen","created<?",new String[]{Long.toString(System.currentTimeMillis()-7L*86400000)});
        ContentValues v=new ContentValues();v.put("hash",hash);v.put("created",System.currentTimeMillis());
        return db.insertWithOnConflict("seen",null,v,SQLiteDatabase.CONFLICT_IGNORE)==-1;
    }
    public synchronized JSONObject exportFile() throws JSONException {
        try(Cursor size=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(LENGTH(body)),0) FROM records",null)) {
            if(size.moveToFirst()&&size.getLong(0)>4*1024*1024)throw new JSONException("数据超过安全导出上限，请使用服务器增量同步；大文件分卷导出将在后续加入");
        }
        JSONArray records=new JSONArray();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM records ORDER BY id",null)){while(c.moveToNext())records.put(new JSONObject(c.getString(0)));}
        return new JSONObject().put("format","dayflow").put("schema",1).put("exported_at",Instant.now().toString()).put("records",records);
    }
    public synchronized String importFile(JSONObject file) throws Exception {
        if(!"dayflow".equals(file.optString("format"))||file.optInt("schema")!=1)throw new Exception("不支持的文件格式");
        JSONArray items=file.getJSONArray("records");if(items.length()>20000)throw new Exception("文件事项超过20000条，请拆分");
        for(int i=0;i<items.length();i++)validate(items.getJSONObject(i));
        int added=0,conflicts=0;SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            for(int i=0;i<items.length();i++) {
                JSONObject remote=items.getJSONObject(i),local=find(remote.getString("id"));
                if(local==null){put(remote,true,0);added++;continue;}
                if(local.optString("revision").equals(remote.optString("revision")))continue;
                if(contains(local.optJSONArray("ancestors"),remote.optString("revision")))continue;
                if(contains(remote.optJSONArray("ancestors"),local.optString("revision"))) {put(remote,true,version(local.getString("id")));added++;}
                else {conflictCopy(remote);conflicts++;}
            }db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        return "已合并 "+added+" 条；保留冲突副本 "+conflicts+" 条";
    }
}
