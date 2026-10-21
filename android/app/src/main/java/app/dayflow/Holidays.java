package app.dayflow;
import android.content.Context;
import android.util.LruCache;
import org.json.*;
import java.io.*;
import java.time.*;
import java.util.*;

/** Small annual JSON files from NateScarlet/holiday-cn (MIT), cached offline. */
final class Holidays {
 private static Context context;private static final LruCache<Integer,Map<String,JSONObject>> cache=new LruCache<>(4);
 static void init(Context c){context=c.getApplicationContext();}
 private static synchronized Map<String,JSONObject> year(int year){Map<String,JSONObject> found=cache.get(year);if(found!=null)return found;found=new HashMap<>();if(context!=null)try{File file=new File(context.getFilesDir(),"holidays-"+year+".json");try(InputStream in=file.isFile()?new FileInputStream(file):context.getAssets().open("holidays/"+year+".json")){JSONArray days=new JSONObject(Network.read(in,128*1024)).getJSONArray("days");for(int i=0;i<days.length();i++){JSONObject d=days.getJSONObject(i);found.put(d.getString("date"),d);}}}catch(Exception ignored){}cache.put(year,found);return found;}
 static boolean covered(LocalDate date){return !year(date.getYear()).isEmpty();}
 static String label(LocalDate date){JSONObject d=year(date.getYear()).get(date.toString());return d==null?"":d.optBoolean("isOffDay")?"休":"班";}
 static String description(LocalDate date){JSONObject d=year(date.getYear()).get(date.toString());return d==null?covered(date)?"":"调休数据未覆盖该年度":d.optString("name")+" · "+(d.optBoolean("isOffDay")?"休息":"补班");}
 static boolean working(LocalDate date){JSONObject d=year(date.getYear()).get(date.toString());return d==null?date.getDayOfWeek().getValue()<6:!d.optBoolean("isOffDay");}
 static String update(Context c,int year)throws Exception{if(year<2000||year>2100)throw new IOException("不支持的年度");JSONObject data=Network.request("https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/"+year+".json",null,"");if(data.optInt("year")!=year||data.getJSONArray("days").length()>366)throw new IOException("节假日数据格式错误");JSONArray days=data.getJSONArray("days");for(int i=0;i<days.length();i++){JSONObject d=days.getJSONObject(i);if(LocalDate.parse(d.getString("date")).getYear()!=year||!(d.opt("isOffDay") instanceof Boolean))throw new IOException("无效节假日条目");}byte[] bytes=data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);if(bytes.length>128*1024)throw new IOException("节假日数据过大");File target=new File(c.getFilesDir(),"holidays-"+year+".json"),temp=new File(c.getFilesDir(),"holidays-download.tmp");java.nio.file.Files.write(temp.toPath(),bytes);java.nio.file.Files.move(temp.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);synchronized(Holidays.class){cache.remove(year);}return "已更新"+year+"年节假日／调休";}
}
