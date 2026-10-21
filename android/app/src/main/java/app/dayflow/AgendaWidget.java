package app.dayflow;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.widget.RemoteViews;
import android.os.*;
import android.view.View;
import android.content.res.Configuration;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.json.*;

public final class AgendaWidget extends AppWidgetProvider {
    public static void refresh(Context c){AppWidgetManager m=AppWidgetManager.getInstance(c);new AgendaWidget().onUpdate(c,m,m.getAppWidgetIds(new ComponentName(c,AgendaWidget.class)));}
    private static PendingIntent open(Context c,String id,boolean quick){return open(c,id,quick,null);}
    private static PendingIntent open(Context c,String id,boolean quick,JSONObject record){Intent intent=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);if(id!=null)intent.putExtra("record_id",id);if(record!=null)intent.putExtra("series_id",record.optString("series_id")).putExtra("occurrence_date",record.optString("occurrence_date"));if(quick)intent.putExtra("quick",true).putExtra("quick_kind","task");intent.setData(android.net.Uri.parse("dayflow://widget/"+(quick?"new":id==null?"home":id)));return PendingIntent.getActivity(c,0,intent,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);}
    public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle options){onUpdate(c,m,new int[]{id});}
    public void onUpdate(Context c,AppWidgetManager manager,int[] ids){
        SharedPreferences prefs=c.getSharedPreferences("settings",0);String theme=prefs.getString("theme","system");boolean dark=theme.equals("dark")||(theme.equals("system")&&(c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);int ink=dark?0xffe0e8e0:0xff18201d,muted=dark?0xffb3c1b8:0xff58675c,accent=dark?0xffa0d0bf:0xff386a5c;
        accent=ThemeColors.accent(c,dark);
        JSONArray rows=new JSONArray();try{rows=Data.get(c).range(LocalDate.now(),LocalDate.now().plusDays(7),false);}catch(Exception ignored){}
        JSONObject pending=null;int pendingCount=0;try{if(prefs.getBoolean("widget_inbox",true)){pendingCount=Data.get(c).inboxCount();pending=Data.get(c).list("inbox",1).optJSONObject(0);}}catch(Exception ignored){}
        int[] rowIds={R.id.widget_row1,R.id.widget_row2,R.id.widget_row3},titles={R.id.widget_title1,R.id.widget_title2,R.id.widget_title3},meta={R.id.widget_meta1,R.id.widget_meta2,R.id.widget_meta3},opens={R.id.widget_open1,R.id.widget_open2,R.id.widget_open3},done={R.id.widget_done1,R.id.widget_done2,R.id.widget_done3};
        for(int id:ids){RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget);v.setInt(R.id.widget_root,"setBackgroundResource",dark?R.drawable.widget_bg_dark:R.drawable.widget_bg);v.setTextColor(R.id.widget_date,accent);v.setTextColor(R.id.widget_weather,muted);v.setTextColor(R.id.widget_empty,muted);v.setTextColor(R.id.widget_add,accent);v.setTextColor(R.id.widget_footer,muted);v.setTextViewText(R.id.widget_date,LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 EEEE",Locale.CHINA)));v.setTextViewText(R.id.widget_weather,prefs.getString("weather_cache","").isEmpty()?"日栖 · 今日安排":prefs.getString("city","")+"  "+prefs.getString("weather_cache",""));v.setViewVisibility(R.id.widget_empty,rows.length()==0?View.VISIBLE:View.GONE);
            v.setViewVisibility(R.id.widget_inbox,pending==null?View.GONE:View.VISIBLE);v.setTextColor(R.id.widget_inbox,accent);if(pending!=null){v.setTextViewText(R.id.widget_inbox,"待确认 "+pendingCount+" 条 · "+pending.optString("title"));v.setOnClickPendingIntent(R.id.widget_inbox,open(c,pending.optString("id"),false,pending));}
            int height=manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,240),limit=height<180?1:height<230?2:3;if(pending!=null)limit=Math.max(0,limit-1);
            for(int n=0;n<3;n++){v.setViewVisibility(rowIds[n],n<rows.length()&&n<limit?View.VISIBLE:View.GONE);if(n>=rows.length()||n>=limit)continue;JSONObject r=rows.optJSONObject(n);String rid=r.optString("id");v.setTextViewText(titles[n],r.optString("title"));LocalDateTime start=PlanTime.start(r);v.setTextViewText(meta[n],(start.toLocalDate().equals(LocalDate.now())?"今天 ":start.format(DateTimeFormatter.ofPattern("M/d ")))+(r.optString("start_at").isEmpty()?"截止":r.optBoolean("all_day")?"全天":start.toLocalTime().toString())+"  "+r.optString("location"));v.setTextColor(titles[n],ink);v.setTextColor(meta[n],muted);v.setTextColor(done[n],accent);v.setOnClickPendingIntent(opens[n],open(c,rid,false,r));Intent complete=new Intent(c,AgendaWidget.class).setAction("app.dayflow.WIDGET_COMPLETE").setData(android.net.Uri.parse("dayflow://complete/"+rid)).putExtra("id",rid).putExtra("series_id",r.optString("series_id")).putExtra("occurrence_date",r.optString("occurrence_date"));v.setOnClickPendingIntent(done[n],PendingIntent.getBroadcast(c,0,complete,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));}
            v.setTextViewText(R.id.widget_footer,rows.length()>limit?"另有 "+(rows.length()-limit)+" 项 · 点击查看":"点击事项查看 · ○ 标记完成");v.setOnClickPendingIntent(R.id.widget_add,open(c,null,true));v.setOnClickPendingIntent(R.id.widget_header,open(c,null,false));v.setOnClickPendingIntent(R.id.widget_empty,open(c,null,true));v.setOnClickPendingIntent(R.id.widget_footer,open(c,null,false));manager.updateAppWidget(id,v);
        }
    }
    public void onReceive(Context c,Intent intent){if("app.dayflow.WIDGET_COMPLETE".equals(intent.getAction())){PendingResult result=goAsync();new Thread(()->{try{JSONObject r=Data.get(c).resolve(intent.getStringExtra("id"),intent.getStringExtra("series_id"),intent.getStringExtra("occurrence_date"));if(r!=null&&!r.optBoolean("deleted")&&r.optString("completed").isEmpty())TaskActions.complete(c,r);ReminderReceiver.refresh(c);refresh(c);}catch(Exception ignored){}finally{result.finish();}},"widget-action").start();}else super.onReceive(c,intent);}
}
