package app.dayflow;

import android.content.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Network {
    public static String endpoint(String value,boolean local) throws Exception {
        URI uri=new URI(value.trim());String host=uri.getHost();
        boolean privateHost=host!=null&&(host.equals("localhost")||host.equals("127.0.0.1")||host.equals("::1")||host.matches("10\\.\\d+\\.\\d+\\.\\d+")||host.matches("192\\.168\\.\\d+\\.\\d+")||host.matches("172\\.(1[6-9]|2[0-9]|3[01])\\.\\d+\\.\\d+"));
        if(host==null||uri.getRawUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null||
            !("https".equals(uri.getScheme())||(local&&privateHost&&"http".equals(uri.getScheme()))))
            throw new Exception("云端需使用 HTTPS；HTTP 仅允许明确填写的局域网 IP／localhost");
        return value.trim().replaceAll("/+$","");
    }
    public static String serverEndpoint(String value)throws Exception{
        String input=value.trim();if(!input.contains("://")){String host=input.split("/",2)[0].split(":",2)[0];input=(host.equals("localhost")||(host.matches("(?:127|10)\\..*")||host.startsWith("192.168.")||host.matches("172\\.(1[6-9]|2[0-9]|3[01])\\..*"))?"http://":"https://")+input;}
        String root=endpoint(input,true);for(String suffix:new String[]{"/v1/auth/login","/v1/auth/register","/v1/sync","/health","/v1"})if(root.endsWith(suffix)){root=root.substring(0,root.length()-suffix.length());break;}return root;
    }
    public static final class ApiException extends IOException{public final int status;ApiException(int code,String message){super(message);status=code;}}
    public static JSONObject request(String url,JSONObject body,String token)throws Exception{return request(url,body,token,30);}
    public static JSONObject request(String url,JSONObject body,String token,int timeoutSeconds)throws Exception{
        URL target=new URL(url);byte[] payload=body==null?null:body.toString().getBytes(StandardCharsets.UTF_8);
        try{for(int redirects=0;redirects<4;redirects++){
            HttpURLConnection connection=(HttpURLConnection)target.openConnection();connection.setConnectTimeout(15000);connection.setReadTimeout(Math.max(10,Math.min(300,timeoutSeconds))*1000);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("Accept","application/json");if(!token.isEmpty())connection.setRequestProperty("Authorization","Bearer "+token);
            try{if(payload!=null){connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setFixedLengthStreamingMode(payload.length);connection.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream out=connection.getOutputStream()){out.write(payload);}}
                int status=connection.getResponseCode();if(status==301||status==302||status==307||status==308){String location=connection.getHeaderField("Location");if(location==null)throw new ApiException(status,"服务器重定向缺少目标地址");URL next=new URL(target,location);boolean same=next.getHost().equalsIgnoreCase(target.getHost())&&next.getProtocol().equals(target.getProtocol())&&next.getPort()==target.getPort();boolean upgrade=next.getHost().equalsIgnoreCase(target.getHost())&&target.getProtocol().equals("http")&&next.getProtocol().equals("https")&&(next.getPort()==-1||next.getPort()==443);if(!same&&!upgrade)throw new ApiException(status,"服务器重定向到其他域名，请填写最终服务地址");target=next;continue;}
                if(status<200||status>=300){String message="HTTP "+status+"：请检查服务器地址、反向代理或账号信息";InputStream err=connection.getErrorStream();if(err!=null)try(err){String text=read(err,8192);try{JSONObject error=new JSONObject(text);Object reason=error.has("error")?error.opt("error"):error.opt("message");if(reason instanceof JSONObject)message=((JSONObject)reason).optString("message",message);else if(reason instanceof String)message=(String)reason;}catch(JSONException ignored){if(text.trim().startsWith("<"))message="HTTP "+status+"：服务返回网页，请确认连接的是日栖服务端";}}throw new ApiException(status,message);}
                try(InputStream input=connection.getInputStream()){String text=url.endsWith("/v1/sync")?MarkdownFiles.read(input):read(input,4*1024*1024);try{return new JSONObject(text);}catch(JSONException e){throw new IOException("服务返回了非 JSON 内容，请检查地址和反向代理配置");}}
            }finally{connection.disconnect();}
        }throw new IOException("服务器重定向次数过多，请填写最终地址");}
        catch(UnknownHostException e){throw new IOException("无法解析服务器域名，请检查地址和网络",e);}catch(SocketTimeoutException e){throw new IOException(url.contains("/chat/completions")?"AI 响应超时，可在 AI 设置增加超时时间":"服务器响应超时，请检查网络、端口和服务状态",e);}catch(ConnectException e){throw new IOException("无法连接服务器，请检查端口、服务是否启动及防火墙",e);}catch(javax.net.ssl.SSLException e){throw new IOException("HTTPS 证书或 TLS 连接失败，请检查服务器证书配置",e);}
    }
    public static String checkServer(String address)throws Exception{String base=serverEndpoint(address);JSONObject result=request(base+"/health",null,"");if(!"dayflow".equals(result.optString("service"))||result.optInt("protocol")!=1)throw new IOException("目标不是兼容的日栖同步服务");return "连接正常 · 协议 v1 · "+base;}
    public static String read(InputStream input,int max) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=input.read(buffer))!=-1){if(out.size()+n>max)throw new Exception("文件或响应超过大小限制");out.write(buffer,0,n);}return out.toString("UTF-8");
    }
    public static synchronized String sync(Context c) throws Exception {
        if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("系统已暂停同步，本地内容保留");
        if(c.getSharedPreferences("settings",0).getString("sync_backend","dayflow").equals("taskflow"))return TaskFlowSync.sync(c);
        return syncDayFlow(c);
    }
    private static String syncDayFlow(Context c) throws Exception {
        SharedPreferences p=c.getSharedPreferences("settings",0);String base=serverEndpoint(p.getString("server","")),token=Secrets.get(c,"token");
        if(token.isEmpty())throw new Exception("本地模式：请先配置服务器并登录，或使用文件同步");
        Data db=Data.get(c);int count=0,conflicts=0;
        for(int round=0;round<200;round++) {
            JSONArray batch=db.pending();Map<String,String> revisions=new HashMap<>();
            for(int i=0;i<batch.length();i++){JSONObject r=batch.getJSONObject(i).getJSONObject("record");revisions.put(r.getString("id"),r.optString("revision"));}
            JSONObject result=request(base+"/v1/sync",new JSONObject().put("cursor",p.getLong("cursor",0)).put("mutations",batch),token);
            JSONArray accepted=result.getJSONArray("accepted");for(int i=0;i<accepted.length();i++){JSONObject a=accepted.getJSONObject(i);db.ack(a.getString("id"),a.getLong("version"),revisions.get(a.getString("id")));count++;}
            JSONArray rejected=result.getJSONArray("conflicts");for(int i=0;i<rejected.length();i++){JSONObject a=rejected.getJSONObject(i);if(!a.isNull("record")){if(db.remote(a.getJSONObject("record"),a.getLong("version")))conflicts++;}}
            JSONArray changes=result.getJSONArray("changes");for(int i=0;i<changes.length();i++){JSONObject a=changes.getJSONObject(i);if(db.remote(a.getJSONObject("record"),a.getLong("version")))conflicts++;}
            p.edit().putLong("cursor",result.getLong("cursor")).commit();
            if(!result.getBoolean("has_more")&&!db.hasPending()){AgendaWidget.refresh(c);ReminderReceiver.refresh(c);String message="同步完成：上传 "+count+" 条，冲突副本 "+conflicts+" 条";p.edit().putString("sync_last",java.time.Instant.now().toString()).putString("sync_message",message).apply();return message;}
        }throw new Exception("本批同步已保存，请再次同步以继续");
    }
}
