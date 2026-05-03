package com.example.mediconnect;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Privacy Policy Activity
 *
 * Reads from Firestore: collection "siteContent" → document "privacyPolicy"
 * Expected fields:
 *   content     : String  (HTML or plain text — rendered in WebView)
 *   lastUpdated : String  (e.g. "April 29, 2026")
 *
 * If the document doesn't exist or fetch fails, the embedded fallback policy is shown.
 *
 * To seed this in Firestore:
 *   db.collection("siteContent").document("privacyPolicy").set({
 *     content: "<h2>Our Policy</h2><p>...</p>",
 *     lastUpdated: "April 29, 2026"
 *   });
 */
public class PrivacyPolicyActivity extends AppCompatActivity {

    private ProgressBar policyLoading;
    private ScrollView  policyScroll;
    private WebView     wvPolicy;
    private TextView    tvLastUpdated;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_policy);

        policyLoading  = findViewById(R.id.policy_loading);
        policyScroll   = findViewById(R.id.policy_scroll);
        wvPolicy       = findViewById(R.id.wv_policy);
        tvLastUpdated  = findViewById(R.id.tv_last_updated);

        // Back button
        View back = findViewById(R.id.back_button);
        if (back != null) back.setOnClickListener(v -> finish());

        // WebView setup — transparent background so card_bg shows through
        wvPolicy.setBackgroundColor(Color.TRANSPARENT);
        wvPolicy.getSettings().setJavaScriptEnabled(false);
        wvPolicy.getSettings().setLoadWithOverviewMode(true);
        wvPolicy.getSettings().setUseWideViewPort(false);
        wvPolicy.setWebViewClient(new WebViewClient());

        fetchPolicyFromFirestore();
    }

    // ── Fetch ─────────────────────────────────────────────────────────────────

    private void fetchPolicyFromFirestore() {
        FirebaseFirestore.getInstance()
            .collection("siteContent")
            .document("privacyPolicy")
            .get()
            .addOnSuccessListener(this::onPolicyFetched)
            .addOnFailureListener(e -> loadFallback());
    }

    private void onPolicyFetched(DocumentSnapshot doc) {
        if (!doc.exists()) { loadFallback(); return; }

        String content     = doc.getString("content");
        String lastUpdated = doc.getString("lastUpdated");

        if (content == null || content.trim().isEmpty()) { loadFallback(); return; }

        if (lastUpdated != null) tvLastUpdated.setText("Last updated: " + lastUpdated);
        renderHtml(content);
    }

    private void loadFallback() {
        tvLastUpdated.setText("Last updated: April 29, 2026");
        renderHtml(FALLBACK_POLICY_HTML);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void renderHtml(String html) {
        if (isFinishing()) return;

        // Wrap in a styled HTML shell that respects the dark/light surface color
        String textColor   = isDarkMode() ? "#E6EDF3" : "#1A1A1A";
        String secondColor = isDarkMode() ? "#8B949E" : "#555555";

        String fullHtml = "<html><head><meta name='viewport' content='width=device-width'>"
                + "<style>"
                + "body { font-family: sans-serif; color:" + textColor + "; font-size:14px;"
                + "       line-height:1.7; background:transparent; margin:0; padding:0; }"
                + "h2 { color:" + textColor + "; font-size:16px; margin-top:20px; margin-bottom:6px; }"
                + "h3 { color:" + secondColor + "; font-size:14px; margin-top:14px; margin-bottom:4px; }"
                + "p  { color:" + secondColor + "; margin:0 0 12px 0; }"
                + "ul { color:" + secondColor + "; padding-left:20px; }"
                + "li { margin-bottom:6px; }"
                + "a  { color:#1ABCD6; text-decoration:none; }"
                + "</style></head><body>"
                + html
                + "</body></html>";

        wvPolicy.loadDataWithBaseURL(null, fullHtml, "text/html", "UTF-8", null);
        policyLoading.setVisibility(View.GONE);
        policyScroll.setVisibility(View.VISIBLE);
    }

    private boolean isDarkMode() {
        int nightModeFlags = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    // ── Fallback content ──────────────────────────────────────────────────────
    // This is shown if Firestore fetch fails or the document is missing.
    // To update the live policy, just update the Firestore document — no code change needed.

    private static final String FALLBACK_POLICY_HTML =
        "<h2>1. Information We Collect</h2>"
      + "<p>MediConnect collects personal information you provide when registering and using the app, "
      + "including your name, email address, phone number, date of birth, and health appointment data.</p>"

      + "<h2>2. How We Use Your Information</h2>"
      + "<ul>"
      + "<li>To create and manage your patient account.</li>"
      + "<li>To facilitate appointment bookings with healthcare providers.</li>"
      + "<li>To send you appointment confirmations and reminders.</li>"
      + "<li>To improve our services and user experience.</li>"
      + "<li>To respond to your support requests.</li>"
      + "</ul>"

      + "<h2>3. Data Storage & Security</h2>"
      + "<p>Your data is stored securely using Google Firebase services. We use industry-standard "
      + "encryption and access controls to protect your information. Only you and your chosen "
      + "healthcare providers can access your appointment and health data.</p>"

      + "<h2>4. Sharing of Information</h2>"
      + "<p>We do not sell or rent your personal data. Information may be shared with:</p>"
      + "<ul>"
      + "<li>Healthcare providers you book appointments with.</li>"
      + "<li>Service providers who help operate our platform (e.g., cloud storage).</li>"
      + "<li>Authorities when required by law.</li>"
      + "</ul>"

      + "<h2>5. Your Rights</h2>"
      + "<p>You have the right to access, correct, or delete your personal data at any time. "
      + "You can update your profile information from the Profile tab or contact us at "
      + "<a href='mailto:support@mediconnect.ph'>support@mediconnect.ph</a>.</p>"

      + "<h2>6. Cookies & Tracking</h2>"
      + "<p>MediConnect does not use advertising cookies or third-party tracking services. "
      + "Analytics are limited to app crash reports and performance monitoring to improve reliability.</p>"

      + "<h2>7. Children's Privacy</h2>"
      + "<p>Our service is not directed at children under 13. If you believe a child has provided "
      + "personal information, please contact us immediately so we can remove it.</p>"

      + "<h2>8. Changes to This Policy</h2>"
      + "<p>We may update this Privacy Policy periodically. Changes will be reflected in the "
      + "\"Last updated\" date shown above. Continued use of MediConnect after changes constitutes "
      + "acceptance of the updated policy.</p>"

      + "<h2>9. Contact Us</h2>"
      + "<p>For privacy-related concerns, please contact:<br>"
      + "Email: <a href='mailto:privacy@mediconnect.ph'>privacy@mediconnect.ph</a><br>"
      + "Address: MediConnect Philippines, Inc.</p>";
}
