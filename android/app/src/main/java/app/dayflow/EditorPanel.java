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
    private LocalDateTime start,end;private String kind;private LinearLayout scheduleGroup,noteTools;
    public EditorPanel(MainActivity activity,JSONObject record){a=activity;source=record;kind=record.optString("kind","event");
        LinearLayout form=a.fullPage("inbox".equals(kind)?"确认这条通知":"编辑事项",a::home);
        title=Ui.field(a,form,"标题",record.optString("title"),false);title.setId(5101);
        ChipGroup types=new ChipGroup(a);types.setSingleSelection(true);types.setSelectionRequired(true);String[] names={"日程","待办","笔记","课程"},values={"event","task","note","course"};
        for(int i=0;i<names.length;i++){String value=values[i];Chip chip=new Chip(a);chip.setText(names[i]);chip.setCheckable(true);chip.setChecked(value.equals(kind)||("inbox".equals(kind)&&i==0));chip.setOnClickListener(v->{kind=value;if(scheduleGroup!=null)scheduleGroup.setVisibility(kind.equals("note")?View.GONE:View.VISIBLE);if(noteTools!=null)noteTools.setVisibility(kind.equals("note")?View.VISIBLE:View.GONE);});types.addView(chip);}if(kind.equals("inbox"))kind="event";form.addView(types);
        content=Ui.field(a,form,"正文 / 备注",record.optString("content"),true);noteTools=Ui.column(a);noteTools.addView(NoteTools.toolbar(a,content));noteTools.addView(Ui.help(a,"支持 Markdown；笔记正文、置顶和附图都会同步。"));noteTools.addView(Ui.button(a,"预览笔记",false,this::preview));noteTools.setVisibility(kind.equals("note")?View.VISIBLE:View.GONE);form.addView(noteTools);scheduleGroup=Ui.column(a);form.addView(scheduleGroup);
        start=record.optString("start_at").isEmpty()?LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0):LocalDateTime.parse(record.optString("start_at"));end=record.optString("end_at").isEmpty()?start.plusHours(1):LocalDateTime.parse(record.optString("end_at"));
        scheduled=new MaterialSwitch(a);scheduled.setText("安排具体时间");scheduled.setChecked(!record.optString("start_at").isEmpty());scheduled.setPadding(0,Ui.dp(a,10),0,Ui.dp(a,10));scheduleGroup.addView(scheduled);
        LinearLayout timing=Ui.column(a);scheduleGroup.addView(timing);TextView startLabel=Ui.text(a,"",15,true),endLabel=Ui.text(a,"",15,true);timing.addView(startLabel);timing.addView(Ui.button(a,"选择开始时间",false,()->pickTime(false,startLabel,endLabel)));timing.addView(endLabel);timing.addView(Ui.button(a,"选择结束时间",false,()->pickTime(true,startLabel,endLabel)));updateLabels(startLabel,endLabel);
        hint=Ui.field(a,scheduleGroup,"大概什么时候？例如下周、发芽后",record.optString("time_hint"),false);
        scheduled.setOnCheckedChangeListener((b,on)->{timing.setVisibility(on?View.VISIBLE:View.GONE);((View)hint.getParent().getParent()).setVisibility(on?View.GONE:View.VISIBLE);});timing.setVisibility(scheduled.isChecked()?View.VISIBLE:View.GONE);if(scheduled.isChecked())((View)hint.getParent().getParent()).setVisibility(View.GONE);
        location=Ui.field(a,scheduleGroup,"地点（选填）",record.optString("location"),false);
        reminder=new MaterialSwitch(a);reminder.setText("提前 10 分钟提醒");reminder.setChecked(record.optBoolean("reminder"));scheduleGroup.addView(reminder);
        scheduleGroup.addView(Ui.help(a,"通知提醒受系统省电设置影响。也可在详情中添加到系统日历。"));
        scheduleGroup.addView(Ui.text(a,"重复",13,true));repeat=new Spinner(a);repeat.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,new String[]{"不重复","每天完成后生成下一次","每周完成后生成下一次","每月完成后生成下一次"}));String[] rules={"","daily","weekly","monthly"};for(int i=0;i<rules.length;i++)if(rules[i].equals(record.optString("repeat")))repeat.setSelection(i);scheduleGroup.addView(repeat);scheduleGroup.setVisibility(kind.equals("note")?View.GONE:View.VISIBLE);
        form.addView(Ui.button(a,"保存事项",true,this::save));a.toolbarAction("保存",this::save);
    }
    private void updateLabels(TextView one,TextView two){DateTimeFormatter f=DateTimeFormatter.ofPattern("M月d日 EEE HH:mm",java.util.Locale.CHINA);one.setText("开始  "+start.format(f));two.setText("结束  "+end.format(f));}
    private void pickTime(boolean ending,TextView one,TextView two){
        LinearLayout form=a.fullPage(ending?"结束时间":"开始时间",()->restore());LocalDateTime value=ending?end:start;
        DatePicker date=new DatePicker(a);date.init(value.getYear(),value.getMonthValue()-1,value.getDayOfMonth(),null);form.addView(date,new LinearLayout.LayoutParams(-1,-2));
        TimePicker time=new TimePicker(a);time.setIs24HourView(true);time.setHour(value.getHour());time.setMinute(value.getMinute());form.addView(time,new LinearLayout.LayoutParams(-1,-2));
        form.addView(Ui.button(a,"确定",true,()->{LocalDateTime picked=LocalDate.of(date.getYear(),date.getMonth()+1,date.getDayOfMonth()).atTime(time.getHour(),time.getMinute());if(ending)end=picked;else{long duration=Math.max(15,Duration.between(start,end).toMinutes());start=picked;end=picked.plusMinutes(duration);}restore();}));
    }
    private JSONObject snapshot()throws JSONException{JSONObject r=new JSONObject(source.toString());r.put("title",title.getText().toString().trim()).put("kind",kind).put("content",content.getText().toString()).put("location",location.getText().toString()).put("time_hint",hint.getText().toString()).put("start_at",scheduled.isChecked()?start.toString():"").put("end_at",scheduled.isChecked()?end.toString():"").put("reminder",reminder.isChecked()).put("remind_minutes",10).put("repeat",new String[]{"","daily","weekly","monthly"}[repeat.getSelectedItemPosition()]);return r;}
    public JSONObject draft(){try{return snapshot();}catch(JSONException e){return source;}}
    private void restore(){a.edit(draft());}
    private void preview(){JSONObject draft=draft();LinearLayout page=a.fullPage("笔记预览",()->a.edit(draft));page.addView(Ui.text(a,title.getText().toString(),24,true));TextView text=Ui.text(a,"",16,false);NoteTools.render(text,content.getText().toString());text.setTextIsSelectable(true);page.addView(text);page.addView(Ui.button(a,"继续编辑",true,()->a.edit(draft)));}
    private void save(){try{JSONObject r=snapshot();if(r.getString("title").isEmpty()){title.setError("请填写标题");title.requestFocus();return;}if(scheduled.isChecked()&&!end.isAfter(start))throw new Exception("结束时间需要晚于开始时间");Data.validate(r);Data.get(a).save(r);a.editorState=null;a.changed();a.home();a.message("已保存");if(reminder.isChecked())a.notificationPermission();}catch(Exception e){a.message(e.getMessage());}}
}
