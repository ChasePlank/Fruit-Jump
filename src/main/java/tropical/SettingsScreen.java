package tropical;

import javafx.scene.Parent;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.HBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.text.Font;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;

/**
 * Settings screen: Master Volume, Music, SFX.
 *
 * Sliders are 0-100. Changes are immediate (no Save button).
 * ESC or Back returns to previous screen.
 */
public class SettingsScreen extends Screen {
    private final StackPane root;

    public SettingsScreen(ScreenManager manager) {
        super(manager);

        Label title = new Label("Settings");
        title.getStyleClass().add("menu-title");
        title.setFont(Font.font("Arial", 32));

        Slider master = createSlider("Master Volume", 80);
        Slider music = createSlider("Music", 70);
        Slider sfx = createSlider("SFX", 100);

        MenuButton back = new MenuButton("Back", () -> manager.pop());

        VBox content = new VBox(30, title,
            sliderRow("Master:", master),
            sliderRow("Music:", music),
            sliderRow("SFX:", sfx),
            back);
        content.getStyleClass().add("center-column");

        root = new StackPane(content);
        root.getStyleClass().add("screen-bg");
    }

    private Slider createSlider(String name, double initial) {
        Slider slider = new Slider(0, 100, initial);
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        slider.setMajorTickUnit(25);
        slider.setBlockIncrement(5);
        slider.getStyleClass().add("volume-slider");

        // Immediate feedback
        slider.valueProperty().addListener((obs, oldVal, newVal) -> {
            // Audio: SET_VOLUME(name, newVal.doubleValue())
            System.out.printf("%s: %.0f%%%n", name, newVal.doubleValue());
        });

        return slider;
    }

    private HBox sliderRow(String label, Slider slider) {
        Label l = new Label(label);
        l.getStyleClass().add("slider-label");
        l.setFont(Font.font("Arial", 18));
        l.setMinWidth(100);

        HBox row = new HBox(20, l, slider);
        row.getStyleClass().add("slider-row");
        return row;
    }

    @Override
    public Parent getRoot() {
        return root;
    }

    @Override
    public void handleKey(KeyEvent e) {
        if (e.getCode() == KeyCode.ESCAPE) {
            manager.pop();
        }
    }
}
