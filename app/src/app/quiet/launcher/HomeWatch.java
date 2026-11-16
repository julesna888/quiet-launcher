package app.quiet.launcher;

import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;

/**
 * Riserva per i firmware dove Amazon blocca i servizi di accessibilità di terze parti (Fire OS 6.x, es.
 * Fire TV Stick 4K AFTMM su Fire OS 6.7.1.1): lì l'impostazione viene cancellata subito e HomeGuard non parte.
 * Legge gli eventi d'uso (serve l'accesso ai dati di utilizzo, lo concede scripts/install.sh:
 * adb shell appops set app.quiet.launcher GET_USAGE_STATS allow) e, quando la home Amazon torna in primo
 * piano, riporta Quiet Launcher. Apre anche il launcher all'avvio.
 * Strade provate e chiuse su Fire OS 6.7: ACTION_CLOSE_SYSTEM_DIALOGS "homekey" richiede
 * com.amazon.permission.ENABLE_KEYGUARD_FLAGS (firma Amazon); amazon.intent.action.HOME_PRESSED arriva solo
 * a pacchetti Amazon; getRunningTasks() non mostra il task della home.
 * Rispetta la stessa pausa "guardUntil" di HomeGuard; se HomeGuard è attivo lascia fare a lui.
 */
public class HomeWatch extends Service {
    static final String TAG = "HomeWatch";
    static final String AMAZON_HOME = "com.amazon.tv.launcher/";
    static final long POLL_MS = 400;

    private final Handler h = new Handler();
    private UsageStatsManager usm;
    private PowerManager pm;
    private long cursor;

    private final Runnable poll = new Runnable() {
        public void run() {
            try { check(); } catch (Exception e) { Log.w(TAG, "poll", e); }
            h.postDelayed(this, POLL_MS);
        }
    };

    static boolean paused(Context c) {
        try { return c.getSharedPreferences("g", MODE_PRIVATE).getLong("guardUntil", 0) > System.currentTimeMillis(); }
        catch (Exception e) { return false; }   // memoria cifrata non ancora sbloccata subito dopo l'avvio
    }

    static boolean usageAccess(Context c) {
        AppOpsManager ops = (AppOpsManager) c.getSystemService(APP_OPS_SERVICE);
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    /** HomeGuard abilitato (Fire OS 7): fa già lui il lavoro. Su Fire OS 6 l'impostazione torna sempre null. */
    static boolean guardActive(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(c.getPackageName() + "/");
    }

    static void start(Context c) {
        if (!usageAccess(c) || guardActive(c)) return;
        Intent i = new Intent(c, HomeWatch.class);
        try { if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i); }
        catch (Exception e) { Log.w(TAG, "avvio non riuscito", e); }
    }

    /**
     * Dopo Home, Android trattiene per 5 s gli avvii di activity delle app ("app switch") e poi li esegue:
     * è il ritardo che si vede. Un PendingIntent via AlarmManager non aiuta: su API 25 gli allarmi
     * vengono spostati almeno 5 s avanti.
     */
    static void launch(Context c) {
        c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    void check() {
        long now = System.currentTimeMillis();
        if (!pm.isInteractive() || !usageAccess(this) || guardActive(this)) { cursor = now; return; }
        if (cursor == 0) cursor = now;   // ignora quello che è successo prima dell'avvio del servizio
        UsageEvents ev = usm.queryEvents(cursor, now + 1);
        UsageEvents.Event e = new UsageEvents.Event();
        String front = null;
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e);
            if (e.getTimeStamp() < cursor) continue;
            cursor = e.getTimeStamp() + 1;
            if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) front = e.getPackageName() + "/" + e.getClassName();
        }
        // la home Amazon è in primo piano (le sue Impostazioni e le altre schermate restano libere)
        if (front != null && front.startsWith(AMAZON_HOME) && front.contains("HomeActivity") && !paused(this)) {
            Log.i(TAG, "home Amazon in primo piano: riporto Quiet Launcher");
            launch(this);
        }
    }

    static Notification note(Context c) {
        if (Build.VERSION.SDK_INT >= 26) return Api26.note(c);
        @SuppressWarnings("deprecation")
        Notification n = new Notification.Builder(c)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(c.getString(R.string.app_name))
                .setPriority(Notification.PRIORITY_MIN)
                .setOngoing(true)
                .build();
        return n;
    }

    /** Da Android 8 una notifica in primo piano senza canale manda in crash il servizio. */
    static class Api26 {
        static Notification note(Context c) {
            NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel("watch", c.getString(R.string.app_name), NotificationManager.IMPORTANCE_MIN));
            return new Notification.Builder(c, "watch")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(c.getString(R.string.app_name))
                    .setOngoing(true)
                    .build();
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        usm = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        pm = (PowerManager) getSystemService(POWER_SERVICE);
        startForeground(1, note(this));   // tiene vivo il processo mentre app pesanti (Netflix, Prime) usano la RAM
        h.post(poll);
    }

    @Override public int onStartCommand(Intent i, int flags, int id) { return START_STICKY; }
    @Override public void onDestroy() { h.removeCallbacks(poll); super.onDestroy(); }
    @Override public IBinder onBind(Intent i) { return null; }

    /**
     * Riavvia il servizio dopo l'avvio e dopo un aggiornamento dell'app, e apre Quiet Launcher all'avvio.
     * Fire OS consegna BOOT_COMPLETED alle app una alla volta (anche 2 minuti), quindi il receiver prende
     * anche il precedente LOCKED_BOOT_COMPLETED e ha priorità alta. Apre il launcher una sola volta per
     * avvio: il secondo segnale non deve toglierti da un'app aperta nel frattempo.
     */
    public static class Boot extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) {
            start(c);
            String a = i.getAction();
            if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(a)) return;
            if (paused(c)) return;
            Context dp = Build.VERSION.SDK_INT >= 24 ? c.createDeviceProtectedStorageContext() : c;
            SharedPreferences p = dp.getSharedPreferences("boot", MODE_PRIVATE);
            int boot = Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
            if (boot != -1 && p.getInt("launched", -2) == boot) return;
            try { launch(c); p.edit().putInt("launched", boot).commit(); } catch (Exception e) { Log.w(TAG, "apertura all'avvio", e); }
        }
    }
}
