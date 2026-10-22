package app.dayflow;
import android.content.SharedPreferences;
import android.widget.*;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import java.util.Arrays;

public final class HotUpdateScreen {
    public static void show(MainActivity a){
        SharedPreferences p=a.getSharedPreferences("settings",0);LinearLayout page=a.fullPage("功能热更新",()->new SettingsPanels(a).updates());
        page.addView(Ui.text(a,"功能热更新",24,true));page.addView(Ui.help(a,"下载签名功能模块，无需安装APK；返回主界面后生效。新增系统权限等基础能力仍需升级主包。"));
        page.addView(Ui.help(a,"本地模块："+p.getString("hot_version","未安装")+"\n"+p.getString("hot_status","默认使用基础版本")));
        MaterialSwitch enabled=new MaterialSwitch(a);enabled.setText("启用已签名的功能热更新");enabled.setChecked(p.getBoolean("hot_enabled",false));page.addView(enabled);
        enabled.setOnCheckedChangeListener((v,on)->{p.edit().putBoolean("hot_enabled",on).apply();HotUpdates.reset();HotUpdateJob.configure(a);});
        Spinner source=TaskTypes.spinner(a,page,"获取来源",Arrays.asList("GitHub","已登录的日栖服务端"));source.setSelection("server".equals(p.getString("hot_source","github"))?1:0);
        TextInputEditText feed=Ui.field(a,page,"GitHub签名包地址（服务端模式忽略）",p.getString("hot_feed",HotUpdates.DEFAULT_FEED),false);
        MaterialSwitch automatic=new MaterialSwitch(a);automatic.setText("每12小时自动检查并应用（由系统调度）");automatic.setChecked(p.getBoolean("hot_auto",false));page.addView(automatic);
        Runnable save=()->{p.edit().putString("hot_source",source.getSelectedItemPosition()==1?"server":"github").putString("hot_feed",feed.getText().toString().trim()).putBoolean("hot_auto",automatic.isChecked()).apply();HotUpdateJob.configure(a);};
        page.addView(Ui.button(a,"保存设置",false,()->{save.run();a.message("已保存热更新设置");}));
        page.addView(Ui.button(a,"下载并应用热更新",true,()->{save.run();if(!enabled.isChecked()){a.message("请先开启功能热更新");return;}a.job(()->HotUpdates.check(a),()->show(a));}));
        page.addView(Ui.button(a,"回退上一模块",false,()->a.job(()->HotUpdates.rollback(a),()->show(a))));
        page.addView(Ui.help(a,"服务端使用现有账号登录态，每账号共享请求额度；不会自动切换到GitHub绕过限制。关闭开关始终回到基础界面，设置入口不会被热更新替换。"));
    }
}
