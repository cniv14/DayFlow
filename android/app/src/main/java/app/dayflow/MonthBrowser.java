package app.dayflow;

import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import com.kizitonwose.calendar.core.*;
import com.kizitonwose.calendar.view.*;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.json.*;

/** RecyclerView-based month browsing delegates paging and cell recycling to Calendar. */
public final class MonthBrowser {
    private static final class DayCell extends ViewContainer {
        final View root;final TextView number,lunar,marker;
        DayCell(View view){super(view);root=view;number=view.findViewById(R.id.day_number);lunar=view.findViewById(R.id.day_lunar);marker=view.findViewById(R.id.day_marker);}
    }
    public static void show(MainActivity a,LocalDate initial,Consumer<LocalDate> choose){
        LinearLayout page=a.fullPage("月历与农历",a::home);
        TextView title=Ui.text(a,"",23,true);page.addView(title);
        TextView info=Ui.help(a,ChineseDates.description(initial));page.addView(info);
        LinearLayout weekdays=Ui.row(a);for(String name:new String[]{"一","二","三","四","五","六","日"}){TextView t=Ui.help(a,name);t.setGravity(Gravity.CENTER);weekdays.addView(t,new LinearLayout.LayoutParams(0,-2,1));}page.addView(weekdays);
        com.kizitonwose.calendar.view.CalendarView calendar=new com.kizitonwose.calendar.view.CalendarView(a,null);calendar.setDayViewResource(R.layout.calendar_day);calendar.setScrollPaged(true);calendar.setItemAnimator(null);calendar.setNestedScrollingEnabled(false);
        LocalDate[] selected={initial};YearMonth[] visible={YearMonth.from(initial)};Map<LocalDate,Integer> counts=new HashMap<>();
        Runnable load=()->{counts.clear();try{LocalDate from=visible[0].atDay(1).minusDays(7);JSONArray rows=Data.get(a).range(from,visible[0].plusMonths(1).atDay(1).plusDays(7),false);for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);LocalDate date=LocalDateTime.parse(row.getString("start_at")).toLocalDate();counts.put(date,counts.getOrDefault(date,0)+1);}}catch(Exception e){a.message("读取日期安排失败："+e.getMessage());}};
        calendar.setDayBinder(new MonthDayBinder<DayCell>(){
            public DayCell create(View view){return new DayCell(view);}
            public void bind(DayCell cell,CalendarDay day){LocalDate date=day.getDate();boolean active=day.getPosition()==DayPosition.MonthDate,chosen=date.equals(selected[0]);int ink=chosen?Ui.color(a,com.google.android.material.R.attr.colorOnPrimaryContainer):active?Ui.ink(a):Ui.muted(a);
                cell.number.setText(String.valueOf(date.getDayOfMonth()));cell.number.setTextColor(ink);cell.lunar.setText(ChineseDates.label(date));cell.lunar.setTextColor(ink);int count=counts.getOrDefault(date,0);cell.marker.setText(count>0?"• "+count:" ");cell.marker.setTextColor(chosen?ink:Ui.primary(a));cell.root.setAlpha(active?1f:.45f);
                GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(Ui.dp(a,12));bg.setColor(chosen?Ui.color(a,com.google.android.material.R.attr.colorPrimaryContainer):android.graphics.Color.TRANSPARENT);if(date.equals(LocalDate.now()))bg.setStroke(Ui.dp(a,1),Ui.primary(a));cell.root.setBackground(bg);cell.root.setContentDescription(date+"，"+ChineseDates.label(date)+"，"+count+"项待办");
                cell.root.setOnClickListener(v->{selected[0]=date;info.setText(ChineseDates.description(date));calendar.notifyCalendarChanged();});
            }
        });
        calendar.setMonthScrollListener(month->{visible[0]=month.getYearMonth();title.setText(visible[0].getYear()+"年 "+visible[0].getMonthValue()+"月");load.run();calendar.post(calendar::notifyCalendarChanged);return kotlin.Unit.INSTANCE;});
        YearMonth center=YearMonth.now();calendar.setup(center.minusYears(10),center.plusYears(10),DayOfWeek.MONDAY);load.run();calendar.scrollToMonth(YearMonth.from(initial));
        page.addView(calendar,new LinearLayout.LayoutParams(-1,-2));
        page.addView(Ui.help(a,"左右滑动切换月份 · 圆点数字为未完成安排（含课程）"));
        page.addView(Ui.button(a,"选择这个日期",true,()->choose.accept(selected[0])));
        page.addView(Ui.button(a,"回到今天",false,()->{selected[0]=LocalDate.now();calendar.scrollToMonth(YearMonth.now());calendar.notifyCalendarChanged();info.setText(ChineseDates.description(selected[0]));}));
    }
}
