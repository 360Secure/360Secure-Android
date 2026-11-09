package in.secure360.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;

public final class CallNotifier {
    public static final String CHANNEL = "doorbell_calls_v1";
    public static final int ID = 36010;
    public static void createChannel(Context c) {
        NotificationManager m = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Doorbell calls", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Incoming calls when someone presses your front doorbell");
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ch.enableVibration(true);
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        if (sound != null) ch.setSound(sound, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build());
        m.createNotificationChannel(ch);
    }
    private static PendingIntent action(Context c,String what,String cameraId,String callId,int code) {
        Intent i = new Intent(c, CallActionReceiver.class).setAction(what).putExtra("cameraId",cameraId).putExtra("callId",callId);
        return PendingIntent.getBroadcast(c,code,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static void show(Context c,String cameraId,String callId) {
        NotificationManager m=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        Intent full = new Intent(c,IncomingCallActivity.class).putExtra("cameraId",cameraId).putExtra("callId",callId);
        full.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent fullIntent=PendingIntent.getActivity(c,2,full,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(c,CHANNEL)
            .setSmallIcon(R.drawable.ic_secure).setColor(Color.rgb(30,211,108))
            .setContentTitle("Smart Door Lock")
            .setContentText("Someone is at your door · Incoming call")
            .setCategory(Notification.CATEGORY_CALL)
            .setPriority(Notification.PRIORITY_HIGH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setTimeoutAfter(45000)
            .setContentIntent(fullIntent).setFullScreenIntent(fullIntent,true)
            .addAction(new Notification.Action.Builder(null,"Decline",action(c,"DECLINE",cameraId,callId,3)).build())
            .addAction(new Notification.Action.Builder(null,"Accept",action(c,"ACCEPT",cameraId,callId,4)).build())
            .build();
        try { m.notify(ID,n); } catch (SecurityException ignored) { /* permission may be denied */ }
    }
    public static void cancel(Context c) {
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(ID);
    }
    private CallNotifier(){}
}
