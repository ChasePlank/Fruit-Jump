package tropical.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Audio system: sound effect triggers, music states, volume control.
 * 
 * Headless architecture: gameplay code posts audio EVENTS, and this
 * system decides what to play. A real audio backend (JavaFX, OpenAL,
 * etc.) can subscribe to the same events later — no gameplay code
 * changes needed.
 * 
 * The event log is the "output" in headless mode.
 */
public class AudioSystem {
    
    // --- Sound effects ---
    enum Sfx {
        JUMP("jump"),
        LAND("land"),
        HURT("hurt"),
        STOMP("stomp"),
        PICKUP("pickup"),
        KEY("key"),
        DOOR("door"),
        EXPLOSION("explosion"),
        ARROW("arrow"),
        HOOKSHOT("hookshot"),
        SPLASH("splash"),
        DEATH("death");
        
        final String name;
        Sfx(String name) { this.name = name; }
    }

    /**
     * Every cue name this system can post.
     *
     * <p>For a check that every one of them has a file. The gate asks for cues by NAME, so it needs the list,
     * and deriving it from the enum means a cue added here cannot be added without the check seeing it. The
     * alternative - a second list in the test - is a list that goes stale the first time someone adds a sound.
     */
    public static String[] sfxNames() {
        Sfx[] values = Sfx.values();
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) out[i] = values[i].name;
        return out;
    }
    
    // --- Music states ---
    enum Music {
        TITLE("title-theme"),
        LEVEL("level-theme"),
        BOSS("boss-theme"),
        VICTORY("victory-theme"),
        NONE(null);
        
        final String trackName;
        Music(String track) { this.trackName = track; }
    }
    
    // --- Event log (headless output) ---
    /**
     * The event log, bounded.
     *
     * <p>Nothing outside this class reads or clears it - it exists so a headless run can say what it would have
     * played. Unbounded, that is a leak: every jump, landing, pickup and hit appends a string that is never
     * removed, so a level played for a few minutes holds thousands of entries that nothing will ever look at,
     * and {@code printLog} would print all of them.
     *
     * <p>It keeps the most recent {@link #LOG_LIMIT}. Diagnosis wants the end of a run, not its beginning.
     */
    public final List<String> eventLog = new ArrayList<>();

    /** How many events to keep. See {@link #eventLog}. */
    public static final int LOG_LIMIT = 500;

    /** Append an event, dropping the oldest once the log is full. */
    private void log(String event) {
        eventLog.add(event);
        while (eventLog.size() > LOG_LIMIT) eventLog.remove(0);
    }
    
    // --- Volume ---
    double sfxVolume = 0.8;
    double musicVolume = 0.6;
    boolean muted = false;
    
    // --- Music state ---
    Music currentMusic = Music.NONE;
    double musicTransitionTimer = 0;
    
    // --- Cooldowns (prevent spam) ---
    double landCooldown = 0;
    static final double LAND_COOLDOWN_TIME = 0.1;  // 100ms between land sounds
    
    /** Post a sound effect event. */
    /**
     * Cue names posted since the last drain, for a backend to play.
     *
     * <p>The event log is prose - "SFX jump vol=1.00" - which is right for a person reading a headless run and
     * wrong for a machine. A backend wants the NAME. Bounded the same way the log is: a backend that stops
     * being called must not grow a queue forever.
     */
    public final java.util.ArrayDeque<String> pending = new java.util.ArrayDeque<>();

    /** Take everything posted since the last call. The caller plays it; this class still knows nothing about
     *  JavaFX, which is the whole point of the split. */
    public java.util.List<String> drainPending() {
        java.util.List<String> out = new java.util.ArrayList<>(pending);
        pending.clear();
        return out;
    }

    private void post(String cue) {
        if (cue == null) return;
        pending.add(cue);
        while (pending.size() > LOG_LIMIT) pending.removeFirst();
    }

    public void playSfx(Sfx sfx) {
        post(sfx.name);
        if (muted) return;
        if (sfx == Sfx.LAND && landCooldown > 0) return;
        if (sfx == Sfx.LAND) landCooldown = LAND_COOLDOWN_TIME;
        
        log(String.format("SFX %s vol=%.2f", sfx.name, sfxVolume));
    }
    
    /** Switch music track. Only logs if the track actually changes. */
    public void playMusic(Music music) {
        if (currentMusic == music) return;  // no restart on same track
        if (muted) {
            currentMusic = music;
            return;
        }
        
        String from = currentMusic.trackName != null ? currentMusic.trackName : "silence";
        String to = music.trackName != null ? music.trackName : "silence";
        log(String.format("MUSIC %s -> %s vol=%.2f", from, to, musicVolume));
        currentMusic = music;
    }
    
    /** Set SFX volume (0-1). */
    public void setSfxVolume(double v) {
        sfxVolume = Math.max(0, Math.min(1, v));
    }
    
    /** Set music volume (0-1). */
    public void setMusicVolume(double v) {
        musicVolume = Math.max(0, Math.min(1, v));
    }
    
    /** Toggle mute. */
    public void toggleMute() {
        muted = !muted;
        log(muted ? "MUTED" : "UNMUTED");
    }
    
    /** Update cooldowns. */
    public void update(double dt) {
        if (landCooldown > 0) landCooldown -= dt;
    }
    
    /** Print the event log (headless verification). */
    public void printLog() {
        for (String e : eventLog) System.out.println("  " + e);
    }
}
