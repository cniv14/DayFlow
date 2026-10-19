package app.dayflow;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import com.google.android.material.card.MaterialCardView;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Native, bounded-view day/week timetable. Empty cells are direct creation targets. */
public final class ScheduleTable extends LinearLayout {
    public interface Listener {void open(JSONObject record);void create(LocalDateTime start);}
    public static final class Segment {
        public final JSONObject record;public final int start,end;public int lane,lanes;
        Segment(JSONObject r,int s,int e){record=r;start=s;end=e;}
    }
    public static List<Segment> segments(JSONArray rows,LocalDate day)throws JSONException{
        List<Segment> out=new ArrayList<>();LocalDateTime begin=day.atStartOfDay(),finish=day.plusDays(1).atStartOfDay();
        for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);if(r.optString("start_at").isEmpty())continue;
            LocalDateTime start=LocalDateTime.parse(r.getString("start_at")),end=r.optString("end_at").isEmpty()?start.plusMinutes(60):LocalDateTime.parse(r.getString("end_at"));
            if(!end.isAfter(start))end=start.plusMinutes(30);if(!start.isBefore(finish)||!end.isAfter(begin))continue;
            int a=start.isBefore(begin)?0:start.getHour()*60+start.getMinute(),b=end.isAfter(finish)||end.equals(finish)?1440:end.getHour()*60+end.getMinute();out.add(new Segment(r,a,Math.max(a+15,b)));
        }
        out.sort(Comparator.comparingInt(s->s.start));
        int first=0;while(first<out.size()){
            int last=first,groupEnd=out.get(first).end;while(last+1<out.size()&&out.get(last+1).start<groupEnd){last++;groupEnd=Math.max(groupEnd,out.get(last).end);}
            List<Integer> ends=new ArrayList<>();for(int i=first;i<=last;i++){Segment s=out.get(i);int lane=0;while(lane<ends.size()&&ends.get(lane)>s.start)lane++;if(lane==ends.size())ends.add(s.end);else ends.set(lane,s.end);s.lane=lane;}
            for(int i=first;i<=last;i++)out.get(i).lanes=ends.size();first=last+1;
        }return out;
    }
    public ScheduleTable(Context c,LocalDate first,int days,JSONArray records,Listener listener)throws JSONException{
        super(c);setOrientation(VERTICAL);int gutter=Ui.dp(c,40),available=c.getResources().getDisplayMetrics().widthPixels-Ui.dp(c,32),column=Math.max(Ui.dp(c,days==1?180:44),(available-gutter)/days);int width=gutter+column*days,hour=Ui.dp(c,58);
        HorizontalScrollView horizontal=new HorizontalScrollView(c);horizontal.setFillViewport(true);horizontal.setHorizontalScrollBarEnabled(false);addView(horizontal,new LayoutParams(-1,-1));
        LinearLayout sheet=Ui.column(c);horizontal.addView(sheet,new HorizontalScrollView.LayoutParams(width,-1));
        LinearLayout header=Ui.row(c);TextView corner=Ui.text(c,"",10,false);header.addView(corner,new LayoutParams(gutter,Ui.dp(c,60)));
        for(int d=0;d<days;d++){LocalDate date=first.plusDays(d);TextView label=Ui.text(c,date.format(DateTimeFormatter.ofPattern("EEE",Locale.CHINA))+"\n"+date.getDayOfMonth(),13,date.equals(LocalDate.now()));label.setGravity(Gravity.CENTER);label.setTextColor(date.equals(LocalDate.now())?Ui.primary(c):Ui.muted(c));label.setContentDescription(date.toString());header.addView(label,new LayoutParams(column,Ui.dp(c,60)));}sheet.addView(header);
        ScrollView vertical=new ScrollView(c);vertical.setFillViewport(false);sheet.addView(vertical,new LayoutParams(-1,0,1));
        FrameLayout grid=new FrameLayout(c);grid.setMinimumHeight(hour*24);vertical.addView(grid,new ScrollView.LayoutParams(width,hour*24));
        View lines=new View(c){final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);protected void onDraw(Canvas canvas){
            p.setStrokeWidth(Ui.dp(c,1));p.setColor(Ui.color(c,com.google.android.material.R.attr.colorOutlineVariant));
            for(int h=0;h<=24;h++)canvas.drawLine(gutter,h*hour,width,h*hour,p);
            for(int d=0;d<=days;d++)canvas.drawLine(gutter+d*column,0,gutter+d*column,24*hour,p);
            p.setTextSize(Ui.dp(c,10));p.setColor(Ui.muted(c));for(int h=0;h<24;h++)canvas.drawText(String.format(Locale.ROOT,"%02d:00",h),0,h*hour+Ui.dp(c,12),p);
            long offset=java.time.temporal.ChronoUnit.DAYS.between(first,LocalDate.now());if(offset>=0&&offset<days){LocalTime now=LocalTime.now();float y=(now.getHour()+now.getMinute()/60f)*hour;p.setColor(Ui.primary(c));p.setStrokeWidth(Ui.dp(c,2));canvas.drawLine(gutter+offset*column,y,gutter+(offset+1)*column,y,p);}
        }};
        lines.setContentDescription("时间表；点击空白时间段新建日程");lines.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_UP&&e.getX()>=gutter){int day=Math.min(days-1,Math.max(0,(int)((e.getX()-gutter)/column)));int minute=Math.min(1410,Math.max(0,(int)(e.getY()/hour*60)/30*30));listener.create(first.plusDays(day).atTime(minute/60,minute%60));return true;}return e.getAction()==MotionEvent.ACTION_DOWN;});grid.addView(lines,new FrameLayout.LayoutParams(width,hour*24));
        for(int d=0;d<days;d++)for(Segment s:segments(records,first.plusDays(d))){
            MaterialCardView card=Ui.card(c);card.setRadius(Ui.dp(c,10));card.setStrokeWidth(0);String kind=s.record.optString("kind");
            int attr="course".equals(kind)?com.google.android.material.R.attr.colorTertiaryContainer:"task".equals(kind)?com.google.android.material.R.attr.colorSecondaryContainer:com.google.android.material.R.attr.colorPrimaryContainer;
            int on="course".equals(kind)?com.google.android.material.R.attr.colorOnTertiaryContainer:"task".equals(kind)?com.google.android.material.R.attr.colorOnSecondaryContainer:com.google.android.material.R.attr.colorOnPrimaryContainer;
            card.setCardBackgroundColor(Ui.color(c,attr));LinearLayout text=Ui.column(c);text.setPadding(Ui.dp(c,5),Ui.dp(c,5),Ui.dp(c,4),Ui.dp(c,2));TextView title=Ui.text(c,s.record.optString("title"),days==1?15:11,true);title.setTextColor(Ui.color(c,on));title.setMaxLines(days==1?2:4);text.addView(title);
            TextView time=Ui.text(c,LocalTime.of(s.start/60,s.start%60).toString(),10,false);time.setTextColor(Ui.color(c,on));text.addView(time);if(days==1&&!s.record.optString("location").isEmpty()){TextView place=Ui.text(c,s.record.optString("location"),12,false);place.setTextColor(Ui.color(c,on));text.addView(place);}card.addView(text);
            int slot=column/Math.max(1,s.lanes);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(slot-Ui.dp(c,3),Math.max(Ui.dp(c,28),(int)((s.end-s.start)/60f*hour)-Ui.dp(c,2)));p.leftMargin=gutter+d*column+s.lane*slot+Ui.dp(c,2);p.topMargin=(int)(s.start/60f*hour)+Ui.dp(c,1);grid.addView(card,p);
            card.setContentDescription(s.record.optString("title")+"，"+s.record.optString("start_at")+"，点击查看");card.setOnClickListener(v->listener.open(s.record));
        }
        vertical.post(()->vertical.scrollTo(0,Math.max(0,(LocalDate.now().isBefore(first)||LocalDate.now().isAfter(first.plusDays(days-1))?7:Math.min(18,Math.max(6,LocalTime.now().getHour()-1))))*hour));
    }
}
