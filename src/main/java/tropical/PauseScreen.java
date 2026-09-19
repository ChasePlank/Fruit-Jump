package tropical;

import javafx.scene.Parent;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.control.Label;
import javafx.scene.text.Font;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;

/**
 * Pause menu overlay: pushed on top of GameplayScreen.
 * The gameplay freezes underneath (its pause() stops the timer).
 * Resume pops; Quit to Menu replaces the whole stack.
 */
public class PauseScreen extends Screen {
    private final javafx.scene.layout.StackPane root;
    private final MenuButton[] buttons;
    private int focusIndex = 0;
    private final GameplayScreen game;

    public PauseScreen(ScreenManager manager, GameplayScreen game) {
        super(manager);
        this.game = game;

        Label title = new Label("Paused");
        title.getStyleClass().add("menu-title");
        title.setFont(Font.font("Arial", 32));

        MenuButton resume = new MenuButton("Resume", manager::pop);
        MenuButton quit = new MenuButton("Quit to Menu", () -> {
            // Replace everything with a fresh main menu
            manager.pop();      // remove pause
            manager.replace(new MainMenu(manager));  // swap gameplay for menu
        });

        buttons = new MenuButton[]{resume, quit};

        VBox menu = new VBox(20, title, resume, quit);
        menu.getStyleClass().add("center-column");

        root = new StackPane(menu);
        root.getStyleClass().add("pause-overlay");
    }

    @Override
    public Parent getRoot() {
        return root;
    }

    @Override
    public void enter() {
        buttons[focusIndex].requestFocus();
    }

    @Override
    public void handleKey(KeyEvent e) {
        if (e.getCode() == KeyCode.UP) {
            focusIndex = (focusIndex - 1 + buttons.length) % buttons.length;
            buttons[focusIndex].requestFocus();
        } else if (e.getCode() == KeyCode.DOWN) {
            focusIndex = (focusIndex + 1) % buttons.length;
            buttons[focusIndex].requestFocus();
        } else if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) {
            buttons[focusIndex].fire();
            e.consume();
        } else if (e.getCode() == KeyCode.ESCAPE) {
            manager.pop(); // ESC = resume
        }
    }
}
