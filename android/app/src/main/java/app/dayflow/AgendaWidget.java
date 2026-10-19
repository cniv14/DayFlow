package app.dayflow;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.widget.RemoteViews;
import org.json.*;

public final class AgendaWidget extends AppWidgetProvider {
    public static void refresh(Context c){AppWidgetManager m=AppWidgetManager.getInstance(c);new AgendaWidget().onUpdate(c,m,m.getAppWidgetIds(new ComponentName(c,AgendaWidget.class)));}
    public void onUpdate(Context c,AppWidgetManager manager,int[] ids){
        StringBuilder text=new StringBuilder();
        try {JSONArray rows=Data.get(c).list(null,500);int count=0;
            for(int i=0;i<rows.length()&&count<5;i++){JSONObject r=rows.getJSONObject(i);String kind=r.optString("kind");
                if(!r.optString("completed").isEmpty()||kind.equals("note")||kind.equals("inbox"))continue;
                String date=r.optString("start_at");if(!date.isEmpty()&&date.compareTo(java.time.LocalDate.now().toString())<0)continue;
                text.append(date.isEmpty()?"待定":date.substring(5).replace("T"," ")).append("  ").append(r.optString("title")).append('\n');count++;
            }
        }catch(Exception ignored){}
        if(text.length()==0)text.append("暂时没有安排 · 点击记录");
        PendingIntent open=PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        for(int id:ids){RemoteViews views=new RemoteViews(c.getPackageName(),R.layout.widget);views.setTextViewText(R.id.widget_text,text.toString().trim());views.setOnClickPendingIntent(R.id.widget_root,open);manager.updateAppWidget(id,views);}
    }
}
