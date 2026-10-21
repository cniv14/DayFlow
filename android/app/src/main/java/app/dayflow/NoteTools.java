package app.dayflow;
import android.widget.TextView;
/** Basic text display; full Markdown, image and formula preview belongs to the optional plugin. */
public final class NoteTools {public static void render(TextView view,String markdown){view.setText(markdown);}}
