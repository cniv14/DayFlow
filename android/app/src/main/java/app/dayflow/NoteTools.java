package app.dayflow;

import android.widget.*;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.ext.latex.JLatexMathPlugin;
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin;

/** Markwon renders directly into TextView; notes need no WebView or remote assets. */
public final class NoteTools {
    private static final java.util.concurrent.ThreadPoolExecutor math=new java.util.concurrent.ThreadPoolExecutor(1,1,20,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.ArrayBlockingQueue<>(64),new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
    static{math.allowCoreThreadTimeOut(true);}
    public static void render(TextView view,String markdown){android.content.Context c=view.getContext();Markwon.builder(c).usePlugin(StrikethroughPlugin.create()).usePlugin(TaskListPlugin.create(Ui.primary(c),Ui.primary(c),Ui.surface(c))).usePlugin(MarkwonInlineParserPlugin.create()).usePlugin(JLatexMathPlugin.create(view.getTextSize(),builder->{builder.inlinesEnabled(true);builder.executorService(math);builder.theme().textColor(Ui.ink(c));})).build().setMarkdown(view,mathSyntax(markdown));}
    // Markwon's native renderer uses $$ for inlines; preserve the user's original $...$ on disk.
    private static String mathSyntax(String text){StringBuilder out=new StringBuilder();boolean fenced=false;java.util.regex.Pattern inline=java.util.regex.Pattern.compile("(?<![\\\\$])\\$(?!\\$)([^$\\n]+?)(?<!\\\\)\\$(?!\\$)");for(String line:text.split("\\n",-1)){String trimmed=line.trim();if(trimmed.startsWith("```")||trimmed.startsWith("~~~"))fenced=!fenced;if(!fenced&&!trimmed.startsWith("$$")){String[] chunks=line.split("`",-1);for(int i=0;i<chunks.length;i+=2){java.util.regex.Matcher matcher=inline.matcher(chunks[i]);StringBuffer result=new StringBuffer();while(matcher.find())matcher.appendReplacement(result,java.util.regex.Matcher.quoteReplacement("$$"+matcher.group(1)+"$$"));matcher.appendTail(result);chunks[i]=result.toString();}line=String.join("`",chunks);}if(out.length()>0)out.append('\n');out.append(line);}return out.toString();}
}
