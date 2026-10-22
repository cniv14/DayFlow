package app.dayflow;
import android.content.*;import android.widget.*;import org.json.*;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import java.util.*;
public final class UpdateCenter {
 static String updateModules(Context c){StringBuilder status=new StringBuilder();for(String id:ModuleUpdates.IDS){if(!UpdateSources.prefs(c).getBoolean("plugin_enabled_app.dayflow."+id,true))continue;try{status.append(ModuleUpdates.update(c,id));}catch(Exception e){status.append(id).append("：").append(e.getMessage());}status.append('\n');}if(UpdateSources.prefs(c).getBoolean("hot_enabled",false))try{status.append(HotUpdates.check(c));}catch(Exception e){status.append("界面模块：").append(e.getMessage());}String value=status.toString().trim();UpdateSources.prefs(c).edit().putString("updates_status",value).apply();return value;}
 public static void show(MainActivity a){SharedPreferences p=UpdateSources.prefs(a);LinearLayout page=a.fullPage("统一更新",a::home);page.addView(Ui.text(a,"应用与功能更新",24,true));page.addView(Ui.help(a,"应用 "+version(a)+" · AI助手、Markdown与黄历已内嵌。功能包无需另外安装，关闭重开对应功能后生效；基础APK仍由系统确认安装。"));
 Spinner source=TaskTypes.spinner(a,page,"所有更新来源",Arrays.asList("GitHub","已登录的日栖服务端"));source.setSelection(UpdateSources.server(a)?1:0);TextInputEditText github=Ui.field(a,page,"GitHub附件目录",p.getString("updates_github",UpdateSources.GITHUB),false);
 MaterialSwitch auto=new MaterialSwitch(a);auto.setText("每12小时自动检查（系统调度）");auto.setChecked(p.getBoolean("updates_auto",false));page.addView(auto);MaterialSwitch runtime=new MaterialSwitch(a);runtime.setText("同时启用主界面功能热更新");runtime.setChecked(p.getBoolean("hot_enabled",false));page.addView(runtime);
 Runnable save=()->{p.edit().putString("updates_source",source.getSelectedItemPosition()==1?"server":"github").putString("updates_github",github.getText().toString().trim()).putBoolean("updates_auto",auto.isChecked()).putBoolean("hot_enabled",runtime.isChecked()).apply();HotUpdates.reset();Updates.configureJob(a);HotUpdateJob.configure(a);};
 page.addView(Ui.button(a,"保存统一设置",false,()->{save.run();a.message("所有更新使用同一来源与自动检查设置");}));
 page.addView(Ui.button(a,"检查全部更新",true,()->{save.run();final JSONObject[] apk={null};a.job(()->{String modules=updateModules(a);try{apk[0]=Updates.check(a);}catch(Exception e){modules+="\n主包："+e.getMessage();}p.edit().putString("updates_status",modules).apply();return apk[0]==null?"更新检查完成，请查看各项结果":"有基础版本可更新";},()->{if(apk[0]!=null)new SettingsPanels(a).showUpdate(apk[0]);else show(a);});}));page.addView(Ui.help(a,p.getString("updates_status","尚未检查更新")));
 String[] labels={"AI助手","完整Markdown","黄历","语音识别"};for(int i=0;i<ModuleUpdates.IDS.length;i++){String id=ModuleUpdates.IDS[i];page.addView(Ui.text(a,labels[i]+" · "+p.getString("module_version_"+id,"内置版本"),17,true));page.addView(Ui.help(a,p.getString("module_status_"+id,"无需单独安装")));page.addView(Ui.button(a,"回退或恢复内置版本",false,()->a.job(()->ModuleUpdates.rollback(a,id),()->show(a))));}
 page.addView(Ui.button(a,"回退主界面模块",false,()->a.job(()->HotUpdates.rollback(a),()->show(a))));page.addView(Ui.help(a,"服务端更新均使用现有登录态与账号额度，不跨来源绕过限流。旧独立插件APK可自行卸载，卸载前先确认笔记和对话均已保存到日栖。Android 8使用内置功能并随主包升级；模块热加载支持Android 9及以上。"));
 }
 private static String version(Context c){try{return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}catch(Exception e){return "";}}
}
