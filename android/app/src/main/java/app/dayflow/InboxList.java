package app.dayflow;

import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import org.json.JSONArray;
import org.json.JSONObject;

/** Paged human-review queue shared by the inbox tab and the home-screen widget. */
final class InboxList {
    private static final int PAGE_SIZE=50;
    private final MainActivity a;
    private int offset;
    private Parcelable scrollState;
    private LinearLayoutManager layout;

    InboxList(MainActivity activity){a=activity;}

    void reset(){offset=0;scrollState=null;layout=null;}
    void restore(Bundle state){offset=Math.max(0,state.getInt("inbox_offset",0));scrollState=state.getParcelable("inbox_scroll");}
    void save(Bundle state){remember();state.putInt("inbox_offset",offset);state.putParcelable("inbox_scroll",scrollState);}
    private void remember(){if(layout!=null)scrollState=layout.onSaveInstanceState();}

    void show(FrameLayout container)throws Exception{
        int total=Data.get(a).inboxCount();
        int lastOffset=total==0?0:((total-1)/PAGE_SIZE)*PAGE_SIZE;
        if(offset>lastOffset){offset=lastOffset;scrollState=null;}
        JSONArray rows=Data.get(a).inboxPage(offset);
        LinearLayout page=Ui.column(a);
        page.setPadding(Ui.dp(a,16),0,Ui.dp(a,16),0);
        container.addView(page,new FrameLayout.LayoutParams(-1,-1));
        page.addView(Ui.text(a,"待确认信息",24,true));
        page.addView(Ui.help(a,total==0?"没有待确认信息。新收集的通知会显示在这里。":"共 "+total+" 条 · 信息无误可直接确认，需要修正时点修改。"));
        LinearLayout add=Ui.row(a);
        add.addView(Ui.button(a,"＋ 粘贴通知",true,()->a.newRecord("inbox")),new LinearLayout.LayoutParams(0,-2,1));
        add.addView(Ui.button(a,"截图",false,()->a.pickFile(106,"image/*")),new LinearLayout.LayoutParams(Ui.dp(a,90),-2));
        page.addView(add);
        LinearLayout tools=Ui.row(a);
        tools.addView(Ui.button(a,"一键交给AI",false,()->{remember();InboxAgent.quick(a);}),new LinearLayout.LayoutParams(0,-2,1));
        tools.addView(Ui.button(a,"收集规则",false,()->{remember();GroupCaptureSettings.show(a);}),new LinearLayout.LayoutParams(0,-2,1));
        page.addView(tools);

        RecyclerView list=new RecyclerView(a);
        layout=new LinearLayoutManager(a);
        list.setLayoutManager(layout);
        list.setAdapter(new RecyclerView.Adapter<Holder>(){
            public Holder onCreateViewHolder(ViewGroup parent,int type){
                MaterialCardView card=Ui.card(a);
                RecyclerView.LayoutParams params=new RecyclerView.LayoutParams(-1,-2);
                params.setMargins(0,Ui.dp(a,4),0,Ui.dp(a,4));
                card.setLayoutParams(params);
                return new Holder(card);
            }
            public void onBindViewHolder(Holder holder,int position){
                JSONObject record=rows.optJSONObject(position);
                holder.card.removeAllViews();
                LinearLayout body=a.recordContents(record);
                if(!record.optString("location").isEmpty())body.addView(Ui.help(a,"地点："+record.optString("location")));
                if(!record.optString("source").isEmpty())body.addView(Ui.help(a,"来源："+record.optString("source")));
                LinearLayout actions=Ui.row(a);
                actions.addView(Ui.button(a,"原文",false,()->{remember();a.detail(record);}),new LinearLayout.LayoutParams(0,-2,1));
                actions.addView(Ui.button(a,"修改",false,()->review(record)),new LinearLayout.LayoutParams(0,-2,1));
                actions.addView(Ui.button(a,"确认",true,()->confirm(record)),new LinearLayout.LayoutParams(0,-2,1));
                body.addView(actions);
                holder.card.addView(body);
                holder.card.setOnClickListener(v->{remember();a.detail(record);});
            }
            public int getItemCount(){return rows.length();}
        });
        page.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        if(scrollState!=null)layout.onRestoreInstanceState(scrollState);
        if(total>PAGE_SIZE){
            page.addView(Ui.help(a,"第 "+(offset+1)+"–"+(offset+rows.length())+" 条 / 共 "+total+" 条"));
            LinearLayout paging=Ui.row(a);
            View previous=Ui.button(a,"上一页",false,()->move(-PAGE_SIZE));
            previous.setEnabled(offset>0);
            paging.addView(previous,new LinearLayout.LayoutParams(0,-2,1));
            View next=Ui.button(a,"下一页",false,()->move(PAGE_SIZE));
            next.setEnabled(offset+rows.length()<total);
            paging.addView(next,new LinearLayout.LayoutParams(0,-2,1));
            page.addView(paging);
        }
    }

    private void move(int delta){offset=Math.max(0,offset+delta);scrollState=null;layout=null;a.home();}
    private void review(JSONObject record){remember();a.edit(record);}
    private void confirm(JSONObject record){
        remember();
        final JSONObject[] saved={null};
        a.job(()->{saved[0]=InboxReview.confirm(Data.get(a),record);return "已确认，加入日程";},()->{
            a.home();
            if(saved[0].optBoolean("reminder"))a.reminderPermission();
        },error->a.home());
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        Holder(MaterialCardView view){super(view);card=view;}
    }
}
