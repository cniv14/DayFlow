package app.dayflow;
import android.view.Gravity;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.json.*;
/** Bounded native grid shared by month browsing and date selection. */
public final class MonthBrowser {
 public static void show(MainActivity a,LocalDate initial,Consumer<LocalDate> choose){select(a,initial,choose,a::home,true);}
 public static void select(MainActivity a,LocalDate initial,Consumer<LocalDate> choose,Runnable back,boolean appointments){
  LinearLayout page=a.fullPage("选择日期",back);LocalDate safe=initial.getYear()<1901?LocalDate.of(1901,1,1):initial.getYear()>2099?LocalDate.of(2099,12,31):initial;LocalDate[] selected={safe};YearMonth[] visible={YearMonth.from(safe)};
  LinearLayout nav=Ui.row(a);page.addView(nav);TextView title=Ui.text(a,"",18,true);title.setGravity(Gravity.CENTER);
  LinearLayout jump=Ui.row(a);page.addView(jump);NumberPicker year=new NumberPicker(a),month=new NumberPicker(a);
  year.setMinValue(1901);year.setMaxValue(2099);year.setValue(Math.max(1901,Math.min(2099,initial.getYear())));month.setMinValue(1);month.setMaxValue(12);month.setValue(initial.getMonthValue());year.setContentDescription("年份");month.setContentDescription("月份");
  jump.addView(year,new LinearLayout.LayoutParams(0,Ui.dp(a,110),1));jump.addView(month,new LinearLayout.LayoutParams(0,Ui.dp(a,110),1));
  TextView info=Ui.help(a,"");page.addView(info);LinearLayout grid=Ui.column(a);page.addView(grid);Runnable[] render={null};boolean[] updating={false};
  render[0]=()->{updating[0]=true;year.setValue(visible[0].getYear());month.setValue(visible[0].getMonthValue());updating[0]=false;
   title.setText(visible[0].getYear()+"年 "+visible[0].getMonthValue()+"月");info.setText(selected[0]+" · "+ChineseDates.description(selected[0]));grid.removeAllViews();
   Map<LocalDate,Integer> counts=new HashMap<>();if(appointments)try{JSONArray rows=Data.get(a).range(visible[0].atDay(1),visible[0].plusMonths(1).atDay(1),false);for(int i=0;i<rows.length();i++){LocalDateTime at=PlanTime.start(rows.getJSONObject(i));if(at!=null)counts.put(at.toLocalDate(),counts.getOrDefault(at.toLocalDate(),0)+1);}}catch(Exception e){a.message("读取安排失败："+e.getMessage());}
   LinearLayout header=Ui.row(a);for(String day:new String[]{"一","二","三","四","五","六","日"}){TextView label=Ui.help(a,day);label.setGravity(Gravity.CENTER);header.addView(label,new LinearLayout.LayoutParams(0,-2,1));}grid.addView(header);
   LocalDate first=visible[0].atDay(1);int offset=first.getDayOfWeek().getValue()-1;
   for(int row=0;row<6;row++){LinearLayout line=Ui.row(a);grid.addView(line);for(int col=0;col<7;col++){
    int number=row*7+col-offset+1;LinearLayout cell=Ui.column(a);cell.setGravity(Gravity.CENTER);cell.setMinimumHeight(Ui.dp(a,56));line.addView(cell,new LinearLayout.LayoutParams(0,-2,1));if(number<1||number>visible[0].lengthOfMonth())continue;
    LocalDate date=visible[0].atDay(number);boolean active=date.equals(selected[0]);TextView day=Ui.text(a,String.valueOf(number),16,active);cell.addView(day);TextView lunar=Ui.text(a,ChineseDates.label(date),9,false);lunar.setSingleLine();lunar.setEllipsize(android.text.TextUtils.TruncateAt.END);cell.addView(lunar);if(counts.containsKey(date))cell.addView(Ui.text(a,"• "+counts.get(date),9,false));
    GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(Ui.dp(a,10));bg.setColor(active?Ui.color(a,com.google.android.material.R.attr.colorPrimaryContainer):android.graphics.Color.TRANSPARENT);if(date.equals(LocalDate.now()))bg.setStroke(Ui.dp(a,1),Ui.primary(a));cell.setBackground(bg);cell.setContentDescription(date+"，"+ChineseDates.label(date));cell.setOnClickListener(v->{selected[0]=date;render[0].run();});
   }}
  };
  nav.addView(Ui.button(a,"‹",false,()->{if(visible[0].isAfter(YearMonth.of(1901,1))){visible[0]=visible[0].minusMonths(1);render[0].run();}}),new LinearLayout.LayoutParams(Ui.dp(a,48),-2));nav.addView(title,new LinearLayout.LayoutParams(0,-2,1));nav.addView(Ui.button(a,"›",false,()->{if(visible[0].isBefore(YearMonth.of(2099,12))){visible[0]=visible[0].plusMonths(1);render[0].run();}}),new LinearLayout.LayoutParams(Ui.dp(a,48),-2));
  NumberPicker.OnValueChangeListener changed=(picker,old,value)->{if(!updating[0]){visible[0]=YearMonth.of(year.getValue(),month.getValue());render[0].run();}};year.setOnValueChangedListener(changed);month.setOnValueChangedListener(changed);
  page.addView(Ui.button(a,"确定 · 使用选中日期",true,()->choose.accept(selected[0])));page.addView(Ui.button(a,"今天",false,()->{selected[0]=LocalDate.now();visible[0]=YearMonth.now();render[0].run();}));if(appointments)page.addView(Ui.button(a,"打印本月 / 保存 PDF",false,()->CalendarPrint.show(a,visible[0].atDay(1),visible[0].plusMonths(1).atDay(1))));render[0].run();
 }
}
