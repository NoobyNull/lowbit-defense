package com.matthew.lowbitdefense;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;

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
    protected void onResume() {
        super.onResume();
        view.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        view.pause();
    }

    static final class GameView extends View {
        // Palette: every colour lives here.
        static final int C_BG = 0xFF0D0B1E, C_FIELD = 0xFF141231, C_BAR_BG = 0xFF262352,
                C_STAR = 0xFF4A4780, C_AIM = 0xFF6F6BC4, C_SHIP = 0xFF3FE0C5, C_ENEMY = 0xFFFF5470, C_BOSS = 0xFFFFA62B, C_HIT = 0xFFFFFFFF,
                C_TEXT = 0xFFEDEBFF, C_DIM = 0xFF7572A8, C_CASH = 0xFF7CE577, C_GEM = 0xFFC792FF, C_BEAM = 0xFFFFF27A,
                C_PANEL = 0xFF1C1A40, C_BTN = 0xFF2B2860, C_BTN_HI = 0xFF4A46A0, C_BTN_LO = 0xFF12102C, C_GO = 0xFF1F7A6B;

        // Design constants. All geometry below is derived from these, the screen, and its insets.
        static final int TARGET_LOW_W = 150;                  // low-res canvas width; screen scale is the integer fit
        static final int TOUCH_DP = 48;                       // minimum touch target
        static final int GLYPH_W = 5, GLYPH_H = 7, CHAR_ADV = GLYPH_W + 1, LINE = GLYPH_H + 2, BAR_H = 2;
        static final int STAR_AREA = 180, CHARGE_DOTS = 32, BLAST_BITS = 8, BOSS_SCALE = 2, TREE_ROWS = 3;
        static final double TWINKLE = 2.0;
        static final String PREFS = "lowbit-save";

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
                "...######...",
                "..########..",
                ".##.####.##.",
                "############",
                "##.##..##.##",
                "############",
                "..#.####.#..",
                ".##.#..#.##."};

        // 5x7 font, one byte per column, bit 0 at the top. '*' is the gem icon.
        static final String FONT_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ$.:+-=/!%*";
        static final int[] FONT = {
                0x3E, 0x51, 0x49, 0x45, 0x3E, 0x00, 0x42, 0x7F, 0x40, 0x00, 0x42, 0x61, 0x51, 0x49, 0x46,
                0x21, 0x41, 0x45, 0x4B, 0x31, 0x18, 0x14, 0x12, 0x7F, 0x10, 0x27, 0x45, 0x45, 0x45, 0x39,
                0x3C, 0x4A, 0x49, 0x49, 0x30, 0x01, 0x71, 0x09, 0x05, 0x03, 0x36, 0x49, 0x49, 0x49, 0x36,
                0x06, 0x49, 0x49, 0x29, 0x1E, 0x7E, 0x11, 0x11, 0x11, 0x7E, 0x7F, 0x49, 0x49, 0x49, 0x36,
                0x3E, 0x41, 0x41, 0x41, 0x22, 0x7F, 0x41, 0x41, 0x22, 0x1C, 0x7F, 0x49, 0x49, 0x49, 0x41,
                0x7F, 0x09, 0x09, 0x09, 0x01, 0x3E, 0x41, 0x49, 0x49, 0x7A, 0x7F, 0x08, 0x08, 0x08, 0x7F,
                0x00, 0x41, 0x7F, 0x41, 0x00, 0x20, 0x40, 0x41, 0x3F, 0x01, 0x7F, 0x08, 0x14, 0x22, 0x41,
                0x7F, 0x40, 0x40, 0x40, 0x40, 0x7F, 0x02, 0x0C, 0x02, 0x7F, 0x7F, 0x04, 0x08, 0x10, 0x7F,
                0x3E, 0x41, 0x41, 0x41, 0x3E, 0x7F, 0x09, 0x09, 0x09, 0x06, 0x3E, 0x41, 0x51, 0x21, 0x5E,
                0x7F, 0x09, 0x19, 0x29, 0x46, 0x46, 0x49, 0x49, 0x49, 0x31, 0x01, 0x01, 0x7F, 0x01, 0x01,
                0x3F, 0x40, 0x40, 0x40, 0x3F, 0x1F, 0x20, 0x40, 0x20, 0x1F, 0x3F, 0x40, 0x38, 0x40, 0x3F,
                0x63, 0x14, 0x08, 0x14, 0x63, 0x07, 0x08, 0x70, 0x08, 0x07, 0x61, 0x51, 0x49, 0x45, 0x43,
                0x24, 0x2A, 0x7F, 0x2A, 0x12, 0x00, 0x60, 0x60, 0x00, 0x00, 0x00, 0x36, 0x36, 0x00, 0x00,
                0x08, 0x08, 0x3E, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x14, 0x14, 0x14, 0x14, 0x14,
                0x20, 0x10, 0x08, 0x04, 0x02, 0x00, 0x00, 0x5F, 0x00, 0x00, 0x23, 0x13, 0x08, 0x64, 0x62,
                0x08, 0x1C, 0x3E, 0x1C, 0x08};

        private final Game game = new Game(new Random());
        private final SharedPreferences prefs;
        private final Paint paint = new Paint();              // no anti-aliasing: hard pixels only
        private final Paint blit = new Paint();
        private final Rect field = new Rect(), hud = new Rect(), tabsBar = new Rect(), about = new Rect(),
                src = new Rect(), dst = new Rect();
        private final Rect[] nodes = new Rect[Game.NODES], ctrls = new Rect[4], tabs = new Rect[Game.TREES.length];
        private int[][] starField = new int[0][];
        private Bitmap low;
        private Canvas lc;
        private int scale = 1, pad = 1, insetL, insetT, insetR, insetB, tree, selected = Game.WEAPONS;
        private int aimX, aimY;                               // finger position in low-res pixels
        private long lastFrame;
        private double clock;
        private boolean running, wasActive, treeOpen;

        private final Runnable frame = new Runnable() {
            @Override
            public void run() {
                if (!running) return;
                long now = System.nanoTime();
                double dt = Math.min((now - lastFrame) / 1e9, 0.1);   // clamp so a stall can't teleport enemies
                lastFrame = now;
                clock += dt;
                game.update(dt);
                if (wasActive && !game.waveActive) save();
                wasActive = game.waveActive;
                invalidate();
                postOnAnimation(this);
            }
        };

        GameView(Context c) {
            super(c);
            prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            for (int i = 0; i < nodes.length; i++) nodes[i] = new Rect();
            for (int i = 0; i < ctrls.length; i++) ctrls[i] = new Rect();
            for (int i = 0; i < tabs.length; i++) tabs[i] = new Rect();
            blit.setFilterBitmap(false);
            setContentDescription("Lowbit Defense game");
            setOnApplyWindowInsetsListener((v, in) -> {
                readInsets(in);
                layoutGame();
                return in;
            });
            load();
        }

        void resume() {
            if (running) return;
            running = true;
            lastFrame = System.nanoTime();
            postOnAnimation(frame);
        }

        void pause() {
            running = false;
            removeCallbacks(frame);
            save();
        }

        private void readInsets(WindowInsets in) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets i = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                insetL = i.left;
                insetT = i.top;
                insetR = i.right;
                insetB = i.bottom;
            } else {
                insetL = in.getSystemWindowInsetLeft();
                insetT = in.getSystemWindowInsetTop();
                insetR = in.getSystemWindowInsetRight();
                insetB = in.getSystemWindowInsetBottom();
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            layoutGame();
        }

        private void layoutGame() {
            int w = getWidth(), h = getHeight();
            if (w == 0 || h == 0) return;
            scale = Math.max(1, w / TARGET_LOW_W);
            int lowW = (w + scale - 1) / scale, lowH = (h + scale - 1) / scale;
            low = Bitmap.createBitmap(lowW, lowH, Bitmap.Config.ARGB_8888);
            lc = new Canvas(low);
            src.set(0, 0, lowW, lowH);
            dst.set(0, 0, lowW * scale, lowH * scale);

            pad = Math.max(1, lowW / TARGET_LOW_W * 2);
            int left = toLow(insetL) + pad, right = lowW - toLow(insetR) - pad;
            int top = toLow(insetT) + pad, bottom = lowH - toLow(insetB) - pad;
            int touch = toLow(Math.round(TOUCH_DP * getResources().getDisplayMetrics().density));
            int cardH = Math.max(touch, 2 * LINE + pad);

            hud.set(left, top, right, top + 2 * LINE + 2 * BAR_H + 2 * pad);
            int y = bottom - touch;
            int btnW = (right - left - 3 * pad) / 4;
            for (int i = 0; i < ctrls.length; i++) {
                int x = left + i * (btnW + pad);
                ctrls[i].set(x, y, x + btnW, bottom);
            }

            field.set(0, hud.bottom + pad, lowW, lowH);
            game.halfH = (double) field.height() / field.width();

            int tabW = (right - left - (tabs.length - 1) * pad) / tabs.length;
            int tabTop = hud.bottom + pad;
            tabsBar.set(left, tabTop, right, tabTop + touch);
            for (int i = 0; i < tabs.length; i++) {
                int x = left + i * (tabW + pad);
                tabs[i].set(x, tabTop, x + tabW, tabsBar.bottom);
            }
            about.set(left, y - pad - 2 * LINE, right, y - pad);
            int cardW = (right - left - pad) / 2;
            int rowGap = Math.max(pad, (about.top - tabsBar.bottom - TREE_ROWS * cardH) / (TREE_ROWS + 1));
            for (int i = 0; i < Game.NODES; i++) {
                Game.Node n = Game.NODE[i];
                int x = left + (int) (n.col * (right - left - cardW));
                int cy = tabsBar.bottom + rowGap + n.row * (cardH + rowGap);
                nodes[i].set(x, cy, x + cardW, cy + cardH);
            }

            Random r = new Random(1);                         // fixed seed: same sky every layout
            starField = new int[field.width() * Math.max(0, field.height()) / STAR_AREA][];
            for (int i = 0; i < starField.length; i++) {
                starField[i] = new int[]{field.left + r.nextInt(field.width()), field.top + r.nextInt(Math.max(1, field.height())), r.nextInt(100)};
            }
        }

        private int toLow(int px) { return (px + scale - 1) / scale; }

        @Override
        protected void onDraw(Canvas c) {
            if (low == null) return;
            lc.drawColor(C_BG);
            if (treeOpen) {
                drawTree();
            } else {
                drawField();
                if (!game.waveActive) drawFieldControls();
            }
            drawHud();
            c.drawBitmap(low, src, dst, blit);
        }

        private void drawField() {
            rect(field.left, field.top, field.right, field.bottom, C_FIELD);
            lc.save();
            lc.clipRect(field);

            for (int[] s : starField) {
                if (Math.sin(clock * TWINKLE + s[2]) > -0.5) rect(s[0], s[1], s[0] + 1, s[1] + 1, C_STAR);
            }

            int cx = field.centerX(), cy = field.centerY();
            if (game.charging && game.held >= Game.TAP_TIME) {
                dottedLine(cx, cy, aimX, aimY);
                int ring = GLYPH_H + BAR_H, lit = (int) (CHARGE_DOTS * game.chargeFrac());
                for (int i = 0; i < CHARGE_DOTS; i++) {
                    double a = i * 2 * Math.PI / CHARGE_DOTS - Math.PI / 2;
                    int x = cx + (int) (ring * Math.cos(a)), y = cy + (int) (ring * Math.sin(a));
                    rect(x, y, x + 1, y + 1, i < lit ? C_BEAM : C_BAR_BG);
                }
            }
            sprite(SHIP_SPR, cx, cy, 1, C_SHIP);

            for (Game.Enemy e : game.enemies) {
                int x = wx(e.x), y = wy(e.y), s = e.boss ? BOSS_SCALE : 1;
                String[] spr = e.boss ? BOSS_SPR : ENEMY_SPR;
                int color = e.boss ? C_BOSS : C_ENEMY;
                sprite(spr, x, y, s, e.flash > 0 ? C_HIT : color);
                if (e.hp < e.maxHp) {
                    int barW = spr[0].length() * s, bx = x - barW / 2, by = y - spr.length * s / 2 - BAR_H;
                    rect(bx, by, bx + barW, by + 1, C_BAR_BG);
                    rect(bx, by, bx + Math.max(1, (int) (barW * e.hp / e.maxHp)), by + 1, color);
                }
            }

            for (Game.Shot s : game.shots) {
                int size = (int) Math.round(s.size * BAR_H);
                int x = wx(s.x) - size / 2, y = wy(s.y) - size / 2;
                rect(x, y, x + size, y + size, s.player ? C_HIT : C_BEAM);
            }

            for (double[] b : game.blasts) {
                double age = b[2] / Game.BLAST_TIME;
                int reach = (int) (age * b[3] * field.width() / 2);
                int color = age < 0.5 ? C_BEAM : C_ENEMY;
                for (int i = 0; i < BLAST_BITS; i++) {
                    double a = i * 2 * Math.PI / BLAST_BITS;
                    int x = wx(b[0]) + (int) (reach * Math.cos(a)), y = wy(b[1]) + (int) (reach * Math.sin(a));
                    rect(x, y, x + BAR_H, y + BAR_H, color);
                }
            }
            lc.restore();

            String msg = fieldMessage();
            if (!msg.isEmpty()) {
                int y = cy + GLYPH_H * 2;
                rect(field.left, y - pad, field.right, y + LINE + pad, C_BG);
                textCentered(msg, cx, y + 1, game.lost ? C_ENEMY : C_TEXT);
            }
        }

        private void dottedLine(int x0, int y0, int x1, int y1) {
            int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) / CHAR_ADV;
            for (int i = 1; i <= steps; i++) {
                int x = x0 + (x1 - x0) * i / steps, y = y0 + (y1 - y0) * i / steps;
                rect(x, y, x + 1, y + 1, C_AIM);
            }
        }

        private String fieldMessage() {
            if (game.waveActive) return "";
            if (game.lost) return "HULL LOST - BACK TO WAVE " + (game.wave + 1);
            if (game.level[Game.SHIP] == 0 && game.level[Game.WEAPONS] == 0) return "BUY SHIP AND WEAPONS";
            if (game.level[Game.SHIP] == 0) return "BUY A SHIP IN SKILLS";
            if (game.level[Game.WEAPONS] == 0) return "BUY WEAPONS";
            boolean boss = (game.wave + 1) % Game.BOSS_EVERY == 0;
            return (boss ? "BOSS WAVE " : "WAVE ") + (game.wave + 1) + " READY";
        }

        private void drawHud() {
            int y = hud.top;
            text("WAVE " + (game.wave + 1), hud.left, y, C_TEXT);
            String lv = "LV " + game.playerLevel;
            text(lv, hud.right - textW(lv), y, C_TEXT);

            y += LINE;
            text("$" + fmt(game.cash), hud.left, y, C_CASH);
            textCentered("*" + fmt(Math.floor(game.gems)), hud.centerX(), y, C_GEM);
            String rate = rateText();
            text(rate, hud.right - textW(rate), y, C_DIM);

            y += LINE + pad;
            bar(hud.left, y, hud.right, game.hullMax() > 0 ? game.hull / game.hullMax() : 0, C_SHIP);
            bar(hud.left, y + BAR_H + pad, hud.right, game.xp / game.xpNext, C_GEM);
        }

        private void bar(int l, int y, int r, double frac, int color) {
            rect(l, y, r, y + BAR_H, C_BAR_BG);
            rect(l, y, l + (int) ((r - l) * Math.max(0, Math.min(1, frac))), y + BAR_H, color);
        }

        private void drawTree() {
            for (int i = 0; i < tabs.length; i++) {
                bevel(tabs[i], i == tree ? C_GO : C_BTN);
                textCentered(Game.TREES[i], tabs[i], C_TEXT);
            }
            paint.setStrokeWidth(1);
            paint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < Game.NODES; i++) {
                Game.Node n = Game.NODE[i];
                if (n.tree != tree || n.parent < 0) continue;
                Rect a = nodes[n.parent], b = nodes[i];
                paint.setColor(game.unlocked(i) ? C_SHIP : C_BTN);
                lc.drawLine(a.centerX(), a.bottom, b.centerX(), b.top, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < Game.NODES; i++) {
                if (Game.NODE[i].tree == tree) drawNode(i);
            }
            text(Game.NODE[selected].name + " L" + game.level[selected], about.left, about.top, C_TEXT);
            text(Game.NODE[selected].about, about.left, about.top + LINE, C_DIM);

            boolean can = game.canBuy(selected);
            bevel(ctrls[0], can ? C_GO : C_PANEL);
            textCentered("BUY", ctrls[0], can ? C_TEXT : C_DIM);
            bevel(ctrls[1], C_BTN);
            textCentered("BACK", ctrls[1], C_TEXT);
            drawTradeControls();
        }

        private void drawNode(int i) {
            Rect b = nodes[i];
            int lvl = game.level[i];
            boolean open = game.unlocked(i), can = game.canBuy(i);
            bevel(b, i == selected ? C_BTN_HI : can ? C_BTN : C_PANEL);
            int y = b.top + (b.height() - 2 * LINE) / 2 + 1, x = b.left + pad + 1;
            text(Game.NODE[i].name, x, y, open ? C_TEXT : C_DIM);
            if (lvl > 0) {
                String l = "L" + lvl;
                text(l, b.right - pad - 1 - textW(l), y + LINE, C_TEXT);
            }
            String cost = !open ? "LOCKED" : game.maxed(i) ? "MAX"
                    : game.paysCash(i) ? "$" + fmt(Game.NODE[i].cash) : "*" + fmt(Math.ceil(game.gemCost[i]));
            text(cost, x, y + LINE, !can ? C_DIM : game.paysCash(i) ? C_CASH : C_GEM);
        }

        private void drawFieldControls() {
            boolean start = game.canStart();
            bevel(ctrls[0], start ? C_GO : C_PANEL);
            textCentered(game.lost ? "RETRY" : "START", ctrls[0], start ? C_TEXT : C_DIM);
            bevel(ctrls[1], start ? C_BTN : C_GO);
            textCentered("TREES", ctrls[1], C_TEXT);
            drawTradeControls();
        }

        private void drawTradeControls() {
            bevel(ctrls[2], game.autoTrade ? C_GO : C_BTN);
            textCentered("AUTO", ctrls[2], C_TEXT);
            boolean trade = game.cash - game.requiredReserve(-1) > 0;
            bevel(ctrls[3], trade ? C_BTN : C_PANEL);
            textCentered("TRADE", ctrls[3], trade ? C_GEM : C_DIM);
        }

        private void bevel(Rect b, int face) {
            rect(b.left, b.top, b.right, b.bottom, C_BTN_LO);
            rect(b.left, b.top, b.right - 1, b.bottom - 1, C_BTN_HI);
            rect(b.left + 1, b.top + 1, b.right - 1, b.bottom - 1, face);
        }

        private void rect(int l, int t, int r, int b, int color) {
            paint.setColor(color);
            lc.drawRect(l, t, r, b, paint);
        }

        private void sprite(String[] rows, int cx, int cy, int s, int color) {
            int x0 = cx - rows[0].length() * s / 2, y0 = cy - rows.length * s / 2;
            for (int r = 0; r < rows.length; r++) {
                for (int col = 0; col < rows[r].length(); col++) {
                    if (rows[r].charAt(col) == '#') {
                        rect(x0 + col * s, y0 + r * s, x0 + (col + 1) * s, y0 + (r + 1) * s, color);
                    }
                }
            }
        }

        private static int textW(String s) { return s.length() * CHAR_ADV - 1; }

        private void textCentered(String s, int cx, int y, int color) { text(s, cx - textW(s) / 2, y, color); }

        private void textCentered(String s, Rect b, int color) {
            textCentered(s, b.centerX(), b.centerY() - GLYPH_H / 2, color);
        }

        private void text(String s, int x, int y, int color) {
            paint.setColor(color);
            for (int i = 0; i < s.length(); i++, x += CHAR_ADV) {
                int g = FONT_CHARS.indexOf(s.charAt(i));
                if (g < 0) continue;                           // space
                for (int col = 0; col < GLYPH_W; col++) {
                    int bits = FONT[g * GLYPH_W + col];
                    for (int row = 0; row < GLYPH_H; row++) {
                        if ((bits >> row & 1) != 0) lc.drawRect(x + col, y + row, x + col + 1, y + row + 1, paint);
                    }
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            aimX = (int) e.getX() / scale;
            aimY = (int) e.getY() / scale;
            int action = e.getActionMasked();
            if (game.waveActive || game.charging) {
                // During a wave the whole screen is the battlefield.
                double x = toWorldX(aimX), y = toWorldY(aimY);
                if (action == MotionEvent.ACTION_DOWN) game.press(x, y);
                else if (action == MotionEvent.ACTION_UP) game.release(x, y);
                else if (action == MotionEvent.ACTION_CANCEL) game.charging = false;
                return true;
            }
            if (action != MotionEvent.ACTION_UP) return true;
            if (ctrls[2].contains(aimX, aimY)) game.autoTrade = !game.autoTrade;
            else if (ctrls[3].contains(aimX, aimY)) game.tradeCash(game.cash);
            else if (treeOpen) treeTouch();
            else if (ctrls[0].contains(aimX, aimY)) game.startWave();
            else if (ctrls[1].contains(aimX, aimY)) treeOpen = true;
            save();
            return true;
        }

        private void treeTouch() {
            if (ctrls[0].contains(aimX, aimY)) game.buy(selected);
            else if (ctrls[1].contains(aimX, aimY)) treeOpen = false;
            for (int i = 0; i < tabs.length; i++) {
                if (tabs[i].contains(aimX, aimY)) {
                    tree = i;
                    for (int n = Game.NODES - 1; n >= 0; n--) {
                        if (Game.NODE[n].tree == i) selected = n;
                    }
                }
            }
            for (int i = 0; i < Game.NODES; i++) {
                if (Game.NODE[i].tree == tree && nodes[i].contains(aimX, aimY)) selected = i;
            }
        }

        private String rateText() {
            double rate = Game.cashPerGem(game.playerLevel);
            return rate >= 1 ? "$" + fmt(rate) + "=*" : "$1=" + fmt(1 / rate) + "*";
        }

        private int wx(double x) { return field.centerX() + (int) (x * field.width() / 2); }
        private int wy(double y) { return field.centerY() + (int) (y * field.width() / 2); }
        private double toWorldX(int x) { return (x - field.centerX()) * 2.0 / field.width(); }
        private double toWorldY(int y) { return (y - field.centerY()) * 2.0 / field.width(); }

        static String fmt(double v) {
            if (v < 10 && v != Math.floor(v)) return String.format(Locale.US, "%.2f", v);
            if (v < 1000) return String.valueOf(Math.round(v));
            String suffix = "KMBT";
            int i = -1;
            while (v >= 1000 && i < suffix.length() - 1) {
                v /= 1000;
                i++;
            }
            if (v >= 1000) return String.format(Locale.US, "%.1E", v * 1e15).replace("+", "");
            return String.format(Locale.US, "%.1f", v) + suffix.charAt(i);
        }

        void save() {
            SharedPreferences.Editor ed = prefs.edit();
            putD(ed, "cash", game.cash);
            putD(ed, "gems", game.gems);
            putD(ed, "xp", game.xp);
            putD(ed, "xpNext", game.xpNext);
            putD(ed, "waveHp", game.waveHp);
            putD(ed, "checkpointHp", game.checkpointHp);
            ed.putInt("playerLevel", game.playerLevel);
            ed.putInt("wave", game.wave);
            ed.putInt("ships", game.ships);
            ed.putInt("checkpointWave", game.checkpointWave);
            ed.putInt("checkpointShips", game.checkpointShips);
            ed.putBoolean("autoTrade", game.autoTrade);
            for (int i = 0; i < Game.NODES; i++) {
                ed.putInt("node" + i, game.level[i]);
                putD(ed, "gemCost" + i, game.gemCost[i]);
            }
            ed.apply();
        }

        private void load() {
            game.cash = getD("cash", game.cash);
            game.gems = getD("gems", game.gems);
            game.xp = getD("xp", game.xp);
            game.xpNext = getD("xpNext", game.xpNext);
            game.waveHp = getD("waveHp", game.waveHp);
            game.checkpointHp = getD("checkpointHp", game.checkpointHp);
            game.playerLevel = prefs.getInt("playerLevel", game.playerLevel);
            game.wave = prefs.getInt("wave", game.wave);
            game.ships = prefs.getInt("ships", game.ships);
            game.checkpointWave = prefs.getInt("checkpointWave", game.checkpointWave);
            game.checkpointShips = prefs.getInt("checkpointShips", game.checkpointShips);
            game.autoTrade = prefs.getBoolean("autoTrade", game.autoTrade);
            for (int i = 0; i < Game.NODES; i++) {
                game.level[i] = prefs.getInt("node" + i, 0);
                game.gemCost[i] = getD("gemCost" + i, game.gemCost[i]);
            }
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
