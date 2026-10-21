package app.dayflow;
import android.app.Notification;import android.os.Bundle;import android.service.notification.*;import androidx.core.app.NotificationCompat;import java.util.concurrent.*;
/** Event driven, single bounded worker. No background LLM, polling or notification history scan. */
public final class CaptureService extends NotificationListenerService {
 private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),new ThreadPoolExecutor.DiscardPolicy());
 public CaptureService(){worker.allowCoreThreadTimeOut(true);}
 public void onNotificationPosted(StatusBarNotification sbn){try{if(!CaptureRules.enabled(this))return;Notification n=sbn.getNotification();if((n.flags&(Notification.FLAG_GROUP_SUMMARY|Notification.FLAG_ONGOING_EVENT))!=0||n.extras==null)return;String pkg=sbn.getPackageName();if(pkg.equals(getPackageName())||pkg.startsWith("app.dayflow."))return;worker.execute(()->{try{
  Bundle extras=n.extras;String title=String.valueOf(extras.getCharSequence(Notification.EXTRA_TITLE,"")),body=String.valueOf(extras.getCharSequence(Notification.EXTRA_BIG_TEXT,extras.getCharSequence(Notification.EXTRA_TEXT,"")));NotificationCompat.MessagingStyle style=NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n);String group="";
  if(CaptureRules.chat(pkg)){CharSequence conversation=style==null?null:style.getConversationTitle();group=conversation==null?String.valueOf(extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE,"")):conversation.toString();if(group.isEmpty()){group=title.replaceFirst("^\\[\\d+条\\]\\s*","").trim();int left=Math.max(group.lastIndexOf('('),group.lastIndexOf('（'));if(left>=0&&(group.endsWith(")")||group.endsWith("）")))group=group.substring(left+1,group.length()-1);}}
  CaptureRules.discover(this,pkg,group);if(!CaptureRules.app(this,pkg)||!CaptureRules.group(this,pkg,group))return;
  // When SMS broadcasts are enabled, avoid collecting the same SMS again as a notification.
  if(SmsCapture.isSmsApp(this,pkg)&&CaptureRules.prefs(this).getBoolean("capture_sms",false)&&checkSelfPermission(android.Manifest.permission.RECEIVE_SMS)==android.content.pm.PackageManager.PERMISSION_GRANTED)return;
  if(style!=null&&!style.getMessages().isEmpty()){java.util.List<NotificationCompat.MessagingStyle.Message> batch=style.getMessages();for(int i=Math.max(0,batch.size()-20);i<batch.size();i++){NotificationCompat.MessagingStyle.Message item=batch.get(i);String text=String.valueOf(item.getText());if(text.equals("null")||text.trim().isEmpty())continue;CaptureRules.save(this,pkg,group,String.valueOf(item.getSender()),group.isEmpty()?title:group,text,Long.toString(item.getTimestamp()));}}
  else{if(body.isEmpty()){CharSequence[] lines=extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);if(lines!=null)body=android.text.TextUtils.join("\n",lines);}CaptureRules.save(this,pkg,group,"",title,title+"\n"+body,"");}
 }catch(Exception ignored){/* Never log private notification text. */}});}catch(RuntimeException ignored){}}
 public void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
