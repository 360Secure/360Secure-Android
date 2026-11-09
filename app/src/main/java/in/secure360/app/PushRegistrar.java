package in.secure360.app;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONObject;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class PushRegistrar {
    /** Never call on main thread. Sends token only to HTTPS backend after login. */
    public static void sync(Context context) {
        if (SessionStore.access(context).isEmpty() || FirebaseApp.getApps(context).isEmpty()) return;
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(t -> {
            new Thread(() -> post(context.getApplicationContext(),t)).start();
        });
    }
    public static void post(Context context, String token) {
        if (token == null || token.isEmpty()) return;
        String auth = SessionStore.access(context);
        if (auth.isEmpty()) return;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(SecureApp.API_BASE + "/api/devices/push-token");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST"); conn.setConnectTimeout(8000); conn.setReadTimeout(8000);
            conn.setDoOutput(true); conn.setRequestProperty("Content-Type","application/json");
            conn.setRequestProperty("Authorization", "Bearer " + auth);
            JSONObject body = new JSONObject(); body.put("token", token); body.put("platform","android");
            body.put("deviceId", Settings.Secure.getString(context.getContentResolver(),Settings.Secure.ANDROID_ID));
            byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = conn.getOutputStream()) { out.write(data); }
            conn.getResponseCode(); // failure is non-fatal; next login / token refresh will retry
        } catch (Exception ignored) { /* no secret-bearing logs */ }
        finally { if(conn != null) conn.disconnect(); }
    }
    private PushRegistrar(){}
}
