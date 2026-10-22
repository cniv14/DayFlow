package app.dayflow;

import android.widget.*;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.card.MaterialCardView;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.json.*;

/** A course occurrence is a scheduled, completable item in the same daily list. */
public final class DailyTasks {
    public static void show(MainActivity a,LocalDate day){
        LinearLayout page=a.fullPage(day.format(DateTimeFormatter.ofPattern("M月d日 EEEE",java.util.Locale.CHINA))+" · 每日待办",a::home);populate(a,page,day,false);
    }
    public static void today(MainActivity a,LinearLayout page){populate(a,page,LocalDate.now(),true);}
    private static void populate(MainActivity a,LinearLayout page,LocalDate day,boolean home){
        page.addView(Ui.help(a,ChineseDates.description(day)));
        rows(a,page,day,()->{if(home)a.home();else show(a,day);});
        page.addView(Ui.button(a,"搜索、项目与全部安排",false,()->PlanHub.show(a)));
        page.addView(Ui.button(a,"＋ 新建待办",true,()->{try{LocalDateTime start=day.atTime(LocalTime.now().withSecond(0).withNano(0));a.edit(Data.make("task","").put("start_at",start.toString()));}catch(Exception e){a.message(e.getMessage());}}));page.addView(Ui.button(a,"管理其他日期与时间待定",false,()->ScheduleEditor.show(a,day,false)));
    }
    static void rows(MainActivity a,LinearLayout page,LocalDate day,Runnable refresh){
        try{JSONArray rows=Data.get(a).dayTasks(day);int done=0;for(int i=0;i<rows.length();i++)if(!rows.getJSONObject(i).optString("completed").isEmpty())done++;page.addView(Ui.text(a,"已完成 "+done+" / "+rows.length(),18,true));
            for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);MaterialCardView card=Ui.card(a);LinearLayout row=Ui.row(a);MaterialCheckBox check=new MaterialCheckBox(a);check.setChecked(!r.optString("completed").isEmpty());check.setContentDescription("完成或恢复「"+r.optString("title")+"」");check.setOnClickListener(v->{try{TaskActions.complete(a,r);a.changed();refresh.run();}catch(Exception e){check.setChecked(!r.optString("completed").isEmpty());a.message(e.getMessage());}});row.addView(check);LinearLayout body=Ui.padded(a);TextView title=Ui.text(a,r.optString("title"),16,true);title.setMaxLines(2);if(!r.optString("completed").isEmpty()){title.setPaintFlags(title.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);body.setAlpha(.6f);}body.addView(title);body.addView(Ui.help(a,PlanTime.dailyLabel(r,day)+(r.optString("location").isEmpty()?"":" · "+r.optString("location"))));body.setOnClickListener(v->a.detail(r));body.setOnLongClickListener(v->{TaskQuickActions.show(a,r,()->a.openSchedule(day));return true;});card.setOnLongClickListener(v->{TaskQuickActions.show(a,r,()->a.openSchedule(day));return true;});row.addView(body,new LinearLayout.LayoutParams(0,-2,1));card.addView(row);page.addView(card);}
            if(rows.length()==0)page.addView(Ui.help(a,"这一天没有安排，也没有进行中的长任务或时间待定事项。"));
        }catch(Exception e){a.message(e.getMessage());}
    }

}
