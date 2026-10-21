package app.dayflow;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;

/** Pinned RSA public key; SHA-256-checked packages; OS always confirms installation. */
public final class Updates {
    public static final String DEFAULT_FEED="https://github.com/cniv14/DayFlow/releases/latest/download/android-update.json";
    private static final String CACHE="dayflow-update.apk";
    private static final String PENDING="update_pending_path";
    public static String feed(Context c){return c.getSharedPreferences("settings",0).getString("update_feed",DEFAULT_FEED);}
    private static HttpURLConnection open(String value)throws Exception{
        String current=value;for(int i=0;i<6;i++){
            URI transport=new URI(current);if(transport.getRawUserInfo()!=null||transport.getRawFragment()!=null)throw new Exception("无效更新地址");Network.endpoint(transport.getScheme()+"://"+transport.getRawAuthority()+transport.getRawPath(),true);HttpURLConnection connection=(HttpURLConnection)new URL(current).openConnection();
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(30000);connection.setRequestProperty("User-Agent","DayFlow/0.1");
            int status=connection.getResponseCode();if(status>=300&&status<400){String location=connection.getHeaderField("Location");connection.disconnect();if(location==null)throw new Exception("无效更新跳转");String next=new URL(new URL(current),location).toString();if(current.startsWith("https:")&&!next.startsWith("https:"))throw new Exception("更新地址不允许降级 HTTP");current=next;continue;}
            if(status!=200){connection.disconnect();throw new Exception(status==404?"尚未发布更新清单，当前安装包可继续使用":"更新服务 HTTP "+status);}return connection;
        }throw new Exception("更新跳转次数过多");
    }
    public static JSONObject verify(Context c,JSONObject envelope)throws Exception{
        byte[] payload=Base64.decode(envelope.getString("payload"),Base64.NO_WRAP),signature=Base64.decode(envelope.getString("signature"),Base64.NO_WRAP);
        String pem;try(InputStream in=c.getResources().openRawResource(R.raw.update_public)){pem=Network.read(in,8192);}
        PublicKey key=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.decode(pem.replaceAll("-----[^-]+-----|\\s",""),Base64.NO_WRAP)));
        Signature verifier=Signature.getInstance("SHA256withRSA");verifier.initVerify(key);verifier.update(payload);if(!verifier.verify(signature))throw new Exception("更新清单签名无效，已拒绝更新");
        JSONObject info=new JSONObject(new String(payload,StandardCharsets.UTF_8));
        if(info.getInt("schema")!=1||!"android".equals(info.getString("platform"))||!info.getString("sha256").matches("[a-f0-9]{64}")||info.getLong("size")<=0||info.getLong("size")>200L*1024*1024)throw new Exception("无效更新清单");
        Network.endpoint(info.getString("url"),true);return info;
    }
    public static JSONObject check(Context c)throws Exception{
        HttpURLConnection connection=open(feed(c));try(InputStream in=connection.getInputStream()){JSONObject info=verify(c,new JSONObject(Network.read(in,65536)));
            int installed=c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionCode;return info.getInt("version_code")>installed?info:null;
        }finally{connection.disconnect();}
    }
    public static void configureJob(Context c){JobScheduler scheduler=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);scheduler.cancel(701);
        if(c.getSharedPreferences("settings",0).getBoolean("update_auto",false))scheduler.schedule(new JobInfo.Builder(701,new ComponentName(c,UpdateJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setPeriodic(12L*60*60*1000).build());
    }
    public static boolean canInstall(Context c){return c.getPackageManager().canRequestPackageInstalls();}
    private static String sha256(File file)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream input=new FileInputStream(file)){byte[] buffer=new byte[32768];int n;while((n=input.read(buffer))!=-1)digest.update(buffer,0,n);}
        StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return hash.toString();
    }
    /** Returns a verified APK already in the cache, so a permission detour never downloads it twice. */
    public static String cached(Context c,JSONObject info)throws Exception{
        File file=new File(c.getCacheDir(),CACHE);
        if(!file.isFile()||file.length()!=info.getLong("size")||!sha256(file).equals(info.getString("sha256")))return null;
        android.content.pm.PackageInfo apk=c.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),0);
        if(apk==null||!c.getPackageName().equals(apk.packageName)||apk.versionCode!=info.getInt("version_code"))return null;
        return file.getAbsolutePath();
    }
    public static String download(Context c,JSONObject info)throws Exception{
        String existing=cached(c,info);if(existing!=null)return existing;
        File file=new File(c.getCacheDir(),CACHE),temp=new File(c.getCacheDir(),CACHE+".part");HttpURLConnection connection=open(info.getString("url"));MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0;
        try(InputStream in=connection.getInputStream();OutputStream out=new FileOutputStream(temp)){
            byte[] bytes=new byte[32768];int n;while((n=in.read(bytes))!=-1){total+=n;if(total>info.getLong("size"))throw new Exception("更新包长度不符");digest.update(bytes,0,n);out.write(bytes,0,n);}
        }catch(Exception error){temp.delete();throw error;}finally{connection.disconnect();}
        StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        if(total!=info.getLong("size")||!hash.toString().equals(info.getString("sha256"))){temp.delete();throw new Exception("更新包 SHA-256 校验失败");}
        android.content.pm.PackageInfo apk=c.getPackageManager().getPackageArchiveInfo(temp.getAbsolutePath(),0);
        if(apk==null||!c.getPackageName().equals(apk.packageName)||apk.versionCode!=info.getInt("version_code")){temp.delete();throw new Exception("更新包应用身份或版本不匹配");}
        try{Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}catch(Exception error){temp.delete();throw new Exception("无法保存更新包",error);}
        return file.getAbsolutePath();
    }
    public static void install(Activity activity,String path)throws Exception{
        if(!canInstall(activity)){
            activity.getSharedPreferences("settings",0).edit().putString(PENDING,path).apply();
            android.widget.Toast.makeText(activity,"更新已下载。开启“安装未知应用”后返回日栖，会自动继续安装。",android.widget.Toast.LENGTH_LONG).show();
            try{activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));}
            catch(Exception error){throw new Exception("未找到安装授权设置，请在系统设置中允许日栖安装未知应用",error);}
            return;
        }
        activity.getSharedPreferences("settings",0).edit().remove(PENDING).apply();
        PackageInstaller installer=activity.getPackageManager().getPackageInstaller();PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);params.setAppPackageName(activity.getPackageName());
        int sessionId=installer.createSession(params);try(PackageInstaller.Session session=installer.openSession(sessionId)){
            try(InputStream input=new FileInputStream(path);OutputStream out=session.openWrite("base.apk",0,new File(path).length())){byte[] buffer=new byte[32768];int n;while((n=input.read(buffer))!=-1)out.write(buffer,0,n);session.fsync(out);}
            Intent callback=new Intent(activity,UpdateInstaller.class).setAction("app.dayflow.INSTALL_RESULT");
            int flags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
            session.commit(PendingIntent.getBroadcast(activity,sessionId,callback,flags).getIntentSender());
        }
    }
    /** Called from Activity.onResume after the user returns from the unknown-sources settings page. */
    public static boolean resumePending(Activity activity){
        String path=activity.getSharedPreferences("settings",0).getString(PENDING,"");
        if(path.isEmpty()||!canInstall(activity))return false;
        File file=new File(path);if(!file.isFile()){activity.getSharedPreferences("settings",0).edit().remove(PENDING).apply();return false;}
        activity.getSharedPreferences("settings",0).edit().remove(PENDING).apply();
        try{install(activity,path);return true;}catch(Exception error){android.widget.Toast.makeText(activity,error.getMessage()==null?"未能启动安装":error.getMessage(),android.widget.Toast.LENGTH_LONG).show();return false;}
    }
}
