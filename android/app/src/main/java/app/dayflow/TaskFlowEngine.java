package app.dayflow;
import android.content.Context;
import android.util.Base64;
import androidx.javascriptengine.*;
import org.json.*;
import java.io.*;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/** One short-lived, isolated engine per sync. UI remains entirely native. */
final class TaskFlowEngine implements AutoCloseable {
    private final JavaScriptSandbox sandbox;private final JavaScriptIsolate isolate;
    TaskFlowEngine(Context c,byte[] cache)throws Exception{
        if(!JavaScriptSandbox.isSupported())throw new IOException("当前系统 WebView 不支持 TaskFlow 同步引擎，请更新 Android System WebView；本地功能可继续使用");
        sandbox=JavaScriptSandbox.createConnectedInstanceAsync(c.getApplicationContext()).get(15,TimeUnit.SECONDS);
        JavaScriptIsolate created=null;
        try{if(!sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_ISOLATE_MAX_HEAP_SIZE)||!sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_EVALUATE_WITHOUT_TRANSACTION_LIMIT))throw new IOException("系统 WebView 版本较旧，请更新后使用 Windows 同步");IsolateStartupParameters params=new IsolateStartupParameters();params.setMaxHeapSizeBytes(64L*1024*1024);created=sandbox.createIsolate(params);isolate=created;
            eval(asset(c,"taskflow-bootstrap.js"));byte[] entropy=new byte[8192];new SecureRandom().nextBytes(entropy);StringBuilder hex=new StringBuilder(entropy.length*2);for(byte b:entropy)hex.append(Character.forDigit((b>>>4)&15,16)).append(Character.forDigit(b&15,16));eval("installRandom("+JSONObject.quote(hex.toString())+")");eval(asset(c,"taskflow-engine.js"));call("init",Base64.encodeToString(cache,Base64.NO_WRAP));
        }catch(Exception e){if(created!=null)created.close();sandbox.close();throw e;}
    }
    private static String asset(Context c,String name)throws Exception{try(InputStream in=c.getAssets().open(name)){return Network.read(in,2*1024*1024);}}
    String eval(String source)throws Exception{try{return isolate.evaluateJavaScriptAsync(source).get(15,TimeUnit.SECONDS);}catch(java.util.concurrent.ExecutionException e){throw new IOException("Windows 同步数据处理失败："+(e.getCause()==null?e.getMessage():e.getCause().getMessage()),e);}}
    String call(String method,String... args)throws Exception{StringBuilder code=new StringBuilder("DayFlowBridge.").append(method).append('(');for(int i=0;i<args.length;i++){if(i>0)code.append(',');code.append(JSONObject.quote(args[i]));}return eval(code.append(')').toString());}
    JSONObject page(String table,int offset)throws Exception{return new JSONObject(eval("DayFlowBridge.page("+JSONObject.quote(table)+","+offset+",50)"));}
    JSONObject row(String table,String id)throws Exception{String value=call("get",table,id);return value.equals("null")?null:new JSONObject(value);}
    void apply(String table,String id,JSONObject fields)throws Exception{call("apply",new JSONArray().put(new JSONObject().put("table",table).put("id",id).put("fields",fields)).toString());}
    byte[] save()throws Exception{String value=call("save");if(value.length()>24*1024*1024)throw new IOException("Windows 工作区超过本机16MiB安全缓存上限");byte[] bytes=Base64.decode(value,Base64.NO_WRAP);if(bytes.length>16*1024*1024)throw new IOException("Windows 工作区过大");return bytes;}
    public void close(){try{isolate.close();}finally{sandbox.close();}}
}
