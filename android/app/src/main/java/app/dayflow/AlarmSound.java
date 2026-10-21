package app.dayflow;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.LinearLayout;
import org.json.JSONObject;

/** A short-lived foreground service exists only while a user-requested alarm rings. */
public final class AlarmSound extends Service {
    private MediaPlayer player;private AudioManager audio;private AudioFocusRequest focus;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable timeout=()->stopSelf();
    public static boolean exact(Context c){return Build.VERSION.SDK_INT<31||((AlarmManager)c.getSystemService(ALARM_SERVICE)).canScheduleExactAlarms();}
    public static void permissions(MainActivity a){
        JSONObject draft=a.editorState==null?null:a.editorState.draft();Runnable back=()->{if(draft==null)a.home();else a.edit(draft);};LinearLayout page=a.fullPage("提醒权限",back);
        page.addView(Ui.help(a,"通知权限用于显示提醒和停止响铃按钮。精确闹钟权限用于按指定时间响铃。设备静音、勿扰及厂商后台限制仍由系统控制。"));
        page.addView(Ui.button(a,"允许通知",true,a::notificationPermission));
        page.addView(Ui.button(a,"系统通知设置",false,()->{try{a.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,a.getPackageName()));}catch(Exception e){a.message(e.getMessage());}}));
        page.addView(Ui.button(a,exact(a)?"精确闹钟已允许":"允许精确闹钟",false,()->{if(Build.VERSION.SDK_INT>=31)try{a.startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+a.getPackageName())));}catch(Exception e){a.message("请在系统特殊应用权限中开启闹钟与提醒");}}));
    }
    public IBinder onBind(Intent intent){return null;}
    public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null||"stop".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        try{
            String id=intent.getStringExtra("id");JSONObject r=Data.get(this).resolve(id,intent.getStringExtra("series_id"),intent.getStringExtra("occurrence_date"));
            if(r==null||r.optBoolean("deleted")||!r.optString("completed").isEmpty()||PlanTime.cancelled(r)||!r.optBoolean("reminder")||!java.util.Arrays.asList("alarm","both").contains(r.optString("reminder_mode"))){stopSelf();return START_NOT_STICKY;}
            NotificationManager nm=getSystemService(NotificationManager.class);NotificationChannel channel=new NotificationChannel("alarm_playback","闹钟响铃",NotificationManager.IMPORTANCE_HIGH);channel.setSound(null,null);nm.createNotificationChannel(channel);
            Intent target=new Intent(this,MainActivity.class).setData(Uri.parse("dayflow://alarm/"+id)).putExtra("record_id",id).putExtra("series_id",r.optString("series_id")).putExtra("occurrence_date",r.optString("occurrence_date"));PendingIntent open=PendingIntent.getActivity(this,0,target,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            PendingIntent stop=PendingIntent.getService(this,0,new Intent(this,AlarmSound.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE);
            Notification notification=new Notification.Builder(this,"alarm_playback").setSmallIcon(R.drawable.ic_notification).setContentTitle(r.optString("title")).setContentText(PlanTime.label(r)+" · 点停止结束响铃").setCategory(Notification.CATEGORY_ALARM).setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"停止响铃",stop).build()).build();
            if(Build.VERSION.SDK_INT>=29)startForeground(711,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);else startForeground(711,notification);
            release();AudioAttributes attributes=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();audio=getSystemService(AudioManager.class);focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes).setOnAudioFocusChangeListener(change->{if(change==AudioManager.AUDIOFOCUS_LOSS)stopSelf();}).build();
            if(audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED){Uri sound=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);if(sound==null)sound=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);player=new MediaPlayer();player.setAudioAttributes(attributes);player.setWakeMode(this,PowerManager.PARTIAL_WAKE_LOCK);player.setDataSource(this,sound);player.setLooping(true);player.setOnErrorListener((mp,what,extra)->{stopSelf();return true;});player.setOnPreparedListener(MediaPlayer::start);player.prepareAsync();}
            handler.removeCallbacks(timeout);handler.postDelayed(timeout,120000);
        }catch(Exception e){stopSelf();}
        return START_NOT_STICKY;
    }
    private void release(){if(player!=null){player.release();player=null;}if(audio!=null&&focus!=null){audio.abandonAudioFocusRequest(focus);focus=null;}}
    public void onDestroy(){handler.removeCallbacksAndMessages(null);release();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}
