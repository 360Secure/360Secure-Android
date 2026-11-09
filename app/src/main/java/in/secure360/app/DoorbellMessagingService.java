package in.secure360.app;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

/** Requires server-sent HIGH-priority FCM data message to the owner's registered token. */
public class DoorbellMessagingService extends FirebaseMessagingService {
    @Override public void onNewToken(String token) {
        new Thread(() -> PushRegistrar.post(getApplicationContext(), token)).start();
    }
    @Override public void onMessageReceived(RemoteMessage remote) {
        Map<String,String> data = remote.getData();
        if (!"doorbell".equals(data.get("type"))) return;
        String cameraId = data.get("cameraId"), callId = data.get("callId");
        if (cameraId == null || !cameraId.matches("[A-Za-z0-9_-]{4,64}") || callId == null || !callId.matches("[A-Za-z0-9_-]{8,128}")) return;
        // Server must target FCM tokens belonging to the authenticated camera owner.
        if (SessionStore.access(this).isEmpty()) return;
        CallNotifier.show(this, cameraId, callId);
    }
}
