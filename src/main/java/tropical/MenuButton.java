package tropical;

import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Font;

/**
 * Reusable menu button with hover states and audio feedback.
 *
 * Styling is in style.css:
 * - .menu-button: normal state
 * - .menu-button:hover: hover state
 *
 * Usage:
 *   MenuButton btn = new MenuButton("New Game", () -> manager.replace(new MainMenu(manager)));
 */
public class MenuButton extends Button {
    private final Runnable action;

    public MenuButton(String text, Runnable action) {
        super(text);
        this.action = action;

        getStyleClass().add("menu-button");
        setFont(Font.font("Arial", 32));
        setMinWidth(400);
        setFocusTraversable(true);

        // Mouse click
        setOnMouseClicked((MouseEvent e) -> fire());

        // Keyboard activation is owned by the containing screen
        // (MainMenu.handleKey) — JavaFX's Button ALSO fires natively on
        // ENTER/SPACE when focused, so any button-level key listener
        // would double- or triple-fire. consume() on the scene key
        // handler is the other half of this contract.

        // Hover changes style via CSS
        hoverProperty().addListener((obs, wasHover, isHover) -> {
            if (isHover) {
                // Audio: UI_HOVER (when audio system is integrated)
            }
        });
    }

    public void fire() {
        // Audio: UI_SELECT
        action.run();
    }
}
