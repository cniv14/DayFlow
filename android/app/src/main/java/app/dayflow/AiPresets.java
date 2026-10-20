package app.dayflow;

import android.content.*;
import android.widget.*;
import com.google.android.material.chip.*;
import com.google.android.material.textfield.TextInputEditText;
import org.json.*;

/** Official Chat Completions presets. Keys are provided by the user, never bundled. */
public final class AiPresets {
    private static final String[][] PRESETS={
        {"DeepSeek Flash","https://api.deepseek.com","deepseek-flash","适合快速提取日程，默认关闭思考。"},
        {"DeepSeek Pro","https://api.deepseek.com","deepseek-v4-pro","使用 Pro 模型，默认关闭思考。"},
        {"通义千问","https://dashscope.aliyuncs.com/compatible-mode/v1","qwen-plus","阿里云百炼中国内地接口。此预设用于文字；截图请选择视觉模型。"},
        {"通义视觉","https://dashscope.aliyuncs.com/compatible-mode/v1","qwen3-vl-plus","用于截图／图片识别，使用百炼中国内地接口。"},
        {"Kimi","https://api.moonshot.cn/v1","kimi-k2.6","Kimi 通用模型，默认关闭思考。"},
        {"OpenAI","https://api.openai.com/v1","gpt-4.1-mini","兼容 Chat Completions 的文本／图片模型。"}
    };
    public static void show(MainActivity a){
        SharedPreferences p=a.prefs;LinearLayout form=a.fullPage("AI 服务与模型",a::home);form.addView(Ui.help(a,"选择预设后填写自己的 API Key。接口地址和模型仍可修改；只在你主动识别时发送所选内容。"));
        ChipGroup group=new ChipGroup(a);group.setSingleSelection(true);form.addView(group);
        TextInputEditText endpoint=Ui.field(a,form,"API 根地址（不要包含 /chat/completions）",p.getString("api_url",""),false),model=Ui.field(a,form,"模型 ID",p.getString("model",""),false),key=Ui.field(a,form,"API Key（相同接口留空保留）","",false);key.setInputType(129);TextView hint=Ui.help(a,"选择服务商，或填写自定义 OpenAI 兼容接口。");form.addView(hint);
        for(String[] preset:PRESETS){Chip chip=new Chip(a);chip.setText(preset[0]);chip.setCheckable(true);chip.setChecked(preset[1].equals(p.getString("api_url",""))&&preset[2].equals(p.getString("model","")));chip.setOnClickListener(v->{if(!endpoint.getText().toString().trim().equals(preset[1]))key.setText("");endpoint.setText(preset[1]);model.setText(preset[2]);hint.setText(preset[3]);});group.addView(chip);}
        Chip custom=new Chip(a);custom.setText("自定义");custom.setCheckable(true);custom.setOnClickListener(v->hint.setText("填写完整根地址与模型 ID。局域网可使用明确的 HTTP 私有 IP 地址。"));group.addView(custom);
        form.addView(Ui.button(a,"保存配置",true,()->{try{String root=Network.endpoint(endpoint.getText().toString(),true),name=model.getText().toString().trim(),secret=key.getText().toString().trim();if(root.endsWith("/chat/completions"))throw new Exception("请填写根地址，去掉末尾 /chat/completions");if(name.isEmpty())throw new Exception("请填写模型 ID");String previous=p.getString("api_url","");if(secret.isEmpty()&&!root.equals(previous))throw new Exception("切换接口时请填写该服务商的 API Key");if(secret.isEmpty()&&Secrets.get(a,"api_key").isEmpty())throw new Exception("请填写 API Key");if(!secret.isEmpty())Secrets.put(a,"api_key",secret);p.edit().putString("api_url",root).putString("model",name).apply();key.setText("");a.message("已保存 AI 配置");}catch(Exception e){a.message(e.getMessage());}}));
    }
    public static JSONObject request(String endpoint,String model,JSONArray messages)throws JSONException{
        JSONObject body=new JSONObject().put("model",model).put("messages",messages).put("stream",false);
        if(endpoint.equals("https://api.deepseek.com")&&(model.equals("deepseek-flash")||model.equals("deepseek-v4-pro")))body.put("thinking",new JSONObject().put("type","disabled"));
        if(endpoint.equals("https://api.moonshot.cn/v1")&&model.equals("kimi-k2.6"))body.put("thinking",new JSONObject().put("type","disabled"));
        return body;
    }
}
