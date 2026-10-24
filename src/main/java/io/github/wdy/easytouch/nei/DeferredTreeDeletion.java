package io.github.wdy.easytouch.nei;

/** Finish the click in the tree, then leave it before removing the group it renders. */
public final class DeferredTreeDeletion {
    public interface Context {
        boolean sourceCurrent();
        boolean inputReleased();
        boolean exitTree();
        void removeGroup();
    }

    private final Context context;
    private boolean done;

    public DeferredTreeDeletion(Context context) { this.context = context; }

    public void tick() {
        if (done) return;
        if (!context.sourceCurrent()) {
            cancel();
        } else if (context.inputReleased()) {
            done = true;
            // A vetoed screen change must leave the displayed tree's bookmarks intact.
            if (context.exitTree()) context.removeGroup();
        }
    }

    public boolean done() { return done; }
    public void cancel() { done = true; }
}
