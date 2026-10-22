package app.dayflow;

import android.content.*;
import android.util.AtomicFile;
import android.util.Base64;
import android.widget.LinearLayout;
import dalvik.system.InMemoryDexClassLoader;
import org.json.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Only publisher-signed executable modules. No external storage or downloaded APK installs. */
public final class HotUpdates {
    public static final String DEFAULT_FEED="https://github.com/cniv14/DayFlow/releases/latest/download/runtime-update.json";
    private static final int MAX=12*1024*1024;
    private static volatile HotModule module;private static boolean loaded;
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("settings",0);}
    private static AtomicFile file(Context c,String name){return new AtomicFile(new File(c.getFilesDir(),"runtime-"+name+".json"));}
    public static void init(Context c){
        if(prefs(c).getBoolean("hot_rendering",false))prefs(c).edit().putBoolean("hot_enabled",false).remove("hot_rendering").putString("hot_status","上次热更新异常，已恢复基础界面").commit();
        Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{if(module!=null)prefs(c).edit().putBoolean("hot_enabled",false).putString("hot_status","热更新运行异常，已停用；重新打开使用基础界面").commit();if(previous!=null)previous.uncaughtException(thread,error);});
    }
    static JSONObject validate(Context c,JSONObject envelope)throws Exception{
        JSONObject p=Updates.signedPayload(c,envelope);int base=c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionCode;
        if(p.getInt("schema")!=1||!"android-runtime".equals(p.getString("platform"))||p.getInt("api")!=1||p.getLong("version_code")<=0||p.getInt("min_app_code")>base||p.getInt("max_app_code")<base||!p.getString("entry").matches("app\\.dayflow\\.hotfix\\.[A-Za-z0-9_.$]+"))throw new IOException("热更新不兼容当前基础版本");
        byte[] dex=Base64.decode(p.getString("dex"),Base64.NO_WRAP);
        if(dex.length<112||dex.length>8*1024*1024||dex[0]!='d'||dex[1]!='e'||dex[2]!='x'||dex[3]!='\n')throw new IOException("热更新代码格式无效");
        StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(dex))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        if(!hash.toString().equals(p.getString("sha256")))throw new IOException("热更新校验失败");
        return p;
    }
    private static String read(AtomicFile f)throws Exception{try(InputStream in=f.openRead()){return Network.read(in,MAX);}}
    private static void write(AtomicFile f,String text)throws Exception{
        FileOutputStream out=null;try{out=f.startWrite();out.write(text.getBytes(StandardCharsets.UTF_8));f.finishWrite(out);}catch(Exception e){if(out!=null)f.failWrite(out);throw e;}
    }
    public static String check(Context c)throws Exception{
        SharedPreferences p=prefs(c);JSONObject envelope;
        if("server".equals(p.getString("hot_source","github"))){String token=Secrets.get(c,"token");if(token.isEmpty())throw new IOException("请先登录日栖服务端再获取热更新");envelope=Network.request(Network.serverEndpoint(p.getString("server",""))+"/v1/runtime-update",null,token);}
        else {HttpURLConnection connection=Updates.open(p.getString("hot_feed",DEFAULT_FEED));try(InputStream in=connection.getInputStream()){envelope=new JSONObject(Network.read(in,MAX));}finally{connection.disconnect();}}
        JSONObject payload=validate(c,envelope);long version=payload.getLong("version_code");String encoded=envelope.toString();if(encoded.getBytes(StandardCharsets.UTF_8).length>MAX)throw new IOException("热更新包过大");
        synchronized(HotUpdates.class){if(version<=p.getLong("hot_highwater",0))return "已是最新热更新；本地回退不会被自动覆盖";
            // Validate bytecode/interface before changing the working slot; no constructor runs here.
            try{Class<?> entry=new InMemoryDexClassLoader(ByteBuffer.wrap(Base64.decode(payload.getString("dex"),Base64.NO_WRAP)),c.getClassLoader()).loadClass(payload.getString("entry"));
                if(!HotModule.class.isAssignableFrom(entry))throw new IOException("热更新入口接口不匹配");entry.getConstructor();
            }catch(LinkageError error){throw new IOException("热更新模块依赖不兼容，保留当前版本",error);}
            AtomicFile current=file(c,"current");if(current.getBaseFile().isFile())write(file(c,"previous"),read(current));write(current,encoded);
            p.edit().putLong("hot_highwater",version).putString("hot_version",payload.getString("version")).putString("hot_status","已校验下载，返回主界面生效").commit();loaded=false;module=null;
        }
        return "热更新 "+payload.getString("version")+" 已就绪，无需重新安装";
    }
    public static synchronized void reset(){module=null;loaded=false;}
    public static synchronized String rollback(Context c)throws Exception{
        String previous=read(file(c,"previous"));JSONObject p=validate(c,new JSONObject(previous));write(file(c,"current"),previous);file(c,"previous").delete();prefs(c).edit().putString("hot_version",p.getString("version")).putString("hot_status","已回退；返回主界面生效").apply();reset();return "已回退热更新";
    }
    public static synchronized boolean render(MainActivity a,String screen,LinearLayout page){
        // Recovery/settings stays in the base APK even when a module is broken.
        if("settings".equals(screen)||!prefs(a).getBoolean("hot_enabled",false))return false;
        try{if(!loaded){loaded=true;if(!file(a,"current").getBaseFile().isFile())return false;JSONObject p=validate(a,new JSONObject(read(file(a,"current"))));module=(HotModule)new InMemoryDexClassLoader(ByteBuffer.wrap(Base64.decode(p.getString("dex"),Base64.NO_WRAP)),a.getClassLoader()).loadClass(p.getString("entry")).getConstructor().newInstance();}
            if(module==null)return false;prefs(a).edit().putBoolean("hot_rendering",true).commit();return module.render(a,screen,page);
        }catch(Throwable error){module=null;prefs(a).edit().putBoolean("hot_enabled",false).putString("hot_status","模块加载失败，已恢复基础界面").commit();return false;}
        finally{prefs(a).edit().remove("hot_rendering").commit();}
    }
}
