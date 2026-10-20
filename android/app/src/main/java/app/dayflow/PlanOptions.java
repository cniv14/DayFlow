package app.dayflow;
import android.widget.*;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.chip.*;
import com.google.android.material.textfield.TextInputEditText;
import org.json.*;
import java.time.*;
import java.util.*;

/** Advanced fields stay in the native editor and retain draft state across date pickers. */
public final class PlanOptions {
    private final MainActivity a;private final Spinner type,parent,status;private final MaterialSwitch allDay;private final TextInputEditText due,until,minutes;private final ChipGroup weekdays;private final List<JSONObject> types,projects=new ArrayList<>();
    public PlanOptions(MainActivity a,LinearLayout form,JSONObject r){this.a=a;List<JSONObject> loaded;try{loaded=TaskTypes.all(a,false);}catch(Exception e){loaded=new ArrayList<>();a.message(e.getMessage());}types=loaded;
        List<String> names=new ArrayList<>();names.add("沿用事项类型");for(JSONObject t:types)names.add(t.optString("icon")+" "+TaskTypes.path(t,types));type=TaskTypes.spinner(a,form,"自定义任务类型",names);for(int i=0;i<types.size();i++)if(types.get(i).optString("id").equals(r.optString("type_id")))type.setSelection(i+1);
        names=new ArrayList<>();names.add("不归属长任务");try{JSONArray rows=Data.get(a).list("project",500);for(int i=0;i<rows.length();i++){JSONObject p=rows.getJSONObject(i);if(!p.optString("id").equals(r.optString("id"))){projects.add(p);names.add(p.optString("title"));}}}catch(Exception e){a.message(e.getMessage());}parent=TaskTypes.spinner(a,form,"所属长任务",names);for(int i=0;i<projects.size();i++)if(projects.get(i).optString("id").equals(r.optString("parent_id")))parent.setSelection(i+1);
        status=TaskTypes.spinner(a,form,"状态",Arrays.asList("计划中","已完成","已取消"));status.setSelection(PlanTime.cancelled(r)?2:r.optString("completed").isEmpty()?0:1);
        allDay=new MaterialSwitch(a);allDay.setText("全天安排（有具体日期时）");allDay.setChecked(r.optBoolean("all_day"));form.addView(allDay);
        due=Ui.field(a,form,"待办截止日期（选填，YYYY-MM-DD）",r.optString("due_date"),false);due.setInputType(android.text.InputType.TYPE_CLASS_DATETIME|android.text.InputType.TYPE_DATETIME_VARIATION_DATE);
        form.addView(Ui.button(a,"选择截止日期",false,()->pickDate(due,"due_date")));form.addView(Ui.help(a,"没有安排具体时间的待办会在截止当天显示，不占用虚构的时间段。"));
        form.addView(Ui.text(a,"按星期重复",14,true));weekdays=new ChipGroup(a);String[] days={"一","二","三","四","五","六","日"};JSONArray selected=r.optJSONArray("weekdays");for(int i=1;i<=7;i++){Chip chip=new Chip(a);chip.setText(days[i-1]);chip.setCheckable(true);chip.setTag(i);if(selected!=null)for(int j=0;j<selected.length();j++)if(selected.optInt(j)==i)chip.setChecked(true);weekdays.addView(chip);}form.addView(weekdays);
        until=Ui.field(a,form,"重复结束日期（含当天，可留空）",r.optString("recurrence_until"),false);form.addView(Ui.button(a,"选择重复结束日期",false,()->pickDate(until,"recurrence_until")));
        minutes=Ui.field(a,form,"提前提醒分钟，可填多个（如 10,60,1440）",r.optString("reminder_offsets",Integer.toString(r.optInt("remind_minutes",10))),false);
    }
    public void repeatVisibility(boolean weekly){weekdays.setVisibility(weekly?android.view.View.VISIBLE:android.view.View.GONE);}
    private void pickDate(TextInputEditText field,String key){try{JSONObject draft=a.editorState.draft();LinearLayout page=a.fullPage("选择日期",()->a.edit(draft));LocalDate initial;try{initial=LocalDate.parse(field.getText().toString());}catch(Exception e){initial=LocalDate.now();}DatePicker picker=new DatePicker(a);picker.init(initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth(),null);page.addView(picker);page.addView(Ui.button(a,"确定日期",true,()->{try{a.edit(draft.put(key,LocalDate.of(picker.getYear(),picker.getMonth()+1,picker.getDayOfMonth()).toString()));}catch(Exception e){a.message(e.getMessage());}}));page.addView(Ui.button(a,"清除日期",false,()->{try{a.edit(draft.put(key,""));}catch(Exception e){a.message(e.getMessage());}}));}catch(Exception e){a.message(e.getMessage());}}
    public JSONObject apply(JSONObject r)throws JSONException{
        int t=type.getSelectedItemPosition();if(t>0){JSONObject selected=types.get(t-1);r.put("type_id",selected.optString("id"));if(!r.optString("type_id").equals(a.editorState==null?"":a.editorState.originalType()))r.put("kind",selected.optString("base_kind","event"));}
        r.put("parent_id",parent.getSelectedItemPosition()==0?"":projects.get(parent.getSelectedItemPosition()-1).optString("id"));
        if(r.optString("kind").equals("project"))r.put("parent_id","");
        r.put("status",status.getSelectedItemPosition()==2?"cancelled":status.getSelectedItemPosition()==1?"done":"planned").put("completed",status.getSelectedItemPosition()==1?r.optString("completed").isEmpty()?Instant.now().toString():r.optString("completed"):"");
        r.put("all_day",allDay.isChecked()).put("due_date",due.getText().toString().trim()).put("recurrence_until",until.getText().toString().trim()).put("reminder_offsets",minutes.getText().toString().trim());JSONArray days=new JSONArray();for(int i=0;i<weekdays.getChildCount();i++){Chip chip=(Chip)weekdays.getChildAt(i);if(chip.isChecked())days.put((Integer)chip.getTag());}r.put("weekdays",days);
        if(allDay.isChecked()&&!r.optString("start_at").isEmpty()){LocalDate from=LocalDateTime.parse(r.getString("start_at")).toLocalDate(),to=r.optString("end_at").isEmpty()?from:LocalDateTime.parse(r.getString("end_at")).toLocalDate();if(!to.isAfter(from))to=from.plusDays(1);r.put("start_at",from.atStartOfDay().toString()).put("end_at",to.atStartOfDay().toString());}
        return r;
    }
    public static int[] offsets(JSONObject r)throws Exception{String value=r.optString("reminder_offsets",Integer.toString(r.optInt("remind_minutes",10)));String[] pieces=value.split(",");if(pieces.length>5)throw new Exception("每个事项最多5个提醒");int[] values=new int[pieces.length];for(int i=0;i<pieces.length;i++){values[i]=Integer.parseInt(pieces[i].trim());if(values[i]<0||values[i]>525600)throw new Exception("提醒分钟需要在0–525600之间");}return values;}
}
