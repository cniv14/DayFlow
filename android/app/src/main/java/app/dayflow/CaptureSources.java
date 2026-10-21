package app.dayflow;
import android.widget.*;import org.json.*;
/** Keep collected material available beside a generated candidate during human review. */
final class CaptureSources {
 static void entry(MainActivity a,LinearLayout page,JSONObject record,java.util.function.Supplier<JSONObject> draft){JSONArray sources=record.optJSONArray("source_ids");if(sources==null||sources.length()==0)return;page.addView(Ui.help(a,"AI候选 · 请核验标题、日期、地点与提醒后再保存。"));page.addView(Ui.button(a,"查看原始通知资料",false,()->show(a,draft.get(),sources)));}
 private static void show(MainActivity a,JSONObject record,JSONArray sources){LinearLayout page=a.fullPage("原始资料",()->a.edit(record));for(int i=0;i<sources.length();i++)try{JSONObject ref=sources.getJSONObject(i),source=Data.get(a).find(ref.getString("id"));if(source==null){page.addView(Ui.help(a,"一条原始信息已不存在"));continue;}page.addView(Ui.text(a,source.optString("title"),18,true));page.addView(Ui.help(a,source.optString("source")+" · "+source.optString("source_group")+(!source.optString("revision").equals(ref.optString("revision"))?" · 原文已更新":"")));TextView body=Ui.text(a,source.optString("content"),16,false);body.setTextIsSelectable(true);page.addView(body);}catch(Exception e){page.addView(Ui.help(a,"无法读取这条原始资料"));}page.addView(Ui.button(a,"返回核验编辑",true,()->a.edit(record)));}
}
