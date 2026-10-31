package app.quiet.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Fire OS non lascia cambiare la home predefinita (e il tasto Home non passa dai filtri di accessibilità):
 * appena compare la home di Amazon, riportiamo in primo piano la nostra.
 */
public class HomeGuard extends AccessibilityService {
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (getSharedPreferences("g", MODE_PRIVATE).getLong("guardUntil", 0) > System.currentTimeMillis()) return;   // interruttore dal menu del launcher
        CharSequence c = e.getClassName();
        if (c != null && c.toString().contains("HomeActivity"))
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    @Override public void onInterrupt() {}
}
