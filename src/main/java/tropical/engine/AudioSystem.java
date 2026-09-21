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
        DEATH("death");
        
        final String name;
        Sfx(String name) { this.name = name; }
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
    public final List<String> eventLog = new ArrayList<>();
    
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
    public void playSfx(Sfx sfx) {
        if (muted) return;
        if (sfx == Sfx.LAND && landCooldown > 0) return;
        if (sfx == Sfx.LAND) landCooldown = LAND_COOLDOWN_TIME;
        
        eventLog.add(String.format("SFX %s vol=%.2f", sfx.name, sfxVolume));
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
        eventLog.add(String.format("MUSIC %s -> %s vol=%.2f", from, to, musicVolume));
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
        eventLog.add(muted ? "MUTED" : "UNMUTED");
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
