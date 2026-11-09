package in.secure360.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;

/** Tokens never stored in plaintext preferences and never written to logs. */
public final class SessionStore {
    private static final String KEY_ALIAS = "secure360_session_aes_v1";
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("secure360_private", Context.MODE_PRIVATE); }
    private static SecretKey secret() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        SecretKey key = (SecretKey) ks.getKey(KEY_ALIAS, null);
        if (key == null) {
            KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
            key = gen.generateKey();
        }
        return key;
    }
    public static synchronized void save(Context c, String access, String refresh, String phone) {
        try {
            JSONObject json = new JSONObject(); json.put("accessToken", access); json.put("refreshToken", refresh);
            json.put("phone", phone);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, secret());
            byte[] encrypted = cipher.doFinal(json.toString().getBytes(StandardCharsets.UTF_8));
            prefs(c).edit().putString("session", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)).apply();
        } catch (Exception e) { throw new IllegalStateException("Could not securely save session",e); }
    }
    public static synchronized JSONObject read(Context c) {
        try {
            String value = prefs(c).getString("session", null), iv = prefs(c).getString("iv", null);
            if (value == null || iv == null) return new JSONObject();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secret(), new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            return new JSONObject(new String(cipher.doFinal(Base64.decode(value, Base64.NO_WRAP)), StandardCharsets.UTF_8));
        } catch (Exception e) { clear(c); return new JSONObject(); }
    }
    public static String access(Context c) { return read(c).optString("accessToken", ""); }
    public static void setCamera(Context c, String cameraId) { prefs(c).edit().putString("cameraId",cameraId).apply(); }
    public static String camera(Context c) { return prefs(c).getString("cameraId", ""); }
    public static void clear(Context c) { prefs(c).edit().remove("session").remove("iv").remove("cameraId").apply(); }
    private SessionStore(){}
}
