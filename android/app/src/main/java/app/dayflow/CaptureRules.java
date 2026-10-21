package app.dayflow;
import android.content.*;import org.json.*;import java.util.*;import com.google.re2j.Pattern;
/** RE2/J avoids catastrophic backtracking on the notification worker. */
final class CaptureRules {
 static final String DEFAULT_PACKAGES="com.tencent.mobileqq,com.tencent.mm,com.tencent.tim,com.google.android.gm";
 static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("settings",0);}
 static boolean enabled(Context c){return prefs(c).getBoolean("capture",false);}
 static boolean app(Context c,String pkg){if(pkg.equals(c.getPackageName())||pkg.startsWith("app.dayflow."))return false;if(prefs(c).getBoolean("capture_all_apps",false))return true;return packages(c).contains(pkg);}
 static Set<String> packages(Context c){Set<String> set=new LinkedHashSet<>();for(String pkg:prefs(c).getString("packages",DEFAULT_PACKAGES).split(","))if(!pkg.trim().isEmpty())set.add(pkg.trim());return set;}
 private static final Pattern KEYWORDS=Pattern.compile("会议|讲座|报名|截止|上课|通知|活动|明天|后天|周[一二三四五六日天]|[0-9]{1,2}[:：][0-9]{2}|[0-9]{1,2}月[0-9]{1,2}");
 private static volatile RegexSet cached;private static final class RegexSet {final String include,exclude;final List<Pattern> yes=new ArrayList<>(),no=new ArrayList<>();RegexSet(String a,String b){include=a;exclude=b;for(String line:a.split("\\R"))if(!line.trim().isEmpty())yes.add(Pattern.compile(line.trim()));for(String line:b.split("\\R"))if(!line.trim().isEmpty())no.add(Pattern.compile(line.trim()));}boolean accepts(String value){for(Pattern p:no)if(p.matcher(value).find())return false;if(yes.isEmpty())return true;for(Pattern p:yes)if(p.matcher(value).find())return true;return false;}}
 static boolean text(Context c,String value){
  android.content.SharedPreferences p=prefs(c);if(value.trim().isEmpty())return false;
  if(!p.getBoolean("capture_all_messages",false)&&SmsCapture.category(value).isEmpty()&&!KEYWORDS.matcher(value).find())return false;
  try{String include=p.getString("capture_regex_include",""),exclude=p.getString("capture_regex_exclude","");RegexSet rules=cached;if(rules==null||!rules.include.equals(include)||!rules.exclude.equals(exclude)){rules=new RegexSet(include,exclude);cached=rules;}return rules.accepts(value);}catch(RuntimeException e){return false;}
 }
 static void validate(String rules){for(String row:rules.split("\\R"))if(!row.trim().isEmpty())Pattern.compile(row.trim());}
 static boolean chat(String pkg){return Arrays.asList("com.tencent.mobileqq","com.tencent.mm","com.tencent.tim").contains(pkg);}
 static boolean group(Context c,String pkg,String name){android.content.SharedPreferences p=prefs(c);String rules=p.getString("capture_groups","").trim();if(!chat(pkg)||!p.getBoolean("capture_group_filter",!rules.isEmpty()))return true;for(String row:rules.split("\\R")){String value=row.trim();if(value.equals(name)||value.equals(pkg+"|"+name)||value.equals((pkg.equals("com.tencent.mm")?"微信":pkg.equals("com.tencent.tim")?"TIM":"QQ")+"|"+name))return true;}return false;}
 static synchronized void discover(Context c,String pkg,String group){try{String label=pkg;try{label=c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(Exception ignored){}boolean changed=false;JSONObject apps=new JSONObject(prefs(c).getString("capture_known_apps","{}"));if(!apps.has(pkg)&&apps.length()<256){apps.put(pkg,label);changed=true;}JSONObject groups=new JSONObject(prefs(c).getString("capture_known_groups","{}"));String key=pkg+"|"+group;if(chat(pkg)&&!group.isEmpty()&&!groups.has(key)&&groups.length()<256){groups.put(key,new JSONObject().put("package",pkg).put("name",group).put("label",label));changed=true;}if(changed)prefs(c).edit().putString("capture_known_apps",apps.toString()).putString("capture_known_groups",groups.toString()).apply();}catch(Exception ignored){}}
 static void save(Context c,String pkg,String group,String sender,String title,String text,String timestamp)throws Exception{
  if(!enabled(c)||!app(c,pkg)||!group(c,pkg,group)||!text(c,title+"\n"+group+"\n"+sender+"\n"+text))return;
  String hash=android.util.Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest((pkg+"|"+group+"|"+sender+"|"+text+"|"+timestamp).getBytes(java.nio.charset.StandardCharsets.UTF_8)),android.util.Base64.NO_WRAP);Data db=Data.get(c);if(db.seen(hash))return;
  JSONObject record=Data.make("inbox",title.trim().isEmpty()?"新通知":title).put("content",text).put("source",pkg).put("source_group",group).put("source_sender",sender).put("confirmation_status","pending").put("captured_at",java.time.Instant.now().toString());try{long sent=Long.parseLong(timestamp);if(sent>0)record.put("source_time",java.time.Instant.ofEpochMilli(sent).toString());}catch(Exception ignored){}SmsCapture.enrich(record,text);Data.validate(record);db.save(record);AgendaWidget.refresh(c);
 }
}
