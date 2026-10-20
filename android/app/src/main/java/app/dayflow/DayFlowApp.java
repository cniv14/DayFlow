package app.dayflow;
import android.app.Application;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;
public final class DayFlowApp extends Application {
    public void onCreate(){super.onCreate();SharedPreferences p=getSharedPreferences("settings",0);
        applyTheme(p.getString("theme","system"));
        DynamicColors.applyToActivitiesIfAvailable(this,new DynamicColorsOptions.Builder().setPrecondition((activity,theme)->getSharedPreferences("settings",0).getBoolean("dynamic_color",true)).build());
        if(!p.getBoolean("md_files_ready",false))new Thread(()->{try{Data.get(this).materializeNotes();p.edit().putBoolean("md_files_ready",true).apply();}catch(Exception ignored){/* Retry migration next launch; never remove original records. */}},"markdown-migration").start();
    }
    public static void applyTheme(String value){AppCompatDelegate.setDefaultNightMode("dark".equals(value)?AppCompatDelegate.MODE_NIGHT_YES:"light".equals(value)?AppCompatDelegate.MODE_NIGHT_NO:AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);}
}
