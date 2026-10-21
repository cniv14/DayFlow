package app.dayflow;

import android.content.*;
import android.widget.*;
import com.google.android.material.chip.*;
import com.google.android.material.textfield.TextInputEditText;
import org.json.*;

/** Official Chat Completions presets. Keys are provided by the user, never bundled. */
public final class AiPresets {
    public static JSONObject request(Context context,String endpoint,String model,JSONArray messages)throws JSONException{
        JSONObject body=new JSONObject().put("model",model).put("messages",messages).put("stream",false);
        if(endpoint.equals("https://api.deepseek.com")&&(model.equals("deepseek-flash")||model.equals("deepseek-v4-pro")))body.put("thinking",new JSONObject().put("type","disabled"));
        if(endpoint.equals("https://api.moonshot.cn/v1")&&model.equals("kimi-k2.6"))body.put("thinking",new JSONObject().put("type","disabled"));
        SharedPreferences p=context.getSharedPreferences("settings",0);String thinking=p.getString("ai_thinking","默认");if(!thinking.equals("默认")){boolean on=thinking.equals("开启");if(endpoint.contains("dashscope")||endpoint.contains("maas.aliyuncs.com"))body.put("enable_thinking",on);else body.put("thinking",new JSONObject().put("type",on?"enabled":"disabled"));}
        String temperature=p.getString("ai_temperature",""),topP=p.getString("ai_top_p",""),tokens=p.getString("ai_max_tokens","");if(!temperature.isEmpty())body.put("temperature",Double.parseDouble(temperature));if(!topP.isEmpty())body.put("top_p",Double.parseDouble(topP));if(!tokens.isEmpty())body.put(p.getString("ai_token_field","max_tokens"),Integer.parseInt(tokens));String extra=p.getString("ai_extra","");if(!extra.isEmpty()){JSONObject params=new JSONObject(extra);java.util.Iterator<String> keys=params.keys();while(keys.hasNext()){String key=keys.next();if(!java.util.Arrays.asList("model","messages","stream").contains(key))body.put(key,params.get(key));}}
        return body;
    }
}
