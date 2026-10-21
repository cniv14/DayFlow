package app.dayflow;
import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

final class GroupCaptureSettings {
    static void show(MainActivity a){
        LinearLayout page=a.fullPage("群聊与通知收集",a::home);
        MaterialSwitch enabled=new MaterialSwitch(a);enabled.setText("启用通知收集");enabled.setChecked(a.prefs.getBoolean("capture",false));page.addView(enabled);
        page.addView(Ui.button(a,"打开系统通知访问授权",false,()->a.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));
        TextInputEditText packages=Ui.field(a,page,"允许应用包名（英文逗号分隔）",a.prefs.getString("packages","com.tencent.mobileqq,com.tencent.mm,com.tencent.tim,com.google.android.gm"),true);
        TextInputEditText groups=Ui.field(a,page,"指定群名（每行一个，按通知中的名称填写）",a.prefs.getString("capture_groups",""),true);
        page.addView(Ui.help(a,"群名与通知会话标题精确匹配，不搜索正文。可写 QQ|群名、微信|群名 或 TIM|群名 限定应用。填写后，这些聊天应用仅收集匹配群；留空恢复日程关键词筛选。邮件仍按日程关键词筛选。"));
        MaterialSwitch all=new MaterialSwitch(a);all.setText("指定群的全部通知进入待确认收集箱");all.setChecked(a.prefs.getBoolean("capture_group_all",true));page.addView(all);
        page.addView(Ui.help(a,"仅能接收授权后系统实际发出的通知。静音、内容隐藏或前台未通知的消息无法读取，不读取历史聊天。不会自动上传 AI。待确认数量与最新内容会显示在桌面小组件，请注意桌面隐私。群筛选规则保留在本机，收集的事项参与全部数据同步。"));
        page.addView(Ui.button(a,"保存收集规则",true,()->{a.prefs.edit().putBoolean("capture",enabled.isChecked()).putBoolean("capture_group_all",all.isChecked()).putString("packages",packages.getText().toString().trim()).putString("capture_groups",groups.getText().toString().trim()).apply();a.message("已保存，下一条匹配通知将进入收集箱");}));
    }
    static String match(String rules,String pkg,Bundle extras){
        String[] candidates={String.valueOf(extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE,"")),String.valueOf(extras.getCharSequence(Notification.EXTRA_TITLE,"")),String.valueOf(extras.getCharSequence(Notification.EXTRA_SUB_TEXT,""))};
        for(String row:rules.split("\\R")){String name=row.trim();int separator=name.indexOf('|');if(separator>=0){String app=name.substring(0,separator).trim();name=name.substring(separator+1).trim();String expected=app.equalsIgnoreCase("QQ")?"com.tencent.mobileqq":app.equals("微信")?"com.tencent.mm":app.equalsIgnoreCase("TIM")?"com.tencent.tim":app;if(!expected.equals(pkg))continue;}if(name.isEmpty())continue;
            for(String value:candidates){String candidate=value.replaceFirst("^\\[\\d+条\\]\\s*","").trim();if(candidate.equals(name)||candidate.endsWith("("+name+")")||candidate.endsWith("（"+name+"）"))return name;}
        }return "";
    }
}
