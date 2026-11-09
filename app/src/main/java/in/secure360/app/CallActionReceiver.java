package in.secure360.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class CallActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i) {
        CallNotifier.cancel(c);
        if (!"ACCEPT".equals(i.getAction())) return;
        Intent launch = new Intent(c, MainActivity.class).setAction("in.secure360.app.ANSWER_DOORBELL")
                .putExtra("cameraId",i.getStringExtra("cameraId"))
                .putExtra("callId",i.getStringExtra("callId"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        c.startActivity(launch);
    }
}
