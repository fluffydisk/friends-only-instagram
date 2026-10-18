package dev.friendsonly.patch.runtime;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Own first-run welcome, followed by the original native app and its real authentication. */
public final class WelcomeActivity extends Activity {
    private static final String PREFS = "friends_only_welcome";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!Intent.ACTION_MAIN.equals(getIntent().getAction()) || getIntent().getData() != null
                || (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("seen", false)
                && !getIntent().getBooleanExtra("show_welcome", false))) {
            continueToInstagram(false);
            return;
        }
        DesignSystem.Colors colors = new DesignSystem.Colors(QuietChrome.isDarkTheme(getWindow().getDecorView()));
        DesignSystem.window(getWindow(), colors);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(colors.canvas);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        int gutter = DesignSystem.dp(this, 28);
        body.setPadding(gutter, DesignSystem.dp(this, 32), gutter, DesignSystem.dp(this, 32));
        TextView brand = DesignSystem.label(this, "Friends Only", 18, colors.accent, true);
        DesignSystem.add(body, brand, 0);
        TextView title = DesignSystem.label(this, DesignSystem.text(this,
                "Arkadaşlarına\nzaman ayır.", "Make time\nfor your friends.", "Zeit für\ndeine Freunde."), 36, colors.text, true);
        DesignSystem.heading(title);
        DesignSystem.add(body, title, 36);
        DesignSystem.add(body, DesignSystem.label(this, DesignSystem.text(this,
                "Takip ettiğin insanlar, hikâyeler ve sohbetler.\nKendi hızında.",
                "People you follow, stories and conversations.\nAt your own pace.",
                "Menschen, denen du folgst, Stories und Gespräche.\nIn deinem Tempo."), 17, colors.secondary, false), 20);
        TextView connect = DesignSystem.button(this, DesignSystem.text(this,
                "Instagram ile bağlan", "Connect with Instagram", "Mit Instagram verbinden"), colors, true);
        connect.setOnClickListener(view -> continueToInstagram(true));
        DesignSystem.add(body, connect, 40);
        DesignSystem.add(body, DesignSystem.label(this, DesignSystem.text(this,
                "Mevcut Instagram hesabın kullanılır. Giriş ve güvenlik kontrolleri Instagram'ın kendi ekranlarında tamamlanır. Friends Only bağımsız bir projedir; Meta'nın resmî uygulaması değildir.",
                "Uses your existing Instagram account. Sign-in and security checks stay on Instagram's own screens. Friends Only is an independent project, not an official Meta app.",
                "Du nutzt dein bestehendes Instagram-Konto. Anmeldung und Sicherheitsprüfungen erfolgen auf Instagrams eigenen Seiten. Friends Only ist ein unabhängiges Projekt und keine offizielle Meta-App."), 14, colors.secondary, false), 24);
        scroll.addView(body);
        // Respect Android 15+ edge-to-edge, display cutouts and gesture bars without fixed sizes.
        if (android.os.Build.VERSION.SDK_INT >= 30) scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        setContentView(scroll);
        scroll.requestApplyInsets();
    }

    private void continueToInstagram(boolean markSeen) {
        // Forward data, action and extras unchanged to the genuine native entry point.
        // No incoming link, login result or notification payload is read or logged here.
        Intent nativeEntry = new Intent(getIntent());
        nativeEntry.removeExtra("show_welcome");
        nativeEntry.setComponent(new ComponentName(getPackageName(), "com.instagram.mainactivity.InstagramMainActivity"));
        nativeEntry.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(nativeEntry);
            if (markSeen) getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("seen", true).apply();
            finish();
        } catch (RuntimeException failure) {
            // A changed component must leave an actionable screen, not hide a failed login.
            android.widget.Toast.makeText(this, DesignSystem.text(this,
                    "Uygulama açılamadı. Bu sürümü bildir.", "Could not open the app. Report this build.",
                    "Die App konnte nicht geöffnet werden. Bitte diese Version melden."), android.widget.Toast.LENGTH_LONG).show();
        }
    }
}
