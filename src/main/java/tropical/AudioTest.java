package tropical;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import tropical.engine.AudioSystem;

/**
 * Are this build's sound cues actually present and playable?
 *
 * <p>THE RELEASE HAD A DIAGNOSTIC FOR THIS AND NO CHECK. {@link Sound#main} prints "cues loaded: N of M" and lists
 * anything missing - and it exits 0 either way, so even run by hand it reported rather than failed. Nothing ran it,
 * and its own comment gave a reason that is not true: "it needs a JavaFX toolkit and a display, so it cannot live in
 * the gate". The gate has both. Measured: with the audio directory hidden the diagnostic prints "cues loaded: 0 of
 * 12" with all twelve cues named, and STILL exits 0.
 *
 * <p>That combination is the whole bug. A tool that is not wired in, cannot fail if it were, and explains itself
 * with a reason that does not hold, reads as settled - which is why nobody looked at it.
 *
 * <p>Sound is the one thing in this build that travels as FILES rather than as code. Every sprite here is a pixel
 * grid compiled into the program; the cues are 98 files beside it and inside the jar. Lose them and the game is
 * silent, everything still runs, and every other check passes. The question this asks is silent when the answer is
 * no, which is exactly why it has to be asked by something that fails.
 */
public class AudioTest extends Application {

    public static void main(String[] args) { launch(args); }

    @Override public void start(Stage stage) {
        Sound s = Sound.load(".");
        int total = AudioSystem.sfxNames().length;
        int missing = s.missing().size();
        int loaded = s.loaded();

        System.out.println("cues loaded: " + loaded + " of " + total);
        if (missing > 0) System.out.println("missing: " + s.missing());
        // One assertion, and it is the presence one: every cue the engine can post has to be here. A cue that is
        // present but unplayable is already recorded by Sound.load as missing with the exception that stopped it.
        boolean ok = (missing == 0) && (loaded == total);

        // AND THE MUSIC, which is a separate list and was a separate silence: AudioSystem.Music declared four
        // tracks, the boss asked for one of them on every phase change, and NOT ONE had a file. The cues were in
        // the same state until a generator was written for them. This is the check that keeps the tracks honest.
        int tracksWanted = AudioSystem.musicNames().length;
        int tracksLoaded = s.musicLoaded();
        System.out.println("music tracks loaded: " + tracksLoaded + " of " + tracksWanted);
        ok = ok && tracksLoaded == tracksWanted;

        System.out.println(ok
                ? "SUCCESS: all " + total + " cue(s) and " + tracksWanted + " track(s) present and loaded"
                : "FAILURE: " + missing + " missing, " + loaded + " of " + total + " cue(s) loaded, "
                  + tracksLoaded + " of " + tracksWanted + " track(s) loaded");
        Platform.exit();
        System.exit(ok ? 0 : 1);
    }
}
