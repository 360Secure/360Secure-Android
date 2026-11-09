package in.secure360.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.Button;
import android.os.Build;

/** Dedicated portrait call screen; Android restrictions may show a heads-up notification instead. */
public class IncomingCallActivity extends Activity {
    private String cameraId,callId;
    private int dp(float v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    private GradientDrawable shape(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private TextView text(String s,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);v.setGravity(Gravity.CENTER);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private LinearLayout.LayoutParams lp(int h){return new LinearLayout.LayoutParams(-1,dp(h));}
    @Override public void onCreate(Bundle b){super.onCreate(b);
        cameraId=getIntent().getStringExtra("cameraId");callId=getIntent().getStringExtra("callId");
        if(Build.VERSION.SDK_INT>=27){setShowWhenLocked(true);setTurnScreenOn(true);}
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(9,15,18));
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(28),dp(20),dp(28),dp(32));
        root.addView(page,new FrameLayout.LayoutParams(-1,-1));setContentView(root);SecureApp.fitWindow(this,root,page);
        TextView brand=text("360SECURE",12,0xff92A99C,true);brand.setLetterSpacing(.22f);page.addView(brand,lp(44));
        View flex1=new View(this);page.addView(flex1,new LinearLayout.LayoutParams(1,0,1));
        TextView logo=text("⌂",65,0xff28D66B,true);logo.setBackground(shape(0xff102C1D,100));
        LinearLayout.LayoutParams logoLp=new LinearLayout.LayoutParams(dp(124),dp(124));logoLp.gravity=Gravity.CENTER_HORIZONTAL;logoLp.bottomMargin=dp(34);page.addView(logo,logoLp);
        TextView name=text("Smart Door Lock",30,Color.WHITE,true);page.addView(name,lp(52));
        TextView state=text("Incoming doorbell call",15,0xff9DAFA4,false);page.addView(state,lp(35));
        TextView helper=text("Someone is waiting at your door",13,0xff677B71,false);page.addView(helper,lp(35));
        View flex2=new View(this);page.addView(flex2,new LinearLayout.LayoutParams(1,0,1));
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setGravity(Gravity.CENTER);page.addView(actions,lp(130));
        Button decline=new Button(this);decline.setAllCaps(false);decline.setText("✕\nDecline");decline.setTextSize(15);decline.setTextColor(Color.WHITE);decline.setBackground(shape(0xffE2484A,36));
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(108),1);a.setMargins(dp(8),0,dp(16),0);actions.addView(decline,a);
        Button accept=new Button(this);accept.setAllCaps(false);accept.setText("✓\nAccept");accept.setTextSize(15);accept.setTextColor(0xff082113);accept.setBackground(shape(0xff2FD86A,36));
        LinearLayout.LayoutParams z=new LinearLayout.LayoutParams(0,dp(108),1);z.setMargins(dp(16),0,dp(8),0);actions.addView(accept,z);
        decline.setOnClickListener(v->{CallNotifier.cancel(this);finish();});
        accept.setOnClickListener(v->{CallNotifier.cancel(this);Intent i=new Intent(this,MainActivity.class).setAction("in.secure360.app.ANSWER_DOORBELL").putExtra("cameraId",cameraId).putExtra("callId",callId).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(i);finish();});
        TextView footer=text("Encrypted connection · 360Secure",12,0xff718479,false);page.addView(footer,lp(44));
        new Handler(Looper.getMainLooper()).postDelayed(()->{if(!isFinishing()){CallNotifier.cancel(this);finish();}},45000);
    }
}
