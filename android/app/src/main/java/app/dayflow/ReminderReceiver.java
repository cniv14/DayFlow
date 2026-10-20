package app.dayflow;
import android.app.*;
import android.content.*;
import android.net.Uri;
import org.json.*;
import java.time.*;
import java.util.concurrent.*;

/** Bounded OS alarms with a daily horizon refresh; no resident polling service. */
public final class ReminderReceiver extends BroadcastReceiver {
    private static final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,20,TimeUnit.SECONDS,new LinkedBlockingQueue<>());static{worker.allowCoreThreadTimeOut(true);}
    private static PendingIntent pending(Context c,String key,JSONObject r,int minutes){Intent intent=new Intent(c,ReminderReceiver.class).setData(Uri.parse("dayflow://reminder/"+key));if(r!=null)intent.putExtra("id",r.optString("id")).putExtra("series_id",r.optString("series_id")).putExtra("occurrence_date",r.optString("occurrence_date")).putExtra("minutes",minutes);return PendingIntent.getBroadcast(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    public static void refresh(Context context){Context c=context.getApplicationContext();worker.execute(()->{try{schedule(c);}catch(Exception ignored){}});}
    private static void schedule(Context c)throws Exception{
        AlarmManager manager=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);SharedPreferences p=c.getSharedPreferences("alarms",0);JSONArray old=new JSONArray(p.getString("ids","[]"));for(int i=0;i<old.length();i++)manager.cancel(pending(c,old.getString(i),null,0));
        JSONArray ids=new JSONArray(),rows=Data.get(c).reminders();long now=System.currentTimeMillis();java.util.Set<String> seen=new java.util.HashSet<>();
        java.util.List<Alarm> alarms=new java.util.ArrayList<>();
        for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);if(!r.optBoolean("reminder")||!r.optString("completed").isEmpty()||PlanTime.cancelled(r))continue;int[] offsets;try{offsets=PlanOptions.offsets(r);}catch(Exception e){offsets=new int[]{10};}
            for(int minutes:offsets){long when=PlanTime.start(r).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()-minutes*60000L;String key=r.optString("id")+":"+minutes+":"+when;if(when>now&&seen.add(key))alarms.add(new Alarm(when,key,r,minutes));}
        }
        alarms.sort(java.util.Comparator.comparingLong(Alarm::when));for(Alarm alarm:alarms){if(ids.length()>=100)break;manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,alarm.when,pending(c,alarm.key,alarm.record,alarm.minutes));ids.put(alarm.key);}
        long midnight=LocalDate.now().plusDays(1).atTime(0,5).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,midnight,pending(c,"rollover",null,0));ids.put("rollover");p.edit().putString("ids",ids.toString()).apply();
    }
    private record Alarm(long when,String key,JSONObject record,int minutes){}
    public void onReceive(Context c,Intent intent){PendingResult result=goAsync();worker.execute(()->{try{String id=intent.getStringExtra("id");if(id!=null){JSONObject r=Data.get(c).resolve(id,intent.getStringExtra("series_id"),intent.getStringExtra("occurrence_date"));if(r!=null&&!r.optBoolean("deleted")&&r.optString("completed").isEmpty()&&!PlanTime.cancelled(r)&&r.optBoolean("reminder")){
            NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);manager.createNotificationChannel(new NotificationChannel("schedule","日程提醒",NotificationManager.IMPORTANCE_HIGH));Intent target=new Intent(c,MainActivity.class).setData(Uri.parse("dayflow://record/"+id)).putExtra("record_id",id).putExtra("series_id",r.optString("series_id")).putExtra("occurrence_date",r.optString("occurrence_date"));PendingIntent open=PendingIntent.getActivity(c,0,target,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            Notification notification=new Notification.Builder(c,"schedule").setSmallIcon(R.drawable.ic_notification).setContentTitle(r.optString("title")).setContentText(PlanTime.label(r)+" "+r.optString("location")).setContentIntent(open).setAutoCancel(true).build();try{manager.notify(id+":"+intent.getIntExtra("minutes",10),0,notification);}catch(SecurityException ignored){}
        }}schedule(c);AgendaWidget.refresh(c);}catch(Exception ignored){}finally{result.finish();}});}
}
