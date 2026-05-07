package com.tuhin.youtubetv;

import android.os.Bundle;
import android.view.KeyEvent;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import java.util.List;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.GeckoSession.PromptDelegate.BasePrompt;
import org.mozilla.geckoview.GeckoSession.PromptDelegate.TextPrompt;
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse;

public class MainActivity extends AppCompatActivity {

    private GeckoView geckoView;
    private GeckoSession geckoSession;
    private android.view.View loadingOverlay;
    private android.widget.TextView errorMessage;
    private android.widget.Button retryButton;
    private android.widget.ProgressBar loadingSpinner;
    private static GeckoRuntime sRuntime;
    private boolean mCanGoBack = false;
    private String mCurrentUrl = "";
    private long mLastBackPressTime = 0;

    // Modern WebOS User-Agent for best compatibility with YouTube TV
    private static final String TV_USER_AGENT = "Mozilla/5.0 (WebOS; SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.5735.199 Safari/537.36";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        geckoView = findViewById(R.id.geckoview);
        loadingOverlay = findViewById(R.id.loading_overlay);
        errorMessage = findViewById(R.id.error_message);
        retryButton = findViewById(R.id.retry_button);
        loadingSpinner = findViewById(R.id.loading_spinner);

        retryButton.setOnClickListener(v -> {
            showLoadingOverlay();
            geckoSession.loadUri("https://www.youtube.com/tv");
        });

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

        // Track back navigation state
        geckoSession.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public void onCanGoBack(GeckoSession session, boolean canGoBack) {
                mCanGoBack = canGoBack;
            }

            @Override
            public void onLocationChange(GeckoSession session, String url, List<GeckoSession.PermissionDelegate.ContentPermission> perms) {
                Log.d("YouTubeTV", "Location changed to: " + url);
                mCurrentUrl = url;
            }
        });

        // Handle loading progress
        geckoSession.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(GeckoSession session, String url) {
                showLoadingOverlay();
            }

            @Override
            public void onPageStop(GeckoSession session, boolean success) {
                Log.d("YouTubeTV", "onPageStop success=" + success);
                if (!success) {
                    showErrorState();
                } else {
                    // Fallback 1: Hide when page stops loading successfully
                    hideLoadingOverlay();
                }
            }

            @Override
            public void onProgressChange(GeckoSession session, int progress) {
                // Fallback 2: Hide when progress is high enough (90%+)
                if (progress >= 90) {
                    hideLoadingOverlay();
                }
            }
        });

        // Use ContentDelegate to know when the page is actually rendered
        geckoSession.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onFirstComposite(GeckoSession session) {
                Log.d("YouTubeTV", "First composite - page is rendering");
                hideLoadingOverlay();
            }
        });

        // Use PromptDelegate as a bridge for JS events
        geckoSession.setPromptDelegate(new GeckoSession.PromptDelegate() {
            @Override
            public GeckoResult<PromptResponse> onTextPrompt(GeckoSession session, TextPrompt prompt) {
                if (prompt.message != null) {
                    if (prompt.message.equals("APP_LOADING_START")) {
                        runOnUiThread(() -> showLoadingOverlay());
                        return GeckoResult.fromValue(prompt.confirm("ok"));
                    } else if (prompt.message.equals("APP_LOADING_STOP")) {
                        runOnUiThread(() -> hideLoadingOverlay());
                        return GeckoResult.fromValue(prompt.confirm("ok"));
                    }
                }
                return GeckoSession.PromptDelegate.super.onTextPrompt(session, prompt);
            }
        });

        // Basic session setup
        geckoSession.open(sRuntime);
        geckoView.setSession(geckoSession);

        // Load YouTube TV
        geckoSession.loadUri("https://www.youtube.com/tv");
        
        // Inject script to monitor video buffering whenever a page finishes loading
        // We'll call this in hideLoadingOverlay to ensure it's active
        
        // Ensure the view has focus for remote control interaction
        geckoView.requestFocus();
    }

    private void injectVideoMonitor() {
        if (geckoSession == null) return;
        
        Log.d("YouTubeTV", "Injecting video monitor script");
        String script = 
            "(function() {" +
            "  if (window.ytMonitorInjected) return;" +
            "  window.ytMonitorInjected = true;" +
            "  let lastState = null;" +
            "  const notify = (msg) => {" +
            "    if (lastState === msg) return;" +
            "    lastState = msg;" +
            "    prompt(msg);" +
            "  };" +
            "  const checkState = () => {" +
            "    const videos = document.querySelectorAll('video');" +
            "    let anyBuffering = false;" +
            "    let anyPlaying = false;" +
            "    videos.forEach(v => {" +
            "      if (v.readyState < 3 && !v.paused) anyBuffering = true;" +
            "      if (v.readyState >= 3 && !v.paused) anyPlaying = true;" +
            "    });" +
            "    if (anyBuffering) notify('APP_LOADING_START');" +
            "    else if (anyPlaying) notify('APP_LOADING_STOP');" +
            "  };" +
            "  setInterval(checkState, 1000);" +
            "  const monitor = () => {" +
            "    document.querySelectorAll('video').forEach(video => {" +
            "      if (video.dataset.monitored) return;" +
            "      video.dataset.monitored = 'true';" +
            "      video.addEventListener('waiting', () => notify('APP_LOADING_START'));" +
            "      video.addEventListener('loadstart', () => notify('APP_LOADING_START'));" +
            "      video.addEventListener('playing', () => notify('APP_LOADING_STOP'));" +
            "      video.addEventListener('canplay', () => notify('APP_LOADING_STOP'));" +
            "      video.addEventListener('seeked', () => notify('APP_LOADING_STOP'));" +
            "    });" +
            "  };" +
            "  setInterval(monitor, 2000);" +
            "  monitor();" +
            "})();";
        geckoSession.loadUri("javascript:" + script);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            // On ACTION_UP, check if we should exit the app
            if (event.getAction() == KeyEvent.ACTION_UP) {
                if (!handleBackNavigation()) {
                    return super.dispatchKeyEvent(event); // Exit
                }
            }

            // Translate the BACK key event into an ESCAPE key event for the web app
            // This is the native "back" button for YouTube TV's interface
            KeyEvent escapeEvent = new KeyEvent(
                event.getDownTime(),
                event.getEventTime(),
                event.getAction(),
                KeyEvent.KEYCODE_ESCAPE,
                event.getRepeatCount(),
                event.getMetaState()
            );
            geckoView.dispatchKeyEvent(escapeEvent);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean handleBackNavigation() {
        if (geckoSession == null) return false;

        Log.d("YouTubeTV", "Current URL for Back: " + mCurrentUrl);

        // If we're not at the home page, we always stay in the app and let the translated ESCAPE key handle it
        if (!isAtRootUrl(mCurrentUrl)) {
            mLastBackPressTime = 0; // Reset exit timer
            return true;
        }

        // We are at root - check for double-back-to-exit
        long currentTime = System.currentTimeMillis();
        if (mLastBackPressTime != 0 && (currentTime - mLastBackPressTime < 2000)) {
            Log.d("YouTubeTV", "Exiting from root");
            return false; // Allow super.dispatchKeyEvent to exit
        }

        mLastBackPressTime = currentTime;
        Log.d("YouTubeTV", "Root back press detected, waiting for second press to exit");
        return true; 
    }

    private void showLoadingOverlay() {
        if (loadingOverlay != null) {
            loadingOverlay.animate().cancel();
            loadingOverlay.setAlpha(1.0f);
            loadingOverlay.setVisibility(android.view.View.VISIBLE);
            loadingSpinner.setVisibility(android.view.View.VISIBLE);
            errorMessage.setVisibility(android.view.View.GONE);
            retryButton.setVisibility(android.view.View.GONE);
            
            // Safety timeout: hide after 15 seconds if it's still showing and no error
            loadingOverlay.postDelayed(() -> {
                if (loadingOverlay.getVisibility() == android.view.View.VISIBLE && 
                    errorMessage.getVisibility() == android.view.View.GONE) {
                    hideLoadingOverlay();
                }
            }, 15000);
        }
    }

    private void hideLoadingOverlay() {
        if (loadingOverlay != null && loadingOverlay.getVisibility() == android.view.View.VISIBLE) {
            // Re-inject monitor just in case page changed
            injectVideoMonitor();
            
            loadingOverlay.animate().cancel();
            loadingOverlay.animate()
                    .alpha(0f)
                    .setDuration(300) // Faster hide
                    .withEndAction(() -> {
                        loadingOverlay.setVisibility(android.view.View.GONE);
                    });
        }
    }

    private void showErrorState() {
        if (loadingOverlay != null) {
            loadingOverlay.setAlpha(1.0f);
            loadingOverlay.setVisibility(android.view.View.VISIBLE);
            loadingSpinner.setVisibility(android.view.View.GONE);
            errorMessage.setVisibility(android.view.View.VISIBLE);
            retryButton.setVisibility(android.view.View.VISIBLE);
        }
    }

    private boolean isAtRootUrl(String url) {
        if (url == null || url.isEmpty() || url.equals("about:blank")) {
            return true;
        }
        
        String cleanUrl = url.toLowerCase();
        
        // If it's a specific view, it's definitely not the root home page
        if (cleanUrl.contains("/watch") || cleanUrl.contains("/search") || 
            cleanUrl.contains("/browse") || cleanUrl.contains("/playlist") ||
            cleanUrl.contains("/channel") || cleanUrl.contains("/settings")) {
            return false;
        }

        // Normalize URL for comparison
        if (cleanUrl.endsWith("/")) cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 1);
        if (cleanUrl.endsWith("#")) cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 1);
        if (cleanUrl.endsWith("#/")) cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 2);

        return cleanUrl.equals("https://www.youtube.com/tv") || 
               cleanUrl.endsWith(".com/tv") || 
               cleanUrl.endsWith("/tv/home") ||
               cleanUrl.endsWith("/tv#/home");
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
