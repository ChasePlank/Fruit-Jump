package tropical.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Boss system: multi-phase AI, telegraphed attacks, weak point windows.
 *
 * Design rules (from boss design convention):
 *   1. Every attack is TELEGRAPHED — a windup state that announces what's
 *      coming. The player can always react. No unavoidable damage.
 *   2. Every attack has a RECOVERY window afterward — the boss is
 *      vulnerable. This is where the player deals damage.
 *   3. Phases trigger at HP thresholds — each phase changes the attack
 *      pattern (faster, new attacks, new movement).
 *   4. The boss has a weak point that is only vulnerable during certain
 *      states (recovery, stunned). Attacking outside those windows does
 *      nothing — this forces the dodge-then-punish rhythm.
 *
 * State machine:
 *   IDLE -> WINDUP_<attack> -> EXECUTE_<attack> -> RECOVER -> IDLE
 *   Phase changes interrupt anything -> PHASE_ENTER (roar/pause) -> IDLE
 */
public class Boss {
    // --- Identity ---
    final int id;
    final Physics.Body body;
    boolean dead = false;
    double deadTimer = 0;

    // --- Health ---
    double maxHP = 30;
    double hp = 30;

    // --- Phases ---
    int phase = 1;
    int maxPhase = 3;
    // HP thresholds: phase 2 at 2/3 HP, phase 3 at 1/3 HP
    double phase2Threshold = 20;
    double phase3Threshold = 10;

    // --- AI states ---
    enum State {
        IDLE,           // repositioning, choosing next attack
        WINDUP,         // telegraphing — player must react
        EXECUTE,        // attack active — dangerous
        RECOVER,        // vulnerable window — weak point open
        PHASE_ENTER,    // phase transition — invulnerable, dramatic pause
        DYING
    }
    State state = State.IDLE;

    // --- Attack types ---
    enum Attack { CHARGE, JUMP_SLAM, VOLLEY }
    Attack currentAttack = Attack.CHARGE;

    // --- Timers (seconds) ---
    double stateTimer = 0;
    double idleDuration = 1.0;
    double windupDuration = 0.6;
    double executeDuration = 0.8;
    double recoverDuration = 1.2;
    double phaseEnterDuration = 1.5;

    // --- Wall-stun (charge into wall = big punish window) ---
    double executeStartX = 0;
    static final double STUNNED_MULTIPLIER = 2.0;  // stunned recovery lasts longer
    boolean stunned = false;

    // --- Attack params (scale by phase) ---
    double chargeSpeed = 260;
    double slamJumpV = -520;
    int volleyCount = 3;
    double volleyInterval = 0.25;

    // --- Volley state ---
    int volleysFired = 0;
    double volleyTimer = 0;

    // --- Weak point ---
    boolean weakPointOpen = false;   // true during RECOVER and PHASE_ENTER
    double weakPointY = -14;        // relative to body center (on top)

    // --- Movement ---
    int dir = 1;
    double walkSpeed = 80;

    // --- Event log ---
    public final List<String> events = new ArrayList<>();

    // --- Audio (optional) ---
    AudioSystem audio = null;

    // --- Callbacks for attack execution (set by the test/world) ---
    public interface VolleyCallback {
        void fire(double x, double y, double dirX);
    }
    VolleyCallback volleyCallback = null;

    static int nextId = 0;

    public Boss(double x, double y, double w, double h) {
        this.id = nextId++;
        this.body = new Physics.Body(x, y, w, h);
    }

    public void setAudio(AudioSystem audio) { this.audio = audio; }
    public void setVolleyCallback(VolleyCallback cb) { this.volleyCallback = cb; }

    /** Damage the boss. Only counts if weak point is open.
     *  Hit limit per recovery window: 2 hits — forces the player to
     *  earn damage each cycle instead of dumping everything in one window. */
    int hitsThisWindow = 0;
    static final int MAX_HITS_PER_WINDOW = 2;

    public boolean hit(double damage) {
        if (dead || !weakPointOpen) return false;
        if (state == State.PHASE_ENTER) return false;  // invulnerable mid-transition
        if (hitsThisWindow >= MAX_HITS_PER_WINDOW) return false;  // window exhausted

        hitsThisWindow++;
        hp -= damage;
        events.add(String.format("HIT: hp=%.0f (%.0f dmg)", hp, damage));

        // Phase transitions
        if (phase == 1 && hp <= phase2Threshold) {
            enterPhase(2);
        } else if (phase == 2 && hp <= phase3Threshold) {
            enterPhase(3);
        }

        if (hp <= 0) {
            hp = 0;
            state = State.DYING;
            stateTimer = 0;
            weakPointOpen = false;
            events.add("BOSS DYING");
            if (audio != null) audio.playSfx(AudioSystem.Sfx.DEATH);
        }
        return true;
    }

    private void enterPhase(int newPhase) {
        phase = newPhase;
        state = State.PHASE_ENTER;
        stateTimer = 0;
        weakPointOpen = false;
        events.add("PHASE " + newPhase + " ENTER");
        if (audio != null) audio.playMusic(AudioSystem.Music.BOSS);

        // Phase scaling: faster attacks, more volleys
        idleDuration = 1.0 - (newPhase - 1) * 0.2;
        windupDuration = 0.6 - (newPhase - 1) * 0.1;
        chargeSpeed = 260 + (newPhase - 1) * 70;
        volleyCount = 3 + (newPhase - 1) * 2;
    }

    // --- Attack selection RNG (seedable for deterministic tests) ---
    java.util.Random rng = new java.util.Random();
    public void setSeed(long seed) { rng.setSeed(seed); }

    /** Choose the next attack based on distance to player and phase. */
    private Attack chooseAttack(double distX) {
        if (distX > 250) {
            // Far: volley (phase 2+) or charge
            return phase >= 2 && rng.nextDouble() < 0.5 ? Attack.VOLLEY : Attack.CHARGE;
        } else {
            // Close: charge or jump slam
            return rng.nextDouble() < 0.5 ? Attack.CHARGE : Attack.JUMP_SLAM;
        }
    }

    /**
     * AI update. The player position drives attack choice and direction.
     * Physics (gravity, collision) is handled by the World as usual.
     */
    public void update(double dt, Physics.Body player) {
        if (dead) {
            deadTimer += dt;
            return;
        }

        stateTimer += dt;

        switch (state) {
            case IDLE: {
                // Walk toward player
                double dx = player.x - body.x;
                dir = dx > 0 ? 1 : -1;
                body.vx = dir * walkSpeed;

                if (stateTimer >= idleDuration) {
                    currentAttack = chooseAttack(Math.abs(dx));
                    state = State.WINDUP;
                    stateTimer = 0;
                    weakPointOpen = false;
                    events.add("WINDUP: " + currentAttack);
                }
                break;
            }

            case WINDUP: {
                // Telegraph: stop moving, face the player
                body.vx = 0;
                double dx = player.x - body.x;
                dir = dx > 0 ? 1 : -1;

                if (stateTimer >= windupDuration) {
                    state = State.EXECUTE;
                    stateTimer = 0;
                    executeStartX = body.x;  // track progress for wall-stun detection
                    events.add("EXECUTE: " + currentAttack);

                    switch (currentAttack) {
                        case CHARGE:
                            body.vx = dir * chargeSpeed;
                            break;
                        case JUMP_SLAM:
                            body.vy = slamJumpV;
                            body.vx = dir * 100;
                            break;
                        case VOLLEY:
                            volleysFired = 0;
                            volleyTimer = 0;
                            break;
                    }
                }
                break;
            }

            case EXECUTE: {
                switch (currentAttack) {
                    case CHARGE:
                        // Wall-stun: if the charge hasn't moved the boss in
                        // the last frames, it hit a wall — big punish window.
                        // This prevents the charge from pinning the player
                        // against a wall (the corner-camping flaw).
                        if (stateTimer > 0.2 && Math.abs(body.x - executeStartX) < 5 && stateTimer > 0.25) {
                            stunned = true;
                            events.add("WALL STUN");
                            toRecover();
                            break;
                        }
                        // Keep charging; timeout as normal
                        if (stateTimer >= executeDuration) {
                            toRecover();
                        }
                        break;

                    case JUMP_SLAM:
                        // Slam lands when the boss touches ground after the jump
                        if (body.grounded && stateTimer > 0.1) {
                            events.add("SLAM landed");
                            if (audio != null) audio.playSfx(AudioSystem.Sfx.EXPLOSION);
                            toRecover();
                        }
                        break;

                    case VOLLEY:
                        // Fire volleys at intervals
                        volleyTimer += dt;
                        if (volleysFired < volleyCount && volleyTimer >= volleyInterval) {
                            volleyTimer = 0;
                            volleysFired++;
                            if (volleyCallback != null) {
                                volleyCallback.fire(body.x, body.y - 10, dir);
                            }
                            events.add("VOLLEY " + volleysFired + "/" + volleyCount);
                        }
                        if (volleysFired >= volleyCount) {
                            toRecover();
                        }
                        break;
                }
                break;
            }

            case RECOVER: {
                body.vx = 0;
                // Weak point open — player's punish window
                if (stateTimer >= recoverDuration) {
                    state = State.IDLE;
                    stateTimer = 0;
                    weakPointOpen = false;
                }
                break;
            }

            case PHASE_ENTER: {
                // Dramatic pause, invulnerable
                body.vx = 0;
                if (stateTimer >= phaseEnterDuration) {
                    state = State.IDLE;
                    stateTimer = 0;
                }
                break;
            }

            case DYING: {
                body.vx = 0;
                if (stateTimer >= 1.0) {
                    dead = true;
                    events.add("BOSS DEFEATED");
                }
                break;
            }
        }
    }

    private void toRecover() {
        state = State.RECOVER;
        stateTimer = 0;
        weakPointOpen = true;
        hitsThisWindow = 0;  // fresh punish window
        // Stunned charges give a longer window (earned punish)
        if (stunned) {
            recoverDuration = 1.2 * STUNNED_MULTIPLIER;
            stunned = false;
        } else {
            recoverDuration = 1.2;
        }
        events.add("RECOVER: weak point open");
    }

    /** Boss attacks damage the player on contact during EXECUTE. */
    public boolean isDangerous() {
        return state == State.EXECUTE && !dead;
    }

    /** Weak point AABB (relative to body). */
    public Physics.AABB weakPointBox() {
        return new Physics.AABB(
            body.x - body.hw * 0.5,
            body.y + weakPointY - 10,
            body.x + body.hw * 0.5,
            body.y + weakPointY + 10
        );
    }
}
