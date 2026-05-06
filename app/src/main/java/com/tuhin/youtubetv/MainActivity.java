package com.tuhin.youtubetv;

import android.os.Bundle;
import android.view.KeyEvent;
import androidx.appcompat.app.AppCompatActivity;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;

public class MainActivity extends AppCompatActivity {

    private GeckoView geckoView;
    private GeckoSession geckoSession;
    private static GeckoRuntime sRuntime;

    // Modern WebOS User-Agent for best compatibility with YouTube TV
    private static final String TV_USER_AGENT = "Mozilla/5.0 (WebOS; SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.5735.199 Safari/537.36";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        geckoView = findViewById(R.id.geckoview);

        // Initialize GeckoRuntime if not already done
        if (sRuntime == null) {
            sRuntime = GeckoRuntime.create(this);
        }

        // Configure session settings
        GeckoSessionSettings settings = new GeckoSessionSettings.Builder()
                .usePrivateMode(false)
                .useTrackingProtection(false)
                .userAgentOverride(TV_USER_AGENT)
                .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE) // Use override
                .build();

        geckoSession = new GeckoSession(settings);

        // Basic session setup
        geckoSession.open(sRuntime);
        geckoView.setSession(geckoSession);

        // Load YouTube TV
        geckoSession.loadUri("https://www.youtube.com/tv");
        
        // Ensure the view has focus for remote control interaction
        geckoView.requestFocus();
    }

    @Override
    public void onBackPressed() {
        // Handle back navigation in the browser if possible
        // GeckoView back navigation is handled via the session
        // For simplicity, we just exit or use a basic check
        super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // GeckoView handles D-pad events natively when focused.
        // We only need to intercept if we want custom behavior.
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (geckoSession != null) {
            // Optional: Pause video playback on background
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (geckoSession != null) {
            geckoSession.close();
        }
    }
}
