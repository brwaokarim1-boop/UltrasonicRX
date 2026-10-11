package com.brwa.ultrasonic;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

/**
 * Wraps index.html (in app/src/main/assets) in a full-screen WebView
 * and passes the microphone permission through to the web page.
 */
public class MainActivity extends Activity {

    private static final int REQ_MIC = 1;
    private WebView web;
    private PermissionRequest pendingRequest; // web mic request waiting for Android permission
    private boolean micWasMissing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0f172a"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        // Serve assets from https://appassets.androidplatform.net/assets/...
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }
        });

        // Page called getUserMedia(): grant it once Android RECORD_AUDIO is granted
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED) {
                        request.grant(request.getResources());
                    } else {
                        pendingRequest = request;
                        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
                    }
                });
            }
        });

        web.loadUrl("https://appassets.androidplatform.net/assets/index.html");

        // Ask for the microphone right away, when the app opens
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
        }
    }

    /** Mic was refused permanently ("Don't ask again"): send the user to App Settings. */
    private void showSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Microphone needed")
                .setMessage("This app needs the microphone to receive messages.\n\n"
                        + "Open Settings > Permissions > Microphone > Allow.")
                .setPositiveButton("Open Settings", (d, w) -> {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", getPackageName(), null));
                    startActivity(i);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Coming back from Settings with the mic allowed: reload so Start works
        if (web != null && micWasMissing
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            micWasMissing = false;
            web.reload();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_MIC) return;
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;

        if (pendingRequest != null) {
            if (granted) pendingRequest.grant(pendingRequest.getResources());
            else pendingRequest.deny();
            pendingRequest = null;
        }
        if (!granted) {
            micWasMissing = true;
            // false here after a refusal means Android will not show the prompt again
            if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                showSettingsDialog();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
