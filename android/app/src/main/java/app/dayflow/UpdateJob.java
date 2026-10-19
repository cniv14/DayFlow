package app.dayflow;
import android.app.*;
import android.app.job.*;
import android.content.*;
import java.util.concurrent.*;
import org.json.*;
public final class UpdateJob extends JobService {
    private Future<?> future;private ExecutorService worker;
    public boolean onStartJob(JobParameters params){worker=Executors.newSingleThreadExecutor();future=worker.submit(()->{try{
        JSONObject info=Updates.check(this);if(info!=null){NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);manager.createNotificationChannel(new NotificationChannel("updates","版本更新",NotificationManager.IMPORTANCE_DEFAULT));
            PendingIntent open=PendingIntent.getActivity(this,702,new Intent(this,MainActivity.class).putExtra("check_update",true),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            try{manager.notify("updates",0,new Notification.Builder(this,"updates").setSmallIcon(R.drawable.ic_dayflow).setContentTitle("日栖 "+info.getString("version")+" 可更新").setContentText("点击查看并安装更新，保留本地数据").setContentIntent(open).setAutoCancel(true).build());}catch(SecurityException ignored){}
        }
    }catch(Exception ignored){}finally{jobFinished(params,false);worker.shutdown();}});return true;}
    public boolean onStopJob(JobParameters params){if(future!=null)future.cancel(true);if(worker!=null)worker.shutdownNow();return true;}
}
