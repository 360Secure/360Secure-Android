package in.secure360.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Private rich image alert. Photos are fetched with bearer auth, never bundled in FCM/SMS.
 * Call only from the FirebaseMessagingService background handler.
 */
public final class VisitorNotifier {
    public static final String CHANNEL = "visitor_events_v1";
    private static final int MAX_PHOTO = 750_000;
    static void createChannel(Context c) {
        NotificationManager m = (NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Visitor activity", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Person detection alerts (rate-limited by 360Secure)");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        m.createNotificationChannel(channel);
    }
    private static String verifiedAccess(Context c) {
        // If access has expired, use the encrypted refresh token once; do not store plaintext.
        return SessionStore.access(c);
    }
    private static void refresh(Context c) {
        String refresh = SessionStore.read(c).optString("refreshToken", "");
        if (refresh.length() < 40) return;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(SecureApp.API_BASE + "/api/auth/refresh").openConnection();
            conn.setConnectTimeout(3500); conn.setReadTimeout(3500);
            conn.setRequestMethod("POST"); conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            JSONObject req = new JSONObject();req.put("refreshToken", refresh);
            try (OutputStream os=conn.getOutputStream()) {
                os.write(req.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (conn.getResponseCode() != 200) return;
            try (InputStream stream=conn.getInputStream()) {
                ByteArrayOutputStream body=new ByteArrayOutputStream();
                byte[] buf=new byte[2048];int n;
                while((n=stream.read(buf))!=-1){if(body.size()+n>8192)return;body.write(buf,0,n);}
                byte[] bytes=body.toByteArray();
                JSONObject json=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
                String access=json.optString("accessToken","");
                String next=json.optString("refreshToken","");
                if (!access.isEmpty() && !next.isEmpty())
                    SessionStore.save(c,access,next,SessionStore.read(c).optString("phone",""));
            }
        } catch (Exception ignored) { /* continue with text-only notification */ }
        finally { if (conn!=null) conn.disconnect(); }
    }
    private static Bitmap photo(Context c, String eventId) {
        for (int attempt=0;attempt<2;attempt++) {
            String token=verifiedAccess(c);
            if(token.isEmpty())return null;
            HttpURLConnection conn=null;
            try {
                URL url=new URL(SecureApp.API_BASE + "/api/events/" + eventId + "/photo");
                conn=(HttpURLConnection)url.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(3500);conn.setReadTimeout(3500);
                conn.setRequestProperty("Authorization","Bearer "+token);
                int status=conn.getResponseCode();
                if(status==401 && attempt==0){conn.disconnect();refresh(c);continue;}
                if(status!=200)return null;
                String type=conn.getContentType();
                if(type==null || !(type.startsWith("image/jpeg") || type.startsWith("image/png")))return null;
                try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[4096];int count;
                    while((count=in.read(buffer))!=-1){
                        if(out.size()+count>MAX_PHOTO)return null;
                        out.write(buffer,0,count);
                    }
                    byte[] payload=out.toByteArray();
                    BitmapFactory.Options opt=new BitmapFactory.Options();opt.inSampleSize=2;
                    return BitmapFactory.decodeByteArray(payload,0,payload.length,opt);
                }
            } catch(Exception ignored) { return null; }
            finally { if(conn!=null)conn.disconnect(); }
        }
        return null;
    }
    public static void show(Context c, String eventId, String cameraId) {
        if (!VisitorAlertPolicy.allow(c,eventId,cameraId))return;
        Bitmap picture=photo(c,eventId);
        Intent tap = new Intent(Intent.ACTION_VIEW,
            Uri.parse("https://camera.360secure.in/e/" + eventId), c, MainActivity.class);
        tap.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending=PendingIntent.getActivity(c,eventId.hashCode(),tap,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder n = new Notification.Builder(c,CHANNEL)
            .setSmallIcon(R.drawable.ic_secure)
            .setColor(0xff27d465)
            .setContentTitle("360Secure · Visitor detected")
            .setContentText("Front Door · View Visitor Photo")
            .setCategory(Notification.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setVisibility(Notification.VISIBILITY_PRIVATE);
        Notification publicVersion=new Notification.Builder(c,CHANNEL)
            .setSmallIcon(R.drawable.ic_secure)
            .setContentTitle("360Secure")
            .setContentText("A visitor event is available")
            .build();
        n.setPublicVersion(publicVersion);
        if (picture!=null) n.setStyle(new Notification.BigPictureStyle()
            .bigPicture(picture).setSummaryText("Tap to view in Events"));
        try { ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE))
            .notify("visitor",eventId.hashCode(),n.build()); }
        catch (SecurityException ignored) { /* notification permission denied */ }
    }
    private VisitorNotifier() {}
}
