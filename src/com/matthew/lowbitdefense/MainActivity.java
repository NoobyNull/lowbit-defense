package com.matthew.lowbitdefense;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {
    private GameView view;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        view = new GameView(this);
        setContentView(view);
    }

    @Override
    protected void onPause() {
        super.onPause();
        view.save();
    }

    static final class GameView extends View {
        // Palette: every colour lives here.
        static final int C_BG = 0xFF0B0F1A, C_FIELD = 0xFF121A2B, C_LANE = 0xFF1E2C4A, C_SHIP = 0xFF4FD1C5,
                C_ENEMY = 0xFFF56565, C_BOSS = 0xFFED8936, C_TEXT = 0xFFE2E8F0, C_DIM = 0xFF64748B,
                C_CASH = 0xFF68D391, C_GEM = 0xFF9F7AEA, C_BTN = 0xFF1F2937, C_BEAM = 0xFFFEFCBF;

        // Layout as fractions of screen height.
        static final float HUD_FRAC = 0.12f, FIELD_FRAC = 0.60f, ROW_FRAC = 0.055f, TEXT_FRAC = 0.024f;

        // Low-bit sprites: '#' = filled cell.
        static final String[] SHIP_SPR = {
                "...##...",
                "..####..",
                "..####..",
                ".######.",
                "########",
                "#.####.#",
                "##.##.##",
                "#......#"};
        static final String[] ENEMY_SPR = {
                "..####..",
                ".######.",
                "##.##.##",
                "########",
                ".##..##.",
                "#.####.#",
                "#......#",
                ".#....#."};
        static final String[] BOSS_SPR = {
                "...########.",
                "..##########",
                ".##.######.#",
                "############",
                "##.##.##.##.",
                "############",
                "..#.####.#..",
                ".##.#..#.##."};

        private static final String PREFS = "lowbit-save";

        private final Game game = new Game(new Random());
        private final SharedPreferences prefs;
        private final Paint paint = new Paint();
        private final RectF fieldRect = new RectF();
        private final RectF[] buyRects = new RectF[4], ctrlRects = new RectF[4];
        private long lastFrame;
        private boolean wasActive;

        private final Runnable frame = new Runnable() {
            @Override
            public void run() {
                long now = System.nanoTime();
                double dt = (now - lastFrame) / 1e9;
                lastFrame = now;
                game.update(Math.min(dt, 0.1));                 // clamp so a stall can't teleport enemies
                if (wasActive && !game.waveActive) save();      // wave just ended
                wasActive = game.waveActive;
                invalidate();
                postOnAnimation(this);
            }
        };

        GameView(Context c) {
            super(c);
            prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            for (int i = 0; i < 4; i++) {
                buyRects[i] = new RectF();
                ctrlRects[i] = new RectF();
            }
            paint.setTypeface(Typeface.MONOSPACE);
            load();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            lastFrame = System.nanoTime();
            postOnAnimation(frame);
        }

        @Override
        protected void onDetachedFromWindow() {
            removeCallbacks(frame);
            super.onDetachedFromWindow();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            float W = w, H = h, m = W * 0.03f;
            float fieldBottom = H * (HUD_FRAC + FIELD_FRAC), rowH = H * ROW_FRAC;
            fieldRect.set(0, H * HUD_FRAC, W, fieldBottom);
            for (int i = 0; i < 4; i++) {
                float top = fieldBottom + i * rowH;
                buyRects[i].set(W * 0.58f, top + rowH * 0.12f, W - m, top + rowH * 0.88f);
            }
            float btnW = (W - 5 * m) / 4, top = fieldBottom + 4 * rowH;
            for (int i = 0; i < 4; i++) {
                ctrlRects[i].set(m + i * (btnW + m), top + m * 0.4f, m + i * (btnW + m) + btnW, H - m * 0.4f);
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            float W = getWidth(), H = getHeight(), m = W * 0.03f, px = W / 128f, ts = H * TEXT_FRAC;
            float fieldH = fieldRect.height();
            c.drawColor(C_BG);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(C_FIELD);
            c.drawRect(fieldRect, paint);

            // Lane
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(px * 3);
            paint.setColor(C_LANE);
            for (int i = 0; i < Game.PATH.length - 1; i++) {
                c.drawLine(fx(Game.PATH[i][0], W), fy(Game.PATH[i][1]), fx(Game.PATH[i + 1][0], W), fy(Game.PATH[i + 1][1]), paint);
            }

            // Ship and its range
            float sx = fx(Game.SHIP_X, W), sy = fy(Game.SHIP_Y);
            float rx = (float) (Game.SHIP_RANGE * W), ry = (float) (Game.SHIP_RANGE * fieldH);
            c.save();
            c.clipRect(fieldRect);
            paint.setStrokeWidth(px * 0.5f);
            c.drawOval(sx - rx, sy - ry, sx + rx, sy + ry, paint);
            c.restore();

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(C_SHIP);
            drawSprite(c, SHIP_SPR, sx, sy, px, paint);

            // Enemies with HP bars
            for (Game.Enemy e : game.enemies) {
                double[] p = Game.pointAt(e.t);
                float x = fx(p[0], W), y = fy(p[1]), s = e.boss ? px * 1.5f : px;
                String[] spr = e.boss ? BOSS_SPR : ENEMY_SPR;
                paint.setColor(e.boss ? C_BOSS : C_ENEMY);
                drawSprite(c, spr, x, y, s, paint);
                float barW = spr[0].length() * s, barY = y - spr.length * s / 2f - px * 2;
                paint.setColor(C_LANE);
                c.drawRect(x - barW / 2, barY, x + barW / 2, barY + px, paint);
                paint.setColor(e.boss ? C_BOSS : C_ENEMY);
                c.drawRect(x - barW / 2, barY, x - barW / 2 + barW * (float) (e.hp / e.maxHp), barY + px, paint);
            }

            // Beam
            if (game.beamTimer > 0) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(px * 0.6f);
                paint.setColor(C_BEAM);
                c.drawLine(sx, sy, fx(game.beamTo[0], W), fy(game.beamTo[1]), paint);
                paint.setStyle(Paint.Style.FILL);
            }

            // HUD
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(ts);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setColor(C_TEXT);
            c.drawText("WAVE " + game.wave, m, H * 0.04f, paint);
            paint.setTextAlign(Paint.Align.RIGHT);
            c.drawText("LV " + game.playerLevel + "  " + rateText(), W - m, H * 0.04f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setColor(C_CASH);
            c.drawText("$" + fmt(game.cash), m, H * 0.078f, paint);
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setColor(C_GEM);
            c.drawText("GEM " + fmt(Math.floor(game.gems)), W - m, H * 0.078f, paint);
            float hullMax = (float) game.hullMax();
            paint.setColor(C_BTN);
            c.drawRect(m, H * 0.095f, W - m, H * 0.105f, paint);
            if (hullMax > 0) {
                paint.setColor(C_SHIP);
                c.drawRect(m, H * 0.095f, m + (W - 2 * m) * (float) Math.max(0, game.hull / hullMax), H * 0.105f, paint);
            }

            // Upgrade rows
            paint.setTextAlign(Paint.Align.LEFT);
            for (int i = 0; i < 4; i++) {
                RectF b = buyRects[i];
                int lvl = game.module[i];
                String label = lvl == 0 ? "BUY $" + fmt(Game.BASE_CASH[i])
                        : lvl >= Game.MAX_MODULE_LEVEL ? "MAX"
                        : "UP " + fmt(Game.upgradeCost(lvl)) + " GEM";
                boolean can = lvl == 0 ? game.cash >= Game.BASE_CASH[i]
                        : lvl < Game.MAX_MODULE_LEVEL && game.gems >= Game.upgradeCost(lvl);
                paint.setColor(C_TEXT);
                c.drawText(Game.MODULE_NAMES[i] + (lvl == 0 ? " LOCKED" : " LV " + lvl), m, b.centerY() + ts * 0.35f, paint);
                paint.setColor(C_BTN);
                c.drawRect(b, paint);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setColor(can ? C_TEXT : C_DIM);
                c.drawText(label, b.centerX(), b.centerY() + ts * 0.35f, paint);
                paint.setTextAlign(Paint.Align.LEFT);
            }

            // Controls
            paint.setTextAlign(Paint.Align.CENTER);
            String[] labels = {
                    game.lost ? "RETRY" : game.waveActive ? "WAVE " + game.wave : "START",
                    game.module[Game.SPECIAL] == 0 ? "LOCKED"
                            : game.specialCooldown > 0 ? "CD " + (int) Math.ceil(game.specialCooldown) : "SPECIAL",
                    "AUTO " + (game.autoTrade ? "ON" : "OFF"),
                    "TRADE ALL"};
            for (int i = 0; i < 4; i++) {
                RectF b = ctrlRects[i];
                paint.setColor(C_BTN);
                c.drawRect(b, paint);
                paint.setColor(C_TEXT);
                c.drawText(labels[i], b.centerX(), b.centerY() + ts * 0.35f, paint);
            }
            paint.setTextAlign(Paint.Align.LEFT);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() != MotionEvent.ACTION_UP) return true;
            float x = e.getX(), y = e.getY();
            for (int i = 0; i < 4; i++) {
                if (buyRects[i].contains(x, y)) game.buy(i);
            }
            if (ctrlRects[0].contains(x, y)) game.startWave();
            else if (ctrlRects[1].contains(x, y)) game.useSpecial();
            else if (ctrlRects[2].contains(x, y)) game.autoTrade = !game.autoTrade;
            else if (ctrlRects[3].contains(x, y)) game.tradeCash(game.cash);
            save();
            return true;
        }

        private String rateText() {
            double rate = Game.cashPerGem(game.playerLevel);
            return rate >= 1 ? "$" + fmt(rate) + "=1 GEM" : "$1=" + fmt(1 / rate) + " GEM";
        }

        private float fx(double x, float W) { return (float) (x * W); }
        private float fy(double y) { return fieldRect.top + (float) (y * fieldRect.height()); }

        private static void drawSprite(Canvas c, String[] rows, float cx, float cy, float s, Paint p) {
            float x0 = cx - rows[0].length() * s / 2f, y0 = cy - rows.length * s / 2f;
            for (int r = 0; r < rows.length; r++) {
                for (int col = 0; col < rows[r].length(); col++) {
                    if (rows[r].charAt(col) == '#') {
                        c.drawRect(x0 + col * s, y0 + r * s, x0 + (col + 1) * s, y0 + (r + 1) * s, p);
                    }
                }
            }
        }

        static String fmt(double v) {
            if (v >= 1e6) return String.format(Locale.US, "%.2e", v);
            if (v == Math.floor(v)) return String.format(Locale.US, "%,d", (long) v);
            return String.format(Locale.US, "%.2f", v);
        }

        void save() {
            SharedPreferences.Editor ed = prefs.edit();
            putD(ed, "cash", game.cash);
            putD(ed, "gems", game.gems);
            putD(ed, "xp", game.xp);
            ed.putInt("playerLevel", game.playerLevel);
            ed.putInt("wave", game.wave);
            ed.putBoolean("autoTrade", game.autoTrade);
            for (int i = 0; i < 4; i++) ed.putInt("module" + i, game.module[i]);
            ed.apply();
        }

        private void load() {
            game.cash = getD("cash", game.cash);
            game.gems = getD("gems", 0);
            game.xp = getD("xp", 0);
            game.playerLevel = prefs.getInt("playerLevel", 1);
            game.wave = prefs.getInt("wave", 0);
            game.autoTrade = prefs.getBoolean("autoTrade", false);
            for (int i = 0; i < 4; i++) game.module[i] = prefs.getInt("module" + i, 0);
            game.hull = game.hullMax();
        }

        private static void putD(SharedPreferences.Editor ed, String k, double v) {
            ed.putLong(k, Double.doubleToRawLongBits(v));
        }

        private double getD(String k, double def) {
            return Double.longBitsToDouble(prefs.getLong(k, Double.doubleToRawLongBits(def)));
        }
    }
}
