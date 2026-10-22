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
    private Data(Context c) { super(c, "dayflow.db", null, 5); context=c;setWriteAheadLoggingEnabled(true); }
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE records(id TEXT PRIMARY KEY,kind TEXT,title TEXT,start_at TEXT,deleted INTEGER,dirty INTEGER,version INTEGER DEFAULT 0,body TEXT,updated_at TEXT DEFAULT '',completed TEXT DEFAULT '',end_at TEXT DEFAULT '',due_date TEXT DEFAULT '',parent_id TEXT DEFAULT '',type_id TEXT DEFAULT '',recurrence TEXT DEFAULT '',preview TEXT)");
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
        if(oldV<5){db.execSQL("ALTER TABLE records ADD COLUMN preview TEXT");db.execSQL("UPDATE records SET preview=body");}
    }
    private static void bridgeTables(SQLiteDatabase db){for(String name:new String[]{"bridge_base","bridge_stage"})db.execSQL("CREATE TABLE IF NOT EXISTS "+name+"(bridge TEXT NOT NULL,id TEXT NOT NULL,body TEXT NOT NULL,PRIMARY KEY(bridge,id))");}
    public synchronized JSONArray bridgeRows(String kind,int offset)throws JSONException{
        String sql="SELECT preview AS body FROM records"+(kind==null?"":" WHERE kind=?")+" ORDER BY id LIMIT 50 OFFSET "+Math.max(0,offset);JSONArray rows=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery(sql,kind==null?null:new String[]{kind})){while(c.moveToNext())rows.put(expanded(new JSONObject(c.getString(0))));}return rows;
    }
    public synchronized JSONObject bridgeBase(String bridge,String id,boolean staged)throws JSONException{String[] args={bridge,id};if(staged){JSONObject r=readObject("bridge_stage","bridge=? AND id=?",args);if(r!=null)return r;}return readObject("bridge_base","bridge=? AND id=?",args);}
    public synchronized void bridgeStage(String bridge,String id,JSONObject baseline){ContentValues values=new ContentValues();values.put("bridge",bridge);values.put("id",id);values.put("body",baseline.toString());getWritableDatabase().insertWithOnConflict("bridge_stage",null,values,SQLiteDatabase.CONFLICT_REPLACE);}
    public synchronized void bridgeBegin(String bridge){getWritableDatabase().delete("bridge_stage","bridge=?",new String[]{bridge});}
    public synchronized void bridgeCommit(String bridge){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.execSQL("INSERT OR REPLACE INTO bridge_base SELECT bridge,id,body FROM bridge_stage WHERE bridge=?",new Object[]{bridge});db.delete("bridge_stage","bridge=?",new String[]{bridge});db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public synchronized void bridgeSave(JSONObject r,String expectedRevision)throws JSONException{JSONObject current=find(r.optString("id"));if(current!=null&&!current.optString("revision").equals(expectedRevision))throw new JSONException("事项正在其他页面编辑，已保留本地修改，请稍后再同步");save(r);}
    public synchronized void bridgeConflict(JSONObject r)throws JSONException{conflictCopy(r);}
    public static JSONObject make(String kind,String title) throws JSONException {if(kind.equals("note")&&title.trim().isEmpty())title=java.time.LocalDate.now().toString();
        return new JSONObject().put("id",UUID.randomUUID().toString()).put("kind",kind).put("title",title)
            .put("content","").put("start_at","").put("end_at","").put("time_hint","")
            .put("completed","").put("deleted",false).put("revision",UUID.randomUUID().toString())
            .put("ancestors",new JSONArray()).put("updated_at",Instant.now().toString());
    }
    public static void validate(JSONObject r) throws Exception {
        UUID.fromString(r.getString("id")); UUID.fromString(r.getString("revision"));
        if (!Arrays.asList("task","event","note","inbox","course","project","type").contains(r.getString("kind"))) throw new Exception("未知事项类型");
        if (r.getString("title").length()>1000) throw new Exception("事项过大");
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
    public synchronized JSONObject find(String id)throws JSONException{return readObject("records","id=?",new String[]{id});}
    private JSONObject readObject(String table,String where,String[] args)throws JSONException{StringBuilder body=new StringBuilder();appendBody(table,where,args,body);return body.length()==0?null:new JSONObject(body.toString());}
    private void appendBody(String table,String where,String[] args,Appendable out)throws JSONException{int offset=1;try{while(true){String[] parameters=new String[args.length+1];parameters[0]=Integer.toString(offset);System.arraycopy(args,0,parameters,1,args.length);String part;try(Cursor c=getReadableDatabase().rawQuery("SELECT substr(body,?,32768) FROM "+table+" WHERE "+where,parameters)){if(!c.moveToFirst())return;part=c.getString(0);}if(part==null||part.isEmpty())return;out.append(part);int points=part.codePointCount(0,part.length());if(points<32768)return;offset+=points;}}catch(java.io.IOException e){throw new JSONException("无法写入文档："+e.getMessage());}}
    private static JSONObject preview(JSONObject r)throws JSONException{JSONObject light=new JSONObject();for(java.util.Iterator<String> keys=r.keys();keys.hasNext();){String key=keys.next();Object value=r.get(key);if(value instanceof String&&((String)value).length()>2048)value=((String)value).substring(0,2048);else if((value instanceof JSONObject||value instanceof JSONArray)&&value.toString().length()>16384)continue;light.put(key,value);}return light.put("_dayflow_preview",true);}
    public synchronized JSONObject expanded(JSONObject r)throws JSONException{if(!r.optBoolean("_dayflow_preview"))return r;JSONObject full=resolve(r.getString("id"),r.optString("series_id"),r.optString("occurrence_date"));if(full==null)throw new JSONException("记录已不存在");return full;}
    private void expandForWrite(JSONObject r)throws JSONException{if(!r.optBoolean("_dayflow_preview"))return;JSONObject full=resolve(r.getString("id"),r.optString("series_id"),r.optString("occurrence_date"));if(full==null)throw new JSONException("原记录已不存在");if(!full.optString("revision").equals(r.optString("revision")))throw new JSONException("记录已更新，请刷新后重试");JSONObject before=preview(full);for(java.util.Iterator<String> keys=r.keys();keys.hasNext();){String key=keys.next();if(key.equals("_dayflow_preview"))continue;Object value=r.get(key),old=before.opt(key);if(!Objects.equals(String.valueOf(value),String.valueOf(old)))full.put(key,value);}r.remove("_dayflow_preview");for(java.util.Iterator<String> keys=full.keys();keys.hasNext();){String key=keys.next();r.put(key,full.get(key));}}
    private void put(JSONObject r,boolean dirty,long version) throws JSONException {
        ContentValues v=new ContentValues();v.put("id",r.getString("id"));v.put("kind",r.getString("kind"));
        v.put("title",r.getString("title"));v.put("start_at",r.optString("start_at"));v.put("deleted",r.optBoolean("deleted")?1:0);
        v.put("dirty",dirty?1:0);v.put("version",version);String encoded=r.toString();v.put("body",encoded);v.put("preview",encoded.length()>32768?preview(r).toString():encoded);
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
        expandForWrite(r);
        JSONObject old=find(r.getString("id"));JSONObject undoBefore=old;if(undoBefore==null&&r.optBoolean("deleted")&&!r.optString("series_id").isEmpty())undoBefore=resolve(r.getString("id"),r.optString("series_id"),r.optString("occurrence_date"));
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
        boolean remember=r.optBoolean("_undo_record")||(undoBefore!=null&&!undoBefore.optBoolean("deleted")&&r.optBoolean("deleted"));boolean skip=r.optBoolean("_undo_skip");String source=r.optString("_undo_source");r.remove("_undo_record");r.remove("_undo_source");r.remove("_undo_skip");
        if(remember&&!skip)try{UndoHistory.record(context,undoBefore,r,source);}catch(Exception e){throw new JSONException("未能保存撤销记录，操作未执行："+e.getMessage());}
        r.put("updated_at",Instant.now().toString());put(r,true,version(r.getString("id")));
    }
    public synchronized JSONArray inboxPage(int offset) throws JSONException {
        JSONArray rows=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT preview FROM records WHERE deleted=0 AND kind='inbox' ORDER BY updated_at DESC,id LIMIT 50 OFFSET ?",new String[]{Integer.toString(Math.max(0,offset))})){while(c.moveToNext())rows.put(new JSONObject(c.getString(0)));}return rows;
    }
    public synchronized JSONArray list(String kind,int limit) throws JSONException {
        JSONArray rows=new JSONArray();String sql="SELECT preview AS body FROM records WHERE deleted=0";
        String[] args=null;
        if(kind!=null){sql+=" AND kind=?";args=new String[]{kind};}
        sql+=("note".equals(kind)||"inbox".equals(kind)?" ORDER BY updated_at DESC":" ORDER BY CASE WHEN start_at='' THEN 1 ELSE 0 END,start_at,title")+" LIMIT "+Math.min(500,limit);
        try(Cursor c=getReadableDatabase().rawQuery(sql,args)){while(c.moveToNext())rows.put(new JSONObject(c.getString(0)));}
        return rows;
    }
    public synchronized JSONArray range(LocalDate from,LocalDate to,boolean courses)throws JSONException{return calendarRows(from,to,courses,false);}
    private JSONArray calendarRows(LocalDate from,LocalDate to,boolean courses,boolean includeDone)throws JSONException{
        List<JSONObject> output=new ArrayList<>();String kinds=courses?"kind='course'":"kind IN ('task','event','course','project')";
        String sql="SELECT preview AS body FROM records WHERE deleted=0 AND "+kinds+" AND recurrence='' AND ((start_at<>'' AND end_at<>'' AND start_at<? AND end_at>?) OR (start_at<>'' AND end_at='' AND start_at<? AND start_at>=?) OR (start_at='' AND due_date>=? AND due_date<?)) ORDER BY start_at LIMIT 500";
        try(Cursor cursor=getReadableDatabase().rawQuery(sql,new String[]{to.atStartOfDay().toString(),from.atStartOfDay().toString(),to.atStartOfDay().toString(),from.atStartOfDay().toString(),from.toString(),to.toString()})){
            while(cursor.moveToNext()){JSONObject r=new JSONObject(cursor.getString(0));if(!r.optString("series_id").isEmpty()){JSONObject template=find(r.optString("series_id"));if(template!=null&&(template.optBoolean("deleted")||PlanTime.cancelled(template)||!template.optString("completed").isEmpty()))continue;}if(!PlanTime.cancelled(r)&&(includeDone||r.optString("completed").isEmpty()))output.add(r);}
        }
        try(Cursor cursor=getReadableDatabase().rawQuery("SELECT preview AS body FROM records WHERE deleted=0 AND "+kinds+" AND recurrence<>'' ORDER BY start_at LIMIT 500",null)){
            while(cursor.moveToNext()&&output.size()<1000){JSONObject series=new JSONObject(cursor.getString(0));Recurrences.expand(this,series,from,to,output,1000);}
        }
        output.removeIf(r->PlanTime.cancelled(r)||(!includeDone&&!r.optString("completed").isEmpty()));
        output.sort(Comparator.comparing(PlanTime::start).thenComparing(r->r.optString("title")));
        JSONArray rows=new JSONArray();for(JSONObject r:output)rows.put(r);return rows;
    }
    public synchronized JSONArray business(String query,String kind,int offset)throws JSONException{
        String pattern="%"+query.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        String sql="SELECT preview AS body FROM records WHERE deleted=0 AND kind<>'type' AND (title LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\' OR type_id IN (SELECT id FROM records WHERE kind='type' AND title LIKE ? ESCAPE '\\'))";
        List<String> args=new ArrayList<>(Arrays.asList(pattern,pattern,pattern));if(kind!=null){sql+=" AND kind=?";args.add(kind);}sql+=" ORDER BY updated_at DESC LIMIT 60 OFFSET "+Math.max(0,offset);
        JSONArray result=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery(sql,args.toArray(new String[0]))){while(c.moveToNext())result.put(new JSONObject(c.getString(0)));}return result;
    }
    public synchronized JSONArray related(String column,String id)throws JSONException{
        if(!Arrays.asList("parent_id","type_id").contains(column))throw new JSONException("无效关联");JSONArray result=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT preview AS body FROM records WHERE deleted=0 AND "+column+"=? ORDER BY start_at,title LIMIT 1000",new String[]{id})){while(c.moveToNext())result.put(new JSONObject(c.getString(0)));}return result;
    }
    public synchronized int mergeType(JSONObject source,JSONObject target)throws JSONException{
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();int count=0;
        try{while(true){JSONArray batch=new JSONArray();try(Cursor c=db.rawQuery("SELECT preview AS body FROM records WHERE deleted=0 AND type_id=? LIMIT 20",new String[]{source.getString("id")})){while(c.moveToNext())batch.put(new JSONObject(c.getString(0)));}if(batch.length()==0)break;for(int i=0;i<batch.length();i++){save(batch.getJSONObject(i).put("type_id",target.getString("id")));count++;}}
            save(new JSONObject(source.toString()).put("archived",true).put("merged_into",target.getString("id")));db.setTransactionSuccessful();
        }finally{db.endTransaction();}return count;
    }
    public synchronized JSONObject resolve(String id,String series,String date)throws JSONException{
        JSONObject saved=find(id);if(saved!=null)return saved;if(series==null||series.isEmpty()||date==null||date.isEmpty())return null;
        JSONObject template=find(series);if(template==null||template.optBoolean("deleted")||PlanTime.cancelled(template)||!template.optString("completed").isEmpty()||!Recurrences.series(template))return null;
        LocalDate day=LocalDate.parse(date);if(!Recurrences.matches(template,day))return null;JSONObject occurrence=Recurrences.instance(template,day);return id.equals(occurrence.optString("id"))?occurrence:null;
    }
    public synchronized JSONArray taskBoard(int mode)throws JSONException{JSONArray rows=new JSONArray();String clause=mode==2?"completed<>''":mode==1?"completed='' AND start_at=''":"completed=''";String sql="SELECT preview AS body FROM records WHERE deleted=0 AND kind IN ('task','event','course','project') AND body NOT LIKE '%\"status\":\"cancelled\"%' AND "+clause+(mode==2?" ORDER BY updated_at DESC":" ORDER BY CASE WHEN start_at>='"+LocalDate.now()+"' THEN 0 WHEN start_at='' THEN 1 ELSE 2 END,start_at")+" LIMIT 150";try(Cursor c=getReadableDatabase().rawQuery(sql,null)){while(c.moveToNext())rows.put(new JSONObject(c.getString(0)));}return rows;}
    public synchronized JSONArray undated()throws JSONException{
        JSONArray result=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT preview AS body FROM records WHERE deleted=0 AND completed='' AND start_at='' AND kind IN ('task','event','project') ORDER BY updated_at DESC LIMIT 100",null)){while(c.moveToNext())result.put(new JSONObject(c.getString(0)));}return result;
    }
    public synchronized JSONArray pending()throws JSONException{JSONArray rows=new JSONArray();long bytes=0;try(Cursor c=getReadableDatabase().rawQuery("SELECT id,version FROM records WHERE dirty=1 ORDER BY updated_at,id LIMIT 20",null)){while(c.moveToNext()){JSONObject r=find(c.getString(0));long size=r.toString().length()*3L;if(rows.length()>0&&bytes+size>512*1024)break;rows.put(new JSONObject().put("record",r).put("base_version",c.getLong(1)));bytes+=size;}}return rows;}
    public synchronized boolean hasPending(){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM records WHERE dirty=1 LIMIT 1",null)){return c.moveToFirst();}}
    public synchronized JSONArray conflictsPage(int offset)throws JSONException{JSONArray rows=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT preview FROM records WHERE deleted=0 AND body LIKE '%\"conflict_of\":%' ORDER BY updated_at DESC LIMIT 30 OFFSET "+Math.max(0,offset),null)){while(c.moveToNext())rows.put(new JSONObject(c.getString(0)));}return rows;}
    public synchronized void settleConflict(String id,String copyRevision,String originalRevision,String choice)throws Exception{SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{JSONObject copy=find(id);if(copy==null||copy.optBoolean("deleted")||!copy.optString("revision").equals(copyRevision))throw new Exception("冲突副本已变化，请刷新");String source=copy.optString("conflict_of");JSONObject original=find(source);if(!Objects.equals(original==null?"":original.optString("revision"),originalRevision))throw new Exception("原记录已更新，请重新比较");if(choice.equals("both")){copy.remove("conflict_of");copy.put("title",copy.optString("title").replaceFirst("^\\[冲突副本\\] ",""));save(copy);}else{if(choice.equals("local")){JSONObject merged=original==null?new JSONObject():new JSONObject(original.toString());for(java.util.Iterator<String> keys=copy.keys();keys.hasNext();){String key=keys.next();if(!Arrays.asList("id","revision","ancestors","updated_at","conflict_of","tf_id","tf_table","tf_raw").contains(key))merged.put(key,copy.get(key));}merged.put("id",source).put("title",copy.optString("title").replaceFirst("^\\[冲突副本\\] ","")).put("revision",original==null?UUID.randomUUID().toString():original.optString("revision")).put("ancestors",original==null?new JSONArray():original.optJSONArray("ancestors"));validate(merged);save(merged);}else if(!choice.equals("remote"))throw new Exception("无效处理方式");save(copy.put("deleted",true));}db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public synchronized JSONArray dayTasks(LocalDate date)throws JSONException{return dayTasks(date,true);}
    /** Daily focus adds active projects and floating/overdue todos without creating dated copies. */
    public synchronized JSONArray dayTasks(LocalDate date,boolean includeDone)throws JSONException{
        JSONArray scheduled=calendarRows(date,date.plusDays(1),false,includeDone);List<JSONObject> rows=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(int i=0;i<scheduled.length();i++){JSONObject r=scheduled.getJSONObject(i);if(!"waiting".equals(r.optString("activation"))&&!r.optBoolean("archived")&&seen.add(r.optString("id")))rows.add(r);}
        String sql="SELECT preview FROM records WHERE deleted=0 AND completed='' AND recurrence='' AND kind IN ('project','task','event') AND ((kind='project' AND (start_at='' OR start_at<?)) OR (start_at='' AND (due_date='' OR due_date<=?))) ORDER BY updated_at DESC LIMIT 500";
        try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{date.plusDays(1).atStartOfDay().toString(),date.toString()})){while(c.moveToNext()){JSONObject r=new JSONObject(c.getString(0));if(PlanTime.cancelled(r)||r.optBoolean("archived")||"waiting".equals(r.optString("activation"))||!r.optString("series_id").isEmpty())continue;if(seen.add(r.optString("id")))rows.add(r);}}
        rows.sort(Comparator.comparing((JSONObject r)->!r.optString("completed").isEmpty()).thenComparing(PlanTime::start,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(r->r.optString("title")));
        JSONArray result=new JSONArray();for(JSONObject r:rows)result.put(r);return result;
    }
    public synchronized JSONArray scheduled(LocalDate from,LocalDate to,boolean courses)throws JSONException{return calendarRows(from,to,courses,true);}
    public synchronized JSONArray notes(String query)throws JSONException{
        JSONArray rows=new JSONArray();String match="%"+query.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        try(Cursor c=getReadableDatabase().rawQuery("SELECT preview AS body FROM records WHERE deleted=0 AND kind='note' AND (title LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\') ORDER BY CASE WHEN body LIKE '%\"pinned\":true%' THEN 0 ELSE 1 END,updated_at DESC LIMIT 200",new String[]{match,match})){while(c.moveToNext())rows.put(new JSONObject(c.getString(0)));}return rows;
    }
    public synchronized void materializeNotes()throws Exception{
        try(Cursor c=getReadableDatabase().rawQuery("SELECT preview AS body FROM records WHERE kind='note'",null)){while(c.moveToNext())MarkdownFiles.update(context,expanded(new JSONObject(c.getString(0))));}
    }
    public synchronized int exportMarkdownZip(java.io.OutputStream output)throws Exception{
        int count=0;try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(output);Cursor c=getReadableDatabase().rawQuery("SELECT preview AS body FROM records WHERE deleted=0 AND kind='note' ORDER BY id",null)){
            while(c.moveToNext()){JSONObject r=expanded(new JSONObject(c.getString(0)));String name=MarkdownFiles.filename(r);name=name.substring(0,name.length()-3)+"-"+r.getString("id")+".md";zip.putNextEntry(new java.util.zip.ZipEntry(name));zip.write(MarkdownFiles.markdown(r).getBytes(StandardCharsets.UTF_8));zip.closeEntry();count++;}
        }return count;
    }
    public synchronized JSONArray reminderPage(int mode,int offset)throws JSONException{
        String enabled=mode==1?" AND body LIKE '%\"reminder\":true%'":mode==2?" AND body NOT LIKE '%\"reminder\":true%'":"";JSONArray rows=new JSONArray();String sql="SELECT preview AS body FROM records WHERE deleted=0 AND kind IN ('event','task','course','project') AND (start_at<>'' OR due_date<>'')"+enabled+" ORDER BY CASE WHEN start_at='' THEN due_date ELSE start_at END LIMIT 40 OFFSET "+Math.max(0,offset);try(Cursor c=getReadableDatabase().rawQuery(sql,null)){while(c.moveToNext())rows.put(new JSONObject(c.getString(0)));}return rows;
    }
    public synchronized JSONArray childrenPage(String parent,int offset)throws JSONException{JSONArray rows=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT preview AS body FROM records WHERE deleted=0 AND kind<>'type' AND parent_id=? ORDER BY start_at,title LIMIT 40 OFFSET "+Math.max(0,offset),new String[]{parent})){while(c.moveToNext())rows.put(new JSONObject(c.getString(0)));}return rows;}
    public synchronized JSONArray boardRows(String stage,int offset)throws JSONException{String filter=stage.equals("done")?"completed<>''":stage.equals("doing")?"completed='' AND body LIKE '%\"board_status\":\"doing\"%'":"completed='' AND body NOT LIKE '%\"board_status\":\"doing\"%'";JSONArray rows=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT preview AS body FROM records WHERE deleted=0 AND kind IN ('task','project') AND body NOT LIKE '%\"status\":\"cancelled\"%' AND "+filter+" ORDER BY updated_at DESC LIMIT 40 OFFSET "+Math.max(0,offset),null)){while(c.moveToNext())rows.put(new JSONObject(c.getString(0)));}return rows;}
    public synchronized JSONArray checkinRows(String project,int offset)throws JSONException{JSONArray rows=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT preview AS body FROM records WHERE deleted=0 AND kind='note' AND parent_id=? AND body LIKE '%\"checkin_for\":%' ORDER BY title DESC LIMIT 40 OFFSET "+Math.max(0,offset),new String[]{project})){while(c.moveToNext())rows.put(new JSONObject(c.getString(0)));}return rows;}
    public synchronized int[] checkinStats(String project)throws JSONException{java.util.Set<java.time.LocalDate> dates=new java.util.HashSet<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT preview AS body FROM records WHERE deleted=0 AND kind='note' AND parent_id=? AND body LIKE '%\"checkin_for\":%'",new String[]{project})){while(c.moveToNext())try{dates.add(java.time.LocalDate.parse(new JSONObject(c.getString(0)).optString("checkin_date")));}catch(java.time.DateTimeException ignored){}}int count=0;java.time.LocalDate day=dates.contains(java.time.LocalDate.now())?java.time.LocalDate.now():java.time.LocalDate.now().minusDays(1);while(dates.contains(day)){count++;day=day.minusDays(1);}return new int[]{dates.size(),count};}
    public synchronized int[] projectProgress(String parent){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*),COALESCE(SUM(CASE WHEN completed<>'' THEN 1 ELSE 0 END),0) FROM records WHERE deleted=0 AND kind IN ('task','event','course','project') AND parent_id=? AND body NOT LIKE '%\"status\":\"cancelled\"%'",new String[]{parent})){return c.moveToFirst()?new int[]{c.getInt(0),c.getInt(1)}:new int[]{0,0};}}
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
    public synchronized boolean remote(JSONObject r,long v)throws JSONException{if(r.has("_dayflow_preview"))throw new JSONException("远程记录包含设备私有字段");String id=r.getString("id");if(v<=version(id))return false;JSONObject local=find(id);boolean dirty=false;try(Cursor c=getReadableDatabase().rawQuery("SELECT dirty FROM records WHERE id=?",new String[]{id})){dirty=c.moveToFirst()&&c.getInt(0)==1;}if(local!=null&&dirty&&!local.optString("revision").equals(r.optString("revision"))){if(contains(local.optJSONArray("ancestors"),r.optString("revision"))){ContentValues values=new ContentValues();values.put("version",v);getWritableDatabase().update("records",values,"id=?",new String[]{id});return false;}if(!contains(r.optJSONArray("ancestors"),local.optString("revision"))){conflictCopy(local);put(r,false,v);return true;}}put(r,false,v);return false;}
    public synchronized boolean seen(String hash) {
        SQLiteDatabase db=getWritableDatabase();db.delete("seen","created<?",new String[]{Long.toString(System.currentTimeMillis()-7L*86400000)});
        ContentValues v=new ContentValues();v.put("hash",hash);v.put("created",System.currentTimeMillis());
        return db.insertWithOnConflict("seen",null,v,SQLiteDatabase.CONFLICT_IGNORE)==-1;
    }
    public synchronized JSONObject exportFile()throws JSONException{JSONArray records=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM records ORDER BY id",null)){while(c.moveToNext())records.put(find(c.getString(0)));}return new JSONObject().put("format","dayflow").put("schema",1).put("exported_at",Instant.now().toString()).put("records",records);}
    public synchronized void writeExport(java.io.OutputStream output)throws Exception{if(output==null)throw new java.io.IOException("无法写入文件");java.io.Writer out=new java.io.OutputStreamWriter(output,StandardCharsets.UTF_8);out.write("{\"format\":\"dayflow\",\"schema\":1,\"exported_at\":"+JSONObject.quote(Instant.now().toString())+",\"records\":[");boolean first=true;try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM records ORDER BY id",null)){while(c.moveToNext()){if(!first)out.write(',');first=false;appendBody("records","id=?",new String[]{c.getString(0)},out);}}out.write("]}");out.flush();}
    public synchronized String importStream(java.io.InputStream input)throws Exception{if(input==null)throw new java.io.IOException("无法读取文件");java.io.File temp=java.io.File.createTempFile("dayflow-import-",".json",context.getCacheDir());try{try(java.io.OutputStream out=new java.io.FileOutputStream(temp)){byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1)out.write(buffer,0,n);}FileRecords.visit(temp,r->{validate(r);if(r.has("_dayflow_preview"))throw new JSONException("文件包含设备私有字段");});SQLiteDatabase db=getWritableDatabase();db.beginTransaction();batchWrite=true;final int[] count={0};try{FileRecords.visit(temp,r->{importFile(new JSONObject().put("format","dayflow").put("schema",1).put("records",new JSONArray().put(r)));count[0]++;});db.setTransactionSuccessful();}finally{batchWrite=false;db.endTransaction();}if(count[0]>0)SyncJob.request(context);return "已逐条合并 "+count[0]+" 条，冲突内容保留";}finally{temp.delete();}}
    public synchronized String importFile(JSONObject file) throws Exception {
        if(!"dayflow".equals(file.optString("format"))||file.optInt("schema")!=1)throw new Exception("不支持的文件格式");
        JSONArray items=file.getJSONArray("records");
        for(int i=0;i<items.length();i++){validate(items.getJSONObject(i));if(items.getJSONObject(i).has("_dayflow_preview"))throw new Exception("文件包含设备私有字段");}
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
