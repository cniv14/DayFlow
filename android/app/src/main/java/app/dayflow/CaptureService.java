package app.dayflow;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.*;
import org.json.*;

/** Event-driven listener; never starts an OCR engine or LLM in the background. */
public final class CaptureService extends NotificationListenerService {
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),new ThreadPoolExecutor.DiscardPolicy());
    public CaptureService(){worker.allowCoreThreadTimeOut(true);}
    public void onNotificationPosted(StatusBarNotification sbn) {
        if(!getSharedPreferences("settings",0).getBoolean("capture",false))return;
        String pkg=sbn.getPackageName();
        String allowed=getSharedPreferences("settings",0).getString("packages","com.tencent.mobileqq,com.tencent.mm,com.tencent.tim,com.google.android.gm");
        boolean match=false;for(String p:allowed.split(","))if(pkg.equals(p.trim()))match=true;if(!match)return;
        Notification n=sbn.getNotification();if((n.flags&Notification.FLAG_GROUP_SUMMARY)!=0)return;
        Bundle b=n.extras;String title=String.valueOf(b.getCharSequence(Notification.EXTRA_TITLE,""));
        String text=String.valueOf(b.getCharSequence(Notification.EXTRA_BIG_TEXT,b.getCharSequence(Notification.EXTRA_TEXT,"")));
        String combined=title+"\n"+text;
        if(combined.length()>12000||!combined.matches("(?s).*(会议|讲座|报名|截止|上课|通知|活动|明天|后天|周[一二三四五六日天]|\\d{1,2}[:：]\\d{2}|\\d{1,2}月\\d{1,2}).*"))return;
        final String content=combined;
        worker.execute(()->{try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest((pkg+":"+content).getBytes(StandardCharsets.UTF_8));
            String hash=android.util.Base64.encodeToString(digest,android.util.Base64.NO_WRAP);
            Data db=Data.get(this);if(db.seen(hash))return;
            JSONObject r=Data.make("inbox",title.trim().isEmpty()?"新通知":title);
            r.put("content",content).put("source",pkg);db.save(r);AgendaWidget.refresh(this);
        }catch(Exception ignored){ /* No content or credentials are logged. */ }});
    }
    public void onDestroy(){worker.shutdown();super.onDestroy();}
}
