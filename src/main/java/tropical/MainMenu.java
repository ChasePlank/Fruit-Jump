package tropical;

import javafx.scene.Parent;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.control.Label;
import javafx.scene.text.Font;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import javafx.application.Platform;

/**
 * Main menu: New Game, Continue, Settings, Quit.
 *
 * Keyboard navigation: Up/Down to select, Enter to activate.
 * Mouse: Click to activate.
 */
public class MainMenu extends Screen {
    private final StackPane root;
    private final MenuButton[] buttons;
    private int focusIndex = 0;

    public MainMenu(ScreenManager manager) {
        super(manager);

        Label title = new Label("Main Menu");
        title.getStyleClass().add("menu-title");
        title.setFont(Font.font("Arial", 56));

        MenuButton newGame = new MenuButton("New Game", () -> {
            // Start level 1 — replace the menu (no way back via pop)
            manager.replace(new GameplayScreen(manager, 1));
        });

        MenuButton continueBtn = new MenuButton("Continue", () -> {
            // Load the autosave checkpoint. The save's MODE decides
            // which game resumes — a rooms-mode save used to dump the
            // player into the platformer (playtest round 5).
            String saveFile = System.getProperty("user.home")
                    + "/.tropical-punch-autosave.txt";
            try {
                tropical.engine.SaveSystem.GameState state =
                    new tropical.engine.SaveSystem().load(saveFile);
                if ("rooms".equals(state.mode)) {
                    manager.replace(new RoomsScreen(manager, 1, state));
                } else {
                    int level = state.levelNum > 0 ? state.levelNum : 1;
                    manager.replace(new GameplayScreen(manager, level, state));
                }
            } catch (java.io.IOException ex) {
                // No save — Continue does nothing (honest no-op)
                System.out.println("no save to continue from");
            }
        });

        MenuButton roomsMode = new MenuButton("Rooms Mode (prototype)", () -> {
            // Zelda-style screen transitions — the design Kinger's Scratch
            // project is built around. One grid of rooms, camera fixed
            // per room, edge-crossing moves to the adjacent room.
            manager.replace(new RoomsScreen(manager, 1));
        });

        MenuButton settings = new MenuButton("Settings", () -> {
            manager.push(new SettingsScreen(manager));
        });

        MenuButton quit = new MenuButton("Quit", () -> {
            Platform.exit();
        });

        buttons = new MenuButton[]{newGame, continueBtn, roomsMode, settings, quit};

        VBox menu = new VBox(20, title, newGame, continueBtn, roomsMode, settings, quit);
        menu.getStyleClass().add("center-column");

        root = new StackPane(menu);
        root.getStyleClass().add("screen-bg");
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
            // Fire exactly once from here. JavaFX's Button ALSO fires
            // natively on ENTER/SPACE when focused — but only for events
            // that reach the scene's focus-based dispatch. This handler
            // IS the keyboard path (Main routes scene keys here), so we
            // fire and consume. The native path never sees the event.
            buttons[focusIndex].fire();
            e.consume();
        } else if (e.getCode() == KeyCode.ESCAPE) {
            manager.pop(); // Back to title
        }
    }
}
