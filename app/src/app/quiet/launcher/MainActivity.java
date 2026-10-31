package app.quiet.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

public class MainActivity extends Activity {
    private LauncherView v;
    private final BroadcastReceiver pkgs = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { v.markDirty(); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        v = new LauncherView(this);
        setContentView(v);
        IntentFilter f = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        registerReceiver(pkgs, f);
        if (getIntent().hasExtra("ss")) v.post(new Runnable() { public void run() { v.startSs(); } });
    }

    private void hideUi() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override protected void onResume() { super.onResume(); hideUi(); v.onResumed();  }
    @Override protected void onPause() { v.onPaused(); super.onPause(); }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); v.goHome(); }
    @Override protected void onDestroy() { unregisterReceiver(pkgs); super.onDestroy(); }
    @Override public boolean dispatchKeyEvent(KeyEvent e) { return v.key(e) || super.dispatchKeyEvent(e); }
    @Override protected void onActivityResult(int req, int res, Intent d) { v.onUninstallResult(res); }
}
