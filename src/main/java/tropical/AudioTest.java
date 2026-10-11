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
        // AN UNCAUGHT EXCEPTION ON A JAVAFX THREAD IS NOT A VERDICT, AND IT MUST NOT LOOK LIKE ONE.
        //
        // The sibling repository spent three gate runs finding this: loading a clip with no audio device throws from
        // JavaFX's own media thread, where nothing here can catch it, so the stack trace lands on stderr whenever that
        // thread gets there - sometimes after this suite's summary. The gate reads the last summary-shaped line, so a
        // green run came back red with a bare "#" as the reason.
        //
        // One line, never a trace. A player it could not create is the SANDBOX'S; anything else counts against the
        // run, so the fix is not silence.
        final java.util.List<String> asyncErrors = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        final java.util.concurrent.atomic.AtomicInteger noDevice = new java.util.concurrent.atomic.AtomicInteger();
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            boolean device = "MediaException".equals(e.getClass().getSimpleName())
                    && String.valueOf(e.getMessage()).contains("Could not create player");
            if (device) noDevice.incrementAndGet();
            else asyncErrors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("  (media thread: " + e.getClass().getSimpleName() + " - "
                    + (device ? "no audio device here, expected" : "UNEXPECTED") + ")");
        });

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

        // A settle, so anything the media threads had to say is in the report rather than after it. The verdict does
        // not depend on it: the presence checks above are the ones that decide.
        try { Thread.sleep(400); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        if (!asyncErrors.isEmpty()) {
            System.out.println("async errors: " + asyncErrors);
            ok = false;
        }
        if (noDevice.get() > 0) {
            System.out.println("note: " + noDevice.get() + " media thread(s) could not create a player - no audio"
                    + " device in this sandbox. The cue list above is the part that works everywhere.");
        }

        System.out.println(ok
                ? "SUCCESS: all " + total + " cue(s) and " + tracksWanted + " track(s) present and loaded"
                : "FAILURE: " + missing + " missing, " + loaded + " of " + total + " cue(s) loaded, "
                  + tracksLoaded + " of " + tracksWanted + " track(s) loaded");
        Platform.exit();
        System.exit(ok ? 0 : 1);
    }
}
