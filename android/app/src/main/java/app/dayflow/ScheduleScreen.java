package app.dayflow;
import android.widget.*;
import java.time.*;
import org.json.*;

final class ScheduleScreen {
 static void show(MainActivity a,LinearLayout page,LocalDate initial){
  LinearLayout below=Ui.column(a);MonthGrid.add(a,page,initial,date->{a.setScheduleDate(date);day(a,below,date);});page.addView(below);day(a,below,initial);
 }
 private static void day(MainActivity a,LinearLayout below,LocalDate date){below.removeAllViews();below.addView(Ui.text(a,date+" · "+ChineseDates.description(date),17,true));String holiday=Holidays.description(date);if(!holiday.isEmpty())below.addView(Ui.help(a,holiday));Forecast.add(a,below,date,()->day(a,below,date));
  LinearLayout menu=Ui.row(a);menu.addView(Ui.button(a,"课表",false,()->courses(a)),new LinearLayout.LayoutParams(0,-2,1));menu.addView(Ui.button(a,"黄历",false,()->almanac(a,date)),new LinearLayout.LayoutParams(0,-2,1));menu.addView(Ui.button(a,"编辑日程",true,()->ScheduleEditor.show(a,date,false)),new LinearLayout.LayoutParams(0,-2,1));below.addView(menu);
  below.addView(Ui.button(a,"＋ 添加这一天的安排",false,()->{try{a.edit(Data.make("event","").put("start_at",date.atTime(9,0).toString()));}catch(Exception e){a.message(e.getMessage());}}));
  DailyTasks.rows(a,below,date,()->day(a,below,date));below.addView(Ui.button(a,"长期任务与每日打卡",false,()->CheckIns.list(a)));below.addView(Ui.button(a,"项目看板",false,()->ProjectBoard.show(a,"planned",0)));below.addView(Ui.button(a,"全部安排与搜索",false,()->PlanHub.show(a)));below.addView(Ui.button(a,"导入、订阅与扩展",false,()->ExtensionHub.show(a)));
 }
 static void courses(MainActivity a){LinearLayout page=a.fullPage("课表",a::home);page.addView(Ui.text(a,"课表",24,true));page.addView(Ui.help(a,"课程统一显示在每日待办；支持单双周、截图识别和网页导入。"));if(TimetableCapture.hasDraft(a))page.addView(Ui.button(a,"恢复待确认课表",false,()->new TimetableCapture(a,null)));page.addView(Ui.button(a,"AI 截图识别课表",true,()->a.pickFile(110,"image/*")));page.addView(Ui.button(a,"编辑课表",false,()->ScheduleEditor.show(a,LocalDate.now(),true)));page.addView(Ui.button(a,"导入 CSV / ICS / HTML",false,()->a.pickFile(103,"*/*")));page.addView(Ui.button(a,"网页课表与通知导入",false,()->WebImports.show(a)));page.addView(Ui.button(a,"离线截图文字识别",false,()->ExtensionHub.pickOcr(a)));page.addView(Ui.button(a,"＋ 新建课程",false,()->a.newRecord("course")));}
 static void almanac(MainActivity a,LocalDate date){LinearLayout page=a.fullPage("黄历",a::home);page.addView(Ui.text(a,date.toString(),24,true));page.addView(Ui.help(a,ChineseDates.description(date)+" · "+Holidays.description(date)));try{com.nlf.calendar.Lunar lunar=ChineseDates.lunar(date);page.addView(Ui.text(a,"宜  "+String.join(" · ",lunar.getDayYi()),17,false));page.addView(Ui.text(a,"忌  "+String.join(" · ",lunar.getDayJi()),17,false));page.addView(Ui.help(a,"黄历内容仅作传统文化参考。天气预报用于出行参考，远期日期不会生成虚构天气。"));}catch(Exception e){page.addView(Ui.help(a,"该日期暂无黄历信息"));}page.addView(Ui.button(a,"更新"+date.getYear()+"年调休数据",false,()->a.job(()->Holidays.update(a,date.getYear()),a::home)));}
}
