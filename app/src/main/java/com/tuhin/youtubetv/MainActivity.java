package com.tuhin.youtubetv;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    // Exact User-Agent from working APK
    private static final String USER_AGENT = "Mozilla/5.0 (SMART-TV; Linux; Tizen 5.0) AppleWebKit/538.1 (KHTML, like Gecko) Version/5.0 NativeTVAds Safari/538.1";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        android.util.Log.d("YouTubeTV", "WebView initialized");
        
        // Force Hardware Acceleration for video playback
        webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);
        webView.setBackgroundColor(android.graphics.Color.BLACK);

        // Enable Cookies
        android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setUserAgentString(USER_AGENT);

        // Improve performance and compatibility
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setDatabaseEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setSupportMultipleWindows(true);
        webSettings.setBuiltInZoomControls(false);
        webSettings.setDisplayZoomControls(false);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            webSettings.setSafeBrowsingEnabled(false);
        }
        
        webView.setWebChromeClient(new WebChromeClient());
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                android.util.Log.d("YouTubeTV", "Page started: " + url);
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                android.util.Log.d("YouTubeTV", "Page finished: " + url);
                super.onPageFinished(view, url);
            }

            @Override
            public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    android.util.Log.e("YouTubeTV", "Error: " + error.getDescription() + " code: " + error.getErrorCode());
                }
                super.onReceivedError(view, request, error);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Google blocks sign-ins from WebViews.
                // If we detect a login URL, temporarily switch to a standard desktop Chrome User-Agent
                if (url.contains("accounts.google.com")) {
                    view.getSettings().setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                } else if (url.contains("youtube.com/tv")) {
                    // Revert to Smart TV User-Agent when returning to the TV interface
                    view.getSettings().setUserAgentString(USER_AGENT);
                }
                
                view.loadUrl(url);
                return true;
            }
        });

        // Load YouTube TV interface
        android.util.Log.d("YouTubeTV", "Loading URL: https://www.youtube.com/tv");
        webView.loadUrl("https://www.youtube.com/tv");
        
        // Request focus to ensure the TV remote's D-pad events are sent directly to the web app
        webView.requestFocus();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    // Pass D-pad controls to WebView if needed
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER) {
            
            // WebView handles these natively usually, but we can intercept if needed
            return super.onKeyDown(keyCode, event);
        }
        return super.onKeyDown(keyCode, event);
    }
}
