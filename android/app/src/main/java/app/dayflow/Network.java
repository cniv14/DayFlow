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
    public static JSONObject request(String url,JSONObject body,String token) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
        connection.setConnectTimeout(15000);connection.setReadTimeout(30000);connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept","application/json");
        if(!token.isEmpty())connection.setRequestProperty("Authorization","Bearer "+token);
        try {
            if(body!=null){connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream out=connection.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int status=connection.getResponseCode();
            if(status<200||status>=300){String message="HTTP "+status;InputStream err=connection.getErrorStream();if(err!=null)try(err){JSONObject e=new JSONObject(read(err,2*1024*1024));message=e.optString("error",message);}throw new Exception(message);}
            try(InputStream input=connection.getInputStream()){return new JSONObject(read(input,4*1024*1024));}
        }finally{connection.disconnect();}
    }
    public static String read(InputStream input,int max) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=input.read(buffer))!=-1){if(out.size()+n>max)throw new Exception("文件或响应超过大小限制");out.write(buffer,0,n);}return out.toString("UTF-8");
    }
    public static String sync(Context c) throws Exception {
        SharedPreferences p=c.getSharedPreferences("settings",0);String base=endpoint(p.getString("server",""),true),token=Secrets.get(c,"token");
        if(token.isEmpty())throw new Exception("本地模式：请先配置服务器并登录，或使用文件同步");
        Data db=Data.get(c);int count=0,conflicts=0;
        for(int round=0;round<200;round++) {
            JSONArray batch=db.pending();Map<String,String> revisions=new HashMap<>();
            for(int i=0;i<batch.length();i++){JSONObject r=batch.getJSONObject(i).getJSONObject("record");revisions.put(r.getString("id"),r.optString("revision"));}
            JSONObject result=request(base+"/v1/sync",new JSONObject().put("cursor",p.getLong("cursor",0)).put("mutations",batch),token);
            JSONArray accepted=result.getJSONArray("accepted");for(int i=0;i<accepted.length();i++){JSONObject a=accepted.getJSONObject(i);db.ack(a.getString("id"),a.getLong("version"),revisions.get(a.getString("id")));count++;}
            JSONArray rejected=result.getJSONArray("conflicts");for(int i=0;i<rejected.length();i++){JSONObject a=rejected.getJSONObject(i);if(!a.isNull("record"))db.remote(a.getJSONObject("record"),a.getLong("version"));conflicts++;}
            JSONArray changes=result.getJSONArray("changes");for(int i=0;i<changes.length();i++){JSONObject a=changes.getJSONObject(i);db.remote(a.getJSONObject("record"),a.getLong("version"));}
            p.edit().putLong("cursor",result.getLong("cursor")).commit();
            if(!result.getBoolean("has_more")&&db.pending().length()==0){AgendaWidget.refresh(c);return "同步完成：上传 "+count+" 条，冲突副本 "+conflicts+" 条";}
        }throw new Exception("本批同步已保存，请再次同步以继续");
    }
}
