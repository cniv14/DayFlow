package app.dayflow;
import android.view.*;
import android.widget.*;
import com.google.android.material.chip.*;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

/** Full-page editor. Dates use native calendar/time controls rather than ISO text fields. */
public final class EditorPanel {
    private final MainActivity a;private final JSONObject source;
    private final TextInputEditText title,content,location,hint;
    private final MaterialSwitch scheduled,reminder;private final Spinner repeat;
    private PlanOptions options;private LocalDateTime start,end;private String kind;private LinearLayout scheduleGroup;
    public EditorPanel(MainActivity activity,JSONObject record){a=activity;source=record;kind=record.optString("kind","event");if(kind.equals("inbox")){String proposed=record.optString("proposed_kind","event");kind=java.util.Arrays.asList("event","task","project","course").contains(proposed)?proposed:"event";}
        LinearLayout form=a.fullPage("inbox".equals(kind)?"确认这条通知":"编辑事项",a::home);
        title=Ui.field(a,form,"标题",record.optString("title"),false);title.setId(5101);
        ChipGroup types=new ChipGroup(a);types.setSingleSelection(true);types.setSelectionRequired(true);String[] names={"日程","待办","笔记","课程","长任务"},values={"event","task","note","course","project"};
        for(int i=0;i<names.length;i++){String value=values[i];Chip chip=new Chip(a);chip.setText(names[i]);chip.setCheckable(true);chip.setChecked(value.equals(kind)||("inbox".equals(kind)&&i==0));chip.setOnClickListener(v->{if("note".equals(value)){try{a.edit(draft().put("kind","note"));}catch(JSONException e){a.message(e.getMessage());}return;}kind=value;if(scheduleGroup!=null)scheduleGroup.setVisibility(kind.equals("note")?View.GONE:View.VISIBLE);});types.addView(chip);}if(kind.equals("inbox"))kind="event";form.addView(types);
        content=Ui.field(a,form,"正文 / 备注",record.optString("content"),true);scheduleGroup=Ui.column(a);form.addView(scheduleGroup);
        start=record.optString("start_at").isEmpty()?LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0):LocalDateTime.parse(record.optString("start_at"));end=record.optString("end_at").isEmpty()?start.plusHours(1):LocalDateTime.parse(record.optString("end_at"));
        scheduled=new MaterialSwitch(a);scheduled.setText("安排具体时间");scheduled.setChecked(!record.optString("start_at").isEmpty());scheduled.setPadding(0,Ui.dp(a,10),0,Ui.dp(a,10));scheduleGroup.addView(scheduled);
        LinearLayout timing=Ui.column(a);scheduleGroup.addView(timing);TextView startLabel=Ui.text(a,"",15,true),endLabel=Ui.text(a,"",15,true);timing.addView(startLabel);timing.addView(Ui.button(a,"选择开始时间",false,()->pickTime(false,startLabel,endLabel)));timing.addView(endLabel);timing.addView(Ui.button(a,"选择结束时间",false,()->pickTime(true,startLabel,endLabel)));updateLabels(startLabel,endLabel);
        hint=Ui.field(a,scheduleGroup,"大概什么时候？例如下周、发芽后",record.optString("time_hint"),false);
        scheduled.setOnCheckedChangeListener((b,on)->{timing.setVisibility(on?View.VISIBLE:View.GONE);((View)hint.getParent().getParent()).setVisibility(on?View.GONE:View.VISIBLE);});timing.setVisibility(scheduled.isChecked()?View.VISIBLE:View.GONE);if(scheduled.isChecked())((View)hint.getParent().getParent()).setVisibility(View.GONE);
        location=Ui.field(a,scheduleGroup,"地点（选填）",record.optString("location"),false);
        reminder=new MaterialSwitch(a);reminder.setText("开启日程提醒");reminder.setChecked(record.optBoolean("reminder"));scheduleGroup.addView(reminder);
        scheduleGroup.addView(Ui.help(a,"通知提醒受系统省电设置影响。也可在详情中添加到系统日历。"));
        scheduleGroup.addView(Ui.text(a,"重复",13,true));repeat=new Spinner(a);repeat.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,new String[]{"不重复","每天","每周（可选择星期）","每月同日"}));String[] rules={"","daily","weekly","monthly"};for(int i=0;i<rules.length;i++)if(rules[i].equals(record.optString("recurrence",record.optString("repeat"))))repeat.setSelection(i);scheduleGroup.addView(repeat);scheduleGroup.setVisibility(kind.equals("note")?View.GONE:View.VISIBLE);
        LinearLayout advanced=Ui.column(a);boolean expanded=kind.equals("project")||!record.optString("type_id").isEmpty()||!record.optString("parent_id").isEmpty()||!record.optString("due_date").isEmpty();advanced.setVisibility(expanded?View.VISIBLE:View.GONE);scheduleGroup.addView(Ui.button(a,"分类、项目、截止日期与更多选项",false,()->advanced.setVisibility(advanced.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE)));scheduleGroup.addView(advanced);options=new PlanOptions(a,advanced,record);options.repeatVisibility(repeat.getSelectedItemPosition()==2);repeat.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int position,long id){options.repeatVisibility(position==2);}});
        if(!record.optString("series_id").isEmpty())form.addView(Ui.button(a,"编辑整个重复系列",false,()->{try{JSONObject series=Data.get(a).find(record.getString("series_id"));if(series!=null)a.edit(series);}catch(Exception e){a.message(e.getMessage());}}));
        form.addView(Ui.button(a,"保存事项",true,this::save));
    }
    private void updateLabels(TextView one,TextView two){DateTimeFormatter f=DateTimeFormatter.ofPattern("M月d日 EEE HH:mm",java.util.Locale.CHINA);one.setText("开始  "+start.format(f));two.setText("结束  "+end.format(f));}
    private void pickTime(boolean ending,TextView one,TextView two){
        LinearLayout form=a.fullPage(ending?"结束时间":"开始时间",()->restore());LocalDateTime value=ending?end:start;
        DatePicker date=new DatePicker(a);date.init(value.getYear(),value.getMonthValue()-1,value.getDayOfMonth(),null);form.addView(date,new LinearLayout.LayoutParams(-1,-2));
        TimePicker time=new TimePicker(a);time.setIs24HourView(true);time.setHour(value.getHour());time.setMinute(value.getMinute());form.addView(time,new LinearLayout.LayoutParams(-1,-2));
        form.addView(Ui.button(a,"确定",true,()->{LocalDateTime picked=LocalDate.of(date.getYear(),date.getMonth()+1,date.getDayOfMonth()).atTime(time.getHour(),time.getMinute());if(ending)end=picked;else{long duration=Math.max(15,Duration.between(start,end).toMinutes());start=picked;end=picked.plusMinutes(duration);}restore();}));
    }
    private JSONObject snapshot()throws JSONException{JSONObject r=new JSONObject(source.toString());r.put("title",title.getText().toString().trim()).put("kind",kind).put("content",content.getText().toString()).put("location",location.getText().toString()).put("time_hint",hint.getText().toString()).put("start_at",scheduled.isChecked()?start.toString():"").put("end_at",scheduled.isChecked()?end.toString():"").put("reminder",reminder.isChecked()).put("remind_minutes",10).put("repeat",new String[]{"","daily","weekly","monthly"}[repeat.getSelectedItemPosition()]);if(options!=null)options.apply(r);r.put("repeat","").put("recurrence",r.optString("series_id").isEmpty()?new String[]{"","daily","weekly","monthly"}[repeat.getSelectedItemPosition()]:"");return r;}
    public String originalType(){return source.optString("type_id");}
    public JSONObject draft(){try{return snapshot();}catch(JSONException e){return source;}}
    private void restore(){a.edit(draft());}

    private void save(){try{JSONObject r=snapshot();if(r.getString("title").isEmpty()){title.setError("请填写标题");title.requestFocus();return;}if(scheduled.isChecked()&&!end.isAfter(start))throw new Exception("结束时间需要晚于开始时间");if(r.optBoolean("reminder"))PlanOptions.offsets(r);if(Recurrences.series(r)&&PlanTime.start(r)==null)throw new Exception("重复任务需要开始日期或截止日期");if(!r.optString("recurrence_until").isEmpty()&&PlanTime.start(r)!=null&&LocalDate.parse(r.getString("recurrence_until")).isBefore(PlanTime.start(r).toLocalDate()))throw new Exception("重复结束日期不能早于开始日期");Data.validate(r);JSONObject current=Data.get(a).find(r.getString("id"));if(current!=null&&!current.optString("revision").equals(source.optString("revision")))throw new Exception("事项已在其他操作中更新，请重新打开后编辑");Data.get(a).save(r);a.editorState=null;a.changed();a.home();a.message("已保存");if(reminder.isChecked())a.notificationPermission();}catch(Exception e){a.message(e.getMessage());}}
}
