package com.kitchenrabbit4.app;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

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
        settings.setAllowFileAccess(true);

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view,
                    WebResourceRequest request
            ) {
                Uri uri = request.getUrl();

                if ("kitchenrabbit.local".equals(uri.getHost())
                        && uri.getPath() != null
                        && uri.getPath().startsWith("/images/")) {

                    try {
                        String fileName = uri.getLastPathSegment();

                        File imageDir =
                                new File(getFilesDir(), "recipe_images");

                        File imageFile =
                                new File(imageDir, fileName);

                        if (imageFile.exists()) {

                            String mime = "image/jpeg";

                            if (fileName.endsWith(".png")) {
                                mime = "image/png";
                            } else if (fileName.endsWith(".webp")) {
                                mime = "image/webp";
                            }

                            return new WebResourceResponse(
                                    mime,
                                    null,
                                    new FileInputStream(imageFile)
                            );
                        }

                    } catch (Exception ignored) {
                    }
                }

                return super.shouldInterceptRequest(view, request);
            }
        });

        webView.addJavascriptInterface(
                new RecipeBridge(),
                "AndroidRecipe"
        );

        webView.loadUrl(
                "file:///android_asset/index.html"
        );
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

                    BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(
                                            connection.getInputStream()
                                    )
                            );

                    StringBuilder html =
                            new StringBuilder();

                    String line;

                    while ((line = reader.readLine()) != null) {
                        html.append(line);
                    }

                    reader.close();
                    connection.disconnect();

                    String js =
                            "window.receiveRecipeHtml(" +
                            JSONObject.quote(html.toString()) +
                            ");";

                    runOnUiThread(() ->
                            webView.evaluateJavascript(
                                    js,
                                    null
                            )
                    );

                } catch (Exception e) {

                    String message =
                            e.getMessage() != null
                                    ? e.getMessage()
                                    : "Unbekannter Fehler";

                    String js =
                            "window.recipeImportError(" +
                            JSONObject.quote(message) +
                            ");";

                    runOnUiThread(() ->
                            webView.evaluateJavascript(
                                    js,
                                    null
                            )
                    );
                }

            }).start();
        }


        @JavascriptInterface
        public void downloadImage(String imageUrl) {

            new Thread(() -> {

                try {

                    URL url = new URL(imageUrl);

                    HttpURLConnection connection =
                            (HttpURLConnection) url.openConnection();

                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(15000);
                    connection.setInstanceFollowRedirects(true);

                    connection.setRequestProperty(
                            "User-Agent",
                            "Mozilla/5.0 KitchenRabbit4"
                    );

                    connection.connect();

                    String contentType =
                            connection.getContentType();

                    String extension = ".jpg";

                    if (contentType != null) {

                        if (contentType.contains("png")) {
                            extension = ".png";
                        } else if (contentType.contains("webp")) {
                            extension = ".webp";
                        }
                    }

                    File imageDir =
                            new File(
                                    getFilesDir(),
                                    "recipe_images"
                            );

                    if (!imageDir.exists()) {
                        imageDir.mkdirs();
                    }

                    String fileName =
                            UUID.randomUUID().toString()
                                    + extension;

                    File imageFile =
                            new File(
                                    imageDir,
                                    fileName
                            );

                    InputStream input =
                            connection.getInputStream();

                    FileOutputStream output =
                            new FileOutputStream(
                                    imageFile
                            );

                    byte[] buffer =
                            new byte[8192];

                    int length;

                    while ((length = input.read(buffer)) > 0) {
                        output.write(
                                buffer,
                                0,
                                length
                        );
                    }

                    output.close();
                    input.close();
                    connection.disconnect();

                    String localUrl =
                            "https://kitchenrabbit.local/images/"
                                    + fileName;

                    String js =
                            "window.recipeImageSaved(" +
                            JSONObject.quote(localUrl) +
                            ");";

                    runOnUiThread(() ->
                            webView.evaluateJavascript(
                                    js,
                                    null
                            )
                    );

                } catch (Exception e) {

                    String message =
                            e.getMessage() != null
                                    ? e.getMessage()
                                    : "Bild konnte nicht gespeichert werden";

                    String js =
                            "window.recipeImageError(" +
                            JSONObject.quote(message) +
                            ");";

                    runOnUiThread(() ->
                            webView.evaluateJavascript(
                                    js,
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
}
