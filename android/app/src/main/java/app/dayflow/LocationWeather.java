package app.dayflow;
import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Foreground single-shot coarse location; no background location service. */
public final class LocationWeather {
    private final MainActivity a;private final Handler handler=new Handler(Looper.getMainLooper());private CancellationSignal cancellation;private LocationListener listener;private final AtomicBoolean done=new AtomicBoolean();
    public LocationWeather(MainActivity a){this.a=a;}
    public void request(){
        if(a.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){a.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},201);return;}
        LocationManager manager=(LocationManager)a.getSystemService(Context.LOCATION_SERVICE);done.set(false);
        Location best=null;for(String provider:manager.getProviders(true))try{Location loc=manager.getLastKnownLocation(provider);if(loc!=null&&SystemClock.elapsedRealtimeNanos()-loc.getElapsedRealtimeNanos()<30L*60*1000000000&&(best==null||loc.getElapsedRealtimeNanos()>best.getElapsedRealtimeNanos()))best=loc;}catch(SecurityException ignored){}
        if(best!=null){load(best);return;}
        if(!manager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER)||!manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){a.message("当前无法获取大致位置。请开启系统定位，或手动设置天气位置。");return;}
        a.message("正在获取当前位置…");handler.postDelayed(()->{if(done.compareAndSet(false,true)){cancel();a.message("定位暂不可用，可重试或手动设置位置");}},15000);
        if(Build.VERSION.SDK_INT>=30){cancellation=new CancellationSignal();manager.getCurrentLocation(LocationManager.NETWORK_PROVIDER,cancellation,a.getMainExecutor(),loc->{if(done.compareAndSet(false,true)){handler.removeCallbacksAndMessages(null);if(loc==null)a.message("没有获得位置，请重试或手动设置");else load(loc);}});}
        else{listener=new LocationListener(){public void onLocationChanged(Location loc){if(done.compareAndSet(false,true)){manager.removeUpdates(this);handler.removeCallbacksAndMessages(null);load(loc);}}public void onProviderEnabled(String p){}public void onProviderDisabled(String p){}public void onStatusChanged(String p,int s,Bundle b){}};manager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER,listener,Looper.getMainLooper());}
    }
    private void load(Location location){a.job(()->{String city="当前位置";if(Geocoder.isPresent()){
        java.util.concurrent.FutureTask<String> resolve=new java.util.concurrent.FutureTask<>(()->{List<Address> addresses=new Geocoder(a,Locale.CHINA).getFromLocation(location.getLatitude(),location.getLongitude(),1);if(addresses!=null&&!addresses.isEmpty()){Address address=addresses.get(0);String name=address.getLocality();if(name==null)name=address.getSubAdminArea();if(name!=null)return name;}return "当前位置";});Thread thread=new Thread(resolve,"city-name");thread.setDaemon(true);thread.start();try{city=resolve.get(3,java.util.concurrent.TimeUnit.SECONDS);}catch(Exception ignored){resolve.cancel(true);}
        }return PlannerServices.weather(a,location.getLatitude(),location.getLongitude(),city);},a::refreshWeatherUi);}
    public void cancel(){handler.removeCallbacksAndMessages(null);if(cancellation!=null)cancellation.cancel();if(listener!=null)try{((LocationManager)a.getSystemService(Context.LOCATION_SERVICE)).removeUpdates(listener);}catch(SecurityException ignored){}done.set(true);}
}
