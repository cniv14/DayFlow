package app.dayflow;
import android.content.*;
import android.os.Build;
import org.json.*;
import java.time.*;
import java.util.*;

public final class PlannerServices {
    public static String login(Context c,String address,String username,String password,String invite,boolean register)throws Exception{
        SharedPreferences p=c.getSharedPreferences("settings",0);String base=Network.serverEndpoint(address),identity=base+"|"+username.trim().toLowerCase(Locale.ROOT),binding=p.getString("binding","");
        if(!binding.isEmpty()&&!binding.equals(identity))throw new Exception("当前本地数据已绑定其他账号，请先导出备份后使用独立数据目录。");
        JSONObject result=Network.request(base+"/v1/auth/"+(register?"register":"login"),new JSONObject().put("username",username).put("password",password).put("invite_code",invite).put("device_id",p.getString("device_id","")).put("device_name",Build.MANUFACTURER+" "+Build.MODEL),"");
        String old=p.getString("user_id","");if(!old.isEmpty()&&!old.equals(result.getString("user_id")))throw new Exception("服务器账号身份发生变化，请先备份数据");
        Secrets.put(c,"token",result.getString("token"));p.edit().putString("server",base).putString("username",username.trim().toLowerCase(Locale.ROOT)).putString("binding",identity).putString("user_id",result.getString("user_id")).putString("sync_backend","dayflow").putBoolean("sync_auto",true).commit();SyncJob.configure(c);return "登录成功，已开启全部内容自动同步";
    }
    public static String weather(Context c,double lat,double lon,String city)throws Exception{
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||lat< -90||lat>90||lon< -180||lon>180)throw new Exception("无效位置");lat=Math.round(lat*100)/100.0;lon=Math.round(lon*100)/100.0;
        JSONObject current=Network.request("https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon+"&current=temperature_2m,weather_code&timezone=auto",null,"").getJSONObject("current");int code=current.getInt("weather_code");
        String sky=code==0?"晴":code<=3?"多云":code<=48?"雾":code<=67?"雨":code<=77?"雪":code<=82?"阵雨":code<=86?"阵雪":"雷雨",value=sky+"  "+Math.round(current.getDouble("temperature_2m"))+"°";
        c.getSharedPreferences("settings",0).edit().putString("lat",Double.toString(lat)).putString("lon",Double.toString(lon)).putString("city",city).putString("weather_cache",value).putString("weather_time",LocalDateTime.now().toString()).putLong("weather_updated",System.currentTimeMillis()).apply();try{Forecast.refresh(c);}catch(Exception ignored){}AgendaWidget.refresh(c);return value;
    }
}
