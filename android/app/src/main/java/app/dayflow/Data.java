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
    private final Context context;private boolean batchWrite;
    private Data(Context c) { super(c, "dayflow.db", null, 4); context=c;setWriteAheadLoggingEnabled(true); }
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE records(id TEXT PRIMARY KEY,kind TEXT,title TEXT,start_at TEXT,deleted INTEGER,dirty INTEGER,version INTEGER DEFAULT 0,body TEXT,updated_at TEXT DEFAULT '',completed TEXT DEFAULT '',end_at TEXT DEFAULT '',due_date TEXT DEFAULT '',parent_id TEXT DEFAULT '',type_id TEXT DEFAULT '',recurrence TEXT DEFAULT '')");
        db.execSQL("CREATE INDEX agenda ON records(deleted,kind,start_at)");
        db.execSQL("CREATE INDEX relations ON records(deleted,kind,parent_id,type_id)");
        db.execSQL("CREATE INDEX deadlines ON records(deleted,due_date)");
        db.execSQL("CREATE INDEX repeat_rules ON records(deleted,recurrence)");
        bridgeTables(db);
        db.execSQL("CREATE TABLE seen(hash TEXT PRIMARY KEY,created INTEGER)");
    }
    public void onUpgrade(SQLiteDatabase db,int oldV,int newV) {
        if(oldV<2){db.execSQL("ALTER TABLE records ADD COLUMN updated_at TEXT DEFAULT ''");db.execSQL("ALTER TABLE records ADD COLUMN completed TEXT DEFAULT ''");db.execSQL("ALTER TABLE records ADD COLUMN end_at TEXT DEFAULT ''");
            try(Cursor c=db.rawQuery("SELECT id,body FROM records",null)){while(c.moveToNext())try{JSONObject r=new JSONObject(c.getString(1));ContentValues v=new ContentValues();v.put("updated_at",r.optString("updated_at"));v.put("completed",r.optString("completed"));v.put("end_at",endIndex(r));db.update("records",v,"id=?",new String[]{c.getString(0)});}catch(JSONException e){throw new IllegalStateException("Cannot migrate existing record",e);}}
        }
        if(oldV<3){
            db.execSQL("ALTER TABLE records ADD COLUMN due_date TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE records ADD COLUMN parent_id TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE records ADD COLUMN type_id TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE records ADD COLUMN recurrence TEXT DEFAULT ''");
            db.execSQL("CREATE INDEX repeat_rules ON records(deleted,recurrence)");
            db.execSQL("CREATE INDEX relations ON records(deleted,kind,parent_id,type_id)");
            db.execSQL("CREATE INDEX deadlines ON records(deleted,due_date)");
            try(Cursor cursor=db.rawQuery("SELECT id,body FROM records",null)){while(cursor.moveToNext())try{JSONObject r=new JSONObject(cursor.getString(1));ContentValues values=new ContentValues();values.put("due_date",r.optString("due_date"));values.put("parent_id",r.optString("parent_id"));values.put("type_id",r.optString("type_id"));values.put("recurrence",Recurrences.series(r)?r.optString("recurrence"):"");db.update("records",values,"id=?",new String[]{cursor.getString(0)});}catch(JSONException e){throw new IllegalStateException("Cannot migrate planning fields",e);}}
        }
        if(oldV<4)bridgeTables(db);
    }
    private static void bridgeTables(SQLiteDatabase db){for(String name:new String[]{"bridge_base","bridge_stage"})db.execSQL("CREATE TABLE IF NOT EXISTS "+name+"(bridge TEXT NOT NULL,id TEXT NOT NULL,body TEXT NOT NULL,PRIMARY KEY(bridge,id))");}
    public synchronized JSONArray bridgeRows(String kind,int offset)throws JSONException{
        String sql="SELECT body FROM records"+(kind==null?"":" WHERE kind=?")+" ORDER BY id LIMIT 50 OFFSET "+Math.max(0,offset);JSONArray rows=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery(sql,kind==null?null:new String[]{kind})){while(c.moveToNext())rows.put(new JSONObject(c.getString(0)));}return rows;
    }
    public synchronized JSONObject bridgeBase(String bridge,String id,boolean staged)throws JSONException{
        if(staged)try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM bridge_stage WHERE bridge=? AND id=?",new String[]{bridge,id})){if(c.moveToFirst())return new JSONObject(c.getString(0));}
        try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM bridge_base WHERE bridge=? AND id=?",new String[]{bridge,id})){return c.moveToFirst()?new JSONObject(c.getString(0)):null;}
    }
    public synchronized void bridgeStage(String bridge,String id,JSONObject baseline){ContentValues values=new ContentValues();values.put("bridge",bridge);values.put("id",id);values.put("body",baseline.toString());getWritableDatabase().insertWithOnConflict("bridge_stage",null,values,SQLiteDatabase.CONFLICT_REPLACE);}
    public synchronized void bridgeBegin(String bridge){getWritableDatabase().delete("bridge_stage","bridge=?",new String[]{bridge});}
    public synchronized void bridgeCommit(String bridge){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.execSQL("INSERT OR REPLACE INTO bridge_base SELECT bridge,id,body FROM bridge_stage WHERE bridge=?",new Object[]{bridge});db.delete("bridge_stage","bridge=?",new String[]{bridge});db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public synchronized void bridgeSave(JSONObject r,String expectedRevision)throws JSONException{JSONObject current=find(r.optString("id"));if(current!=null&&!current.optString("revision").equals(expectedRevision))throw new JSONException("事项正在其他页面编辑，已保留本地修改，请稍后再同步");save(r);}
    public synchronized void bridgeConflict(JSONObject r)throws JSONException{conflictCopy(r);}
    public static JSONObject make(String kind,String title) throws JSONException {
        return new JSONObject().put("id",UUID.randomUUID().toString()).put("kind",kind).put("title",title)
            .put("content","").put("start_at","").put("end_at","").put("time_hint","")
            .put("completed","").put("deleted",false).put("revision",UUID.randomUUID().toString())
            .put("ancestors",new JSONArray()).put("updated_at",Instant.now().toString());
    }
    public static void validate(JSONObject r) throws Exception {
        UUID.fromString(r.getString("id")); UUID.fromString(r.getString("revision"));
        if (!Arrays.asList("task","event","note","inbox","course","project","type").contains(r.getString("kind"))) throw new Exception("未知事项类型");
        if (r.getString("title").length()>1000 || r.toString().getBytes(StandardCharsets.UTF_8).length>65536) throw new Exception("事项过大");
        JSONArray a=r.optJSONArray("ancestors"); if(a==null || a.length()>256) throw new Exception("无效历史");
        String start=r.optString("start_at"); if(!start.isEmpty()) LocalDateTime.parse(start);
        String end=r.optString("end_at"); if(!end.isEmpty()) LocalDateTime.parse(end);
        if(!r.optString("due_date").isEmpty())LocalDate.parse(r.getString("due_date"));
        if(!r.optString("recurrence_until").isEmpty())LocalDate.parse(r.getString("recurrence_until"));
        if(!r.optString("recurrence").isEmpty()&&!Arrays.asList("none","daily","weekly","monthly").contains(r.optString("recurrence")))throw new Exception("无效重复规则");
        JSONArray weekdays=r.optJSONArray("weekdays");if(weekdays!=null){if(weekdays.length()>7)throw new Exception("最多7个重复星期");for(int i=0;i<weekdays.length();i++)if(weekdays.optInt(i)<1||weekdays.optInt(i)>7)throw new Exception("无效重复星期");}
        if(!r.optString("parent_id").isEmpty())UUID.fromString(r.getString("parent_id"));
        if(!r.optString("type_id").isEmpty())UUID.fromString(r.getString("type_id"));
    }
    public synchronized int importNew(JSONArray rows)throws Exception{for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);validate(r);if(!r.optString("kind").equals("course"))throw new Exception("只能批量创建课程");}SQLiteDatabase db=getWritableDatabase();int added=0;db.beginTransaction();batchWrite=true;try{for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);if(find(r.getString("id"))!=null)continue;save(r);added++;}db.setTransactionSuccessful();}finally{batchWrite=false;db.endTransaction();}if(added>0)SyncJob.request(context);return added;}
    public synchronized int inboxCount(){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM records WHERE deleted=0 AND kind='inbox'",null)){return c.moveToFirst()?c.getInt(0):0;}}
    public synchronized JSONObject find(String id) throws JSONException {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM records WHERE id=?",new String[]{id})) {
            return c.moveToFirst()?new JSONObject(c.getString(0)):null;
        }
    }
    private void put(JSONObject r,boolean dirty,long version) throws JSONException {
        ContentValues v=new ContentValues();v.put("id",r.getString("id"));v.put("kind",r.getString("kind"));
        v.put("title",r.getString("title"));v.put("start_at",r.optString("start_at"));v.put("deleted",r.optBoolean("deleted")?1:0);
        v.put("dirty",dirty?1:0);v.put("version",version);v.put("body",r.toString());
        v.put("updated_at",r.optString("updated_at"));v.put("completed",r.optString("completed"));v.put("end_at",endIndex(r));v.put("due_date",r.optString("due_date"));v.put("parent_id",r.optString("parent_id"));v.put("type_id",r.optString("type_id"));v.put("recurrence",Recurrences.series(r)?r.optString("recurrence"):"");
        getWritableDatabase().insertWithOnConflict("records",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        try{MarkdownFiles.update(context,r);}catch(java.io.IOException e){throw new JSONException("记录已保存，但 .md 文件写入失败："+e.getMessage());}
        if(dirty&&!batchWrite)SyncJob.request(context);
    }
    private static String endIndex(JSONObject r){if(!r.optString("end_at").isEmpty())return r.optString("end_at");return r.optString("start_at").isEmpty()?"":LocalDateTime.parse(r.optString("start_at")).plusHours(1).toString();}
    private long version(String id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT version FROM records WHERE id=?",new String[]{id})) {return c.moveToFirst()?c.getLong(0):0;}
    }
    public synchronized void save(JSONObject r) throws JSONException {
        JSONObject old=find(r.getString("id"));
        if(old==null&&!r.optString("series_id").isEmpty()){
            r.put("ancestors",new JSONArray().put(r.optString("revision")));
            r.put("revision",UUID.randomUUID().toString());
        }
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
        sql+=("note".equals(kind)||"inbox".equals(kind)?" ORDER BY updated_at DESC":" ORDER BY CASE WHEN start_at='' THEN 1 ELSE 0 END,start_at,title")+" LIMIT "+Math.min(500,limit);
        try(Cursor c=getReadableDatabase().rawQuery(sql,args)){while(c.moveToNext())rows.put(new JSONObject(c.getString(0)));}
        return rows;
    }
    public synchronized JSONArray range(LocalDate from,LocalDate to,boolean courses)throws JSONException{return calendarRows(from,to,courses,false);}
    private JSONArray calendarRows(LocalDate from,LocalDate to,boolean courses,boolean includeDone)throws JSONException{
        List<JSONObject> output=new ArrayList<>();String kinds=courses?"kind='course'":"kind IN ('task','event','course','project')";
        String sql="SELECT body FROM records WHERE deleted=0 AND "+kinds+" AND recurrence='' AND ((start_at<? AND end_at>?) OR (start_at='' AND due_date>=? AND due_date<?)) ORDER BY start_at LIMIT 500";
        try(Cursor cursor=getReadableDatabase().rawQuery(sql,new String[]{to.atStartOfDay().toString(),from.atStartOfDay().toString(),from.toString(),to.toString()})){
            while(cursor.moveToNext()){JSONObject r=new JSONObject(cursor.getString(0));if(!r.optString("series_id").isEmpty()){JSONObject template=find(r.optString("series_id"));if(template!=null&&(template.optBoolean("deleted")||PlanTime.cancelled(template)||!template.optString("completed").isEmpty()))continue;}if(!PlanTime.cancelled(r)&&(includeDone||r.optString("completed").isEmpty()))output.add(r);}
        }
        try(Cursor cursor=getReadableDatabase().rawQuery("SELECT body FROM records WHERE deleted=0 AND "+kinds+" AND recurrence<>'' ORDER BY start_at LIMIT 500",null)){
            while(cursor.moveToNext()&&output.size()<1000){JSONObject series=new JSONObject(cursor.getString(0));Recurrences.expand(this,series,from,to,output,1000);}
        }
        output.removeIf(r->PlanTime.cancelled(r)||(!includeDone&&!r.optString("completed").isEmpty()));
        output.sort(Comparator.comparing(PlanTime::start).thenComparing(r->r.optString("title")));
        JSONArray rows=new JSONArray();for(JSONObject r:output)rows.put(r);return rows;
    }
    public synchronized JSONArray business(String query,String kind,int offset)throws JSONException{
        String pattern="%"+query.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        String sql="SELECT body FROM records WHERE deleted=0 AND kind<>'type' AND (title LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\' OR type_id IN (SELECT id FROM records WHERE kind='type' AND title LIKE ? ESCAPE '\\'))";
        List<String> args=new ArrayList<>(Arrays.asList(pattern,pattern,pattern));if(kind!=null){sql+=" AND kind=?";args.add(kind);}sql+=" ORDER BY updated_at DESC LIMIT 60 OFFSET "+Math.max(0,offset);
        JSONArray result=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery(sql,args.toArray(new String[0]))){while(c.moveToNext())result.put(new JSONObject(c.getString(0)));}return result;
    }
    public synchronized JSONArray related(String column,String id)throws JSONException{
        if(!Arrays.asList("parent_id","type_id").contains(column))throw new JSONException("无效关联");JSONArray result=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM records WHERE deleted=0 AND "+column+"=? ORDER BY start_at,title LIMIT 1000",new String[]{id})){while(c.moveToNext())result.put(new JSONObject(c.getString(0)));}return result;
    }
    public synchronized int mergeType(JSONObject source,JSONObject target)throws JSONException{
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();int count=0;
        try{while(true){JSONArray batch=new JSONArray();try(Cursor c=db.rawQuery("SELECT body FROM records WHERE deleted=0 AND type_id=? LIMIT 20",new String[]{source.getString("id")})){while(c.moveToNext())batch.put(new JSONObject(c.getString(0)));}if(batch.length()==0)break;for(int i=0;i<batch.length();i++){save(batch.getJSONObject(i).put("type_id",target.getString("id")));count++;}}
            save(new JSONObject(source.toString()).put("archived",true).put("merged_into",target.getString("id")));db.setTransactionSuccessful();
        }finally{db.endTransaction();}return count;
    }
    public synchronized JSONObject resolve(String id,String series,String date)throws JSONException{
        JSONObject saved=find(id);if(saved!=null)return saved;if(series==null||series.isEmpty()||date==null||date.isEmpty())return null;
        JSONObject template=find(series);if(template==null||template.optBoolean("deleted")||PlanTime.cancelled(template)||!template.optString("completed").isEmpty()||!Recurrences.series(template))return null;
        LocalDate day=LocalDate.parse(date);if(!Recurrences.matches(template,day))return null;JSONObject occurrence=Recurrences.instance(template,day);return id.equals(occurrence.optString("id"))?occurrence:null;
    }
    public synchronized JSONArray taskBoard(int mode)throws JSONException{JSONArray rows=new JSONArray();String clause=mode==2?"completed<>''":mode==1?"completed='' AND start_at=''":"completed=''";String sql="SELECT body FROM records WHERE deleted=0 AND kind IN ('task','event','course','project') AND body NOT LIKE '%\"status\":\"cancelled\"%' AND "+clause+(mode==2?" ORDER BY updated_at DESC":" ORDER BY CASE WHEN start_at>='"+LocalDate.now()+"' THEN 0 WHEN start_at='' THEN 1 ELSE 2 END,start_at")+" LIMIT 150";try(Cursor c=getReadableDatabase().rawQuery(sql,null)){while(c.moveToNext())rows.put(new JSONObject(c.getString(0)));}return rows;}
    public synchronized JSONArray undated()throws JSONException{
        JSONArray result=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM records WHERE deleted=0 AND completed='' AND start_at='' AND kind IN ('task','event','project') ORDER BY updated_at DESC LIMIT 100",null)){while(c.moveToNext())result.put(new JSONObject(c.getString(0)));}return result;
    }
    public synchronized JSONArray pending() throws JSONException {
        JSONArray rows=new JSONArray();
        // Twenty maximal records fit the server's 2 MiB request limit, including images.
        try(Cursor c=getReadableDatabase().rawQuery("SELECT body,version FROM records WHERE dirty=1 LIMIT 20",null)) {
            while(c.moveToNext())rows.put(new JSONObject().put("record",new JSONObject(c.getString(0))).put("base_version",c.getLong(1)));
        }return rows;
    }
    public synchronized JSONArray dayTasks(LocalDate date)throws JSONException{return calendarRows(date,date.plusDays(1),false,true);}
    public synchronized JSONArray scheduled(LocalDate from,LocalDate to,boolean courses)throws JSONException{return calendarRows(from,to,courses,true);}
    public synchronized JSONArray notes(String query)throws JSONException{
        JSONArray rows=new JSONArray();String match="%"+query.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM records WHERE deleted=0 AND kind='note' AND (title LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\') ORDER BY CASE WHEN body LIKE '%\"pinned\":true%' THEN 0 ELSE 1 END,updated_at DESC LIMIT 200",new String[]{match,match})){while(c.moveToNext())rows.put(new JSONObject(c.getString(0)));}return rows;
    }
    public synchronized void materializeNotes()throws Exception{
        try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM records WHERE kind='note'",null)){while(c.moveToNext())MarkdownFiles.update(context,new JSONObject(c.getString(0)));}
    }
    public synchronized int exportMarkdownZip(java.io.OutputStream output)throws Exception{
        int count=0;try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(output);Cursor c=getReadableDatabase().rawQuery("SELECT body FROM records WHERE deleted=0 AND kind='note' ORDER BY id",null)){
            while(c.moveToNext()){JSONObject r=new JSONObject(c.getString(0));String name=MarkdownFiles.filename(r);name=name.substring(0,name.length()-3)+"-"+r.getString("id")+".md";zip.putNextEntry(new java.util.zip.ZipEntry(name));zip.write(MarkdownFiles.markdown(r).getBytes(StandardCharsets.UTF_8));zip.closeEntry();count++;}
        }return count;
    }
    public synchronized JSONArray reminderPage(int mode,int offset)throws JSONException{
        String enabled=mode==1?" AND body LIKE '%\"reminder\":true%'":mode==2?" AND body NOT LIKE '%\"reminder\":true%'":"";JSONArray rows=new JSONArray();String sql="SELECT body FROM records WHERE deleted=0 AND kind IN ('event','task','course','project') AND (start_at<>'' OR due_date<>'')"+enabled+" ORDER BY CASE WHEN start_at='' THEN due_date ELSE start_at END LIMIT 40 OFFSET "+Math.max(0,offset);try(Cursor c=getReadableDatabase().rawQuery(sql,null)){while(c.moveToNext())rows.put(new JSONObject(c.getString(0)));}return rows;
    }
    public synchronized JSONArray childrenPage(String parent,int offset)throws JSONException{JSONArray rows=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM records WHERE deleted=0 AND kind<>'type' AND parent_id=? ORDER BY start_at,title LIMIT 40 OFFSET "+Math.max(0,offset),new String[]{parent})){while(c.moveToNext())rows.put(new JSONObject(c.getString(0)));}return rows;}
    public synchronized int[] projectProgress(String parent){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*),COALESCE(SUM(CASE WHEN completed<>'' THEN 1 ELSE 0 END),0) FROM records WHERE deleted=0 AND kind<>'type' AND parent_id=? AND body NOT LIKE '%\"status\":\"cancelled\"%'",new String[]{parent})){return c.moveToFirst()?new int[]{c.getInt(0),c.getInt(1)}:new int[]{0,0};}}
    public synchronized JSONArray reminders()throws JSONException{return calendarRows(LocalDate.now(),LocalDate.now().plusDays(367),false,false);}
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
        copy.remove("tf_id");copy.remove("tf_table");copy.remove("tf_raw");if(copy.optString("kind").equals("type"))copy.put("system_type",false);
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
