package fr.plrk.volley;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    private WebView web;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (Exception ignored) { }
                    return true;
                }
                return false;
            }
        });

        web.addJavascriptInterface(new Bridge(), "Android");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    /** Pont appelé depuis le JavaScript : télécharge une page sans restriction CORS. */
    public class Bridge {
        @JavascriptInterface
        public void fetch(final String id, final String url) {
            new Thread(() -> {
                String body;
                boolean ok;
                try {
                    body = download(url);
                    ok = true;
                } catch (Exception e) {
                    body = e.getClass().getSimpleName() + (e.getMessage() != null ? " : " + e.getMessage() : "");
                    ok = false;
                }
                final String js = "window.onFetched(" + JSONObject.quote(id) + "," + ok + "," + JSONObject.quote(body) + ")";
                mainHandler.post(() -> web.evaluateJavascript(js, null));
            }).start();
        }
    }

    private static String download(String address) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(25000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) PLRKVolley/1.0");
        c.setRequestProperty("Accept-Language", "fr-FR,fr;q=0.9");
        try {
            int code = c.getResponseCode();
            if (code >= 400) throw new IOException("le site a répondu HTTP " + code);
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toString("UTF-8");
            }
        } finally {
            c.disconnect();
        }
    }
}
