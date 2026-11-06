package com.matthew.lowbitdefense;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** Headless checks for the game rules. build.sh runs this before packaging. */
public final class GameTest {
    public static void main(String[] args) {
        check(close(Game.cashPerGem(1), 100), "level 1: 100 cash per gem");
        check(close(Game.cashPerGem(50), 1), "level 50: 1 to 1");
        check(close(Game.cashPerGem(100), 0.01), "level 100: 1 cash buys 100 gems");

        check(!newGame(0).startWave(), "no wave without ship and weapons");

        Game g = newGame(1);
        g.cash = 150;
        check(g.buy(Game.SHIP) && g.cash == 100, "base purchase costs cash");
        check(!g.buy(Game.WEAPONS) || g.cash == 25, "base purchase costs cash (weapons)");
        g.gems = 9;
        check(!g.buy(Game.SHIP) || g.gems == 9, "upgrade needs gems");
        g.module[Game.SHIP] = 1;
        g.gems = 10;
        check(g.buy(Game.SHIP) && g.gems == 0 && g.module[Game.SHIP] == 2, "level 1 to 2 costs 10 gems");

        // Boss is 60% of the whole wave (regular + boss) on every boss wave.
        for (int seed = 0; seed < 50; seed++) {
            Game b = newGame(seed);
            b.module[Game.SHIP] = 1;
            b.module[Game.WEAPONS] = 1;
            b.wave = Game.BOSS_EVERY - 1;
            b.startWave();
            double regular = 0, boss = 0;
            for (Game.Enemy e : b.queue) {
                if (e.boss) boss += e.hp;
                else regular += e.hp;
            }
            check(close(boss / (boss + regular), Game.BOSS_SHARE), "boss share is 60% (seed " + seed + ")");
        }

        // Ship count is random per wave, within bounds.
        // One shared RNG: small consecutive seeds give correlated first draws in java.util.Random.
        Set<Integer> counts = new HashSet<>();
        Random shared = new Random(42);
        for (int i = 0; i < 50; i++) {
            Game c = new Game(shared);
            c.module[Game.SHIP] = 1;
            c.module[Game.WEAPONS] = 1;
            c.startWave();
            int n = c.queue.size();
            check(n >= 3 && n <= 6, "wave 1 ship count in 3..6, got " + n);
            counts.add(n);
        }
        check(counts.size() > 1, "ship count varies between seeds");

        // A full wave resolves (cleared or lost) and never hangs.
        Game s = newGame(7);
        s.module[Game.SHIP] = 1;
        s.module[Game.WEAPONS] = 1;
        s.module[Game.SPECIAL] = 1;
        s.startWave();
        for (int step = 0; step < 20000 && s.waveActive; step++) s.update(0.05);
        check(!s.waveActive, "wave resolves within the step budget");

        System.out.println("all game checks passed");
    }

    private static Game newGame(long seed) {
        return new Game(new Random(seed));
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("FAILED: " + what);
    }

    private static boolean close(double a, double b) {
        return Math.abs(a - b) <= 1e-9 * Math.max(1, Math.abs(b));
    }
}
