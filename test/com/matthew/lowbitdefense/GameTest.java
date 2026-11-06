package com.matthew.lowbitdefense;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** Headless checks for the game rules. build.sh runs this before packaging. */
public final class GameTest {
    public static void main(String[] args) {
        rates();
        growthRule();
        purchases();
        tree();
        noSoftLock();
        autoTrade();
        waves();
        homing();
        aiming();
        boostShot();
        defeatAndClear();
        if (args.length > 0) report();
        playable();
        System.out.println("all game checks passed");
    }

    private static void rates() {
        check(close(Game.cashPerGem(1), 100), "level 1: 100 cash per gem");
        check(close(Game.cashPerGem(50), 1), "level 50: 1 to 1");
        check(close(Game.cashPerGem(100), 0.01), "level 100: 1 cash buys 100 gems");
        check(Game.cashPerGem(25) < 100 && Game.cashPerGem(25) > 1, "mid rate sits between anchors");
        check(close(Game.cashPerGem(0), 100) && close(Game.cashPerGem(500), 0.01), "rate clamps out of range");
    }

    private static void growthRule() {
        Random r = new Random(1);
        double lo = 9, hi = 0;
        for (int i = 0; i < 10000; i++) {
            double m = Game.step(3, r) / 3;
            lo = Math.min(lo, m);
            hi = Math.max(hi, m);
        }
        check(lo >= 4.0 / 3 && hi < 5.0 / 3, "step moves 1/3 of the way to x2 plus less than that again");
        check(hi - lo > 0.3, "step's random part spans its range");
    }

    private static void purchases() {
        Game g = game();
        g.cash = 500;
        check(g.buy(Game.SHIP), "ship buys");
        check(g.cash == 450 && g.level[Game.SHIP] == 1 && g.gems == 0, "root purchase takes cash, not gems");

        g.gems = 0.5;
        check(!g.buy(Game.SHIP), "upgrade refused without gems");
        check(g.level[Game.SHIP] == 1 && g.gems == 0.5 && g.cash == 450, "refused upgrade changes nothing");

        g.gems = 1;
        check(g.buy(Game.SHIP), "upgrade buys with gems");
        check(g.gems == 0 && g.cash == 450 && g.level[Game.SHIP] == 2, "upgrade takes gems, not cash");
        check(g.gemCost[Game.SHIP] > Game.BASE_GEM_COST && g.gemCost[Game.WEAPONS] == Game.BASE_GEM_COST,
                "only the bought node's price grows");
    }

    private static void tree() {
        for (int i = 0; i < Game.NODES; i++) {
            Game.Node n = Game.NODE[i];
            check((n.parent < 0) == (n.cash > 0), n.name + ": exactly the roots cost cash");
            check(n.parent < 0 || Game.NODE[n.parent].tree == n.tree, n.name + ": parent is in the same tree");
        }
        Game g = game();
        g.cash = 1e6;
        g.gems = 1e6;
        check(!g.buy(Game.MULTI) && !g.buy(Game.RAPID), "locked nodes refuse");
        check(g.buy(Game.WEAPONS) && !g.buy(Game.MULTI), "root unlocks children, not grandchildren");
        check(g.buy(Game.RAPID) && g.buy(Game.MULTI), "path opens in order");
        double cash = g.cash;
        check(g.buy(Game.SPLASH) && g.cash == cash, "child's first level is paid in gems");
        while (g.buy(Game.MULTI)) { }
        check(g.level[Game.MULTI] == Game.NODE[Game.MULTI].max, "capped node stops at its max");

        double slow = g.shotInterval();
        g.buy(Game.RAPID);
        check(g.shotInterval() < slow, "RAPID shortens the interval");
    }

    private static void noSoftLock() {
        Game g = game();
        g.tradeCash(g.cash);
        check(g.cash == 125, "trade leaves Ship + Weapons money");
        check(!g.buy(Game.PROTECT), "Protect refused when it would strand Weapons");
        check(g.buy(Game.SHIP) && g.buy(Game.WEAPONS) && g.canStart(), "new game can always reach a wave");
    }

    private static void autoTrade() {
        Game g = strong();
        g.autoTrade = true;
        g.cash = 10000;
        g.level[Game.PROTECT] = 1;
        g.startWave();
        run(g);
        check(!g.lost && close(g.cash, Game.NODE[Game.BOOST].cash), "auto-trade keeps cash for the unbought root");
        check(g.gems > 0, "auto-trade produced gems");
    }

    private static void waves() {
        check(!game().startWave(), "no wave without ship and weapons");

        // Boss is 60% of the whole wave (regular + boss) on boss waves, and only then.
        Random shared = new Random(42);
        for (int i = 0; i < 50; i++) {
            Game b = armed(shared);
            b.wave = Game.BOSS_EVERY - 1;
            b.startWave();
            double regular = 0, boss = 0;
            for (Game.Enemy e : b.queue) {
                if (e.boss) boss += e.hp;
                else regular += e.hp;
            }
            check(close(boss / (boss + regular), Game.BOSS_SHARE), "boss share is 60%");
        }
        Game plain = armed();
        plain.startWave();
        for (Game.Enemy e : plain.queue) check(!e.boss, "no boss on wave 1");

        // Ship count is rolled once per run: every value in range shows up, and it holds between waves.
        Set<Integer> counts = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            Game c = armed(shared);
            c.startWave();
            counts.add(c.queue.size());
        }
        check(counts.size() == Game.SHIP_SPREAD, "all ship counts occur, got " + counts);
        for (int n : counts) check(n >= Game.MIN_SHIPS && n < Game.MIN_SHIPS + Game.SHIP_SPREAD, "ship count in range");

        Game r = strong();
        r.startWave();
        int first = r.ships;
        run(r);
        check(r.wave == 1 && r.ships == first, "ship count holds until growth");
    }

    private static void homing() {
        // Enemies arrive from every quadrant, and every one reaches the tower instead of orbiting.
        boolean[] seen = new boolean[4];
        Random shared = new Random(3);
        for (int i = 0; i < 200; i++) {
            Game g = armed(shared);
            g.level[Game.WEAPONS] = 0;                        // nothing shoots: all must land
            g.level[Game.PROTECT] = 0;
            g.level[Game.SHIP] = 500;
            g.level[Game.WEAPONS] = 1;
            g.startWave();
            g.shotCooldown = 1e9;                             // tower holds fire
            int n = g.queue.size();
            g.update(0.05);
            Game.Enemy first = g.enemies.get(0);
            seen[(first.x > 0 ? 1 : 0) + (first.y > 0 ? 2 : 0)] = true;
            check(Math.abs(first.x) >= 1 || Math.abs(first.y) >= g.halfH, "spawns at the field edge");
            for (int s = 0; s < 40000 && g.waveActive; s++) g.update(0.05);
            check(g.wave == 1 && !g.lost && n > 0, "every homing enemy reaches the tower");
        }
        for (boolean q : seen) check(q, "enemies come from all directions");
    }

    private static Game.Enemy parked(Game g, double x, double y) {
        Game.Enemy e = new Game.Enemy(1000, false);
        e.x = x;
        e.y = y;
        e.heading = Math.atan2(-y, -x);
        g.enemies.add(e);
        return e;
    }

    private static Game inWave() {
        Game g = armed();
        g.startWave();                                        // queue stays non-empty so the wave can't end
        g.spawnTimer = 1e9;
        g.shotCooldown = 1e9;
        return g;
    }

    private static void aiming() {
        Game g = inWave();
        Game.Enemy right = parked(g, 0.6, 0);
        check(!g.tap(0, 0), "tapping the tower itself has no direction");
        g.tap(-1, 0);                                         // wrong way: no assist may bend it
        check(g.shots.get(0).seek == null && g.shots.get(0).vx < 0, "tap fires along the tap, unassisted");
        for (int i = 0; i < 30; i++) g.update(0.05);
        check(right.hp == right.maxHp, "a shot in the wrong direction misses");

        g = inWave();
        right = parked(g, 0.6, 0);
        g.tap(5, 0);
        for (int i = 0; i < 12; i++) g.update(0.05);
        check(right.hp == right.maxHp - g.shotDamage(), "a shot on line hits, at any tap distance");

        g = inWave();
        parked(g, 0.6, 0);
        check(g.tap(1, 0) && !g.tap(1, 0), "taps are rate limited");

        g = armed();
        check(!g.tap(1, 0), "no shooting outside a wave");

        g = inWave();
        g.press(1, 0);
        check(g.shots.size() == 1 && !g.charging, "without BOOST a press fires at once");

        g = armed();
        g.startWave();
        g.queue.clear();
        g.spawnTimer = 1e9;
        Game.Enemy near = parked(g, 0.5, 0);
        g.update(0.02);
        check(g.shots.size() == 1 && g.shots.get(0).seek == near, "the tower auto fires by itself");
    }

    private static void boostShot() {
        Game g = inWave();
        g.level[Game.BOOST] = 1;
        parked(g, 0.6, 0);
        g.press(1, 0);
        check(g.charging && g.shots.isEmpty(), "BOOST press charges and holds fire");
        g.update(Game.TAP_TIME / 2);
        g.release(1, 0);
        check(g.shots.size() == 1 && close(g.shots.get(0).damage, g.shotDamage()), "a quick tap is still a plain shot");

        // Damage is the power reached at release.
        double[] dmg = new double[2];
        int[] hold = {10, 30};
        for (int i = 0; i < 2; i++) {
            Game h = inWave();
            h.level[Game.BOOST] = 1;
            h.press(0, 1);
            for (int t = 0; t < hold[i]; t++) h.update(0.05);
            h.release(0, 1);
            dmg[i] = h.shots.get(0).damage;
            check(h.shots.get(0).seek == null && h.shots.get(0).vy > 0 && !h.charging, "boost fires along the release point");
        }
        check(close(dmg[1] / dmg[0], 3), "boost power tracks hold time, got " + dmg[1] / dmg[0]);

        Game full = inWave();
        full.level[Game.BOOST] = 1;
        full.press(1, 0);
        for (double t = 0; t < 20; t += 0.05) full.update(0.05);
        check(full.chargeFrac() == 1, "charge stops at full");
        full.release(1, 0);
        check(close(full.shots.get(0).damage, full.boostDamage()), "full charge is full power");

        // Pierce carries through; without it the shot stops. Splash reaches neighbours.
        Game p = inWave();
        p.level[Game.BOOST] = 1;
        p.level[Game.PIERCE] = 1;
        Game.Enemy a = parked(p, 0.3, 0), b = parked(p, 0.8, 0);
        p.press(1, 0);
        for (double t = 0; t < 1; t += 0.05) p.update(0.05);
        p.release(1, 0);
        a.x = 0.3;
        for (int i = 0; i < 16; i++) {
            a.x = 0.3;
            b.x = 0.8;
            a.y = b.y = 0;
            p.update(0.05);
        }
        check(a.hp < a.maxHp && b.hp < b.maxHp, "PIERCE passes through");

        Game splash = armed();
        splash.level[Game.SPLASH] = 5;
        splash.startWave();
        splash.queue.clear();
        splash.spawnTimer = 1e9;
        Game.Enemy c = parked(splash, 0.5, 0), d = parked(splash, 0.55, 0.05);
        for (int i = 0; i < 12; i++) {
            splash.shotCooldown = i == 0 ? 0 : 1e9;
            splash.update(0.05);
        }
        check(c.hp < c.maxHp && d.hp < d.maxHp, "SPLASH hits neighbours");

        Game end = inWave();
        end.level[Game.BOOST] = 1;
        end.press(1, 0);
        end.queue.clear();
        end.update(0.05);
        check(!end.waveActive && !end.charging, "wave ending cancels a held charge");
    }

    private static void defeatAndClear() {
        Game w = strong();
        double hp = w.waveHp, cash = w.cash;
        w.startWave();
        run(w);
        check(w.wave == 1 && !w.lost && w.waveHp > hp && w.cash > cash && w.xp > 0, "a clear advances and pays");

        // Reach a boss, clear it, then lose: the run returns to that checkpoint with upgrades intact.
        Game d = strong();
        for (int i = 0; i < Game.BOSS_EVERY + 1; i++) {
            d.startWave();
            run(d);
        }
        check(d.wave == Game.BOSS_EVERY + 1 && d.checkpointWave == Game.BOSS_EVERY, "boss wave sets checkpoint");
        d.level[Game.WEAPONS] = 1;
        d.level[Game.SHIP] = 1;
        d.waveHp = 1e6;
        d.startWave();
        run(d);
        check(d.lost && d.wave == Game.BOSS_EVERY && d.waveHp == d.checkpointHp, "defeat returns to checkpoint");
        check(d.enemies.isEmpty() && d.queue.isEmpty() && d.shots.isEmpty() && d.canStart(), "defeat leaves a clean, retryable state");

        Game m = armed();
        m.startWave();
        check(m.wave == 0, "wave counts clears, so a mid-wave quit replays it rather than skipping it");

        Game sh = armed();
        sh.level[Game.SHIELD] = 1;
        sh.startWave();
        sh.queue.clear();
        sh.spawnTimer = 1e9;
        sh.shotCooldown = 1e9;
        parked(sh, 0.01, 0);
        double before = sh.hull;
        sh.update(0.05);
        check(sh.hull == before && sh.shieldHits == 0, "SHIELD blocks a hit");
    }

    /** A bot using auto fire only must get deep into the game: first gem early, level tracking wave. */
    private static void playable() {
        for (int seed = 1; seed <= 5; seed++) {
            Sim s = new Sim(seed, 3 * 3600);
            check(s.firstUpgrade < 300, "first upgrade inside 5 minutes, got " + (int) s.firstUpgrade + "s");
            check(s.best >= 50, "bot passes wave 50, got " + s.best);
            check(s.level50 > 0, "bot reaches level 50 (1:1 rate) within 3 hours");
        }
    }

    private static void report() {
        for (int seed = 1; seed <= 3; seed++) {
            Sim s = new Sim(seed, 3 * 3600);
            System.out.printf("seed %d: firstUp %.0fs w10 %.0fm w25 %.0fm w50 %.0fm L50 %.0fm best %d lvl %d losses %d weapons %d%n",
                    seed, s.firstUpgrade, s.at[10] / 60, s.at[25] / 60, s.at[50] / 60, s.level50 / 60,
                    s.best, s.g.playerLevel, s.losses, s.g.level[Game.WEAPONS]);
        }
    }

    private static final class Sim {
        final Game g;
        final double[] at = new double[2000];
        double firstUpgrade = Double.MAX_VALUE, level50, clock;
        int best, losses;

        Sim(long seed, double seconds) {
            g = new Game(new Random(seed));
            while (clock < seconds) {
                for (int i : new int[]{Game.SHIP, Game.WEAPONS, Game.PROTECT}) {
                    if (g.level[i] == 0) g.buy(i);
                }
                g.tradeCash(g.cash - g.autoReserve());
                while (true) {                                  // cheapest first, weighted to weapons
                    int pick = Game.WEAPONS;
                    for (int i = 0; i < Game.NODES; i++) {
                        if (i == Game.BOOST || i == Game.CHARGE || i == Game.OVERLOAD || i == Game.BLAST || i == Game.PIERCE) continue;
                        if (g.level[i] > 0 || g.unlocked(i)) {
                            if (!g.paysCash(i) && !g.maxed(i) && g.gemCost[i] * 2 < g.gemCost[pick]) pick = i;
                        }
                    }
                    if (!g.buy(pick)) break;
                    firstUpgrade = Math.min(firstUpgrade, clock);
                }
                g.startWave();
                while (g.waveActive) {
                    g.update(0.05);
                    clock += 0.05;
                }
                if (g.lost) losses++;
                if (g.wave > best) {
                    best = g.wave;
                    if (best < at.length) at[best] = clock;
                }
                if (level50 == 0 && g.playerLevel >= 50) level50 = clock;
            }
        }
    }

    private static Game game() {
        return new Game(new Random(7));
    }

    private static Game armed() {
        return armed(new Random(7));
    }

    private static Game armed(Random r) {
        Game g = new Game(r);
        g.level[Game.SHIP] = 1;
        g.level[Game.WEAPONS] = 1;
        return g;
    }

    private static Game strong() {
        Game g = armed();
        g.level[Game.WEAPONS] = 40;
        return g;
    }

    private static void run(Game g) {
        for (int step = 0; step < 100000 && g.waveActive; step++) g.update(0.05);
        check(!g.waveActive, "wave resolves within the step budget");
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("FAILED: " + what);
    }

    private static boolean close(double a, double b) {
        return Math.abs(a - b) <= 1e-9 * Math.max(1, Math.abs(b));
    }
}
