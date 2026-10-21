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
    public void onNotificationPosted(StatusBarNotification sbn) {try{capture(sbn);}catch(RuntimeException ignored){/* Malformed app extras must not crash the listener. */}}
    private void capture(StatusBarNotification sbn) {
        if(!getSharedPreferences("settings",0).getBoolean("capture",false)&&!getSharedPreferences("settings",0).getBoolean("capture_sms",false))return;
        String pkg=sbn.getPackageName();
        if(SmsCapture.smsPackage(this,pkg)){Bundle extras=sbn.getNotification().extras;if(extras!=null){String sender=String.valueOf(extras.getCharSequence(Notification.EXTRA_TITLE,"")),body=String.valueOf(extras.getCharSequence(Notification.EXTRA_BIG_TEXT,extras.getCharSequence(Notification.EXTRA_TEXT,"")));worker.execute(()->{try{SmsCapture.save(this,sender,body);}catch(Exception ignored){}});}return;}
        if(!getSharedPreferences("settings",0).getBoolean("capture",false))return;
        String allowed=getSharedPreferences("settings",0).getString("packages","com.tencent.mobileqq,com.tencent.mm,com.tencent.tim,com.google.android.gm");
        boolean match=false;for(String p:allowed.split(","))if(pkg.equals(p.trim()))match=true;if(!match)return;
        Notification n=sbn.getNotification();if((n.flags&Notification.FLAG_GROUP_SUMMARY)!=0)return;
        Bundle b=n.extras;if(b==null)return;String title=String.valueOf(b.getCharSequence(Notification.EXTRA_TITLE,""));
        String text=String.valueOf(b.getCharSequence(Notification.EXTRA_BIG_TEXT,b.getCharSequence(Notification.EXTRA_TEXT,"")));
        String groups=getSharedPreferences("settings",0).getString("capture_groups","").trim();boolean chat=pkg.equals("com.tencent.mobileqq")||pkg.equals("com.tencent.mm")||pkg.equals("com.tencent.tim");String group="";if(chat&&!groups.isEmpty()){group=GroupCaptureSettings.match(groups,pkg,b);if(group.isEmpty())return;}
        String combined=(group.isEmpty()?title:group+" · "+title)+"\n"+text;
        if(combined.length()>12000||combined.trim().isEmpty()||((group.isEmpty()||!getSharedPreferences("settings",0).getBoolean("capture_group_all",true))&&!combined.matches("(?s).*(会议|讲座|报名|截止|上课|通知|活动|明天|后天|周[一二三四五六日天]|\\d{1,2}[:：]\\d{2}|\\d{1,2}月\\d{1,2}).*")))return;
        final String content=combined,chatGroup=group;
        java.util.List<String[]> messages=new java.util.ArrayList<>();androidx.core.app.NotificationCompat.MessagingStyle style=androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n);int budget=12000;
        if(!group.isEmpty()&&style!=null){java.util.List<androidx.core.app.NotificationCompat.MessagingStyle.Message> batch=style.getMessages();for(int i=Math.max(0,batch.size()-20);i<batch.size();i++){androidx.core.app.NotificationCompat.MessagingStyle.Message message=batch.get(i);String body=String.valueOf(message.getText());if(body.trim().isEmpty()||body.equals("null"))continue;String sender=String.valueOf(message.getSender());String value=group+" · "+sender+"\n"+body;if(value.length()>4000)value=value.substring(0,4000);if(value.length()>budget)break;budget-=value.length();if(getSharedPreferences("settings",0).getBoolean("capture_group_all",true)||value.matches("(?s).*(会议|讲座|报名|截止|上课|通知|活动|明天|后天|周[一二三四五六日天]|\\d{1,2}[:：]\\d{2}).*"))messages.add(new String[]{value,Long.toString(message.getTimestamp())});}}
        if(messages.isEmpty())messages.add(new String[]{content,""});
        worker.execute(()->{try{
            for(String[] message:messages){byte[] digest=MessageDigest.getInstance("SHA-256").digest((pkg+":"+message[0]+":"+message[1]).getBytes(StandardCharsets.UTF_8));
            String hash=android.util.Base64.encodeToString(digest,android.util.Base64.NO_WRAP);
            Data db=Data.get(this);if(db.seen(hash))continue;
            JSONObject r=Data.make("inbox",!chatGroup.isEmpty()?chatGroup:title.trim().isEmpty()?"新通知":title);
            r.put("content",message[0]).put("source",pkg).put("source_group",chatGroup).put("confirmation_status","pending");db.save(r);}AgendaWidget.refresh(this);
        }catch(Exception ignored){ /* No content or credentials are logged. */ }});
    }
    public void onDestroy(){worker.shutdown();super.onDestroy();}
}
