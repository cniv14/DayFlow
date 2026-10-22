package app.dayflow.hotfix;

import android.app.Activity;
import android.widget.*;
import app.dayflow.HotModule;

/** Small native example. Not enabled automatically: choose the example URL to try it. */
public final class Entry implements HotModule {
    public boolean render(Activity host,String screen,LinearLayout page)throws Exception{
        if(!"agenda".equals(screen))return false;
        TextView heading=new TextView(host);heading.setText("功能热更新已生效");heading.setTextSize(22);page.addView(heading);
        TextView hint=new TextView(host);hint.setText("此页面来自签名数据包，无需重新安装APK。今日待办继续使用本地数据。");hint.setTextSize(14);page.addView(hint);
        // Use the base's existing daily list, including projects and floating todos.
        Class.forName("app.dayflow.DailyTasks").getMethod("today",Class.forName("app.dayflow.MainActivity"),LinearLayout.class).invoke(null,host,page);
        return true;
    }
}
