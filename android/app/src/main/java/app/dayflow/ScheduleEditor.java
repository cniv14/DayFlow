package app.dayflow;

import android.text.InputType;
import android.view.*;
import android.widget.*;
import com.google.android.material.textfield.TextInputEditText;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.json.*;

/** A compact record table for editing, with no predefined timeline or empty time slots. */
public final class ScheduleEditor {
    private final MainActivity a;private final LocalDate day;private final boolean courses;private final int offset;
    private final List<JSONObject> records=new ArrayList<>();private final List<TextInputEditText> titles=new ArrayList<>();
    public static void show(MainActivity a,LocalDate day,boolean courses){new ScheduleEditor(a,day,courses,0);}
    private ScheduleEditor(MainActivity activity,LocalDate date,boolean onlyCourses,int pageOffset){
        a=activity;day=date;courses=onlyCourses;offset=pageOffset;
        LinearLayout form=a.fullPage(courses?"编辑课表":"编辑日程",()->{if(save(false))a.home();});
        form.addView(Ui.text(a,day.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))+" 起的7天",20,true));
        form.addView(Ui.help(a,"仅显示已有安排，不预先划分时间。标题可直接修改；点时间进入完整编辑。切换日期或离开时保存标题。"));
        LinearLayout nav=Ui.row(a);nav.addView(Ui.button(a,"前7天",false,()->go(day.minusDays(7))),new LinearLayout.LayoutParams(0,-2,1));nav.addView(Ui.button(a,"选择日期",false,()->{if(save(false))MonthBrowser.show(a,day,chosen->show(a,chosen,courses));}),new LinearLayout.LayoutParams(0,-2,1));nav.addView(Ui.button(a,"后7天",false,()->go(day.plusDays(7))),new LinearLayout.LayoutParams(0,-2,1));form.addView(nav);
        LinearLayout head=Ui.row(a);head.setPadding(0,Ui.dp(a,8),0,Ui.dp(a,8));head.addView(Ui.help(a,"时间 / 编辑"),new LinearLayout.LayoutParams(Ui.dp(a,94),-2));head.addView(Ui.help(a,courses?"课程名称":"事项名称"),new LinearLayout.LayoutParams(0,-2,1));form.addView(head);
        try{JSONArray rows=Data.get(a).scheduled(day,day.plusDays(7),courses);String lastDate="";int end=Math.min(rows.length(),offset+30);for(int i=offset;i<end;i++){JSONObject r=rows.getJSONObject(i);LocalDateTime start=PlanTime.start(r);String group=start.format(DateTimeFormatter.ofPattern("M月d日 EEE",Locale.CHINA));if(!lastDate.equals(group)){TextView label=Ui.text(a,group,14,true);label.setPadding(0,Ui.dp(a,14),0,Ui.dp(a,5));form.addView(label);lastDate=group;}
                LinearLayout row=Ui.row(a);row.setMinimumHeight(Ui.dp(a,76));String time=r.optString("start_at").isEmpty()?"截止":r.optBoolean("all_day")?"全天":start.format(DateTimeFormatter.ofPattern("HH:mm"));if(!r.optBoolean("all_day")&&!r.optString("end_at").isEmpty())time+="\n"+LocalDateTime.parse(r.getString("end_at")).format(DateTimeFormatter.ofPattern("HH:mm"));android.widget.Button edit=Ui.button(a,time,false,()->{if(save(false))try{a.edit(Data.get(a).find(r.getString("id"))==null?r:Data.get(a).find(r.getString("id")));}catch(Exception e){a.message(e.getMessage());}});edit.setTextSize(13);edit.setPadding(Ui.dp(a,6),0,Ui.dp(a,6),0);row.addView(edit,new LinearLayout.LayoutParams(Ui.dp(a,86),Ui.dp(a,64)));
                LinearLayout text=Ui.column(a);text.setPadding(Ui.dp(a,8),0,0,0);TextInputEditText title=new TextInputEditText(a);title.setText(r.optString("title"));title.setTextSize(16);title.setSingleLine(true);title.setSelectAllOnFocus(false);title.setMinHeight(Ui.dp(a,48));title.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);title.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1000)});title.setContentDescription(group+" "+r.optString("title")+"，编辑标题");text.addView(title,new LinearLayout.LayoutParams(-1,-2));String meta=(r.optString("kind").equals("course")?"课程":"待办 / 日程")+(r.optString("completed").isEmpty()?"":" · 已完成")+(r.optString("location").isEmpty()?"":" · "+r.optString("location"));text.addView(Ui.help(a,meta));row.addView(text,new LinearLayout.LayoutParams(0,-2,1));form.addView(row);View line=new View(a);line.setBackgroundColor(Ui.color(a,com.google.android.material.R.attr.colorOutlineVariant));form.addView(line,new LinearLayout.LayoutParams(-1,Ui.dp(a,1)));records.add(r);titles.add(title);
            }
            if(rows.length()==0)form.addView(Ui.help(a,"这7天还没有安排。添加事项后才会显示表格行。"));
            if(offset>0)form.addView(Ui.button(a,"上一页",false,()->{if(save(false))new ScheduleEditor(a,day,courses,Math.max(0,offset-30));}));
            if(end<rows.length())form.addView(Ui.button(a,"下一页（每页30项）",false,()->{if(save(false))new ScheduleEditor(a,day,courses,offset+30);}));
        }catch(Exception e){a.message(e.getMessage());}
        form.addView(Ui.button(a,"＋ 新建"+(courses?"课程":"事项"),true,()->{if(save(false))a.newRecord(courses?"course":"event");}));if(courses)form.addView(Ui.button(a,"导入课表 CSV / ICS",false,()->{if(save(false))a.pickFile(103,"*/*");}));else form.addView(Ui.button(a,"管理全部待办与时间待定",false,()->{if(save(false))a.tasks();}));
        form.addView(Ui.button(a,"保存表格修改",false,()->save(true)));
    }
    private void go(LocalDate date){if(save(false))show(a,date,courses);}
    private boolean save(boolean feedback){try{List<JSONObject> changes=new ArrayList<>();for(int i=0;i<records.size();i++){String title=titles.get(i).getText().toString().trim();if(title.isEmpty()){titles.get(i).setError("标题不能为空");titles.get(i).requestFocus();return false;}JSONObject original=records.get(i);if(!title.equals(original.optString("title"))){JSONObject current=Data.get(a).find(original.getString("id"));if(current==null&&!original.optString("series_id").isEmpty())current=new JSONObject(original.toString());if(current==null||current.optBoolean("deleted"))throw new Exception("事项已删除，请重新打开表格");if(!current.optString("revision").equals(original.optString("revision")))throw new Exception("事项已在其他操作中更新，请重新打开表格后编辑");JSONObject changed=new JSONObject(current.toString()).put("title",title);Data.validate(changed);changes.add(changed);}}
            for(JSONObject changed:changes){Data.get(a).save(changed);for(int i=0;i<records.size();i++)if(records.get(i).optString("id").equals(changed.optString("id")))records.set(i,changed);}if(!changes.isEmpty())a.changed();if(feedback)a.message("已保存 "+changes.size()+" 项修改");return true;
        }catch(Exception e){a.message(e.getMessage());return false;}}
}
