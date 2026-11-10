package com.vopvox.voxchat;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.net.http.SslError;
import android.view.ViewGroup;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebResourceResponse;
import android.webkit.SslErrorHandler;

public class MainActivity extends Activity {
    private final int background = Color.rgb(15, 14, 33);
    private final int foreground = Color.rgb(245, 242, 255);
    private final int secondary = Color.rgb(183, 176, 212);
    private WebView webView;
    private String serverAddress = "";
    private boolean pageFailed = false;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        showHome();
    }

    private LinearLayout page() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(28, 42, 28, 28);
        layout.setBackgroundColor(background);
        return layout;
    }

    private TextView text(String value, int size, int color) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(size);
        label.setTextColor(color);
        label.setPadding(0, 8, 0, 12);
        return label;
    }

    private Button button(String name) {
        Button b = new Button(this);
        b.setText(name);
        b.setAllCaps(false);
        return b;
    }

    private void showHome() {
        webView = null;
        LinearLayout layout = page();
        layout.addView(text("✳ VoxChat", 36, foreground));
        layout.addView(text("Say hello to someone new.", 22, foreground));
        layout.addView(text("No login • Text chat • Development prototype", 14, secondary));
        layout.addView(text("Your previous app displayed a blank screen because it didn't have an online chat server to open. This version gives you an offline preview and shows helpful connection errors.", 16, secondary));

        Button preview = button("Explore offline preview");
        layout.addView(preview);
        preview.setOnClickListener(v -> showPreview());

        Button connect = button("Connect to my HTTPS chat server");
        layout.addView(connect);
        connect.setOnClickListener(v -> showServerSetup());

        layout.addView(text("An APK is the Android app, not a hosting service. Connecting to real people requires a separately hosted HTTPS server. The prototype has no active moderation, and should not be released publicly yet.", 13, secondary));
        setContentView(layout);
    }

    private void showServerSetup() {
        webView = null;
        LinearLayout layout = page();
        layout.addView(text("Connect to VoxChat", 28, foreground));
        layout.addView(text("Enter the complete HTTPS address of the VoxChat website you have hosted. Example: https://your-site.example", 15, secondary));
        layout.addView(text("Do not enter only https://. GitHub repository links and GitHub Pages do not run the Node.js chat server.", 14, secondary));

        EditText address = new EditText(this);
        address.setSingleLine(true);
        address.setText(serverAddress);
        address.setHint("https://your-site.example");
        address.setTextColor(foreground);
        address.setHintTextColor(secondary);
        address.setInputType(17); // textUri
        layout.addView(address);

        Button open = button("Open hosted VoxChat");
        layout.addView(open);
        open.setOnClickListener(v -> {
            String url = address.getText().toString().trim();
            try {
                Uri parsed = Uri.parse(url);
                String host = parsed.getHost();
                if (!"https".equalsIgnoreCase(parsed.getScheme()) || host == null || !host.contains(".") || parsed.getUserInfo() != null) {
                    address.setError("Enter a full HTTPS website address, not just https://");
                    return;
                }
            } catch (Exception e) {
                address.setError("Invalid website address");
                return;
            }
            serverAddress = url;
            openWebsite(url);
        });

        Button preview = button("Use offline preview instead");
        layout.addView(preview);
        preview.setOnClickListener(v -> showPreview());

        Button back = button("Back");
        layout.addView(back);
        back.setOnClickListener(v -> showHome());
        setContentView(layout);
    }

    private void showPreview() {
        LinearLayout main = page();
        main.addView(text("✳ VoxChat • Offline preview", 25, foreground));
        main.addView(text("These are sample messages only. No stranger is connected, and nothing is sent online.", 14, secondary));

        ScrollView scrolling = new ScrollView(this);
        LinearLayout messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        scrolling.addView(messages);
        main.addView(scrolling, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        messages.addView(text("DEMO  •  Not a real conversation", 13, secondary));
        messages.addView(text("Example stranger: Hey! What kind of games do you like?", 17, foreground));
        messages.addView(text("Example you: I like adventure games!", 17, secondary));
        messages.addView(text("Example stranger: Nice! What's your favorite one?", 17, foreground));

        EditText typed = new EditText(this);
        typed.setSingleLine(false);
        typed.setMaxLines(3);
        typed.setHint("Type a sample message...");
        typed.setTextColor(foreground);
        typed.setHintTextColor(secondary);
        main.addView(typed);

        Button send = button("Add message to preview");
        main.addView(send);
        send.setOnClickListener(v -> {
            String msg = typed.getText().toString().trim();
            if (msg.isEmpty()) return;
            if (msg.length() > 500) msg = msg.substring(0, 500);
            messages.addView(text("Example you: " + msg, 17, secondary));
            typed.setText("");
            scrolling.post(() -> scrolling.fullScroll(View.FOCUS_DOWN));
        });

        Button connect = button("Connect to an online server");
        main.addView(connect);
        connect.setOnClickListener(v -> showServerSetup());

        Button back = button("Back to home");
        main.addView(back);
        back.setOnClickListener(v -> showHome());
        setContentView(main);
    }

    private void showLoadError(String reason) {
        if (pageFailed) return;
        pageFailed = true;
        LinearLayout layout = page();
        layout.addView(text("Can't open the chat website", 27, foreground));
        layout.addView(text(reason, 16, secondary));
        layout.addView(text("This APK needs a working, separately hosted HTTPS VoxChat server. Without it you can use the offline preview, but you cannot chat with real people.", 15, secondary));
        Button retry = button("Try this address again");
        layout.addView(retry);
        retry.setOnClickListener(v -> openWebsite(serverAddress));
        Button change = button("Change server address");
        layout.addView(change);
        change.setOnClickListener(v -> showServerSetup());
        Button offline = button("Open offline preview");
        layout.addView(offline);
        offline.setOnClickListener(v -> showPreview());
        setContentView(layout);
        webView = null;
    }

    private void openWebsite(String url) {
        pageFailed = false;
        LinearLayout root = page();
        TextView status = text("Opening VoxChat website...", 15, foreground);
        root.addView(status);
        Button back = button("Back / Change address");
        root.addView(back);
        back.setOnClickListener(v -> showServerSetup());
        webView = new WebView(this);
        webView.setBackgroundColor(background);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        final String approvedHost = Uri.parse(url).getHost();
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                return !"https".equalsIgnoreCase(uri.getScheme()) || !approvedHost.equalsIgnoreCase(uri.getHost());
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showLoadError("The server couldn't be reached. Check that the URL is hosted and your internet connection works.");
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame() && response.getStatusCode() >= 400) showLoadError("The website returned HTTP " + response.getStatusCode() + ". Check that VoxChat is deployed at this URL.");
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                showLoadError("The HTTPS certificate could not be verified. Please use a valid secure website address.");
            }
            @Override public void onPageFinished(WebView view, String address) {
                if (!pageFailed) status.setText("Website loaded • " + approvedHost);
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        webView.loadUrl(url);
    }

    @Override public void onBackPressed() {
        showHome();
    }
}
