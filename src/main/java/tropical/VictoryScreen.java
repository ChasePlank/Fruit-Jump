package tropical;

import javafx.scene.Parent;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.canvas.Canvas;
import javafx.scene.paint.Color;
import javafx.scene.control.Label;
import javafx.scene.text.Font;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;

/**
 * Game over screen: displayed when player loses all lives.
 * Shows final stats and offers retry or return to menu.
 */
public class VictoryScreen extends Screen {
    private final StackPane root;
    private final int levelReached;
    private final double playTime;
    
    public VictoryScreen(ScreenManager manager, int levelReached, double playTime) {
        super(manager);
        this.levelReached = levelReached;
        this.playTime = playTime;
        
        Label title = new Label("HOME");
        title.getStyleClass().add("title-text");
        title.setFont(Font.font("Arial", 80));
        title.setStyle("-fx-text-fill: #f5a623;");
        
        Label stats = new Label(String.format("Climbed %d levels, and the sun went down on the way%nTime: %.1f seconds",
            levelReached, playTime));
        stats.getStyleClass().add("menu-item");
        stats.setFont(Font.font("Arial", 36));
        stats.setStyle("-fx-text-fill: white;");
        
        Label retry = new Label("Press ENTER to keep climbing");
        retry.getStyleClass().add("menu-item");
        retry.setFont(Font.font("Arial", 28));
        
        Label menu = new Label("Press ESC for Main Menu");
        menu.getStyleClass().add("menu-item");
        menu.setFont(Font.font("Arial", 28));
        
        VBox content = new VBox(40, title, stats, retry, menu);
        content.getStyleClass().add("center-column");
        
        root = new StackPane(content);
        root.getStyleClass().add("screen-bg");

        // THE WAY HOME, AND IT IS THE DEATH SCREEN'S IMAGE WITH ONE THING INVERTED. That screen paints the night
        // sky with the rock EMPTY and says so in its own comment - "the climber is not standing on it". This is the
        // same horizon with the climber standing on it, which is what "a way home" means, and it needs no words
        // either. Two screens differing by one boolean and meaning opposite things is stronger than a new layout.
        //
        // No -fx-background-color here any more: it would paint over the canvas.
        Canvas sky = new Canvas();
        root.widthProperty().addListener((o, a, b) -> paintSky(sky));
        root.heightProperty().addListener((o, a, b) -> paintSky(sky));
        root.getChildren().add(0, sky);
        paintSky(sky);

        // The words sit on a sky rather than on flat paint, so they get a shadow instead of a panel over the art.
        javafx.scene.effect.DropShadow shadow =
                new javafx.scene.effect.DropShadow(18, 0, 4, Color.rgb(0, 0, 0, 0.8));
        title.setEffect(shadow);
        stats.setEffect(shadow);
        retry.setEffect(shadow);
        menu.setEffect(shadow);
    }

    /** The horizon, drawn proportionally to whatever size the window is. */
    private void paintSky(Canvas c) {
        double w = root.getWidth(), h = root.getHeight();
        if (w <= 0 || h <= 0) return;
        c.setWidth(w);
        c.setHeight(h);
        Skyline.paint(c.getGraphicsContext2D(), w, h, Skyline.Mood.NIGHT, true);
    }
    
    @Override
    public Parent getRoot() {
        return root;
    }
    
    @Override
    public void handleKey(KeyEvent e) {
        if (e.getCode() == KeyCode.ENTER) {
            // ON TO THE NEXT LEVEL, AND THE RUN CONTINUES PAST ITS OWN ENDING: the way home is a place you can
            // leave again rather than a full stop. The endless run is untouched - what the ending gives the climb
            // is somewhere to be going.
            manager.replace(new GameplayScreen(manager, levelReached + 1));
        } else if (e.getCode() == KeyCode.ESCAPE) {
            manager.replace(new MainMenu(manager));
        }
    }
}
