package app.dayflow;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,application=Application.class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
public class InboxReviewTest {
    private Data db;
    private ActivityController<MainActivity> controller;

    @Before public void setup()throws Exception{
        Context context=RuntimeEnvironment.getApplication();
        context.getSharedPreferences("settings",0).edit().putBoolean("motion",false).putBoolean("hot_enabled",false).apply();
        context.deleteDatabase("dayflow.db");
        db=Data.get(context);
    }

    @After public void cleanup()throws Exception{
        if(controller!=null)controller.pause().stop().destroy();
        db.close();
        java.lang.reflect.Field instance=Data.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null,null);
    }

    private JSONObject capture(String title)throws Exception{
        JSONObject record=Data.make("inbox",title).put("confirmation_status","pending");
        db.save(record);
        return record;
    }

    @Test public void confirmationPreservesFullContentAndUnknownTime()throws Exception{
        String body="通知正文".repeat(1500);
        JSONObject source=Data.make("inbox","时间待定的通知").put("content",body)
            .put("image_base64","a".repeat(40000)).put("time_hint","下周，具体时间待定")
            .put("proposed_kind","task").put("location","图书馆");
        db.save(source);
        JSONObject preview=db.inboxPage(0).getJSONObject(0);
        assertTrue(preview.optBoolean("_dayflow_preview"));
        InboxReview.confirm(db,preview);
        JSONObject confirmed=db.find(source.getString("id"));
        assertEquals("task",confirmed.getString("kind"));
        assertEquals("confirmed",confirmed.getString("confirmation_status"));
        assertEquals(body,confirmed.getString("content"));
        assertEquals(source.getString("image_base64"),confirmed.getString("image_base64"));
        assertEquals("",confirmed.getString("start_at"));
        assertEquals("下周，具体时间待定",confirmed.getString("time_hint"));
        assertEquals("图书馆",confirmed.getString("location"));
        assertEquals(0,db.inboxCount());
    }

    @Test public void staleAndRepeatedConfirmationsCannotOverwriteRecords()throws Exception{
        JSONObject source=capture("原通知"),stale=new JSONObject(source.toString());
        source.put("title","已经更新的通知");db.save(source);
        assertThrows(Exception.class,()->InboxReview.confirm(db,stale));
        assertEquals("inbox",db.find(source.getString("id")).getString("kind"));
        InboxReview.confirm(db,source);
        assertThrows(Exception.class,()->InboxReview.confirm(db,source));
        assertEquals("已经更新的通知",db.find(source.getString("id")).getString("title"));
    }

    @Test public void invalidTimingRemainsInInboxForEditing()throws Exception{
        JSONObject source=capture("待校正时间");
        source.put("start_at","2026-10-25T15:00").put("end_at","2026-10-25T14:00");db.save(source);
        assertThrows(Exception.class,()->InboxReview.confirm(db,source));
        assertEquals(1,db.inboxCount());
        assertEquals("pending",db.find(source.getString("id")).getString("confirmation_status"));
    }

    @Test public void deletedInformationCannotBeConfirmed()throws Exception{
        JSONObject source=capture("已移除的通知"),preview=new JSONObject(source.toString());
        source.put("deleted",true);db.save(source);
        assertThrows(Exception.class,()->InboxReview.confirm(db,preview));
        assertTrue(db.find(source.getString("id")).getBoolean("deleted"));
    }

    @Test public void widgetOpensListOnColdAndWarmLaunch()throws Exception{
        JSONObject first=capture("第一条通知");capture("第二条通知");
        Intent intent=AgendaWidget.inboxIntent(RuntimeEnvironment.getApplication());
        controller=Robolectric.buildActivity(MainActivity.class,intent).setup();
        MainActivity activity=controller.get();
        assertNull(activity.editorState);
        assertEquals(2,list(activity).getAdapter().getItemCount());
        activity.edit(first);
        assertNotNull(activity.editorState);
        controller.newIntent(intent);
        assertNull(activity.editorState);
        assertEquals(2,list(activity).getAdapter().getItemCount());
    }

    @Test public void editingCanBeCancelledAndSavedBackToQueue()throws Exception{
        JSONObject source=capture("需要修改的通知");capture("另一条通知");
        controller=Robolectric.buildActivity(MainActivity.class,AgendaWidget.inboxIntent(RuntimeEnvironment.getApplication())).setup();
        MainActivity activity=controller.get();
        activity.edit(source);
        activity.getOnBackPressedDispatcher().onBackPressed();
        assertEquals(2,db.inboxCount());
        assertEquals(2,list(activity).getAdapter().getItemCount());
        activity.edit(db.find(source.getString("id")));
        TextView title=activity.findViewById(5101);title.setText("核对后的标题");
        text(activity.getWindow().getDecorView(),"已核验，加入日程").performClick();
        assertEquals("核对后的标题",db.find(source.getString("id")).getString("title"));
        assertEquals(1,list(activity).getAdapter().getItemCount());
    }

    @Test public void rowConfirmsWithoutOpeningEditorAndRefreshesQueue()throws Exception{
        JSONObject source=capture("可以直接确认的通知");
        controller=Robolectric.buildActivity(MainActivity.class,AgendaWidget.inboxIntent(RuntimeEnvironment.getApplication())).setup();
        MainActivity activity=controller.get();
        View decor=activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY));
        decor.layout(0,0,1080,1920);
        TextView confirm=text(decor,"确认");assertNotNull(confirm);confirm.performClick();
        long deadline=System.nanoTime()+5_000_000_000L;
        while(list(activity).getAdapter().getItemCount()!=0&&System.nanoTime()<deadline){
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Thread.sleep(10);
        }
        assertNull(activity.editorState);
        assertEquals("confirmed",db.find(source.getString("id")).getString("confirmation_status"));
        assertEquals(0,list(activity).getAdapter().getItemCount());
    }

    @Test public void queuePaginatesAndClampsAfterLastItemIsConfirmed()throws Exception{
        for(int i=0;i<51;i++)capture("通知 "+i);
        controller=Robolectric.buildActivity(MainActivity.class,AgendaWidget.inboxIntent(RuntimeEnvironment.getApplication())).setup();
        MainActivity activity=controller.get();
        assertEquals(50,list(activity).getAdapter().getItemCount());
        text(activity.getWindow().getDecorView(),"下一页").performClick();
        assertEquals(1,list(activity).getAdapter().getItemCount());
        Bundle saved=new Bundle();controller.saveInstanceState(saved);
        assertEquals(50,saved.getInt("inbox_offset"));
        controller.recreate();activity=controller.get();
        assertEquals(1,list(activity).getAdapter().getItemCount());
        InboxReview.confirm(db,db.inboxPage(50).getJSONObject(0));activity.home();
        assertEquals(50,list(activity).getAdapter().getItemCount());
        assertNull(text(activity.getWindow().getDecorView(),"下一页"));
    }

    private static RecyclerView list(MainActivity activity){return (RecyclerView)find(activity.getWindow().getDecorView(),view->view instanceof RecyclerView);}
    private static TextView text(View root,String value){return (TextView)find(root,view->view instanceof TextView&&value.contentEquals(((TextView)view).getText()));}
    private static View find(View root,java.util.function.Predicate<View> match){
        if(match.test(root))return root;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),match);if(found!=null)return found;}}
        return null;
    }
}
