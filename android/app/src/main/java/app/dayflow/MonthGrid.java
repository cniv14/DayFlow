package app.dayflow;
import android.view.*;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.json.*;

/** A compact embedded month, without nested scrolling or unbounded event views. */
final class MonthGrid {
 private final MainActivity a;private final LinearLayout root,days;private final TextView label;
 private YearMonth visible;private LocalDate selected;private final Consumer<LocalDate> choose;
 static MonthGrid add(MainActivity a,LinearLayout parent,LocalDate date,Consumer<LocalDate> choose){return new MonthGrid(a,parent,date,choose);}
 private MonthGrid(MainActivity a,LinearLayout parent,LocalDate date,Consumer<LocalDate> choose){this.a=a;this.choose=choose;selected=date;visible=YearMonth.from(date);root=Ui.column(a);parent.addView(root);LinearLayout nav=Ui.row(a);root.addView(nav);nav.addView(Ui.button(a,"‹",false,()->move(-1)),new LinearLayout.LayoutParams(Ui.dp(a,48),-2));label=Ui.text(a,"",19,true);label.setGravity(Gravity.CENTER);label.setOnClickListener(v->MonthBrowser.select(a,selected,a::openSchedule,a::home,false));nav.addView(label,new LinearLayout.LayoutParams(0,-2,1));nav.addView(Ui.button(a,"今天",false,()->{selected=LocalDate.now();visible=YearMonth.now();render();choose.accept(selected);}),new LinearLayout.LayoutParams(Ui.dp(a,64),-2));nav.addView(Ui.button(a,"›",false,()->move(1)),new LinearLayout.LayoutParams(Ui.dp(a,48),-2));days=Ui.column(a);root.addView(days);render();}
 private void move(int n){YearMonth next=visible.plusMonths(n);if(next.getYear()<1901||next.getYear()>2099)return;visible=next;selected=visible.atDay(Math.min(selected.getDayOfMonth(),visible.lengthOfMonth()));render();choose.accept(selected);}
 private void render(){label.setText(visible.getYear()+"年"+visible.getMonthValue()+"月");days.removeAllViews();Map<LocalDate,Integer> counts=new HashMap<>();try{JSONArray rows=Data.get(a).range(visible.atDay(1),visible.plusMonths(1).atDay(1),false);for(int i=0;i<rows.length();i++){LocalDateTime date=PlanTime.start(rows.getJSONObject(i));if(date!=null)counts.put(date.toLocalDate(),counts.getOrDefault(date.toLocalDate(),0)+1);}}catch(Exception e){a.message(e.getMessage());}
  LinearLayout head=Ui.row(a);for(String day:new String[]{"一","二","三","四","五","六","日"}){TextView text=Ui.help(a,day);text.setGravity(Gravity.CENTER);head.addView(text,new LinearLayout.LayoutParams(0,-2,1));}days.addView(head);int offset=visible.atDay(1).getDayOfWeek().getValue()-1;int weeks=(offset+visible.lengthOfMonth()+6)/7;
  for(int r=0;r<weeks;r++){LinearLayout row=Ui.row(a);days.addView(row);for(int col=0;col<7;col++){int number=r*7+col-offset+1;LinearLayout cell=Ui.column(a);cell.setGravity(Gravity.CENTER);cell.setMinimumHeight(Ui.dp(a,48));row.addView(cell,new LinearLayout.LayoutParams(0,-2,1));if(number<1||number>visible.lengthOfMonth())continue;LocalDate date=visible.atDay(number);String holiday=Holidays.label(date);TextView day=Ui.text(a,number+(counts.containsKey(date)?"·":"")+holiday,14,date.equals(selected));day.setSingleLine();day.setGravity(Gravity.CENTER);cell.addView(day);TextView lunar=Ui.text(a,ChineseDates.label(date),9,false);lunar.setSingleLine();lunar.setEllipsize(android.text.TextUtils.TruncateAt.END);cell.addView(lunar);GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(Ui.dp(a,12));bg.setColor(date.equals(selected)?Ui.color(a,com.google.android.material.R.attr.colorPrimaryContainer):android.graphics.Color.TRANSPARENT);if(date.equals(LocalDate.now()))bg.setStroke(Ui.dp(a,1),Ui.primary(a));cell.setBackground(bg);cell.setContentDescription(date+" "+ChineseDates.description(date)+" "+Holidays.description(date));cell.setOnClickListener(v->{selected=date;render();choose.accept(date);});}}
 }
}
