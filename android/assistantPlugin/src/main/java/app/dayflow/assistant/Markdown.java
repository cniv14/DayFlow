package app.dayflow.assistant;

import android.text.method.LinkMovementMethod;
import android.widget.TextView;
import com.google.android.material.color.MaterialColors;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;

/** Renders assistant replies with the same Markwon pipeline used by the Markdown note plugin. */
final class Markdown {
    private static Markwon markwon;

    private Markdown() {}

    static void render(TextView view, String source) {
        if (source == null || source.isEmpty()) {
            view.setText("");
            return;
        }
        try {
            if (markwon == null) {
                int accent = MaterialColors.getColor(view, android.R.attr.colorAccent);
                int surface = MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurfaceContainerLow);
                markwon = Markwon.builder(view.getContext().getApplicationContext())
                        .usePlugin(StrikethroughPlugin.create())
                        .usePlugin(TaskListPlugin.create(accent, accent, surface))
                        .usePlugin(TablePlugin.create(view.getContext()))
                        .usePlugin(HtmlPlugin.create())
                        .usePlugin(LinkifyPlugin.create(android.text.util.Linkify.WEB_URLS | android.text.util.Linkify.EMAIL_ADDRESSES))
                        .build();
            }
            markwon.setMarkdown(view, source);
            view.setMovementMethod(LinkMovementMethod.getInstance());
        } catch (Throwable error) {
            // Never lose the assistant's answer just because a markdown extension cannot render it.
            view.setText(source);
        }
    }
}
