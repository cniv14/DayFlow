package app.dayflow;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.json.*;

final class WeekPreview {
 static void show(MainActivity a,LinearLayout page){page.addView(Ui.text(a,"未来一周",25,true));page.addView(Ui.help(a,"右滑回今天 · 左滑到笔记 · 长按安排可提前或延后"));page.addView(Ui.button(a,"更新天气预报",false,()->{if(a.prefs.getString("lat","").isEmpty())new SettingsPanels(a).weather();else a.job(()->Forecast.refresh(a),a::home);}));for(int d=0;d<7;d++){LocalDate date=LocalDate.now().plusDays(d);page.addView(Ui.button(a,date.format(DateTimeFormatter.ofPattern("M月d日 EEE",java.util.Locale.CHINA))+" "+Holidays.label(date),false,()->a.openSchedule(date)));page.addView(Ui.help(a,Forecast.summary(a,date)));try{JSONArray rows=Data.get(a).dayTasks(date);int shown=0;for(int i=0;i<rows.length()&&shown<8;i++){JSONObject r=rows.getJSONObject(i);if(!r.optString("completed").isEmpty())continue;com.google.android.material.card.MaterialCardView card=Ui.card(a);card.addView(a.recordContents(r));card.setOnClickListener(v->a.detail(r));card.setOnLongClickListener(v->{TaskQuickActions.show(a,r,a::home);return true;});page.addView(card);shown++;}if(rows.length()==0)page.addView(Ui.help(a,"暂无安排"));if(rows.length()>8)page.addView(Ui.button(a,"查看当天全部安排",false,()->a.openSchedule(date)));}catch(Exception e){a.message(e.getMessage());}}}
}
