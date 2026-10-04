package tropical;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
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
        // RENAMED 2026-10-04. "Fruit Jump" was the working title; the game is Holdfast. The same rename
        // happened once before here, when this said TROPICAL PUNCH - the wrong game entirely.
        //
        // AND THE NAME IS NO LONGER OPEN. The note above used to end "This is the working title and it is still
        // open", which stopped being true the moment the rename landed and was left behind. It is settled.
        Label title = new Label("HOLDFAST");
        title.getStyleClass().add("title-text");
        title.setFont(Font.font("Arial", 80));

        // THE PREMISE, which is the game's own sentence and was already right. The Aside edition shows it under
        // the title; this screen showed only "Press Start", so the one line that says what the game IS was
        // missing from the first thing a player sees.
        Label subtitle = new Label("a climber, a sunset, and a way home");
        subtitle.setFont(Font.font("Arial", 26));
        subtitle.setTextFill(Color.web("#e8d9c0"));

        pressStart = new Label("Press Start");
        pressStart.getStyleClass().add("press-start");
        pressStart.setFont(Font.font("Arial", 36));

        VBox content = new VBox(26, title, subtitle, pressStart);
        content.getStyleClass().add("center-column");

        root = new StackPane(content);
        root.getStyleClass().add("screen-bg");

        // THE SUNSET, behind the words. "A climber, a sunset, and a way home" is the game's own sentence and this
        // screen had none of it - a flat background with two labels centred on it. The same backdrop was added to
        // the Aside edition's title screen today; this is that scene in this copy's idiom, which is a Canvas in
        // the StackPane rather than a canvas-drawn screen.
        //
        // It is drawn PROPORTIONALLY to whatever size the window is, and redrawn when that changes, so it does
        // not need to know how big the window is going to be - the layout here is centred and the window can be
        // anything from 1600x1200 down.
        Canvas sky = new Canvas();
        root.widthProperty().addListener((o, a, b) -> paintSky(sky));
        root.heightProperty().addListener((o, a, b) -> paintSky(sky));
        root.getChildren().add(0, sky);
        paintSky(sky);

        // The words sit on a bright sky, so they get a shadow rather than a panel over the art.
        javafx.scene.effect.DropShadow shadow =
                new javafx.scene.effect.DropShadow(18, 0, 4, Color.rgb(0, 0, 0, 0.75));
        title.setEffect(shadow);
        subtitle.setEffect(shadow);
        pressStart.setEffect(shadow);

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

    /**
     * Paint the horizon behind the words. The geometry lives in Skyline so this screen and the game-over screen
     * are the same place rather than two drawings that drift apart.
     */
    private void paintSky(Canvas c) {
        double w = root.getWidth(), h = root.getHeight();
        if (w <= 0 || h <= 0) return;
        c.setWidth(w);
        c.setHeight(h);
        Skyline.paint(c.getGraphicsContext2D(), w, h, Skyline.Mood.DUSK, true);
    }

}
