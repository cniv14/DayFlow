package app.dayflow;
import android.content.*;
import org.json.*;
import java.io.*;
import java.net.*;
/** One origin setting for base APK, runtime and built-in feature modules. */
public final class UpdateSources {
 public static final String GITHUB="https://github.com/cniv14/DayFlow/releases/latest/download";
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("settings",0);}
 static boolean server(Context c){return "server".equals(prefs(c).getString("updates_source","github"));}
 static void migrate(Context c){SharedPreferences p=prefs(c);if(!p.contains("updates_source")){p.edit().putString("updates_source",p.getString("hot_source","github")).putBoolean("updates_auto",p.getBoolean("update_auto",false)||p.getBoolean("hot_auto",false)).apply();}}
 static String url(Context c,String name)throws Exception{return server(c)?Network.serverEndpoint(prefs(c).getString("server",""))+"/v1/updates/"+name:Network.endpoint(prefs(c).getString("updates_github",GITHUB),true)+"/"+name;}
 static HttpURLConnection open(Context c,String name)throws Exception{
  if(!name.matches("android-update\\.json|DayFlow-android\\.apk|runtime-update\\.json|voice-engine\\.json|voice-engine-(arm64|armv7)\\.zip|voice-model\\.(json|bin)|(assistant|markdown|almanac|voice)-module\\.json"))throw new IOException("未知更新资源");
  String target=url(c,name);if(!server(c))return Updates.open(target);
  String token=Secrets.get(c,"token");if(token.isEmpty())throw new IOException("请先登录日栖服务端，再获取更新");
  HttpURLConnection connection=(HttpURLConnection)new URL(target).openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(30000);connection.setRequestProperty("Authorization","Bearer "+token);
  int code=connection.getResponseCode();if(code!=200){String message=code==401?"登录已过期，请重新登录":code==429?"该账号更新请求已达限制，请稍后再试":code==404?"服务端尚未发布此更新":"更新服务 HTTP "+code;connection.disconnect();throw new IOException(message);}return connection;
 }
 static JSONObject json(Context c,String name)throws Exception{HttpURLConnection connection=open(c,name);try(InputStream in=connection.getInputStream()){return new JSONObject(Network.read(in,12*1024*1024));}finally{connection.disconnect();}}
}
