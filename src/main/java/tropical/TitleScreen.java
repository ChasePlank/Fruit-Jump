package tropical;

import javafx.scene.Parent;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.control.Label;
import javafx.scene.text.Font;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

/**
 * Title screen: the game's name + "Press Start" (blinking).
 *
 * Any key or click transitions to main menu.
 */
public class TitleScreen extends Screen {
    private final StackPane root;
    private final Label pressStart;

    public TitleScreen(ScreenManager manager) {
        super(manager);

        // NOT "TROPICAL PUNCH". That is the name of a different, unbuilt game, and this file was the last
        // place still saying it - Sprite.java already carries the note that the radioactive premise belongs
        // to Tropical Punch "and not to this game (Kinger, Sept 28)", and the repository is called
        // Fruit-Jump. aside renamed its copy the same day. This is the working title and it is still open;
        // what it is not is the name of something else.
        Label title = new Label("FRUIT JUMP");
        title.getStyleClass().add("title-text");
        title.setFont(Font.font("Arial", 80));

        pressStart = new Label("Press Start");
        pressStart.getStyleClass().add("press-start");
        pressStart.setFont(Font.font("Arial", 36));

        VBox content = new VBox(40, title, pressStart);
        content.getStyleClass().add("center-column");

        root = new StackPane(content);
        root.getStyleClass().add("screen-bg");

        // Blinking "Press Start"
        PauseTransition blink = new PauseTransition(Duration.seconds(0.8));
        blink.setOnFinished(e -> {
            pressStart.setVisible(!pressStart.isVisible());
            blink.playFromStart();
        });
        blink.play();
    }

    @Override
    public Parent getRoot() {
        return root;
    }

    @Override
    public void handleKey(KeyEvent e) {
        if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) {
            manager.replace(new MainMenu(manager));
        }
    }
}
