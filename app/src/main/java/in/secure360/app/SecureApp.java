package in.secure360.app;

import android.app.Application;
import android.app.Activity;
import android.graphics.Insets;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

public class SecureApp extends Application {
    public static final String API_BASE = "https://camera.360secure.in";
    @Override public void onCreate() {
        super.onCreate();
        CallNotifier.createChannel(this);
        VisitorNotifier.createChannel(this);
        String appId = getString(R.string.fcm_application_id);
        String senderId = getString(R.string.fcm_sender_id);
        String projectId = getString(R.string.fcm_project_id);
        String key = getString(R.string.fcm_api_key);
        if (!appId.isEmpty() && !senderId.isEmpty() && !projectId.isEmpty() && !key.isEmpty()
                && FirebaseApp.getApps(this).isEmpty()) {
            FirebaseOptions options = new FirebaseOptions.Builder().setApplicationId(appId)
                .setGcmSenderId(senderId).setProjectId(projectId).setApiKey(key).build();
            FirebaseApp.initializeApp(this, options);
        }
    }
    /** Fit content inside BOTH status bar / cutout and 3-button or gesture navigation area. */
    public static void fitWindow(Activity activity, FrameLayout root, View content) {
        Window window = activity.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.getDecorView().setSystemUiVisibility(0);
        if (Build.VERSION.SDK_INT >= 29) window.setNavigationBarContrastEnforced(false);
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets s = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) content.getLayoutParams();
                if (lp.leftMargin != s.left || lp.topMargin != s.top || lp.rightMargin != s.right || lp.bottomMargin != s.bottom) {
                    lp.setMargins(s.left, s.top, s.right, s.bottom);
                    content.setLayoutParams(lp);
                }
                return insets;
            });
        } else {
            window.setStatusBarColor(Color.rgb(9,15,18));
            window.setNavigationBarColor(Color.rgb(9,15,18));
            window.getDecorView().setSystemUiVisibility(0);
        }
        root.setBackgroundColor(Color.rgb(9,15,18));
    }
}
