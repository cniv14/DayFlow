package app.dayflow;
import android.widget.*;
import org.json.*;

final class ProjectChildren {
    static void show(MainActivity a,JSONObject parent,int offset){try{LinearLayout page=a.fullPage("项目子任务",()->a.detail(parent));int[] progress=Data.get(a).projectProgress(parent.optString("id"));page.addView(Ui.text(a,parent.optString("title"),23,true));page.addView(Ui.help(a,"已完成 "+progress[1]+" / "+progress[0]+" · 每页40项"));JSONArray children=Data.get(a).childrenPage(parent.optString("id"),offset);for(int i=0;i<children.length();i++){JSONObject r=children.getJSONObject(i);page.addView(Ui.button(a,(r.optString("completed").isEmpty()?"○ ":"✓ ")+r.optString("title")+" · "+PlanTime.label(r),false,()->a.detail(r)));}if(children.length()==0)page.addView(Ui.help(a,"尚无子任务"));if(offset>0)page.addView(Ui.button(a,"上一页",false,()->show(a,parent,Math.max(0,offset-40))));if(children.length()==40)page.addView(Ui.button(a,"下一页",false,()->show(a,parent,offset+40)));page.addView(Ui.button(a,"＋ 添加子任务",true,()->{try{a.edit(Data.make("task","").put("parent_id",parent.optString("id")));}catch(Exception e){a.message(e.getMessage());}}));}catch(Exception e){a.message(e.getMessage());}}
}
