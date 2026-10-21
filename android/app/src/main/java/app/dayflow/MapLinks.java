package app.dayflow;
import android.content.*;
import android.net.Uri;
final class MapLinks {
 static void open(MainActivity a,String location){String place=location.trim();if(place.isEmpty()){a.message("请先填写具体地点，例如城市、校区、楼栋");return;}try{Uri uri=new Uri.Builder().scheme("androidamap").authority("poi").appendQueryParameter("sourceApplication","日栖").appendQueryParameter("keywords",place).appendQueryParameter("dev","0").build();a.startActivity(new Intent(Intent.ACTION_VIEW,uri).setPackage("com.autonavi.minimap"));}catch(ActivityNotFoundException e){try{a.startActivity(new Intent(Intent.ACTION_VIEW,new Uri.Builder().scheme("https").authority("uri.amap.com").path("search").appendQueryParameter("keyword",place).appendQueryParameter("src","dayflow").appendQueryParameter("callnative","1").build()));}catch(Exception unavailable){a.message("无法打开地图，请检查是否安装地图或浏览器");}}}
}
