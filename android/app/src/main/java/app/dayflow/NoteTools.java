package app.dayflow;
import android.widget.TextView;
import io.noties.markwon.*;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
/** Basic CommonMark preview remains available without an extension. */
public final class NoteTools {
 public static void render(TextView view,String markdown){
  try{Markwon.builder(view.getContext()).usePlugin(StrikethroughPlugin.create())
   .usePlugin(TaskListPlugin.create(Ui.primary(view.getContext()),Ui.primary(view.getContext()),Ui.surface(view.getContext())))
   .usePlugin(new AbstractMarkwonPlugin(){public void configureConfiguration(MarkwonConfiguration.Builder config){
    config.linkResolver((target,link)->{try{android.net.Uri uri=android.net.Uri.parse(link);
     if(java.util.Arrays.asList("http","https","mailto","tel","geo").contains(uri.getScheme()))target.getContext().startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,uri));
    }catch(Exception ignored){}});
   }}).build().setMarkdown(view,markdown);
  }catch(OutOfMemoryError e){view.setText("当前内存不足以预览，请切回编辑；原文完整保留。");}catch(RuntimeException e){view.setText(markdown);}
 }
}
