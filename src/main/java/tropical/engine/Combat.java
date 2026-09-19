package tropical.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Combat system: player health, contact damage, stomp detection,
 * invincibility frames, knockback.
 * 
 * Stomp rule (Mario convention): if the player is falling (vy > 0)
 * and their feet are above the enemy's midpoint at contact, it's a
 * stomp — the enemy dies and the player bounces. Otherwise the player
 * takes damage and knockback.
 */
public class Combat {
    // Player state
    public double playerHP = 3;
    double invulnTimer = 0;       // seconds of i-frames remaining
    
    static final double INVULN_TIME = 1.0;    // i-frames after a hit
    static final double KNOCKBACK_X = 250;
    static final double KNOCKBACK_Y = -300;   // pop upward
    static final double STOMP_BOUNCE = -400;  // bounce after stomping
    
    // Event log for headless testing
    public final List<String> events = new ArrayList<>();
    
    // Audio system (optional — null means no audio)
    AudioSystem audio = null;

    // AI Director (optional — null means no director)
    AIDirector director = null;

    /** Attach an audio system. */
    public void setAudio(AudioSystem audio) {
        this.audio = audio;
    }

    /** Attach an AI director — it observes damage and kills. */
    public void setDirector(AIDirector director) {
        this.director = director;
    }
    
    /** Player hurt handler. */
    public void hurtPlayer(Physics.Body player, double fromX) {
        if (invulnTimer > 0) return; // i-frames active
        
        playerHP -= 1;
        invulnTimer = INVULN_TIME;
        
        // Knockback away from the damage source
        double dir = (player.x < fromX) ? -1 : 1;
        player.vx = dir * KNOCKBACK_X;
        player.vy = KNOCKBACK_Y;
        
        events.add(String.format("HURT: hp=%.0f knockback dir=%.0f", playerHP, dir));
        if (director != null) director.onPlayerDamaged(1.0 / 3.0);  // 1 HP of 3
        if (audio != null) audio.playSfx(AudioSystem.Sfx.HURT);
    }
    
    /** Stomp check: is this contact a stomp?
     *  Positional rule (Mario convention): the player's feet must be above
     *  the enemy's center at contact. Velocity is NOT part of the check —
     *  the first contact frame often comes while still ascending (the jump
     *  arc crosses the enemy's column before apex), and requiring vy>0 there
     *  turns a legitimate stomp into a hurt. */
    public boolean isStomp(Physics.Body player, Physics.Body enemy) {
        return (player.y + player.hh) < enemy.y;
    }
    
    /** Process contact between player and an enemy. Returns true if enemy died. */
    public boolean processContact(Physics.Body player, Enemy enemy) {
        if (enemy.dead) return false;
        if (!enemy.overlaps(player)) return false;

        if (!enemy.stompImmune && isStomp(player, enemy.body)) {
            enemy.stomp();
            player.vy = STOMP_BOUNCE;
            events.add(String.format("STOMP: enemy at (%.0f,%.0f) defeated", enemy.body.x, enemy.body.y));
            if (director != null) director.onEnemyKilled(0.5);  // basic enemy threat
            if (audio != null) audio.playSfx(AudioSystem.Sfx.STOMP);
            return true;
        }

        // Contact damage (also the result of stomping a spiked enemy)
        hurtPlayer(player, enemy.body.x);
        return false;
    }
    
    /** Tick i-frames. */
    public void update(double dt) {
        if (invulnTimer > 0) {
            invulnTimer -= dt;
        }
    }
    
    public boolean playerDead() {
        return playerHP <= 0;
    }
}
