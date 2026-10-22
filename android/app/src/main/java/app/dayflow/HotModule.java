package app.dayflow;

import android.widget.LinearLayout;

/** Runtime module ABI 1. Implementations use native views and return false for host fallback.
 * Do not keep Activity references or run background work after a view is detached. */
public interface HotModule {
    boolean render(android.app.Activity host,String screen,LinearLayout container) throws Exception;
}
