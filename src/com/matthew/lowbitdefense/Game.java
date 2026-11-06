package com.matthew.lowbitdefense;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Game rules only, no Android types, so GameTest runs on a plain JVM. */
final class Game {
    static final int SHIP = 0, PROTECTION = 1, WEAPONS = 2, SPECIAL = 3;
    static final String[] MODULE_NAMES = {"SHIP", "PROTECTION", "WEAPONS", "SPECIAL"};
    static final double[] BASE_CASH = {50, 100, 75, 300};  // first purchase of each module, paid in cash
    static final int MAX_MODULE_LEVEL = 20;                 // levels 2+ are bought with gems
    static final int MAX_PLAYER_LEVEL = 100;
    static final int BOSS_EVERY = 5;
    static final double BOSS_SHARE = 0.6;                   // boss HP = 60% of the whole wave's HP
    static final double SHOT_INTERVAL = 0.6, SPAWN_GAP = 0.8;

    // Path in field coordinates (0..1 across, 0..1 down the play field). Enemies walk it to the ship.
    static final double[][] PATH = {{0.15, 0}, {0.15, 0.3}, {0.85, 0.3}, {0.85, 0.6}, {0.15, 0.6}, {0.15, 0.8}, {0.5, 0.92}};
    static final double SHIP_X = 0.5, SHIP_Y = 0.92, SHIP_RANGE = 0.5;
    private static final double[] SEG_LEN = new double[PATH.length - 1];
    private static final double PATH_LEN;

    static {
        double total = 0;
        for (int i = 0; i < SEG_LEN.length; i++) {
            SEG_LEN[i] = Math.hypot(PATH[i + 1][0] - PATH[i][0], PATH[i + 1][1] - PATH[i][1]);
            total += SEG_LEN[i];
        }
        PATH_LEN = total;
    }

    static final class Enemy {
        double t, hp;
        final double maxHp;
        final boolean boss;

        Enemy(double hp, boolean boss) {
            this.hp = hp;
            this.maxHp = hp;
            this.boss = boss;
        }
    }

    final int[] module = new int[4];                         // 0 = locked
    double cash = 150, gems, xp;
    int playerLevel = 1, wave;                               // wave = last wave started
    boolean waveActive, lost, autoTrade;
    double hull, shotCooldown, specialCooldown, beamTimer;
    double[] beamTo = new double[2];
    final List<Enemy> enemies = new ArrayList<>();
    final List<Enemy> queue = new ArrayList<>();
    double spawnTimer;
    private final Random rng;

    Game(Random rng) {
        this.rng = rng;
    }

    // ---- stats: one place each, so upgrades stay a one-line change ----

    double hullMax() { return module[SHIP] == 0 ? 0 : 100 + 50 * (module[SHIP] - 1); }
    double reduction() { return Math.min(0.75, 0.1 * module[PROTECTION]); }
    double shotDamage() { return 5 + 3 * (module[WEAPONS] - 1); }
    double specialDamage() { return 30 + 30 * (module[SPECIAL] - 1); }
    double specialCooldownMax() { return Math.max(5, 25 - 2 * (module[SPECIAL] - 1)); }

    /** Gems needed to go from currentLevel to currentLevel + 1. Doubles each level. */
    static double upgradeCost(int currentLevel) { return 10 * Math.pow(2, currentLevel - 1); }

    static double xpToNext(int level) { return 100 * Math.pow(2, level - 1); }

    /**
     * Cash per gem by player level: 100 at level 1, 1 at level 50, 0.01 at level 100.
     * Log-linear between those anchors, so each step is a constant multiplier.
     */
    static double cashPerGem(int level) {
        level = Math.max(1, Math.min(MAX_PLAYER_LEVEL, level));
        return level <= 50
                ? 100 * Math.pow(0.01, (level - 1) / 49.0)
                : Math.pow(0.01, (level - 50) / 50.0);
    }

    /** Base purchase costs cash; upgrades cost gems. Returns false if unaffordable or maxed. */
    boolean buy(int i) {
        if (module[i] == 0) {
            if (cash < BASE_CASH[i]) return false;
            cash -= BASE_CASH[i];
            module[i] = 1;
        } else {
            if (module[i] >= MAX_MODULE_LEVEL) return false;
            double cost = upgradeCost(module[i]);
            if (gems < cost) return false;
            gems -= cost;
            module[i]++;
        }
        hull = Math.min(hull, hullMax());
        return true;
    }

    /** Converts up to amount cash into gems at the current player level's rate. */
    double tradeCash(double amount) {
        amount = Math.min(amount, cash);
        if (amount <= 0) return 0;
        cash -= amount;
        double got = amount / cashPerGem(playerLevel);
        gems += got;
        return got;
    }

    boolean startWave() {
        if (waveActive || module[SHIP] == 0 || module[WEAPONS] == 0) return false;
        wave++;
        waveActive = true;
        lost = false;
        hull = hullMax();
        spawnTimer = 0;
        int min = 3 + wave / 2;
        int count = min + rng.nextInt(3 + wave);             // random ship count each wave
        double regularHp = 10 * Math.pow(1.2, wave - 1), regularSum = 0;
        for (int i = 0; i < count; i++) {
            queue.add(new Enemy(regularHp, false));
            regularSum += regularHp;
        }
        if (wave % BOSS_EVERY == 0) {
            // boss = 0.6 of (regular + boss), so boss = regular * 0.6 / 0.4
            queue.add(new Enemy(regularSum * BOSS_SHARE / (1 - BOSS_SHARE), true));
        }
        return true;
    }

    /** Advances the simulation by dt seconds. */
    void update(double dt) {
        beamTimer -= dt;
        shotCooldown -= dt;
        specialCooldown -= dt;
        if (!waveActive) return;

        spawnTimer -= dt;
        if (spawnTimer <= 0 && !queue.isEmpty()) {
            enemies.add(queue.remove(0));
            spawnTimer = SPAWN_GAP;
        }
        for (Iterator<Enemy> it = enemies.iterator(); it.hasNext(); ) {
            Enemy e = it.next();
            e.t += (e.boss ? 0.025 : 0.05) * dt;
            if (e.t >= 1) {                                    // reached the ship
                hull -= (e.boss ? 25 : 5) * (1 - reduction());
                it.remove();
            }
        }
        if (hull <= 0) {
            defeat();
            return;
        }
        if (shotCooldown <= 0 && module[WEAPONS] > 0) {
            Enemy target = null;                                // furthest along the path in range
            for (Enemy e : enemies) {
                if (inRange(e) && (target == null || e.t > target.t)) target = e;
            }
            if (target != null) {
                target.hp -= shotDamage();
                shotCooldown = SHOT_INTERVAL;
                beamTimer = 0.12;
                beamTo = pointAt(target.t);
                removeDead();
            }
        }
        if (queue.isEmpty() && enemies.isEmpty()) clearWave();
    }

    /** Hits every enemy on the field. Cooldown starts when it fires. */
    boolean useSpecial() {
        if (!waveActive || module[SPECIAL] == 0 || specialCooldown > 0) return false;
        for (Enemy e : enemies) e.hp -= specialDamage();
        removeDead();
        specialCooldown = specialCooldownMax();
        if (queue.isEmpty() && enemies.isEmpty()) clearWave();
        return true;
    }

    double reserve() {
        double r = Double.MAX_VALUE;
        for (int i = 0; i < module.length; i++) {
            if (module[i] == 0) r = Math.min(r, BASE_CASH[i]);
        }
        return r == Double.MAX_VALUE ? 0 : r;
    }

    static double[] pointAt(double t) {
        double d = t * PATH_LEN;
        for (int i = 0; i < SEG_LEN.length; i++) {
            if (d <= SEG_LEN[i] || i == SEG_LEN.length - 1) {
                double f = Math.min(1, d / SEG_LEN[i]);
                return new double[]{
                        PATH[i][0] + (PATH[i + 1][0] - PATH[i][0]) * f,
                        PATH[i][1] + (PATH[i + 1][1] - PATH[i][1]) * f};
            }
            d -= SEG_LEN[i];
        }
        throw new IllegalStateException("unreachable");
    }

    private boolean inRange(Enemy e) {
        double[] p = pointAt(e.t);
        return Math.hypot(p[0] - SHIP_X, p[1] - SHIP_Y) <= SHIP_RANGE;
    }

    private void removeDead() {
        for (Iterator<Enemy> it = enemies.iterator(); it.hasNext(); ) {
            Enemy e = it.next();
            if (e.hp <= 0) {
                cash += wave * (e.boss ? 10 : 1);
                it.remove();
            }
        }
    }

    private void clearWave() {
        waveActive = false;
        cash += 20.0 * wave;
        xp += 20.0 * wave;
        hull = hullMax();
        while (playerLevel < MAX_PLAYER_LEVEL && xp >= xpToNext(playerLevel)) {
            xp -= xpToNext(playerLevel);
            playerLevel++;
        }
        if (autoTrade) tradeCash(cash - reserve());         // keep enough cash for the next base purchase
    }

    private void defeat() {
        waveActive = false;
        lost = true;
        enemies.clear();
        queue.clear();
        wave = 0;                                            // retry starts from wave 1, upgrades kept
    }
}
