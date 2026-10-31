package app.quiet.launcher;

import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/** Intera UI su un'unica View con Canvas: nessun layout, nessuna WebView, bitmap caricate solo quando servono. */
public class LauncherView extends View {
    static final class App {
        String pkg, label, logo;
        Intent intent;
        ActivityInfo ai;
        volatile Bitmap banner;    // banner dichiarato dall'app, usato se non abbiamo un logo
        BitmapShader bs;
        boolean system;
        int glyph;                 // 0 nessuno, 1 ingranaggio, 2 download
        int bg, g;                 // colori del brand (solo app fissate)
        boolean dark, fracH;       // logo scuro su fondo chiaro; frazione riferita all'altezza
        float frac;
        boolean gridH;             // dimensione logo in griglia riferita all'altezza
        float gridPx;
    }

    // pkg, nome, logo, asse griglia (w|h), px griglia
    static final String[][] KNOWN = {
        {"com.netflix.ninja", "Netflix", "netflix", "h", "56"},
        {"com.amazon.firebat", "Prime Video", "primevideo", "w", "150"},
        {"com.disney.disneyplus", "Disney+", "disneyplus", "w", "150"},
        {"tv.pluto.android", "Pluto TV", "plutotv", "w", "150"},
        {"com.amazon.firetv.youtube", "YouTube", "youtube", "h", "52"},
        {"com.apple.atve.amazon.appletv", "Apple TV", "appletv", "w", "104", "#f2f2f4", "1"},
        {"com.dazn", "DAZN", "dazn", "h", "56", "#ffe600", "1"},
        {"com.discovery.discoplus", "Discovery+", "discoveryplus", "w", "190", "#2175d9", "0"},
        {"com.nowtv.it", "NOW", "nowtv", "w", "120", "#0aa0dc", "0"},
        {"it.rainet.androidtv", "RaiPlay", "raiplay", "w", "140", "#1f49b6", "0"},
        {"com.spotify.tv.android", "Spotify", "spotify", "h", "56", "#1aa34a", "0"},
        {"tv.twitch.android.viewer", "Twitch", "twitch", "h", "52", "#8a3ffc", "0"},
        {"com.esaba.downloader", "Downloader", null, "w", "0", "#e8730c", "0"},
        {"com.amazon.venezia", "Appstore", null, "w", "0", "#d9730d", "0"},
    };
    // pkg, bg, ground, dark, asse (h|w), frazione
    static final String[][] PINNED = {
        {"com.netflix.ninja", "#d10f1a", "#4a0f1a", "0", "h", ".50"},
        {"com.amazon.firebat", "#0a6fa6", "#0b3247", "0", "w", ".74"},
        {"com.disney.disneyplus", "#1238b0", "#0f1f66", "0", "w", ".62"},
        {"tv.pluto.android", "#e6cf00", "#453d08", "1", "w", ".62"},
        {"com.amazon.firetv.youtube", "#d41c1c", "#3a1208", "0", "h", ".44"},
    };
    static final int[] SSG = {0xFF2A0B10, 0xFF0B2A3B, 0xFF0E1A55, 0xFF352F06, 0xFF33100C};
    static final long IDLE_MS = 5 * 60 * 1000L;
    static final int INK = 0xFFF4F2EE;

    final MainActivity act;
    final PackageManager pm;
    final Handler h = new Handler();
    final ArrayList<App> all = new ArrayList<>(), main = new ArrayList<>();
    final ConcurrentHashMap<String, Bitmap> bmps = new ConcurrentHashMap<>();
    final Matrix mx = new Matrix();
    boolean dirty = true, grid, busy, ssOn, paused, setup;
    final ArrayList<String> sel = new ArrayList<>();
    final ArrayList<App> vis = new ArrayList<>();   // app mostrate in griglia (filtrate dalla ricerca)
    boolean onBar, kb;
    String query = "";
    int kr, kc;
    final SharedPreferences prefs;
    int idx, j;
    float pos, gx, gy, scr, fade = 1, ovl;
    float pF, pT, gxF, gxT, gyF, gyT, sF, sT;
    App dlg;
    String toast, busyName;

    final ValueAnimator anim = ValueAnimator.ofFloat(0, 1), fadeAnim = ValueAnimator.ofFloat(0, 1), ovlAnim = ValueAnimator.ofFloat(0, 1);
    final PathInterpolator ease = new PathInterpolator(.2f, .8f, .2f, 1f);

    final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), lp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final Paint pBadge, pHi, pClock, pName, pSmall, pHd, pCell, pGnm, pHintN, pHintB, pDh, pDp, pBt, pToast, pLaunch, pSsT, pSsD;
    final PorterDuffColorFilter white = new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
    final Paint.FontMetrics fm = new Paint.FontMetrics();
    final RectF r = new RectF(), tr = new RectF(), cell = new RectF(), btn = new RectF();
    static final String[][] KB = {
        {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M"},
        {"N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"},
        {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "Spazio", "Canc", "Fatto"}};
    static final float[][] DRIFT = {{0, 0}, {190, 110}, {0, 0}, {-190, -110}, {0, 0}};
    final Drawable icGear, icDl, icGrid, icSearch;
    final SimpleDateFormat dateFmt;
    final String[] hint, hintKb, hintSetup, greets;
    final String[][] kbKeys = new String[3][];
    final String sAll, sChoose, sChooseHint, sSearch, sNoApps, sDone, sOpening, sErrOpen, sErrUninstall, sUninstalled, sPickOne, sMaxFive, sSysTitle, sSysBody, sOk, sHide, sShow, sUninstall, sDlgBody, sShowHidden, sHideAgain, sHiddenToast, sUnhiddenToast, sGuardTitle, sGuardOn, sGuardOff, sTurnOff, sTurnOn, sGuardToastOn, sGuardToastOff;
    final App guardApp = new App();
    final java.util.HashSet<String> hidden = new java.util.HashSet<>();
    boolean showHidden, started;
    int dlgSel;
    final Date now = new Date();
    float[] tw = new float[8], th = new float[8], tx = new float[8];
    String clockStr = "";
    long clockMin = -1, ssT0, ssTickAt;
    int ssI;

    final Runnable minuteTick = new Runnable() { public void run() { invalidate(); h.postDelayed(this, 20000); } };
    final Runnable idleRun = new Runnable() { public void run() { startSs(); } };
    final Runnable ssFrame = new Runnable() { public void run() { if (ssOn) { invalidate(); h.postDelayed(this, 250); } } };
    final Runnable busyOff = new Runnable() { public void run() { busy = false; fadeOvl(0); } };
    final Runnable toastOff = new Runnable() { public void run() { toast = null; invalidate(); } };

    public LauncherView(MainActivity a) {
        super(a);
        act = a;
        android.content.res.Resources res = a.getResources();
        hint = res.getStringArray(R.array.hint); hintKb = res.getStringArray(R.array.hint_kb); hintSetup = res.getStringArray(R.array.hint_setup);
        greets = new String[]{a.getString(R.string.greet_night), a.getString(R.string.greet_morning), a.getString(R.string.greet_afternoon), a.getString(R.string.greet_evening)};
        String[] sp = res.getStringArray(R.array.kb_special);
        for (int i = 0; i < 3; i++) kbKeys[i] = KB[i].clone();
        kbKeys[2][10] = sp[0]; kbKeys[2][11] = sp[1]; kbKeys[2][12] = sp[2];
        sAll = a.getString(R.string.all_apps); sChoose = a.getString(R.string.choose_apps); sChooseHint = a.getString(R.string.choose_hint);
        sSearch = a.getString(R.string.search_hint); sNoApps = a.getString(R.string.no_apps); sDone = a.getString(R.string.done);
        sOpening = a.getString(R.string.opening); sErrOpen = a.getString(R.string.err_open); sErrUninstall = a.getString(R.string.err_uninstall);
        sUninstalled = a.getString(R.string.uninstalled); sPickOne = a.getString(R.string.pick_one); sMaxFive = a.getString(R.string.max_five);
        sSysTitle = a.getString(R.string.sys_title); sSysBody = a.getString(R.string.sys_body); sOk = a.getString(R.string.ok);
        sHide = a.getString(R.string.hide); sShow = a.getString(R.string.show); sUninstall = a.getString(R.string.uninstall); sDlgBody = a.getString(R.string.dlg_body);
        sShowHidden = a.getString(R.string.show_hidden); sHideAgain = a.getString(R.string.hide_again); sHiddenToast = a.getString(R.string.hidden_toast); sUnhiddenToast = a.getString(R.string.unhidden_toast);
        sGuardTitle = a.getString(R.string.guard_title); sGuardOn = a.getString(R.string.guard_on); sGuardOff = a.getString(R.string.guard_off);
        sTurnOff = a.getString(R.string.turn_off); sTurnOn = a.getString(R.string.turn_on); sGuardToastOn = a.getString(R.string.guard_toast_on); sGuardToastOff = a.getString(R.string.guard_toast_off);
        guardApp.pkg = "guard"; guardApp.label = "";
        dateFmt = new SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), Locale.getDefault());
        pm = a.getPackageManager();
        setBackgroundColor(Color.BLACK);
        Typeface tf;
        try { tf = Typeface.createFromAsset(a.getAssets(), "fonts/figtree.ttf"); } catch (Exception e) { tf = Typeface.SANS_SERIF; }
        pHi = tp(tf, 36, 400, 0xA8F4F2EE); pClock = tp(tf, 60, 300, INK); pClock.setLetterSpacing(.02f); pClock.setFontFeatureSettings("tnum");
        pName = tp(tf, 44, 500, INK); pName.setLetterSpacing(.01f);
        pSmall = tp(tf, 28, 400, 0xA8F4F2EE);
        pHd = tp(tf, 56, 400, INK); pCell = tp(tf, 32, 600, INK); pGnm = tp(tf, 48, 500, INK);
        pHintN = tp(tf, 32, 400, 0xB8F4F2EE); pHintB = tp(tf, 32, 600, INK);
        pDh = tp(tf, 52, 500, INK); pDp = tp(tf, 32, 400, 0xB8F4F2EE); pBt = tp(tf, 34, 600, INK);
        pToast = tp(tf, 34, 400, INK); pLaunch = tp(tf, 56, 400, INK);
        pSsT = tp(tf, 300, 300, 0xE6F4F2EE); pSsT.setLetterSpacing(.02f); pSsT.setFontFeatureSettings("tnum");
        pSsD = tp(tf, 44, 400, 0x9EF4F2EE);
        pBadge = tp(tf, 28, 600, 0xFF0B0B0D);
        prefs = a.getSharedPreferences("g", Context.MODE_PRIVATE);
        for (String p : prefs.getString("hidden", "").split(",")) if (!p.isEmpty()) hidden.add(p);
        stroke.setStyle(Paint.Style.STROKE);
        icGear = a.getResources().getDrawable(R.drawable.ic_gear, null);
        icDl = a.getResources().getDrawable(R.drawable.ic_dl, null);
        icGrid = a.getResources().getDrawable(R.drawable.ic_grid, null);
        icSearch = a.getResources().getDrawable(R.drawable.ic_search, null);
        anim.setDuration(240); fadeAnim.setDuration(180); ovlAnim.setDuration(200);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator va) {
                float e = ease.getInterpolation((Float) va.getAnimatedValue());
                pos = pF + (pT - pF) * e; gx = gxF + (gxT - gxF) * e; gy = gyF + (gyT - gyF) * e; scr = sF + (sT - sF) * e;
                invalidate();
            }
        });
        fadeAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator va) { fade = (Float) va.getAnimatedValue(); invalidate(); }
        });
    }

    static Paint tp(Typeface tf, int px, int w, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(tf); p.setTextSize(px); p.setColor(color);
        p.setFontVariationSettings("'wght' " + w);
        return p;
    }

    // ---------- dati ----------
    void markDirty() { dirty = true; }

    void scan() {
        dirty = false;
        LinkedHashMap<String, App> m = new LinkedHashMap<>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                String pkg = ri.activityInfo.packageName;
                if (m.containsKey(pkg) || pkg.equals(act.getPackageName()) || pkg.startsWith("com.android.")) continue;
                if (pkg.startsWith("com.amazon.") && !pkg.equals("com.amazon.firebat") && !pkg.equals("com.amazon.venezia") && !pkg.equals("com.amazon.firetv.youtube")) continue;
                App a = new App();
                a.pkg = pkg;
                a.ai = ri.activityInfo;
                a.label = ri.loadLabel(pm).toString();
                a.system = (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                a.intent = new Intent(Intent.ACTION_MAIN).addCategory(cat).setComponent(new ComponentName(pkg, ri.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                for (String[] k : KNOWN) if (k[0].equals(pkg)) {
                    a.label = k[1]; a.logo = k[2]; a.gridH = k[3].equals("h"); a.gridPx = Float.parseFloat(k[4]);
                    if (k.length > 5) { a.bg = Color.parseColor(k[5]); a.dark = k[6].equals("1"); }
                    if (pkg.equals("com.esaba.downloader")) a.glyph = 2;
                }
                for (String[] p : PINNED) if (p[0].equals(pkg)) {
                    a.bg = Color.parseColor(p[1]); a.g = Color.parseColor(p[2]); a.dark = p[3].equals("1"); a.fracH = p[4].equals("h"); a.frac = Float.parseFloat(p[5]);
                }
                m.put(pkg, a);
            }
        }
        App s = new App();
        s.pkg = "settings"; s.label = act.getString(R.string.settings); s.glyph = 1; s.system = true; s.bg = 0xFF4A5568;
        s.intent = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        m.put(s.pkg, s);
        all.clear(); all.addAll(m.values());
        Collections.sort(all, new Comparator<App>() { public int compare(App x, App y) { return x.label.compareToIgnoreCase(y.label); } });
        main.clear();
        String saved = prefs.getString("pinned", "");
        for (String pkg : saved.split(",")) {
            App a = m.get(pkg);
            if (a == null) continue;
            main.add(a);
        }
        if (!started) { started = true; idx = lastIdx(); }
        if (idx > main.size()) idx = main.size();
        rebuildVis();
        if (j >= vis.size()) j = Math.max(0, vis.size() - 1);
        pos = pF = pT = idx;
        preload();
        setGrid(j);
        gx = gxF = gxT; gy = gyF = gyT; scr = sF = sT;
    }

    /** Decodifica loghi e banner fuori dal thread UI: la prima apertura di griglia e home non deve scattare. */
    void preload() {
        final ArrayList<App> l = new ArrayList<>(all);
        new Thread(new Runnable() {
            public void run() {
                for (App a : l) {
                    if (a.bg == 0 && a.glyph == 0 && a.ai != null) {
                        try { a.bg = iconColor(a.ai.loadIcon(pm)); } catch (Exception e) { /* resta il colore neutro */ }
                    }
                    if (a.logo != null) bm(a.logo);
                    else if (a.glyph == 0 && a.banner == null && a.ai != null) {
                        try {
                            Drawable d = a.ai.loadBanner(pm);
                            if (d == null) continue;
                            Bitmap b = Bitmap.createBitmap(288, 150, Bitmap.Config.ARGB_8888);
                            d.setBounds(0, 0, 288, 150);
                            d.draw(new Canvas(b));
                            a.bs = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                            a.banner = b;
                        } catch (Exception e) { /* resta il nome in testo */ }
                    }
                }
                postInvalidate();
            }
        }).start();
    }

    Bitmap bm(String name) {
        if (name == null) return null;
        Bitmap b = bmps.get(name);
        if (b == null) {
            try (InputStream in = act.getAssets().open("logos/" + name + ".png")) {
                b = BitmapFactory.decodeStream(in);
                b.setHasMipMap(true);
                bmps.put(name, b);
            } catch (Exception e) { return null; }
        }
        return b;
    }

    // ---------- ciclo di vita ----------
    void onResumed() {
        paused = false;
        clearBusy();
        if (dirty || all.isEmpty()) scan();
        if (!prefs.contains("pinned") && !setup) startSetup(true);
        stopSs(); resetIdle();
        h.removeCallbacks(minuteTick); h.postDelayed(minuteTick, 20000);
        invalidate();
    }

    void onPaused() {
        paused = true;
        h.removeCallbacks(minuteTick); h.removeCallbacks(idleRun); h.removeCallbacks(busyOff);
        clearBusy(); ssOn = false;
    }

    void clearBusy() { ovlAnim.cancel(); h.removeCallbacks(busyOff); busy = false; ovl = 0; }

    void goHome() {
        dlg = null; stopSs(); resetIdle();
        if (grid) swapScreen(false);
        idx = lastIdx(); moveHome();
    }

    /** Posizione in home dell'ultima app lanciata (0 se non c'è). */
    int lastIdx() {
        String l = prefs.getString("last", "");
        for (int i = 0; i < main.size(); i++) if (main.get(i).pkg.equals(l)) return i;
        return 0;
    }

    void resetIdle() { h.removeCallbacks(idleRun); if (!paused) h.postDelayed(idleRun, IDLE_MS); }

    void startSs() {
        if (ssOn || paused) return;
        ssOn = true; ssT0 = System.currentTimeMillis(); ssI = 0; ssTickAt = ssT0;
        h.post(ssFrame);
    }

    void stopSs() { ssOn = false; h.removeCallbacks(ssFrame); invalidate(); }

    // ---------- movimento ----------
    void startAnim() { anim.cancel(); anim.start(); }

    void moveHome() { pF = pos; pT = idx; gxF = gxT = gx; gyF = gyT = gy; sF = sT = scr; startAnim(); }

    void setGrid(int k) {
        j = k;
        gxT = k % 5; gyT = k / 5;
        float row = gyT;
        sT = Math.max(0, Math.min(row, Math.max(sT, row - 2)));
        if (row < sT) sT = row;
    }

    void moveGrid(int k) {
        gxF = gx; gyF = gy; sF = scr; pF = pT = pos;
        setGrid(k);
        startAnim();
    }

    void swapScreen(boolean toGrid) {
        grid = toGrid; fade = 0;
        if (!toGrid) showHidden = false;
        fadeAnim.cancel(); fadeAnim.start();
    }

    void fadeOvl(final float to) {
        ovlAnim.cancel();
        ovlAnim.removeAllUpdateListeners();
        final float from = ovl;
        ovlAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator va) { ovl = from + (to - from) * (Float) va.getAnimatedValue(); invalidate(); }
        });
        ovlAnim.start();
    }

    void showToast(String s) { toast = s; h.removeCallbacks(toastOff); h.postDelayed(toastOff, 2200); invalidate(); }

    // ---------- azioni ----------
    App current() { return grid ? (onBar || kb || j >= vis.size() ? null : vis.get(j)) : (idx < main.size() ? main.get(idx) : null); }

    int cnt() { return vis.size() + (setup || !hidden.isEmpty() ? 1 : 0); }

    String extraText() { return setup ? sDone : showHidden ? sHideAgain : String.format(sShowHidden, hidden.size()); }

    void rebuildVis() {
        vis.clear();
        String q = query.toLowerCase(Locale.getDefault());
        for (App a : all) if ((setup || showHidden || !hidden.contains(a.pkg)) && (setup || q.isEmpty()) || (!setup && !q.isEmpty() && (showHidden || !hidden.contains(a.pkg)) && a.label.toLowerCase(Locale.getDefault()).contains(q))) vis.add(a);
    }

    void applyFilter() {
        rebuildVis();
        j = 0; gx = gxF = gxT = gy = gyF = gyT = scr = sF = sT = 0;
        invalidate();
    }

    void type(char ch) { query += Character.toLowerCase(ch); applyFilter(); }

    void backspace() { if (!query.isEmpty()) { query = query.substring(0, query.length() - 1); applyFilter(); } }

    void kbNav(int k, KeyEvent e) {
        boolean press = e.getRepeatCount() == 0;
        if (k == KeyEvent.KEYCODE_DPAD_LEFT) kc = Math.max(0, kc - 1);
        else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) kc = Math.min(12, kc + 1);
        else if (k == KeyEvent.KEYCODE_DPAD_UP) kr = Math.max(0, kr - 1);
        else if (k == KeyEvent.KEYCODE_DPAD_DOWN) kr = Math.min(2, kr + 1);
        else if (k == KeyEvent.KEYCODE_BACK) { if (press) kb = false; }
        else if ((k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) && press) {
            String key = KB[kr][kc];
            if (kr == 2 && kc == 12) kb = false;
            else if (kr == 2 && kc == 11) backspace();
            else if (kr == 2 && kc == 10) { if (!query.isEmpty() && !query.endsWith(" ")) { query += " "; applyFilter(); } }
            else type(key.charAt(0));
        }
        invalidate();
    }

    void startSetup(boolean first) {
        sel.clear();
        if (first) { for (String[] p : PINNED) for (App a : all) if (a.pkg.equals(p[0])) sel.add(a.pkg); }
        else for (App a : main) sel.add(a.pkg);
        setup = true; j = 0;
        query = ""; onBar = kb = false; rebuildVis();
        gx = gxF = gxT = gy = gyF = gyT = scr = sF = sT = 0;
        if (!grid) swapScreen(true);
        invalidate();
    }

    void setupAct() {
        if (j == vis.size()) {
            if (sel.isEmpty()) { showToast(sPickOne); return; }
            StringBuilder b = new StringBuilder();
            for (String p : sel) b.append(b.length() > 0 ? "," : "").append(p);
            prefs.edit().putString("pinned", b.toString()).apply();
            setup = false; idx = 0;
            scan();
            swapScreen(false);
            return;
        }
        String pkg = vis.get(j).pkg;
        if (!sel.remove(pkg)) { if (sel.size() < 5) sel.add(pkg); else showToast(sMaxFive); }
        invalidate();
    }

    void activate() {
        if (setup) { setupAct(); return; }
        if (grid && onBar) { kb = true; kr = 0; kc = 0; invalidate(); return; }
        if (!grid) {
            if (idx == main.size()) { j = 0; gx = gxF = 0; gy = gyF = 0; scr = sF = sT = 0; gxT = 0; gyT = 0; query = ""; onBar = kb = false; rebuildVis(); swapScreen(true); return; }
        }
        if (grid && !onBar && !kb && j == vis.size()) { showHidden = !showHidden; applyFilter(); return; }
        App a = current();
        if (a == null) return;
        if (main.contains(a)) prefs.edit().putString("last", a.pkg).apply();
        busy = true; busyName = a.label; fadeOvl(1);
        try { act.startActivity(a.intent); h.postDelayed(busyOff, 1500); }
        catch (Exception e) { busy = false; fadeOvl(0); showToast(String.format(sErrOpen, a.label)); }
    }

    boolean guardOn() { return prefs.getLong("guardUntil", 0) <= System.currentTimeMillis(); }

    void askRemove() {
        App a = !grid && idx == main.size() ? guardApp : current();
        if (a == null) return;
        dlg = a; dlgSel = 0; invalidate();
    }

    void dlgExec() {
        App a = dlg; dlg = null;
        if (a == guardApp) {
            if (dlgSel == 0) { boolean on = !guardOn(); prefs.edit().putLong("guardUntil", on ? 0 : System.currentTimeMillis() + 600000).apply(); showToast(on ? sGuardToastOn : sGuardToastOff); }
            invalidate(); return;
        }
        if (dlgSel == 0) {
            boolean was = hidden.remove(a.pkg);
            if (!was) hidden.add(a.pkg);
            StringBuilder b = new StringBuilder();
            for (String p : hidden) b.append(b.length() > 0 ? "," : "").append(p);
            prefs.edit().putString("hidden", b.toString()).apply();
            if (hidden.isEmpty()) showHidden = false;
            rebuildVis();
            if (j >= cnt()) j = Math.max(0, cnt() - 1);
            setGrid(j); gx = gxF = gxT; gy = gyF = gyT; scr = sF = sT;
            showToast(String.format(was ? sUnhiddenToast : sHiddenToast, a.label));
        } else if (!a.system) {
            Intent i = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:" + a.pkg)).putExtra(Intent.EXTRA_RETURN_RESULT, true);
            uninstallLabel = a.label;
            try { act.startActivityForResult(i, 1); } catch (Exception e) { showToast(String.format(sErrUninstall, a.label)); }
        }
        invalidate();
    }

    String uninstallLabel;

    void onUninstallResult(int res) {
        String l = uninstallLabel;
        scan(); invalidate();
        if (res == -1 && l != null) showToast(String.format(sUninstalled, l));
    }

    boolean key(KeyEvent e) {
        int k = e.getKeyCode();
        if (grid && !setup && (kb || onBar) && e.getAction() == KeyEvent.ACTION_DOWN && !ssOn && !busy && dlg == null) {
            int u = e.getUnicodeChar();
            if (k == KeyEvent.KEYCODE_DEL) { backspace(); resetIdle(); return true; }
            if (u > 32 && Character.isLetterOrDigit(u)) { type((char) u); resetIdle(); return true; }
        }
        boolean mine = k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT || k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
            || k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
        if (!mine) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        resetIdle();
        if (ssOn) { stopSs(); return true; }
        if (busy) return true;
        if (dlg != null) {
            if (e.getRepeatCount() != 0) return true;
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) dlgSel = 0;
            else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) dlgSel = 1;
            else if (k == KeyEvent.KEYCODE_BACK) dlg = null;
            else if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) { dlgExec(); return true; }
            invalidate();
            return true;
        }
        if (grid && !setup && kb) { kbNav(k, e); return true; }
        if (k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { if (!setup && e.getRepeatCount() == 0) startSetup(false); return true; }
        if (k == KeyEvent.KEYCODE_MENU) {
            if (setup) return true; if (e.getRepeatCount() == 0) askRemove(); return true; }
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) { if (e.getRepeatCount() == 0) activate(); return true; }
        if (k == KeyEvent.KEYCODE_BACK) {
            if (grid && e.getRepeatCount() == 0) {
                if (!setup) {
                    if (onBar) { onBar = false; invalidate(); }
                    else if (!query.isEmpty()) { query = ""; applyFilter(); }
                    else swapScreen(false);
                }
                else if (prefs.contains("pinned")) { setup = false; swapScreen(false); }   // al primo avvio non si esce senza scegliere
            }
            return true;
        }
        if (!grid) {
            int n = idx;
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) n++;
            else if (k == KeyEvent.KEYCODE_DPAD_LEFT) n--;
            else if (k == KeyEvent.KEYCODE_DPAD_DOWN && idx == main.size()) { activate(); return true; }
            if (e.getRepeatCount() == 0) { if (n < 0) n = main.size(); else if (n > main.size()) n = 0; }
            if (n != idx && n >= 0 && n <= main.size()) { idx = n; moveHome(); }
        } else {
            if (!setup) {
                if (onBar) { if (k == KeyEvent.KEYCODE_DPAD_DOWN && !vis.isEmpty()) { onBar = false; invalidate(); } return true; }
                if (k == KeyEvent.KEYCODE_DPAD_UP && j < 5) { onBar = true; invalidate(); return true; }
            }
            int n = j, last = cnt() - 1;
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) n++;
            else if (k == KeyEvent.KEYCODE_DPAD_LEFT) n--;
            else if (k == KeyEvent.KEYCODE_DPAD_DOWN) { n += 5; if (n > last && j / 5 < last / 5) n = last; }
            else if (k == KeyEvent.KEYCODE_DPAD_UP) n -= 5;
            if (e.getRepeatCount() == 0 && last > 0) { if (n < 0 && k == KeyEvent.KEYCODE_DPAD_LEFT) n = last; else if (n > last && k == KeyEvent.KEYCODE_DPAD_RIGHT) n = 0; }
            if (n != j && n >= 0 && n <= last) moveGrid(n);
        }
        return true;
    }

    // ---------- disegno ----------
    static final int NEUTRAL = 0xFF3A3F5C;

    static float lum(int c) { return (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f; }

    /** Fondo della home per un'app fissata: scurisce il suo colore se non ha un "ground" dedicato. */
    static int ground(App a) { return a.g != 0 ? a.g : mix(0xFF000000, a.bg != 0 ? a.bg : NEUTRAL, .28f); }

    /** Colore dominante (pesato sulla saturazione) dell'icona, tenuto in una fascia leggibile con testo bianco. */
    static int iconColor(Drawable d) {
        Bitmap b = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, 24, 24); d.draw(new Canvas(b));
        float r = 0, g = 0, bl = 0, w = 0; float[] hsv = new float[3];
        for (int y = 0; y < 24; y++) for (int x = 0; x < 24; x++) {
            int p = b.getPixel(x, y);
            if (Color.alpha(p) < 200) continue;
            Color.colorToHSV(p, hsv);
            float wt = hsv[1] * hsv[2] + 0.02f;
            r += Color.red(p) * wt; g += Color.green(p) * wt; bl += Color.blue(p) * wt; w += wt;
        }
        b.recycle();
        if (w == 0) return 0;
        Color.colorToHSV(Color.rgb((int) (r / w), (int) (g / w), (int) (bl / w)), hsv);
        hsv[2] = Math.max(.45f, Math.min(.8f, hsv[2]));
        return Color.HSVToColor(hsv);
    }

    static float cl(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
    static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    static int mix(int a, int b, float t) {
        return Color.argb((int) lerp(Color.alpha(a), Color.alpha(b), t), (int) lerp(Color.red(a), Color.red(b), t), (int) lerp(Color.green(a), Color.green(b), t), (int) lerp(Color.blue(a), Color.blue(b), t));
    }

    void txt(Canvas c, String s, float x, float top, Paint p, Paint.Align al) {
        p.setTextAlign(al); p.getFontMetrics(fm);
        c.drawText(s, x, top - fm.ascent, p);
    }

    void txtMid(Canvas c, String s, float x, float cy, Paint p) {
        p.setTextAlign(Paint.Align.CENTER); p.getFontMetrics(fm);
        c.drawText(s, x, cy - (fm.ascent + fm.descent) / 2, p);
    }

    void clock() {
        long m = System.currentTimeMillis() / 60000;
        if (m == clockMin) return;
        clockMin = m;
        Calendar c = Calendar.getInstance();
        int hh = c.get(Calendar.HOUR_OF_DAY);
        boolean h24 = android.text.format.DateFormat.is24HourFormat(act);
        clockStr = String.format(Locale.getDefault(), h24 ? "%02d:%02d" : "%d:%02d", h24 ? hh : (hh % 12 == 0 ? 12 : hh % 12), c.get(Calendar.MINUTE));
    }

    String greet() {
        int hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return greets[hr < 6 ? 0 : hr < 13 ? 1 : hr < 18 ? 2 : 3];
    }

    @Override protected void onDraw(Canvas c) {
        int W = getWidth(), H = getHeight();
        float s = Math.min(W / 1920f, H / 1080f);
        c.save();
        c.translate((W - 1920 * s) / 2, (H - 1080 * s) / 2);
        c.scale(s, s);
        c.clipRect(0, 0, 1920, 1080);
        clock();
        if (ssOn) drawSs(c);
        else {
            int sc = fade < 1 ? c.saveLayerAlpha(0, 0, 1920, 1080, (int) (fade * 255)) : -1;
            if (grid) drawGrid(c); else drawHome(c);
            if (sc >= 0) c.restoreToCount(sc);
            if (ovl > 0.01f) {
                fill.setShader(null); fill.setColor(Color.argb((int) (153 * ovl), 0, 0, 0)); c.drawRect(0, 0, 1920, 1080, fill);
                pLaunch.setAlpha((int) (255 * ovl)); txtMid(c, String.format(sOpening, busyName), 960, 540, pLaunch);
            }
            if (dlg != null) drawDlg(c);
            if (toast != null) {
                pToast.setTextAlign(Paint.Align.CENTER);
                float w = pToast.measureText(toast) + 80;
                r.set(960 - w / 2, 1080 - 120 - 80, 960 + w / 2, 1080 - 120);
                fill.setColor(0xFF26262C); c.drawRoundRect(r, 40, 40, fill);
                txtMid(c, toast, 960, r.centerY(), pToast);
            }
        }
        c.restore();
    }

    void sz(int k, int G) {
        float d = Math.abs(k - pos), w, hh;
        if (k == G) { float f = cl(1 - d); w = lerp(112, 280, f); hh = lerp(112, 175, f); }
        else if (d <= 1) { w = lerp(560, 208, d); hh = lerp(350, 130, d); }
        else { float t = cl(d - 1); w = lerp(208, 176, t); hh = lerp(130, 110, t); }
        tw[k] = w; th[k] = hh;
    }

    void shadowRect(Canvas c, RectF rr, float rad, int color, float blur, float dy, float alpha) {
        fill.setColor(color);
        fill.setShadowLayer(blur, 0, dy, Color.argb((int) (255 * alpha), 0, 0, 0));
        c.drawRoundRect(rr, rad, rad, fill);
        fill.clearShadowLayer();
    }

    void ring(Canvas c, RectF rr, float rad, float w, float alpha) {
        stroke.setStrokeWidth(w); stroke.setColor(Color.argb((int) (255 * alpha), 255, 255, 255));
        r.set(rr.left - w / 2, rr.top - w / 2, rr.right + w / 2, rr.bottom + w / 2);
        c.drawRoundRect(r, rad + w / 2, rad + w / 2, stroke);
    }

    void glyph(Canvas c, Drawable d, float cx, float cy, float size, float alpha) {
        d.setBounds((int) (cx - size / 2), (int) (cy - size / 2), (int) (cx + size / 2), (int) (cy + size / 2));
        d.setAlpha((int) (255 * alpha)); d.draw(c);
    }

    void logo(Canvas c, String name, float cx, float cy, float dw, float dh, float a, boolean darkOnFocus, float fa) {
        Bitmap b = bm(name);
        if (b == null) return;
        r.set(cx - dw / 2, cy - dh / 2, cx + dw / 2, cy + dh / 2);
        float tint = darkOnFocus ? 1 - fa : 1;
        if (tint > 0.01f) { lp.setColorFilter(white); lp.setAlpha((int) (255 * a * tint)); c.drawBitmap(b, null, r, lp); }
        if (darkOnFocus && fa > 0.01f) { lp.setColorFilter(null); lp.setAlpha((int) (255 * fa)); c.drawBitmap(b, null, r, lp); }
    }

    void drawHome(Canvas c) {
        int G = main.size(), n = G + 1;
        if (tw.length < n) { tw = new float[n]; th = new float[n]; tx = new float[n]; }
        float x = 0, sepX = -1;
        for (int k = 0; k < n; k++) {
            sz(k, G); tx[k] = x; x += tw[k] + 24;
            if (k == G - 1) { sepX = x; x += 18 + 24; }
        }
        float total = x - 24;
        int lo = Math.max(0, Math.min(n - 1, (int) Math.floor(pos))), hi = Math.min(n - 1, lo + 1);
        float fr = pos - lo;
        float cx = lerp(tx[lo] + tw[lo] / 2, tx[hi] + tw[hi] / 2, fr);
        float off = Math.min(1808 - total, Math.max(112, 960 - cx));
        int ground = lo < G ? ground(main.get(lo)) : 0xFF121216, ground2 = hi < G ? ground(main.get(hi)) : 0xFF121216;
        c.drawColor(mix(ground, ground2, fr));

        txt(c, greet(), 112, 78, pHi, Paint.Align.LEFT);
        txt(c, clockStr, 1808, 58, pClock, Paint.Align.RIGHT);

        if (sepX >= 0) { fill.setColor(0x38FFFFFF); r.set(off + sepX + 8, 500 - 36, off + sepX + 10, 500 + 36); c.drawRect(r, fill); }
        for (int k = 0; k < n; k++) {
            float fa = cl(1 - Math.abs(k - pos));
            float l = off + tx[k], t = 500 - th[k] / 2 - 12 * fa;
            tr.set(l, t, l + tw[k], t + th[k]);
            if (k == G) {
                int bg = mix(0xFF1B1B20, 0xFF2C2C33, fa);
                if (fa > 0.01f) { shadowRect(c, tr, 20, bg, 48, 36, .45f * fa); ring(c, tr, 20, 3, fa); }
                fill.setColor(bg); c.drawRoundRect(tr, 20, 20, fill);
                glyph(c, icGrid, tr.centerX(), tr.centerY(), Math.min(tw[k], th[k]) * .44f, 1);
            } else {
                App a = main.get(k);
                int bg = mix(0xFF1B1B20, a.bg != 0 ? a.bg : NEUTRAL, fa);
                if (fa > 0.01f) { shadowRect(c, tr, 20, bg, 48, 36, .45f * fa); ring(c, tr, 20, 3, fa); }
                fill.setColor(bg); c.drawRoundRect(tr, 20, 20, fill);
                Bitmap b = bm(a.logo);
                int sv = c.save(); c.clipRect(tr);
                if (b != null) {
                    float dw, dh;
                    if (a.frac > 0) {
                        dh = a.fracH ? a.frac * th[k] : a.frac * tw[k] * b.getHeight() / b.getWidth();
                        dw = a.fracH ? dh * b.getWidth() / b.getHeight() : a.frac * tw[k];
                    } else { float sc = tw[k] / 560f; dw = b.getWidth() * sc; dh = b.getHeight() * sc; }
                    logo(c, a.logo, tr.centerX(), tr.centerY(), dw, dh, lerp(.78f, 1, fa), a.dark, fa);
                } else {
                    pCell.setTextSize(22 + (tw[k] - 176) / 384f * 26);
                    while (pCell.measureText(a.label) > tw[k] - 24 && pCell.getTextSize() > 14) pCell.setTextSize(pCell.getTextSize() - 2);
                    pCell.setAlpha((int) (255 * lerp(.78f, 1, fa)));
                    txtMid(c, a.label, tr.centerX(), tr.centerY(), pCell);
                    pCell.setAlpha(255);
                }
                c.restoreToCount(sv);
            }
        }

        float hf = lerp(th[lo], th[hi], fr), dist = Math.min(fr, 1 - fr);
        int near = Math.round(pos);
        float na = cl(1 - 2.2f * Math.abs(pos - near));
        if (na > 0.01f) {
            pName.setAlpha((int) (255 * na));
            txt(c, near < G ? main.get(near).label : sAll, off + cx, 500 + hf / 2 - 12 * (1 - dist) + 40, pName, Paint.Align.CENTER);
            pName.setAlpha(255);
        }
        if (idx < G || pos < G) {
            float ga = 1 - cl(1 - Math.abs(G - pos));
            if (ga > 0.01f) {
                float gc = off + tx[G] + tw[G] / 2, gl = gc - 120;
                pSmall.setAlpha((int) (168 * ga));
                if (gl > 1568) txt(c, sAll, 1808, 578, pSmall, Paint.Align.RIGHT);
                else txt(c, sAll, gl + 120, 578, pSmall, Paint.Align.CENTER);
            }
        }
    }

    void drawGrid(Canvas c) {
        c.drawColor(0xFF0C0C0F);
        txt(c, setup ? sChoose : sAll, 170, 80, pHd, Paint.Align.LEFT);
        if (setup) txt(c, sChooseHint, 170, 152, pSmall, Paint.Align.LEFT);
        else drawSearch(c);
        int sv = c.save();
        c.clipRect(0, 190, 1920, kb ? 400 : 760);
        for (int k = 0; k < cnt(); k++) {
            App a = k < vis.size() ? vis.get(k) : null;
            int col = k % 5, row = k / 5;
            float y = 216 + (row - scr) * 182;
            if (y > 760 || y + 150 < 190) continue;
            float x = 176 + col * 320;
            float fa = onBar || kb ? 0 : cl(1 - (float) Math.hypot(col - gx, row - gy));
            int s2 = c.save();
            float sc = 1 + .04f * fa;
            c.scale(sc, sc, x + 144, y + 75);
            cell.set(x, y, x + 288, y + 150);
            int picked = a != null && sel.indexOf(a.pkg) >= 0 ? sel.indexOf(a.pkg) + 1 : 0;
            int bg = mix(setup && picked > 0 ? 0xFF26324A : 0xFF1C1C1F, !setup && a != null && a.bg != 0 ? a.bg : 0xFF32323A, fa);
            boolean lightBg = !setup && a != null && a.bg != 0 && fa > .5f && lum(a.bg) > .62f;
            if (fa > 0.01f) { shadowRect(c, cell, 16, bg, 37, 18, .5f * fa); ring(c, cell, 16, 4, fa); }
            fill.setColor(bg); c.drawRoundRect(cell, 16, 16, fill);
            float al = lerp(.9f, 1, fa) * (a != null && !setup && hidden.contains(a.pkg) ? .45f : 1);
            Bitmap b = a != null ? bm(a.logo) : null;
            if (a == null) {
                String t = setup ? act.getString(R.string.done_count, sel.size()) : extraText();
                pCell.setTextSize(32);
                while (pCell.measureText(t) > 256 && pCell.getTextSize() > 18) pCell.setTextSize(pCell.getTextSize() - 2);
                txtMid(c, t, cell.centerX(), cell.centerY(), pCell);
            }
            else if (a.glyph == 1) glyph(c, icGear, cell.centerX(), cell.centerY(), 60, 1);
            else if (a.glyph == 2) glyph(c, icDl, cell.centerX(), cell.centerY(), 60, 1);
            else if (b != null) {
                float asp = (float) b.getWidth() / b.getHeight();
                float dw = a.gridH ? a.gridPx * asp : a.gridPx, dh = a.gridH ? a.gridPx : a.gridPx / asp;
                logo(c, a.logo, cell.centerX(), cell.centerY(), dw, dh, al, a.dark, fa);
            } else if (a.banner != null) {
                mx.setTranslate(x, y); a.bs.setLocalMatrix(mx);
                fill.setShader(a.bs); fill.setAlpha((int) (255 * al));
                c.drawRoundRect(cell, 16, 16, fill);
                fill.setShader(null); fill.setAlpha(255);
            } else {
                pCell.setTextSize(32);
                while (pCell.measureText(a.label) > 256 && pCell.getTextSize() > 18) pCell.setTextSize(pCell.getTextSize() - 2);
                if (lightBg) pCell.setColor(0xFF111114);
                txtMid(c, a.label, cell.centerX(), cell.centerY(), pCell);
                pCell.setColor(INK);
            }
            if (setup && picked > 0) {
                fill.setColor(Color.WHITE); c.drawCircle(x + 256, y + 32, 22, fill);
                txtMid(c, String.valueOf(picked), x + 256, y + 32, pBadge);
            }
            c.restoreToCount(s2);
        }
        c.restoreToCount(sv);
        if (!setup && vis.isEmpty() && !query.isEmpty()) { pGnm.setAlpha(150); txt(c, sNoApps, 960, 360, pGnm, Paint.Align.CENTER); pGnm.setAlpha(255); }
        if (kb) drawKb(c);
        else if (!onBar) txt(c, j < vis.size() ? vis.get(j).label : extraText(), 960, 800, pGnm, Paint.Align.CENTER);
        String[] seg = setup ? hintSetup : kb ? hintKb : hint;
        float tot = 0;
        for (int i = 0; i < seg.length; i++) tot += (i % 2 == 0 ? pHintB : pHintN).measureText(seg[i]);
        float hx = 960 - tot / 2;
        for (int i = 0; i < seg.length; i++) {
            Paint p = i % 2 == 0 ? pHintB : pHintN;
            txt(c, seg[i], hx, 977, p, Paint.Align.LEFT);
            hx += p.measureText(seg[i]);
        }
    }

    void drawSearch(Canvas c) {
        boolean f = onBar || kb;
        r.set(1304, 62, 1744, 138);
        if (f) { shadowRect(c, r, 38, 0xFF32323A, 37, 14, .5f); ring(c, r, 38, 4, 1); }
        fill.setColor(f ? 0xFF32323A : 0xFF1C1C1F); c.drawRoundRect(r, 38, 38, fill);
        glyph(c, icSearch, r.left + 52, r.centerY(), 36, 1);
        String t = query.isEmpty() ? sSearch : (query.length() > 14 ? query.substring(query.length() - 14) : query) + (kb ? "|" : "");
        Paint p = query.isEmpty() ? pDp : pHintB;
        p.setTextAlign(Paint.Align.LEFT); p.getFontMetrics(fm);
        c.drawText(t, r.left + 92, r.centerY() - (fm.ascent + fm.descent) / 2, p);
    }

    void drawKb(Canvas c) {
        for (int row = 0; row < 3; row++) for (int col = 0; col < 13; col++) {
            r.set(264 + col * 108, 470 + row * 96, 264 + col * 108 + 96, 470 + row * 96 + 84);
            boolean f = row == kr && col == kc;
            if (f) ring(c, r, 14, 4, 1);
            fill.setColor(f ? 0xFF32323A : 0xFF1C1C1F); c.drawRoundRect(r, 14, 14, fill);
            String k = kbKeys[row][col];
            pCell.setTextSize(k.length() == 1 ? 36 : 24);
            txtMid(c, k, r.centerX(), r.centerY(), pCell);
        }
    }

    void drawDlg(Canvas c) {
        fill.setColor(0xB8000000); c.drawRect(0, 0, 1920, 1080, fill);
        float top = (1080 - 359) / 2f;
        r.set(480, top, 1440, top + 359);
        shadowRect(c, r, 24, 0xFF1B1B20, 80, 40, .6f);
        boolean g = dlg == guardApp;
        txt(c, g ? sGuardTitle : dlg.system ? String.format(sSysTitle, dlg.label) : dlg.label, 544, top + 56, pDh, Paint.Align.LEFT);
        txt(c, g ? (guardOn() ? sGuardOn : String.format(sGuardOff, (int) Math.max(1, (prefs.getLong("guardUntil", 0) - System.currentTimeMillis() + 59999) / 60000))) : dlg.system ? sSysBody : sDlgBody, 544, top + 56 + 60 + 16, pDp, Paint.Align.LEFT);
        String[] lb = g ? new String[]{guardOn() ? sTurnOff : sTurnOn, sOk} : new String[]{hidden.contains(dlg.pkg) ? sShow : sHide, dlg.system ? sOk : sUninstall};
        for (int i = 0; i < 2; i++) {
            RectF b = btn; b.set(544 + i * 432, top + 359 - 48 - 88, 944 + i * 432, top + 359 - 48);
            fill.setColor(0xFF32323A); c.drawRoundRect(b, 16, 16, fill);
            if (i == dlgSel) ring(c, b, 16, 4, 1);
            txtMid(c, lb[i], b.centerX(), b.centerY(), pBt);
        }
    }

    void drawSs(Canvas c) {
        long t = System.currentTimeMillis() - ssT0;
        int step = (int) (t / 20000);
        float ft = cl((t % 20000) / 8000f);
        c.drawColor(mix(SSG[Math.max(0, step - 1 + (step == 0 ? 1 : 0)) % SSG.length], SSG[step % SSG.length], step == 0 ? 1 : ft));
        float ph = (t % 240000) / 240000f, ox, oy;
        float seg = ph * 4; int q = (int) seg; float u = seg - q; u = u * u * (3 - 2 * u);
        float[][] key = DRIFT;
        ox = lerp(key[q][0], key[q + 1][0], u); oy = lerp(key[q][1], key[q + 1][1], u);
        now.setTime(System.currentTimeMillis());
        String d = dateFmt.format(now); d = Character.toUpperCase(d.charAt(0)) + d.substring(1);
        float top = 540 - 186f + oy;
        txt(c, clockStr, 960 + ox, top, pSsT, Paint.Align.CENTER);
        txt(c, d, 960 + ox, top + 300 + 20, pSsD, Paint.Align.CENTER);
    }
}
