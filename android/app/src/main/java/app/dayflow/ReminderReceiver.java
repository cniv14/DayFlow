package app.dayflow;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import org.json.*;
import java.time.*;
import java.util.concurrent.*;

/** OS alarms replace polling. Notifications may be delayed by Doze. */
public final class ReminderReceiver extends android.content.BroadcastReceiver {
    private static final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,20,TimeUnit.SECONDS,new LinkedBlockingQueue<>());
    static{worker.allowCoreThreadTimeOut(true);}
    private static PendingIntent pending(Context c,String id){return PendingIntent.getBroadcast(c,0,new Intent(c,ReminderReceiver.class).setData(Uri.parse("dayflow://reminder/"+id)).putExtra("id",id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    public static void refresh(Context context){Context c=context.getApplicationContext();worker.execute(()->{try{schedule(c);}catch(Exception ignored){}});}
    private static void schedule(Context c)throws Exception{
        AlarmManager manager=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);SharedPreferences p=c.getSharedPreferences("alarms",0);
        JSONArray old=new JSONArray(p.getString("ids","[]"));for(int i=0;i<old.length();i++)manager.cancel(pending(c,old.getString(i)));
        JSONArray ids=new JSONArray();JSONArray rows=Data.get(c).reminders();long now=System.currentTimeMillis();
        for(int i=0;i<rows.length()&&ids.length()<100;i++){JSONObject r=rows.getJSONObject(i);if(!r.optBoolean("reminder")||!r.optString("completed").isEmpty())continue;
            long when=LocalDateTime.parse(r.getString("start_at")).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()-r.optInt("remind_minutes",10)*60000L;
            if(when<=now)continue;manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pending(c,r.getString("id")));ids.put(r.getString("id"));
        }p.edit().putString("ids",ids.toString()).apply();
    }
    public void onReceive(Context c,Intent intent){
        PendingResult result=goAsync();worker.execute(()->{try{
            String id=intent.getStringExtra("id");if(id!=null){JSONObject r=Data.get(c).find(id);
                if(r!=null&&!r.optBoolean("deleted")&&r.optString("completed").isEmpty()&&r.optBoolean("reminder")){
                    NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
                    manager.createNotificationChannel(new NotificationChannel("schedule","日程提醒",NotificationManager.IMPORTANCE_HIGH));
                    PendingIntent open=PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
                    Notification notification=new Notification.Builder(c,"schedule").setSmallIcon(R.drawable.ic_notification).setContentTitle(r.optString("title")).setContentText(r.optString("start_at").replace("T"," ")+" "+r.optString("location")).setContentIntent(open).setAutoCancel(true).build();
                    try{manager.notify(id,0,notification);}catch(SecurityException ignored){}
                }
            }schedule(c);AgendaWidget.refresh(c);
        }catch(Exception ignored){}finally{result.finish();}});
    }
}
