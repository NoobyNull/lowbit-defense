package com.matthew.lowbitdefense;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Game rules only, no Android types, so GameTest runs on a plain JVM.
 * World units: the tower is at (0,0), the field is 2 wide and 2 * halfH tall.
 */
final class Game {
    // Skill tree. Roots cost cash once; every other level costs gems.
    static final int WEAPONS = 0, RAPID = 1, MULTI = 2, SPLASH = 3, CRIT = 4,
            BOOST = 5, CHARGE = 6, OVERLOAD = 7, BLAST = 8, PIERCE = 9,
            SHIP = 10, REGEN = 11, SALVAGE = 12, PROTECT = 13, SHIELD = 14, NODES = 15;
    static final String[] TREES = {"WEAPONS", "POWER", "SKILLS"};
    static final int ENDLESS = Integer.MAX_VALUE;

    static final class Node {
        final String name, about;
        final int tree, parent, max, row;
        final double col, cash;                              // col: 0 left, 0.5 centre, 1 right

        Node(String name, int tree, int parent, int max, double col, int row, double cash, String about) {
            this.name = name;
            this.tree = tree;
            this.parent = parent;
            this.max = max;
            this.col = col;
            this.row = row;
            this.cash = cash;
            this.about = about;
        }
    }

    static final Node[] NODE = {
            new Node("WEAPONS", 0, -1, ENDLESS, 0.5, 0, 75, "AUTO SHOT DAMAGE"),
            new Node("RAPID", 0, WEAPONS, 10, 0, 1, 0, "FASTER AUTO FIRE"),
            new Node("MULTI", 0, RAPID, 4, 0, 2, 0, "MORE AUTO TARGETS"),
            new Node("SPLASH", 0, WEAPONS, 10, 1, 1, 0, "AUTO RADIUS DAMAGE"),
            new Node("CRIT", 0, SPLASH, 10, 1, 2, 0, "CHANCE OF 2X DAMAGE"),
            new Node("BOOST", 1, -1, ENDLESS, 0.5, 0, 300, "HOLD TO CHARGE A SHOT"),
            new Node("CHARGE", 1, BOOST, 10, 0, 1, 0, "CHARGES FASTER"),
            new Node("OVERLOAD", 1, CHARGE, 10, 0, 2, 0, "HIGHER FULL POWER"),
            new Node("BLAST", 1, BOOST, 10, 1, 1, 0, "BOOST RADIUS DAMAGE"),
            new Node("PIERCE", 1, BLAST, 5, 1, 2, 0, "BOOST PASSES THROUGH"),
            new Node("SHIP", 2, -1, ENDLESS, 0, 0, 50, "MORE HULL"),
            new Node("REGEN", 2, SHIP, 10, 0, 1, 0, "HULL REPAIRS IN WAVE"),
            new Node("SALVAGE", 2, REGEN, 10, 0, 2, 0, "MORE CASH PER KILL"),
            new Node("PROTECT", 2, -1, ENDLESS, 1, 0, 100, "LESS HIT DAMAGE"),
            new Node("SHIELD", 2, PROTECT, 5, 1, 1, 0, "BLOCK HITS EACH WAVE"),
    };

    static final double BASE_GEM_COST = 1, BASE_XP = 300, BASE_HP = 10;
    static final int MAX_PLAYER_LEVEL = 100;
    static final int BOSS_EVERY = 5;
    static final double BOSS_SHARE = 0.6;                     // boss HP = 60% of the whole wave's HP
    static final int MIN_SHIPS = 3, SHIP_SPREAD = 6, MAX_SHIPS = 30, SHIP_GROWTH_EVERY = 3;
    static final double SPAWN_GAP = 0.8, ENEMY_SPEED = 0.12, BOSS_SPEED = 0.06, ENEMY_TURN = 2, SPAWN_SKEW = 0.8;
    static final double TOWER_R = 0.1, ENEMY_R = 0.07, BOSS_R = 0.14, EDGE_MARGIN = 1.05;
    static final double SHOT_INTERVAL = 0.6, TAP_INTERVAL = 0.25, SHOT_SPEED = 1.5, AUTO_RANGE = 1.1;
    static final double TAP_TIME = 0.18, CHARGE_TIME = 3;     // shorter press = plain shot, longer = boost
    static final double CASH_PER_HP = 3, XP_PER_HP = 1, LEAK_PER_HP = 0.5;
    static final double HULL_GROWTH = 1.4, DAMAGE_GROWTH = 1.35, DEFENCE_DECAY = 0.85, MAX_REDUCTION = 0.75;
    static final double FLASH_TIME = 0.08, BLAST_TIME = 0.35;

    static final class Enemy {
        double x, y, heading, hp, flash;
        final double maxHp;
        final boolean boss;

        Enemy(double hp, boolean boss) {
            this.hp = hp;
            this.maxHp = hp;
            this.boss = boss;
        }
    }

    static final class Shot {
        double x, y, vx, vy, damage, radius, size;
        int pierce;
        boolean player;
        Enemy seek;                                           // auto shots home in; player shots never do
        final Set<Enemy> hit = new HashSet<>();
    }

    final int[] level = new int[NODES];                       // 0 = not bought
    final double[] gemCost = new double[NODES];               // next level, grown by step() per purchase
    double cash = 150, gems, xp, xpNext = BASE_XP;
    int playerLevel = 1;
    int wave;                                                 // waves cleared this run; the next one is wave + 1
    double waveHp = BASE_HP;                                  // regular enemy HP on the next wave
    int ships;                                                // 0 = not rolled yet; rolled once per run
    int checkpointWave, checkpointShips;                      // last cleared boss wave
    double checkpointHp = BASE_HP;
    boolean waveActive, lost, autoTrade, charging;
    double hull, shotCooldown, tapCooldown, held, spawnTimer, halfH = 1.5;
    int shieldHits;
    final List<Enemy> enemies = new ArrayList<>();
    final List<Enemy> queue = new ArrayList<>();
    final List<Shot> shots = new ArrayList<>();
    final List<double[]> blasts = new ArrayList<>();          // {x, y, age, radius}, drawn by the view
    private final Random rng;

    Game(Random rng) {
        this.rng = rng;
        Arrays.fill(gemCost, BASE_GEM_COST);
    }

    /** The growth rule: double is the target, move 1/3 of that distance, plus a random amount below that addition. */
    static double step(double v, Random rng) {
        double add = v / 3;
        return v + add + rng.nextDouble() * add;
    }

    // ---- stats: one place each, so upgrades stay a one-line change ----

    double hullMax() { return level[SHIP] == 0 ? 0 : 100 * Math.pow(HULL_GROWTH, level[SHIP] - 1); }
    double reduction() { return MAX_REDUCTION * (1 - Math.pow(DEFENCE_DECAY, level[PROTECT])); }
    double shotDamage() { return 5 * Math.pow(DAMAGE_GROWTH, level[WEAPONS] - 1); }
    double shotInterval() { return SHOT_INTERVAL * Math.pow(0.9, level[RAPID]); }
    double splashRadius() { return level[SPLASH] == 0 ? 0 : 0.08 + 0.02 * level[SPLASH]; }
    double critChance() { return 0.05 * level[CRIT]; }
    double boostDamage() { return 30 * Math.pow(DAMAGE_GROWTH, level[BOOST] - 1) * (1 + 0.5 * level[OVERLOAD]); }
    double chargeTime() { return CHARGE_TIME * Math.pow(0.9, level[CHARGE]); }
    double boostRadius() { return 0.1 + 0.04 * level[BLAST]; }
    double regenPerSecond() { return hullMax() * 0.005 * level[REGEN]; }
    double cashBonus() { return 1 + 0.1 * level[SALVAGE]; }
    double chargeFrac() { return Math.min(1, held / chargeTime()); }

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

    /** Cash that must stay unspent so Ship and Weapons can always be bought. Without them there is no income. */
    double requiredReserve(int buying) {
        double r = 0;
        for (int i : new int[]{SHIP, WEAPONS}) {
            if (level[i] == 0 && i != buying) r += NODE[i].cash;
        }
        return r;
    }

    boolean unlocked(int i) { return NODE[i].parent < 0 || level[NODE[i].parent] > 0; }
    boolean maxed(int i) { return level[i] >= NODE[i].max; }
    boolean paysCash(int i) { return level[i] == 0 && NODE[i].cash > 0; }

    boolean canBuy(int i) {
        if (!unlocked(i) || maxed(i)) return false;
        return paysCash(i) ? cash - NODE[i].cash >= requiredReserve(i) : gems >= gemCost[i];
    }

    /** Roots cost cash once; every other level costs gems. Returns false if not allowed. */
    boolean buy(int i) {
        if (!canBuy(i)) return false;
        double oldMax = hullMax();
        if (paysCash(i)) {
            cash -= NODE[i].cash;
        } else {
            gems -= gemCost[i];
            gemCost[i] = step(gemCost[i], rng);
        }
        level[i]++;
        hull += hullMax() - oldMax;
        return true;
    }

    /** Converts up to amount cash into gems at the current level's rate, never touching the reserve. */
    double tradeCash(double amount) {
        amount = Math.min(amount, cash - requiredReserve(-1));
        if (amount <= 0) return 0;
        cash -= amount;
        double got = amount / cashPerGem(playerLevel);
        gems += got;
        return got;
    }

    boolean canStart() { return !waveActive && level[SHIP] > 0 && level[WEAPONS] > 0; }

    boolean startWave() {
        if (!canStart()) return false;
        if (ships == 0) ships = checkpointShips = MIN_SHIPS + rng.nextInt(SHIP_SPREAD);
        waveActive = true;
        lost = false;
        hull = hullMax();
        shieldHits = level[SHIELD];
        spawnTimer = 0;
        for (int i = 0; i < ships; i++) queue.add(new Enemy(waveHp, false));
        if ((wave + 1) % BOSS_EVERY == 0) {
            // boss = 0.6 of (regular + boss), so boss = regular * 0.6 / 0.4
            queue.add(new Enemy(ships * waveHp * BOSS_SHARE / (1 - BOSS_SHARE), true));
        }
        return true;
    }

    /** Finger down. With BOOST owned this starts charging; without it the tap fires straight away. */
    void press(double x, double y) {
        if (!waveActive) return;
        if (level[BOOST] > 0) {
            charging = true;
            held = 0;
        } else {
            tap(x, y);
        }
    }

    /** Finger up at (x, y). A long press fires the boost at the power it reached; a short one is a plain shot. */
    void release(double x, double y) {
        if (!charging) return;
        charging = false;
        if (held < TAP_TIME) {
            tap(x, y);
            return;
        }
        double power = chargeFrac();
        Shot s = aimed(x, y);
        if (s == null) return;
        s.damage = boostDamage() * power;
        s.radius = boostRadius() * power;
        s.pierce = level[PIERCE];
        s.size = 1 + power;
        shots.add(s);
    }

    /** A plain shot along the line from the tower through (x, y). No aim assist: it can miss. */
    boolean tap(double x, double y) {
        if (!waveActive || tapCooldown > 0) return false;
        Shot s = aimed(x, y);
        if (s == null) return false;
        s.damage = shotDamage();
        tapCooldown = TAP_INTERVAL;
        shots.add(s);
        return true;
    }

    private Shot aimed(double x, double y) {
        double d = Math.hypot(x, y);
        if (d == 0) return null;
        Shot s = new Shot();
        s.vx = x / d * SHOT_SPEED;
        s.vy = y / d * SHOT_SPEED;
        s.size = 1;
        s.player = true;
        return s;
    }

    /** Advances the simulation by dt seconds. */
    void update(double dt) {
        shotCooldown -= dt;
        tapCooldown -= dt;
        for (Iterator<double[]> it = blasts.iterator(); it.hasNext(); ) {
            double[] b = it.next();
            b[2] += dt;
            if (b[2] >= BLAST_TIME) it.remove();
        }
        if (!waveActive) return;
        if (charging) held += dt;
        hull = Math.min(hullMax(), hull + regenPerSecond() * dt);

        spawnTimer -= dt;
        if (spawnTimer <= 0 && !queue.isEmpty()) {
            spawn(queue.remove(0));
            spawnTimer = SPAWN_GAP;
        }
        moveEnemies(dt);
        if (hull <= 0) {
            defeat();
            return;
        }
        if (shotCooldown <= 0) autoFire();
        moveShots(dt);
        removeDead();
        if (queue.isEmpty() && enemies.isEmpty()) clearWave();
    }

    /** Places an enemy just off a random point of the field edge, heading roughly at the tower. */
    private void spawn(Enemy e) {
        double a = rng.nextDouble() * 2 * Math.PI, c = Math.cos(a), s = Math.sin(a);
        double r = EDGE_MARGIN * Math.min(1 / Math.max(Math.abs(c), 1e-9), halfH / Math.max(Math.abs(s), 1e-9));
        e.x = c * r;
        e.y = s * r;
        e.heading = a + Math.PI + (rng.nextDouble() * 2 - 1) * SPAWN_SKEW;
        enemies.add(e);
    }

    private void moveEnemies(double dt) {
        for (Iterator<Enemy> it = enemies.iterator(); it.hasNext(); ) {
            Enemy e = it.next();
            e.flash -= dt;
            double want = Math.atan2(-e.y, -e.x);
            double turn = Math.IEEEremainder(want - e.heading, 2 * Math.PI);
            e.heading += Math.max(-ENEMY_TURN * dt, Math.min(ENEMY_TURN * dt, turn));
            double speed = e.boss ? BOSS_SPEED : ENEMY_SPEED;
            e.x += Math.cos(e.heading) * speed * dt;
            e.y += Math.sin(e.heading) * speed * dt;
            if (Math.hypot(e.x, e.y) < TOWER_R + radius(e)) {
                if (shieldHits > 0) shieldHits--;
                else hull -= e.maxHp * LEAK_PER_HP * (1 - reduction());
                blasts.add(new double[]{e.x, e.y, 0, radius(e)});
                it.remove();
            }
        }
    }

    private void autoFire() {
        List<Enemy> targets = new ArrayList<>();
        for (Enemy e : enemies) {
            if (Math.hypot(e.x, e.y) <= AUTO_RANGE) targets.add(e);
        }
        if (targets.isEmpty()) return;
        targets.sort((a, b) -> Double.compare(Math.hypot(a.x, a.y), Math.hypot(b.x, b.y)));
        for (Enemy e : targets.subList(0, Math.min(targets.size(), 1 + level[MULTI]))) {
            Shot s = new Shot();
            s.seek = e;
            s.damage = shotDamage() * (rng.nextDouble() < critChance() ? 2 : 1);
            s.radius = splashRadius();
            s.size = 1;
            shots.add(s);
        }
        shotCooldown = shotInterval();
    }

    private void moveShots(double dt) {
        for (Iterator<Shot> it = shots.iterator(); it.hasNext(); ) {
            Shot s = it.next();
            if (s.seek != null && s.seek.hp > 0 && enemies.contains(s.seek)) {
                double d = Math.max(1e-9, Math.hypot(s.seek.x - s.x, s.seek.y - s.y));
                s.vx = (s.seek.x - s.x) / d * SHOT_SPEED;
                s.vy = (s.seek.y - s.y) / d * SHOT_SPEED;
            }
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            if (Math.abs(s.x) > EDGE_MARGIN || Math.abs(s.y) > halfH * EDGE_MARGIN) {
                it.remove();
                continue;
            }
            for (Enemy e : enemies) {
                if (s.hit.contains(e) || Math.hypot(e.x - s.x, e.y - s.y) > radius(e)) continue;
                strike(s, e);
                s.hit.add(e);
                if (s.pierce-- <= 0) {
                    it.remove();
                    break;
                }
            }
        }
    }

    private void strike(Shot s, Enemy target) {
        for (Enemy e : enemies) {
            if (e == target || (s.radius > 0 && Math.hypot(e.x - s.x, e.y - s.y) <= s.radius)) {
                e.hp -= s.damage;
                e.flash = FLASH_TIME;
            }
        }
        if (s.radius > 0) blasts.add(new double[]{s.x, s.y, 0, s.radius});
    }

    private static double radius(Enemy e) { return e.boss ? BOSS_R : ENEMY_R; }

    /** Cash auto-trade leaves alone: every root not bought yet. */
    double autoReserve() {
        double r = 0;
        for (int i = 0; i < NODES; i++) {
            if (level[i] == 0) r += NODE[i].cash;
        }
        return r;
    }

    // ponytail: kills pay out at once, so quitting mid-wave keeps them and replays the wave. Hold pay until clear if farming matters.
    private void removeDead() {
        for (Iterator<Enemy> it = enemies.iterator(); it.hasNext(); ) {
            Enemy e = it.next();
            if (e.hp <= 0) {
                cash += e.maxHp * CASH_PER_HP * cashBonus();
                xp += e.maxHp * XP_PER_HP;
                blasts.add(new double[]{e.x, e.y, 0, radius(e)});
                it.remove();
            }
        }
        while (playerLevel < MAX_PLAYER_LEVEL && xp >= xpNext) {
            xp -= xpNext;
            xpNext = step(xpNext, rng);
            playerLevel++;
        }
    }

    private void endWave() {
        waveActive = false;
        charging = false;
        enemies.clear();
        queue.clear();
        shots.clear();
    }

    private void clearWave() {
        endWave();
        wave++;
        waveHp = step(waveHp, rng);
        if (wave % SHIP_GROWTH_EVERY == 0) ships = Math.min(MAX_SHIPS, ships + 1);
        if (wave % BOSS_EVERY == 0) {
            checkpointWave = wave;
            checkpointHp = waveHp;
            checkpointShips = ships;
        }
        hull = hullMax();
        if (autoTrade) tradeCash(cash - autoReserve());
    }

    /** Back to the last cleared boss. Upgrades, cash and gems are kept, so a loss can't strand the player. */
    private void defeat() {
        endWave();
        lost = true;
        wave = checkpointWave;
        waveHp = checkpointHp;
        ships = checkpointShips;
    }
}
