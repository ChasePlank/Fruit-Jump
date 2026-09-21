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
 */
public class ScreenManager {
    private final ArrayDeque<Screen> stack = new ArrayDeque<>();
    private final Pane container = new Pane();

    public Pane getContainer() {
        return container;
    }

    public void push(Screen screen) {
        if (!stack.isEmpty()) {
            stack.peek().pause();
        }
        stack.push(screen);
        container.getChildren().setAll(screen.getRoot());
        screen.enter();
    }

    public void pop() {
        Screen top = stack.poll();
        if (top != null) {
            top.exit();
        }
        if (!stack.isEmpty()) {
            Screen next = stack.peek();
            container.getChildren().setAll(next.getRoot());
            next.resume();
        } else {
            container.getChildren().clear();
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
