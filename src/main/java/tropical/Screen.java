package tropical;

import javafx.scene.Parent;
import javafx.scene.input.KeyEvent;

/**
 * Base class for all screens (title, menus, gameplay).
 *
 * Lifecycle:
 * - enter(): screen becomes top of stack
 * - pause(): another screen pushed on top (game paused)
 * - resume(): screen becomes top again (unpause)
 * - exit(): screen removed from stack
 *
 * Input:
 * - handleKey(KeyEvent): top screen receives keyboard input
 */
public abstract class Screen {
    protected final ScreenManager manager;

    public Screen(ScreenManager manager) {
        this.manager = manager;
    }

    public abstract Parent getRoot();

    public void enter() {}
    public void pause() {}
    public void resume() {}
    public void exit() {}

    public void handleKey(KeyEvent e) {}
}
