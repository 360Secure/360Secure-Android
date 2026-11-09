package in.secure360.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Local duplicate defense. Server must independently enforce notification limits. */
public final class VisitorAlertPolicy {
    private static final long CAMERA_GAP_MS = 90_000L;
    public static synchronized boolean allow(Context c, String eventId, String cameraId) {
        if (eventId == null || !eventId.matches("[A-Za-z0-9_-]{8,128}") ||
            cameraId == null || !cameraId.matches("[A-Za-z0-9_-]{4,64}")) return false;
        SharedPreferences p = c.getSharedPreferences("visitor_alerts_v1", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (p.getBoolean("event:" + eventId, false)) return false;
        long previous = p.getLong("camera:" + cameraId, 0);
        if (now - previous < CAMERA_GAP_MS && now >= previous) {
            p.edit().putBoolean("event:" + eventId, true).apply();
            return false;
        }
        // Event dedup is persisted and bounded by a daily namespace. Old entries cleared daily.
        long bucket = now / 86_400_000L;
        if (p.getLong("bucket", -1) != bucket) p.edit().clear().putLong("bucket", bucket).commit();
        p.edit().putBoolean("event:" + eventId, true)
            .putLong("camera:" + cameraId, now).commit();
        return true;
    }
    private VisitorAlertPolicy() {}
}
