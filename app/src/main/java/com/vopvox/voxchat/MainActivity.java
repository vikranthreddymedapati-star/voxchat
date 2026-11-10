package com.vopvox.voxchat;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.net.Uri;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
import android.widget.LinearLayout;

public class MainActivity extends Activity {
    private WebView webView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showServerSetup();
    }

    private void showServerSetup() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 55, 30, 30);
        layout.setBackgroundColor(Color.rgb(19, 17, 39));

        TextView title = new TextView(this);
        title.setText("✳ VoxChat");
        title.setTextSize(32);
        title.setTextColor(Color.WHITE);
        layout.addView(title);

        TextView help = new TextView(this);
        help.setText("\nText-chat prototype. Enter the HTTPS address of your own hosted VoxChat server. The APK doesn't host a chat server.\n");
        help.setTextColor(Color.LTGRAY);
        help.setTextSize(15);
        layout.addView(help);

        EditText serverUrl = new EditText(this);
        serverUrl.setSingleLine(true);
        serverUrl.setText("https://");
        serverUrl.setHint("https://your-domain.example");
        serverUrl.setTextColor(Color.WHITE);
        serverUrl.setHintTextColor(Color.LTGRAY);
        layout.addView(serverUrl);

        Button open = new Button(this);
        open.setText("Open VoxChat");
        layout.addView(open);

        open.setOnClickListener(view -> {
            String input = serverUrl.getText().toString().trim();
            Uri uri = Uri.parse(input);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                serverUrl.setError("Enter a valid HTTPS website address");
                return;
            }
            openWebsite(input);
        });
        setContentView(layout);
    }

    private void openWebsite(String address) {
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setDomStorageEnabled(false);
        webView.setWebViewClient(new WebViewClient());
        setContentView(webView);
        webView.loadUrl(address);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            showServerSetup();
        }
    }
}
