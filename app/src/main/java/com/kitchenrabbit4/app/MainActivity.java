package com.kitchenrabbit4.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        webView.setWebViewClient(new WebViewClient());

        webView.addJavascriptInterface(new RecipeBridge(), "AndroidRecipe");

        webView.loadUrl("file:///android_asset/index.html");
    }

    public class RecipeBridge {

        @JavascriptInterface
        public void loadRecipe(String recipeUrl) {
            new Thread(() -> {
                try {
                    URL url = new URL(recipeUrl);
                    HttpURLConnection connection =
                            (HttpURLConnection) url.openConnection();

                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(15000);
                    connection.setRequestProperty(
                            "User-Agent",
                            "Mozilla/5.0 KitchenRabbit4"
                    );

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream())
                    );

                    StringBuilder html = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        html.append(line);
                    }

                    reader.close();
                    connection.disconnect();

                    String safeHtml = html.toString()
                            .replace("\\", "\\\\")
                            .replace("`", "\\`")
                            .replace("${", "\\${");

                    runOnUiThread(() ->
                            webView.evaluateJavascript(
                                    "window.receiveRecipeHtml(`" + safeHtml + "`);",
                                    null
                            )
                    );

                } catch (Exception e) {
                    runOnUiThread(() ->
                            webView.evaluateJavascript(
                                    "window.recipeImportError(" +
                                            "'" + e.getMessage()
                                            .replace("'", "\\'") + "'" +
                                            ");",
                                    null
                            )
                    );
                }
            }).start();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
