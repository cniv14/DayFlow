package app.dayflow;
import android.content.*;
import android.util.*;import android.util.Base64;
import dalvik.system.InMemoryDexClassLoader;
import org.json.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Built-in fallback + signed optional code patch. Shared dependency/resources live in the base. */
public final class ModuleUpdates {
 static final String[] IDS={"assistant","markdown","almanac","voice"};
 private static Context context;private static final Map<String,ClassLoader> loaders=new HashMap<>();private static volatile String active;
 static String idForPackage(String pkg){for(String id:IDS)if(("app.dayflow."+id).equals(pkg))return id;return null;}
 static void init(Context c){context=c.getApplicationContext();UpdateSources.migrate(c);String failed=UpdateSources.prefs(c).getString("module_launching","");if(!failed.isEmpty())disable(failed,"上次模块未能启动，已使用内置版本");}
 static void resumed(android.app.Activity a){String id=idForPackage(a.getClass().getPackage().getName());if(id!=null&&id.equals(active))UpdateSources.prefs(a).edit().remove("module_launching").apply();}
 static void crash(Throwable error){String id=active;for(StackTraceElement frame:error.getStackTrace()){for(String candidate:IDS)if(frame.getClassName().startsWith("app.dayflow."+candidate+".")){id=candidate;break;}if(id!=null&&!id.equals(active))break;}if(id!=null)disable(id,"模块运行异常，已回到内置版本");}
 static void disable(String id,String reason){if(context!=null)UpdateSources.prefs(context).edit().putBoolean("module_patch_"+id,false).remove("module_launching").putString("module_status_"+id,reason).commit();synchronized(loaders){loaders.remove(id);}}
 static AtomicFile file(Context c,String id,String slot){return new AtomicFile(new File(c.getFilesDir(),"modules/"+id+"-"+slot+".json"));}
 static String read(AtomicFile f)throws Exception{try(InputStream in=f.openRead()){return Network.read(in,12*1024*1024);}}
 static void write(AtomicFile f,String text)throws Exception{FileOutputStream out=null;try{out=f.startWrite();out.write(text.getBytes(StandardCharsets.UTF_8));f.finishWrite(out);}catch(Exception e){if(out!=null)f.failWrite(out);throw e;}}
 static JSONObject validate(Context c,String id,JSONObject e)throws Exception{
  JSONObject p=Updates.signedPayload(c,e);int base=c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionCode;
  if(!Arrays.asList(IDS).contains(id)||p.getInt("schema")!=1||!"android-module".equals(p.getString("platform"))||!id.equals(p.getString("id"))||p.getInt("api")!=1||p.getLong("version_code")<=0||p.getInt("min_app_code")>base||p.getInt("max_app_code")<base)throw new IOException("模块与当前基础版本不兼容");
  byte[] dex=Base64.decode(p.getString("dex"),Base64.NO_WRAP);if(dex.length<112||dex.length>8*1024*1024||dex[0]!='d'||dex[1]!='e'||dex[2]!='x'||dex[3]!='\n')throw new IOException("模块代码无效");StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(dex))hash.append(String.format(Locale.ROOT,"%02x",b&255));if(!hash.toString().equals(p.getString("sha256")))throw new IOException("模块校验失败");return p;
 }
 private static final class Loader extends ClassLoader {
  final ClassLoader delegate;Loader(Context c,String id,JSONObject p)throws Exception{super(null);String prefix="app.dayflow."+id+".";ClassLoader base=c.getClassLoader();ClassLoader parent=new ClassLoader(base){protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{if(name.startsWith(prefix)&&!name.startsWith(prefix+"R$")&&!name.equals(prefix+"R")&&!name.equals("app.dayflow.voice.NativeSpeech"))throw new ClassNotFoundException(name);return base.loadClass(name);}};delegate=new InMemoryDexClassLoader(ByteBuffer.wrap(Base64.decode(p.getString("dex"),Base64.NO_WRAP)),parent);}
  protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{return delegate.loadClass(name);}
 }
 static ClassLoader loader(String name){String id=null;for(String value:IDS)if(name.equals("app.dayflow."+value+".MainActivity"))id=value;if(id==null||context==null||!UpdateSources.prefs(context).getBoolean("module_patch_"+id,true)||!file(context,id,"current").getBaseFile().exists())return null;
  try{synchronized(loaders){ClassLoader cached=loaders.get(id);if(cached!=null){active=id;return cached;}UpdateSources.prefs(context).edit().putString("module_launching",id).commit();ClassLoader l=new Loader(context,id,validate(context,id,new JSONObject(read(file(context,id,"current")))));Class<?> entry=l.loadClass(name);if(!android.app.Activity.class.isAssignableFrom(entry))throw new IOException("模块入口不正确");loaders.put(id,l);active=id;return l;}}
  catch(Throwable e){disable(id,"模块加载失败，已使用内置版本");return null;}
 }
 static String update(Context c,String id)throws Exception{
  if(android.os.Build.VERSION.SDK_INT<28)throw new IOException("Android 8使用内置模块，请通过主包更新；模块热更新需要Android 9及以上");JSONObject envelope=UpdateSources.json(c,id+"-module.json"),p=validate(c,id,envelope);int base=c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionCode;long code=p.getLong("version_code");if(code<=Math.max(base,UpdateSources.prefs(c).getLong("module_highwater_"+id,0)))return id+" 已是最新版本";
  try{new Loader(c,id,p).loadClass("app.dayflow."+id+".MainActivity").getConstructor();}catch(LinkageError e){throw new IOException("模块依赖不兼容，保留原版本",e);}
  synchronized(loaders){AtomicFile current=file(c,id,"current");if(current.getBaseFile().exists())write(file(c,id,"previous"),read(current));write(current,envelope.toString());loaders.remove(id);UpdateSources.prefs(c).edit().putLong("module_highwater_"+id,code).putString("module_version_"+id,p.getString("version")).putString("module_status_"+id,"下次打开此功能时生效").putBoolean("module_patch_"+id,true).commit();}return id+" 更新完成，无需安装";
 }
 static String rollback(Context c,String id)throws Exception{synchronized(loaders){AtomicFile previous=file(c,id,"previous");if(previous.getBaseFile().exists()){String text=read(previous);JSONObject p=validate(c,id,new JSONObject(text));write(file(c,id,"current"),text);previous.delete();UpdateSources.prefs(c).edit().putString("module_version_"+id,p.getString("version")).putBoolean("module_patch_"+id,true).commit();loaders.remove(id);}else{disable(id,"已恢复内置版本");}return "已回退，下次打开生效";}}
}
