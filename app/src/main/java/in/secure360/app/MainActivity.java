package in.secure360.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.app.NotificationManager;
import android.content.Context;
import android.provider.Settings;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.widget.FrameLayout;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView web;
    private PermissionRequest pendingMic;
    private boolean pageLoaded=false;
    private String pendingCameraId=null;
    private String pendingCallId=null;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        FrameLayout root = new FrameLayout(this);
        web = new WebView(this);
        web.setBackgroundColor(0xff090f12);
        root.addView(web, new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
        SecureApp.fitWindow(this,root,web);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setMediaPlaybackRequiresUserGesture(false);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.addJavascriptInterface(new Bridge(), "SecureNative");
        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
        web.setWebViewClient(new WebViewClientCompat(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {return loader.shouldInterceptRequest(req.getUrl());}
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u=req.getUrl();
                return !"https".equals(u.getScheme()) || !"appassets.androidplatform.net".equals(u.getHost());
            }
            @Override public void onPageFinished(WebView v, String url) {pageLoaded=true;sendCallIntent();}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onPermissionRequest(PermissionRequest request) {
                Uri u=request.getOrigin();
                if (!"https".equals(u.getScheme()) || !"appassets.androidplatform.net".equals(u.getHost())) {
                    request.deny();return;
                }
                if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
                    pendingMic=request;
                    runOnUiThread(()->requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},101));
                } else {
                    request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                }
            }
        });
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},102);
        }
        takeCallIntent(getIntent());
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);takeCallIntent(intent);sendCallIntent();}
    private void takeCallIntent(Intent i){
        if (i!=null && "in.secure360.app.ANSWER_DOORBELL".equals(i.getAction())) {
            pendingCameraId=i.getStringExtra("cameraId");pendingCallId=i.getStringExtra("callId");
        }
    }
    private void sendCallIntent(){
        if(!pageLoaded || pendingCameraId==null)return;
        String camera = pendingCameraId, call = pendingCallId;
        pendingCameraId=null;pendingCallId=null;
        try {JSONObject j = new JSONObject();j.put("cameraId", camera);j.put("callId", call);
            web.evaluateJavascript("window.secureDoorbell && window.secureDoorbell("+j.toString()+")", null);
        } catch (Exception ignored) { }
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if (requestCode==101 && pendingMic!=null){
            if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)pendingMic.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            else pendingMic.deny();pendingMic=null;
        }
    }
    @Override public void onBackPressed(){
        if(web!=null)web.evaluateJavascript("window.appBack && window.appBack()",null);
        else super.onBackPressed();
    }
    @Override protected void onDestroy(){if(web!=null){web.destroy();web=null;}super.onDestroy();}
    public class Bridge {
        @JavascriptInterface public String getSession(){return SessionStore.read(MainActivity.this).toString();}
        @JavascriptInterface public void storeSession(String access,String refresh,String phone){
            if(access==null||access.isEmpty())return;
            SessionStore.save(MainActivity.this,access,refresh==null?"":refresh,phone==null?"":phone);
            PushRegistrar.sync(getApplicationContext());
        }
        @JavascriptInterface public void clearSession(){SessionStore.clear(MainActivity.this);}
        @JavascriptInterface public void rememberCamera(String cameraId){SessionStore.setCamera(MainActivity.this,cameraId);}
        @JavascriptInterface public String rememberedCamera(){return SessionStore.camera(MainActivity.this);}
        @JavascriptInterface public void resyncPush(){PushRegistrar.sync(getApplicationContext());}
        @JavascriptInterface public void openNotificationSettings(){runOnUiThread(()->{
            Intent i=new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName())
                .putExtra(Settings.EXTRA_CHANNEL_ID,CallNotifier.CHANNEL);
            try{startActivity(i);}catch(Exception ignored){}
        });}
        @JavascriptInterface public void openFullScreenCallSettings(){runOnUiThread(()->{
            if(Build.VERSION.SDK_INT>=34){
                Intent i=new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                    .setData(Uri.parse("package:"+getPackageName()));
                try{startActivity(i);}catch(Exception ignored){}
            }
        });}
    }
}
