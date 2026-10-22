package app.dayflow;
import android.widget.*;
import com.google.android.material.chip.*;
import com.google.android.material.materialswitch.MaterialSwitch;
import org.json.*;

/** Forty native rows per page instead of retaining hundreds of switches at once. */
final class ReminderCenter {
    static void show(MainActivity a,int mode,int offset){try{LinearLayout page=a.fullPage("提醒中心",()->PlanHub.show(a));page.addView(Ui.text(a,"提醒中心",24,true));ChipGroup filters=new ChipGroup(a);filters.setSingleSelection(true);filters.setSelectionRequired(true);for(int i=0;i<3;i++){int choice=i;Chip chip=new Chip(a);chip.setText(new String[]{"全部","开启","暂停"}[i]);chip.setCheckable(true);chip.setChecked(i==mode);chip.setOnClickListener(v->show(a,choice,0));filters.addView(chip);}page.addView(filters);JSONArray rows=Data.get(a).reminderPage(mode,offset);for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);LinearLayout body=Ui.padded(a);body.addView(Ui.button(a,r.optString("title")+" · "+PlanTime.label(r),false,()->a.detail(r)));MaterialSwitch toggle=new MaterialSwitch(a);toggle.setText("提前 "+r.optString("reminder_offsets",Integer.toString(r.optInt("remind_minutes",10)))+" 分钟提醒");toggle.setChecked(r.optBoolean("reminder"));toggle.setOnCheckedChangeListener((button,on)->{try{JSONObject current=Data.get(a).find(r.getString("id"));if(current==null||current.optBoolean("deleted"))throw new Exception("事项已删除，请刷新");Data.get(a).save(current.put("reminder",on));a.changed();if(on)a.reminderPermission();}catch(Exception e){a.message(e.getMessage());}});body.addView(toggle);com.google.android.material.card.MaterialCardView card=Ui.card(a);card.addView(body);page.addView(card);}if(rows.length()==0)page.addView(Ui.help(a,"没有匹配的提醒。先给事项安排日期即可在这里管理。"));if(offset>0)page.addView(Ui.button(a,"上一页",false,()->show(a,mode,Math.max(0,offset-40))));if(rows.length()==40)page.addView(Ui.button(a,"下一页",false,()->show(a,mode,offset+40)));}catch(Exception e){a.message(e.getMessage());}}
}
