package tropical;

import javafx.scene.Parent;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.control.Label;
import javafx.scene.text.Font;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;

/**
 * Game over screen: displayed when player loses all lives.
 * Shows final stats and offers retry or return to menu.
 */
public class GameOverScreen extends Screen {
    private final StackPane root;
    private final int levelReached;
    private final double playTime;
    
    public GameOverScreen(ScreenManager manager, int levelReached, double playTime) {
        super(manager);
        this.levelReached = levelReached;
        this.playTime = playTime;
        
        Label title = new Label("GAME OVER");
        title.getStyleClass().add("title-text");
        title.setFont(Font.font("Arial", 80));
        title.setStyle("-fx-text-fill: #ff4444;");
        
        Label stats = new Label(String.format("Reached Level %d%nTime: %.1f seconds", 
            levelReached, playTime));
        stats.getStyleClass().add("menu-item");
        stats.setFont(Font.font("Arial", 36));
        stats.setStyle("-fx-text-fill: white;");
        
        Label retry = new Label("Press ENTER to Retry");
        retry.getStyleClass().add("menu-item");
        retry.setFont(Font.font("Arial", 28));
        
        Label menu = new Label("Press ESC for Main Menu");
        menu.getStyleClass().add("menu-item");
        menu.setFont(Font.font("Arial", 28));
        
        VBox content = new VBox(40, title, stats, retry, menu);
        content.getStyleClass().add("center-column");
        
        root = new StackPane(content);
        root.getStyleClass().add("screen-bg");
        root.setStyle("-fx-background-color: #1a1a2e;");
    }
    
    @Override
    public Parent getRoot() {
        return root;
    }
    
    @Override
    public void handleKey(KeyEvent e) {
        if (e.getCode() == KeyCode.ENTER) {
            // Retry the level you died on (fresh 3 HP) — restarting the
            // whole run from level 1 after dying on level 7 would be
            // brutal with procedural levels.
            manager.replace(new GameplayScreen(manager, levelReached));
        } else if (e.getCode() == KeyCode.ESCAPE) {
            manager.replace(new MainMenu(manager));
        }
    }
}
