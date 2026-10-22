package app.dayflow;
import android.content.*;import android.database.Cursor;import android.net.Uri;import android.os.*;import org.json.*;
/** Same-signature plugin IPC only; no public HTTP listener or assistant access key. */
public final class PluginProvider extends ContentProvider {
 public boolean onCreate(){return true;}
 public Bundle call(String method,String arg,Bundle extras){Bundle result=new Bundle();long identity=-1;try{Context c=getContext();if(c.checkCallingOrSelfPermission("app.dayflow.permission.INTERNAL")!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new SecurityException("仅允许官方签名插件");identity=Binder.clearCallingIdentity();if(extras==null)throw new Exception("缺少插件参数");
  if(method.equals("commit_document")){JSONObject saved=DocumentPlugins.commit(c,extras.getString("title"),extras.getString("sha256"),extras.getString("document_token"));result.putInt("status",200);result.putString("json",saved.toString());return result;}
  if(method.startsWith("assistant_"))return AssistantPlugin.call(c,method,extras);
  throw new SecurityException("开放API已移除，请从日栖调用插件");
 }catch(SecurityException e){result.putInt("status",401);result.putString("error",e.getMessage());}catch(Exception e){result.putInt("status",400);result.putString("error",e.getMessage()==null?"插件请求失败":e.getMessage());}finally{if(identity!=-1)Binder.restoreCallingIdentity(identity);}return result;}
 public Cursor query(Uri u,String[] p,String s,String[] a,String o){throw new UnsupportedOperationException();}public String getType(Uri u){return "application/json";}public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
