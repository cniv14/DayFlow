package app.dayflow;
import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.widget.*;
import com.google.android.material.materialswitch.MaterialSwitch;
import org.json.*;
import java.util.regex.*;
import java.nio.charset.StandardCharsets;

/** Local rule extraction only. No SMS history access, cloud upload or payment actions. */
public final class SmsCapture extends BroadcastReceiver {
 static String category(String body){if(body.length()>12000)return "";if(body.matches("(?s).*(快递|包裹|驿站|菜鸟|丰巢|取件|提货).*")&&code(body).length()>0)return "parcel";if(body.matches("(?s).*(话费|通信费|停机|中国移动|中国联通|中国电信).*")&&body.matches("(?s).*(欠费|缴费|缴纳|余额不足|余额低于|即将停机|充值提醒|费用到期|账单已出).*")&&!body.matches("(?s).*(验证码|动态密码).*(切勿|请勿|登录|登陆).*"))return "phone_bill";return "";}
 static String code(String body){Matcher m=Pattern.compile("(?:取件码|提货码|取货码|取件密码|凭码)[\\s:：为是]*[“\"「『【（(]?([A-Za-z0-9][A-Za-z0-9-]{2,23})(?![A-Za-z0-9-])").matcher(body);return m.find()?m.group(1):"";}
 static JSONObject enrich(JSONObject r,String body)throws Exception{String kind=category(body);if(kind.isEmpty())return r;r.put("capture_type",kind).put("confirmation_status","pending").put("proposed_kind","task");if(kind.equals("parcel")){String code=code(body);r.put("pickup_code",code).put("title","快递待取 · "+code).put("time_hint","待取件 · 核对地址与截止时间");}else{Matcher amount=Pattern.compile("(?:欠费|应缴|需缴|待缴|账单金额)[：:\\s为]*(\\d+(?:\\.\\d{1,2})?)\\s*元").matcher(body);String value=amount.find()?amount.group(1):"";r.put("bill_amount",value).put("title","话费缴费提醒"+(value.isEmpty()?"":" · "+value+"元")).put("time_hint","待确认缴费 · 不自动支付");}return r;}
 static boolean isSmsApp(Context c,String pkg){String selected=Telephony.Sms.getDefaultSmsPackage(c);return pkg.equals(selected)||java.util.Arrays.asList("com.google.android.apps.messaging","com.android.mms","com.samsung.android.messaging").contains(pkg);}
 static void save(Context c,String sender,String body)throws Exception{String pkg=Telephony.Sms.getDefaultSmsPackage(c);if(pkg==null)pkg="android.sms";CaptureRules.discover(c,pkg,"");CaptureRules.save(c,pkg,"",sender,"短信 · "+sender,body,"");}
 public void onReceive(Context c,Intent intent){if(!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())||!CaptureRules.enabled(c)||!c.getSharedPreferences("settings",0).getBoolean("capture_sms",false))return;PendingResult pending=goAsync();new Thread(()->{try{SmsMessage[] messages=Telephony.Sms.Intents.getMessagesFromIntent(intent);StringBuilder body=new StringBuilder();String sender="";for(SmsMessage message:messages){body.append(message.getMessageBody());sender=message.getOriginatingAddress();if(body.length()>12000)return;}save(c,sender,body.toString());}catch(Exception ignored){}finally{pending.finish();}},"sms-capture").start();}
 static void settings(MainActivity a){GroupCaptureSettings.show(a);}
 static void details(MainActivity a,LinearLayout page,JSONObject r){if(!r.optString("pickup_code").isEmpty()){TextView code=Ui.text(a,r.optString("pickup_code"),30,true);page.addView(code);page.addView(Ui.button(a,"复制取件码",true,()->{((ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("取件码",r.optString("pickup_code")));a.message("已复制取件码");}));}}
}
