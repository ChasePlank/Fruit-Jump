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
    /** The music tracks, kept apart from the cues: they loop, and at most one of them is playing. */
    private final Map<String, AudioClip> music = new LinkedHashMap<>();
    /** What is playing now, so a request for the same track is not a restart. */
    private String nowPlaying;
    private final Map<String, String> missing = new LinkedHashMap<>();
    private boolean enabled = true;

    /**
     * Load every cue the engine can post, from {@code root/audio} or from the jar.
     *
     * <p>Two places, and the second is not a fallback for tidiness. The README's instruction is
     * {@code java -jar tropical-punch.jar}, and a jar someone downloaded on its own sits next to no audio
     * folder at all - so a folder-only loader means the published jar is silent while the checkout is not, and
     * nothing would say which. The cues are 216KB together, which is nothing beside the JavaFX runtime the jar
     * already carries. Folder first, so a checkout can still override a cue by dropping a file in.
     */
    public static Sound load(String root) {
        Sound s = new Sound();
        File dir = new File(root, "audio");
        // MUSIC, a separate list because it is a separate kind of asset - a loop rather than a cue. Until now the
        // engine could ASK for four tracks and none of them had a file nor a way to play: every request was silence,
        // including the boss asking for its own theme on every phase change.
        for (String track : AudioSystem.musicNames()) {
            String source = null;
            for (String ext : new String[]{".wav", ".mp3"}) {
                File f = new File(dir, track + ext);
                if (f.isFile()) { source = f.toURI().toString(); break; }
            }
            if (source == null) { s.missing.put(track, "no file"); continue; }
            try {
                AudioClip c = new AudioClip(source);
                c.setCycleCount(AudioClip.INDEFINITE);  // a track is a loop: it plays until asked to stop
                s.music.put(track, c);
            } catch (Exception ex) {
                s.missing.put(track, String.valueOf(ex.getMessage()));
            }
        }

        for (String cue : AudioSystem.sfxNames()) {
            String source = null;
            for (String ext : new String[]{".wav", ".mp3"}) {
                File f = new File(dir, cue + ext);
                if (f.isFile()) { source = f.toURI().toString(); break; }
            }
            if (source == null) {
                var res = Sound.class.getResource("/audio/" + cue + ".wav");
                if (res == null) res = Sound.class.getResource("/audio/" + cue + ".mp3");
                if (res != null) source = res.toExternalForm();
            }
            if (source == null) { s.missing.put(cue, "no file"); continue; }
            try {
                s.clips.put(cue, new AudioClip(source));
            } catch (Exception ex) {
                // A file that exists is not a file that plays - the same distinction AudioTest exists for.
                s.missing.put(cue, String.valueOf(ex.getMessage()));
            }
        }
        return s;
    }

    /**
     * Is the audio where the game can find it?
     *
     * <pre>
     *   java -cp out tropical.Sound
     *   java -jar tropical-punch.jar   # then it is reported on the first frame anyway
     * </pre>
     *
     * <p>A diagnostic, and NOT the gate check - {@link AudioTest} is that, and it exists because this one exits 0
     * whether or not anything is missing.
     *
     * <p>This paragraph used to say it "needs a JavaFX toolkit and a display, so it cannot live in the gate". That
     * is not true and the gate has both; the reason it was not in the gate is that nothing put it there, and a
     * reason that sounds like a constraint is how that goes unnoticed.
     */
    public static void main(String[] args) {
        javafx.application.Platform.startup(() -> { });
        Sound s = Sound.load(args.length > 0 ? args[0] : ".");
        System.out.println("cues loaded: " + s.loaded() + " of " + AudioSystem.sfxNames().length);
        if (!s.missing().isEmpty()) System.out.println("missing: " + s.missing());
        javafx.application.Platform.exit();
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

    /**
     * Play one of the engine's tracks, stopping whatever else was playing.
     *
     * <p>The screen that owns this Sound calls it with the engine's current request every frame. Asking for the same
     * track twice does nothing, which is what makes a per-frame call safe - and it has to be per-frame, because the
     * engine changes its mind mid-level: the boss switches to its own theme on every phase change.
     */
    public void playMusic(String track) {
        if (track == null || track.equals(nowPlaying)) return;
        stopMusic();
        if (!enabled) { nowPlaying = track; return; }
        AudioClip c = music.get(track);
        nowPlaying = track;
        if (c == null) return;                  // no file for this track, or no sound device: nothing to do
        try {
            c.play();
        } catch (Exception ignored) {
            // A track that will not start must never take the game with it.
        }
    }

    /** Stop the music, so a menu theme does not play under a level. */
    public void stopMusic() {
        if (nowPlaying == null) return;
        AudioClip c = music.get(nowPlaying);
        nowPlaying = null;
        if (c == null) return;
        try {
            c.stop();
        } catch (Exception ignored) {
        }
    }

    /** How many tracks loaded. A headless run can say what it would have played without a sound device. */
    public int musicLoaded() { return music.size(); }

    public void setEnabled(boolean on) { enabled = on; }
    public boolean isEnabled() { return enabled; }
    public int loaded() { return clips.size(); }
    public Map<String, String> missing() { return missing; }
}
