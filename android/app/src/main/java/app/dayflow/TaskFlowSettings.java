package app.dayflow;
import android.content.*;
import android.widget.*;
import com.google.android.material.textfield.TextInputEditText;
import androidx.javascriptengine.JavaScriptSandbox;

/** Native Android settings for the existing Windows app, with inline retained errors. */
public final class TaskFlowSettings {
    public static void show(MainActivity a){LinearLayout page=a.fullPage("连接 TaskFlow Windows",()->new SettingsPanels(a).sync());page.addView(Ui.text(a,"连接你的 Windows 端",24,true));page.addView(Ui.help(a,"在 TaskFlow Windows 的同步设置中启动服务并设置同步密码，然后填写电脑的局域网 IP 和该界面显示的端口。无需修改 Windows，也不用配对码。"));
        TextInputEditText address=Ui.field(a,page,"电脑地址（IP:端口或 HTTPS 地址）",a.prefs.getString("tf_address",""),false),password=Ui.field(a,page,"Windows 同步密码","",false);password.setInputType(129);TextView status=Ui.help(a,a.prefs.getString("tf_server_id","").isEmpty()?"尚未连接 Windows":("工作区 "+a.prefs.getString("tf_server_id","")));page.addView(status);
        page.addView(Ui.button(a,"检查连接",false,()->{String root=address.getText().toString();status.setText("正在检查电脑服务…");a.job(()->{org.json.JSONObject info=TaskFlowAuth.info(root);return "电脑服务正常 · "+info.optString("serverName");},()->status.setText("连接正常，可输入同步密码登录"),error->status.setText(error));}));
        page.addView(Ui.button(a,"连接 Windows",true,()->{String root=address.getText().toString(),secret=password.getText().toString();if(!JavaScriptSandbox.isSupported()){status.setText("当前系统 WebView 不支持 Windows 同步引擎，请更新 Android System WebView；本地数据和文件同步仍可用。");return;}status.setText("正在认证 Windows 同步密码…");a.job(()->TaskFlowAuth.login(a,root,secret),()->{password.setText("");status.setText("Windows 已连接，已开启自动同步。也可以立即同步。");},error->status.setText("连接失败："+error+"\n输入内容保留，可修改后重试。"));}));
        page.addView(Ui.button(a,"立即同步 Windows",false,()->a.job(()->TaskFlowSync.sync(a),()->status.setText(a.prefs.getString("sync_message","同步已完成")),error->status.setText(error))));page.addView(Ui.help(a,"日程、待办、长任务与类型显示在 Windows 原有界面。该 Windows 版本没有笔记编辑页；Android 笔记、收集箱与单次重复覆盖仍会保存在同一工作区的扩展数据中，可在其他 Android 端还原。"));
        page.addView(Ui.button(a,"仅在本机断开 Windows",false,()->{try{Secrets.put(a,"tf_token","");if(a.prefs.getString("sync_backend","dayflow").equals("taskflow"))a.prefs.edit().putBoolean("sync_auto",false).apply();SyncJob.configure(a);status.setText("已断开，保留本地数据及工作区绑定。Windows 端修改同步密码可撤销旧授权。");}catch(Exception e){status.setText(e.getMessage());}}));
    }
    static String logout(Context c)throws Exception{android.content.SharedPreferences p=c.getSharedPreferences("settings",0);if(p.getString("sync_backend","dayflow").equals("taskflow")){Secrets.put(c,"tf_token","");p.edit().putBoolean("sync_auto",false).apply();SyncJob.configure(c);return "已断开 Windows，保留本地数据";}String token=Secrets.get(c,"token");if(!token.isEmpty())Network.request(p.getString("server","")+"/v1/auth/logout",new org.json.JSONObject(),token);Secrets.put(c,"token","");p.edit().putBoolean("sync_auto",false).apply();SyncJob.configure(c);return "已退出，保留本地数据";}
}
