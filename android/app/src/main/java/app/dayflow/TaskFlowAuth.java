package app.dayflow;
import android.content.*;
import android.os.Build;
import android.util.Base64;
import org.json.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Password login for TaskFlow's existing challenge/proof protocol; no QR flow. */
final class TaskFlowAuth {
    static JSONObject info(String address)throws Exception{JSONObject info=Network.request(Network.serverEndpoint(address)+"/v1/info",null,"");String id=info.optString("serverId");if(info.optInt("protocol")!=1||!id.matches("[A-Za-z0-9_-]{8,128}"))throw new Exception("目标不是支持的 TaskFlow Windows 同步服务");if(info.optBoolean("requiresSetup"))throw new Exception("请先在 Windows 同步设置中设置同步密码");return info;}
    static String login(Context c,String address,String password)throws Exception{
        SharedPreferences p=c.getSharedPreferences("settings",0);String base=Network.serverEndpoint(address);JSONObject server=info(base);String pinned=p.getString("tf_server_id","");if(!pinned.isEmpty()&&!pinned.equals(server.getString("serverId")))throw new Exception("本地数据已绑定另一 Windows 工作区，请先备份再切换");if(password.length()<6||password.length()>128)throw new Exception("同步密码需要6–128个字符");
        String device=p.getString("device_id",UUID.randomUUID().toString());JSONObject start=Network.request(base+"/v1/pair/start",new JSONObject().put("deviceId",device),"");int rounds=start.getInt("iterations");if(rounds<10000||rounds>1000000||!start.getString("serverId").equals(server.getString("serverId")))throw new Exception("Windows 认证参数异常");byte[] salt=Base64.decode(start.getString("salt"),Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);if(salt.length<16||salt.length>64)throw new Exception("Windows 认证盐无效");PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt,rounds,256);byte[] key;try{key=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
        String context="taskflow-pair-v1|"+server.getString("serverId")+"|"+device+"|"+start.getString("nonce")+"|"+start.getLong("epoch");Mac hmac=Mac.getInstance("HmacSHA256");hmac.init(new SecretKeySpec(key,"HmacSHA256"));String proof=Base64.encodeToString(hmac.doFinal(context.getBytes(StandardCharsets.UTF_8)),Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);Arrays.fill(key,(byte)0);
        JSONObject result=Network.request(base+"/v1/pair/finish",new JSONObject().put("deviceId",device).put("deviceName",Build.MANUFACTURER+" "+Build.MODEL).put("platform","android").put("nonce",start.getString("nonce")).put("proof",proof),"");if(!result.getString("serverId").equals(server.getString("serverId")))throw new Exception("认证返回的服务器身份不一致");Secrets.put(c,"tf_token",result.getString("token"));p.edit().putString("tf_address",base).putString("tf_server_id",result.getString("serverId")).putLong("tf_epoch",result.getLong("epoch")).putString("sync_backend","taskflow").putBoolean("sync_auto",true).commit();SyncJob.configure(c);return "Windows 已连接，已启用自动同步";
    }
}
