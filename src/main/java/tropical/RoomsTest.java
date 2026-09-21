package tropical;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.robot.Robot;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;

/**
 * Screen-transition integration test: launch a RoomsScreen, hold RIGHT
 * toward an edge with an east connection, verify the room changes and
 * the player enters from the west edge of the new room.
 */
public class RoomsTest extends Application {
    private ScreenManager screens;
    private int step = 0;
    private int failures = 0;
    private String startRoom;
    private int startX;

    @Override
    public void start(Stage stage) {
        screens = new ScreenManager();
        StackPane root = new StackPane();
        root.getChildren().add(screens.getContainer());
        Scene scene = new Scene(root, 800, 480, Color.BLACK);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        scene.setOnKeyPressed(e -> screens.handleKey(e));
        scene.setOnKeyReleased(e -> {
            Screen top = screens.peek();
            if (top instanceof RoomsScreen r) r.handleKeyReleased(e);
        });
        stage.setScene(scene);
        stage.show();

        screens.push(new RoomsScreen(screens, 1));
        nextStep();
    }

    private void check(String name, boolean ok) {
        System.out.printf("%-52s %s%n", name, ok ? "PASS" : "FAIL");
        if (!ok) failures++;
    }

    private RoomsScreen game() {
        Screen top = screens.peek();
        return top instanceof RoomsScreen r ? r : null;
    }

    private Object field(Object o, String name) throws Exception {
        java.lang.reflect.Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    private void nextStep() {
        javafx.animation.PauseTransition wait = new javafx.animation.PauseTransition(
                javafx.util.Duration.millis(500));
        wait.setOnFinished(e -> {
            step++;
            Robot robot = new Robot();
            try {
                switch (step) {
                    case 1 -> {
                        check("in RoomsScreen", game() != null);
                        startRoom = field(game(), "screens").getClass()
                                .getDeclaredMethod("currentRoomId")
                                .invoke(field(game(), "screens")).toString();
                        var player = (tropical.engine.Physics.Body) field(game(), "player");
                        startX = (int) player.x;
                        System.out.println("    start room: " + startRoom + ", x=" + startX);
                        // teleport near the east edge, then hold RIGHT —
                        // crossing the margin triggers the transition.
                        // (Walking the full 800px at 220px/s takes ~4s of
                        // sim; the transition logic is what's under test.)
                        player.x = 780;
                        robot.keyPress(KeyCode.RIGHT);
                    }
                    case 2, 3, 4 -> {
                        // keep running; transitions happen over ~0.3s
                    }
                    case 5 -> {
                        robot.keyRelease(KeyCode.RIGHT);
                        robot.keyRelease(KeyCode.RIGHT);
                        robot.keyRelease(KeyCode.RIGHT);  // ensure released
                        // let the player settle (stop running) before
                        // asserting position
                        try { Thread.sleep(300); } catch (InterruptedException ie) {}
                        var eng = field(game(), "screens");
                        String nowRoom = eng.getClass().getDeclaredMethod("currentRoomId")
                                .invoke(eng).toString();
                        var player = (tropical.engine.Physics.Body) field(game(), "player");
                        System.out.println("    after run: room=" + nowRoom + ", x=" + (int) player.x);
                        check("transitioned to a different room", !nowRoom.equals(startRoom));
                        // entering from the west edge: x should be small
                        check("entered on the west side (x < 500)", player.x < 500);
                        finish();
                    }
                    default -> finish();
                }
            } catch (Exception ex) {
                check("step " + step + " exception: " + ex, false);
                finish();
                return;
            }
            if (step < 5) nextStep();
        });
        wait.play();
    }

    private void finish() {
        System.out.println(failures == 0
                ? "SUCCESS: screen transition works"
                : "FAILURE: " + failures + " check(s) failed");
        Platform.exit();
        System.exit(failures == 0 ? 0 : 1);
    }

    public static void main(String[] args) { launch(args); }
}
