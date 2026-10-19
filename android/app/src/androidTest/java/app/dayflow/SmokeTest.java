package app.dayflow;

import android.app.Instrumentation;
import android.os.Bundle;
import org.json.*;
import java.time.*;

/** Executes actual SQLite, import, Keystore and merge logic on the device. */
public final class SmokeTest extends Instrumentation {
    public void onCreate(Bundle args){super.onCreate(args);start();}
    private int checks=0;
    private void check(boolean ok,String name)throws Exception{if(!ok)throw new Exception(name);checks++;}
    public void onStart(){Bundle results=new Bundle();try{
        Data db=Data.get(getTargetContext());
        JSONObject task=Data.make("task","准备本周学习计划").put("start_at",LocalDate.now().atTime(9,30).toString()).put("content","先完成重要的事，再留一点时间给自己。");db.save(task);
        check(db.find(task.getString("id"))!=null,"offline save");
        JSONObject old=new JSONObject(task.toString());task.put("title","整理本周学习计划");db.save(task);
        JSONObject changed=db.find(task.getString("id"));check(changed.getJSONArray("ancestors").toString().contains(old.getString("revision")),"revision ancestry");
        JSONObject exported=db.exportFile();int count=exported.getJSONArray("records").length();db.importFile(exported);check(db.exportFile().getJSONArray("records").length()==count,"idempotent file merge");
        JSONObject concurrent=new JSONObject(old.toString());concurrent.put("revision",java.util.UUID.randomUUID().toString()).put("title","另一台设备的修改");
        JSONObject file=new JSONObject().put("format","dayflow").put("schema",1).put("records",new JSONArray().put(concurrent));db.importFile(file);int conflictCount=db.exportFile().getJSONArray("records").length();db.importFile(file);
        check(db.exportFile().getJSONArray("records").length()==conflictCount&&conflictCount==count+1,"stable conflict copy");
        JSONObject copy=db.find(task.getString("id"));copy.put("deleted",true);db.save(copy);check(db.find(task.getString("id")).getBoolean("deleted"),"tombstone");
        Secrets.put(getTargetContext(),"test_secret","test-only-value");check(Secrets.get(getTargetContext(),"test_secret").equals("test-only-value"),"Keystore roundtrip");
        check(!db.exportFile().toString().contains("test-only-value"),"secrets excluded from export");
        String csv="title,weekday,start_time,end_time,start_date,end_date,weeks,location\n高等数学,1,08:00,09:35,2026-10-19,2026-11-15,odd,教学楼 A201\n";
        JSONArray courses=Imports.timetable(csv);check(courses.length()==2,"odd-week timetable");check(courses.getJSONObject(1).getString("start_at").startsWith("2026-11-02"),"week arithmetic");
        check(Imports.timetable(csv).getJSONObject(0).getString("id").equals(courses.getJSONObject(0).getString("id")),"stable course ids");
        String ics="BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:demo\nSUMMARY:每周阅读\nDTSTART:20261020T160000\nDTEND:20261020T170000\nRRULE:FREQ=WEEKLY;COUNT=3\nEND:VEVENT\nEND:VCALENDAR";
        check(Imports.ics(ics).length()==3,"weekly ICS expansion");
        int resource=getContext().getResources().getIdentifier("update_test","raw",getContext().getPackageName());String envelope;
        try(java.io.InputStream in=getContext().getResources().openRawResource(resource)){envelope=Network.read(in,65536);}
        check(Updates.verify(getTargetContext(),new JSONObject(envelope)).getInt("version_code")==2,"signed update accepted");
        JSONObject tampered=new JSONObject(envelope).put("payload",android.util.Base64.encodeToString("{}".getBytes(),android.util.Base64.NO_WRAP));boolean rejected=false;
        try{Updates.verify(getTargetContext(),tampered);}catch(Exception e){rejected=true;}check(rejected,"tampered update rejected");
        JSONObject seed=Data.make("event","阅读分享会").put("start_at",LocalDate.now().atTime(15,0).toString()).put("location","图书馆 · 二楼");db.save(seed);
        db.save(Data.make("note","一个值得留下的想法").put("content","让记录更轻松，让每一天更有余地。"));
        db.save(Data.make("task","计划下一次旅行").put("time_hint","下个月，时间待定"));
        db.save(Data.make("inbox","QQ · 周五讲座通知").put("content","本周五下午三点举办学术讲座，周四晚八点报名截止。").put("source","com.tencent.mobileqq"));
        for(int i=0;i<courses.length();i++)if(db.find(courses.getJSONObject(i).getString("id"))==null)db.save(courses.getJSONObject(i));
        AgendaWidget.refresh(getTargetContext());results.putString("stream","PASS "+checks+" Android database/import/security checks\n");finish(RESULT_OK,results);
    }catch(Exception e){results.putString("stream","FAIL: "+e);finish(RESULT_CANCELED,results);}}
    private static final int RESULT_OK=-1,RESULT_CANCELED=0;
}
