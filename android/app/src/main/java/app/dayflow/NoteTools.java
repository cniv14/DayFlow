package app.dayflow;

import android.widget.*;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;

/** Markwon renders directly into TextView; notes need no WebView or remote assets. */
public final class NoteTools {
    public static void render(TextView view,String markdown){android.content.Context c=view.getContext();Markwon.builder(c).usePlugin(StrikethroughPlugin.create()).usePlugin(TaskListPlugin.create(Ui.primary(c),Ui.primary(c),Ui.surface(c))).build().setMarkdown(view,markdown);}
}
