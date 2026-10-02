package tropical;

import tropical.engine.AudioSystem;
import javafx.scene.media.AudioClip;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The platformer's sound backend: turns posted cue names into sound.
 *
 * <p><b>Why this is a separate class.</b> {@code AudioSystem} posts events and knows nothing about JavaFX -
 * that split is deliberate and worth keeping, because the engine runs headless in the gate and on a machine
 * with no sound device. This is the other half: it reads the names and plays the files.
 *
 * <p><b>Why it lives in {@code games/fruitjump/} and not next to the screens.</b> The screens are "wiring"
 * files that the release sync skips, so a backend written there would have to be written twice and would drift.
 * A file here travels with the sync, and it can, because it does not import {@code aside.ui.*} - it talks to
 * JavaFX directly. That is the same reason {@code CharacterConfig} and {@code WaterSuite} live here.
 *
 * <p>Nothing about this is load-bearing for the game: with no audio folder every cue is silently skipped, and
 * the platformer plays exactly as it did before it had sound.
 */
public class Sound {

    private final Map<String, AudioClip> clips = new LinkedHashMap<>();
    private final Map<String, String> missing = new LinkedHashMap<>();
    private boolean enabled = true;

    /** Load every cue the engine can post from {@code root/audio}. */
    public static Sound load(String root) {
        Sound s = new Sound();
        File dir = new File(root, "audio");
        for (String cue : AudioSystem.sfxNames()) {
            File f = new File(dir, cue + ".wav");
            if (!f.isFile()) f = new File(dir, cue + ".mp3");
            if (!f.isFile()) { s.missing.put(cue, "no file"); continue; }
            try {
                s.clips.put(cue, new AudioClip(f.toURI().toString()));
            } catch (Exception ex) {
                // A file that exists is not a file that plays - the same distinction AudioTest exists for.
                s.missing.put(cue, String.valueOf(ex.getMessage()));
            }
        }
        return s;
    }

    /** Play everything posted since the last call. Call this once a frame. */
    public void drain(AudioSystem audio) {
        if (audio == null) return;
        for (String cue : audio.drainPending()) play(cue);
    }

    public void play(String cue) {
        if (!enabled) return;
        AudioClip c = clips.get(cue);
        if (c == null) return;          // no file for this cue, or no sound device. Either way, nothing to do.
        try {
            c.play();
        } catch (Exception ignored) {
            // A clip that will not start must never take the game with it.
        }
    }

    public void setEnabled(boolean on) { enabled = on; }
    public boolean isEnabled() { return enabled; }
    public int loaded() { return clips.size(); }
    public Map<String, String> missing() { return missing; }
}
