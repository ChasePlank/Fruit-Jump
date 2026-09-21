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
 * Title screen: "TROPICAL PUNCH" + "Press Start" (blinking).
 *
 * Any key or click transitions to main menu.
 */
public class TitleScreen extends Screen {
    private final StackPane root;
    private final Label pressStart;

    public TitleScreen(ScreenManager manager) {
        super(manager);

        Label title = new Label("TROPICAL PUNCH");
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
