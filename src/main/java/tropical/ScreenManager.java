package tropical;

import javafx.scene.layout.Pane;
import javafx.scene.input.KeyEvent;
import java.util.ArrayDeque;

/**
 * Manages a stack of screens (title, menus, gameplay, pause overlay).
 *
 * The top screen receives input. Screens below are paused.
 * Push = overlay (pause menu on top of gameplay).
 * Pop = return to previous screen.
 * Replace = transition (title → main menu).
 *
 * The container is a StackPane: it RESIZES children to fill the
 * window. A plain Pane doesn't, so menu roots sat at their preferred
 * size in the top-left with white filling the rest of the window
 * (playtest round 5). Push also KEEPS the screen below visible — the
 * pause overlay is a translucent scrim over the frozen gameplay.
 */
public class ScreenManager {
    private final ArrayDeque<Screen> stack = new ArrayDeque<>();
    private final javafx.scene.layout.StackPane container = new javafx.scene.layout.StackPane();

    public Pane getContainer() {
        return container;
    }

    public void push(Screen screen) {
        if (!stack.isEmpty()) {
            stack.peek().pause();
        }
        stack.push(screen);
        container.getChildren().add(screen.getRoot());  // on top — gameplay stays visible under the scrim
        screen.enter();
    }

    public void pop() {
        Screen top = stack.poll();
        if (top != null) {
            top.exit();
            container.getChildren().remove(top.getRoot());
        }
        if (!stack.isEmpty()) {
            Screen next = stack.peek();
            // Re-add in case a replace cleared it (pause pushed over a
            // replaced screen edge case)
            if (!container.getChildren().contains(next.getRoot())) {
                container.getChildren().add(next.getRoot());
            }
            next.resume();
        }
    }

    public void replace(Screen screen) {
        Screen top = stack.poll();
        if (top != null) {
            top.exit();
        }
        stack.push(screen);
        container.getChildren().setAll(screen.getRoot());
        screen.enter();
    }

    public void handleKey(KeyEvent e) {
        if (!stack.isEmpty()) {
            stack.peek().handleKey(e);
        }
    }

    /** The top screen, or null if the stack is empty. */
    public Screen peek() {
        return stack.peek();
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }
}
