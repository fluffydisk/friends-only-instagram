package dev.friendsonly.patch.runtime;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Complete Friends Only settings surface; never inspects account or message data. */
final class FocusSettings {
    private FocusSettings() {}

    static AlertDialog create(final Activity activity, final FocusConfig config) {
        DesignSystem.Colors colors = new DesignSystem.Colors(QuietChrome.isDarkTheme(activity.getWindow().getDecorView()));
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        int gutter = DesignSystem.dp(activity, 24);
        body.setPadding(gutter, DesignSystem.dp(activity, 24), gutter, DesignSystem.dp(activity, 32));
        TextView brand = DesignSystem.label(activity, "Friends Only", 14, colors.secondary, true);
        DesignSystem.add(body, brand, 0);
        TextView title = DesignSystem.label(activity, DesignSystem.text(activity, "Ayarlar", "Settings", "Einstellungen"), 28, colors.text, true);
        DesignSystem.heading(title);
        DesignSystem.add(body, title, 12);
        section(body, colors, DesignSystem.text(activity, "Akışın", "Your feed", "Dein Feed"));
        Switch follow = new Switch(activity);
        follow.setText(DesignSystem.text(activity, "Yalnızca takip ettiklerim", "Only people I follow", "Nur Konten, denen ich folge"));
        follow.setTextSize(16);
        follow.setTextColor(colors.text);
        follow.setTypeface(DesignSystem.font(activity, true));
        follow.setMinHeight(DesignSystem.dp(activity, 56));
        follow.setPadding(0, DesignSystem.dp(activity, 12), 0, DesignSystem.dp(activity, 12));
        follow.setChecked(config.followingOnly());
        follow.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},
                new int[]{colors.accent, colors.secondary}));
        follow.setOnCheckedChangeListener((button, checked) -> config.set(FocusConfig.FOLLOWING_ONLY, checked));
        DesignSystem.add(body, follow, 4);
        note(body, colors, DesignSystem.text(activity,
                "Değiştirdikten sonra akışı yenile. Takip bilgisi doğrulanamayan gönderiler gizlenir; bu yüzden akış kısa veya boş olabilir. Filtreyi kapatırsan önerilen gönderiler görünebilir.",
                "Refresh after changing this. Posts without confirmed following information are hidden, so the feed may be short or empty. Turning the filter off can show recommendations.",
                "Danach den Feed aktualisieren. Beiträge ohne bestätigten Folgestatus werden ausgeblendet; der Feed kann dadurch kurz oder leer sein. Ohne Filter können Empfehlungen erscheinen."));
        section(body, colors, DesignSystem.text(activity, "Videolar", "Videos", "Videos"));
        note(body, colors, DesignSystem.text(activity,
                "Akış videosuna basılı tut: 2× hız. Bırak: normal hız. Video bitince aynı video baştan oynar.",
                "Hold a feed video for 2× speed. Release for normal speed. When it ends, the same video repeats.",
                "Feed-Video gedrückt halten: 2× Tempo. Loslassen: normales Tempo. Am Ende wiederholt sich dasselbe Video."));
        section(body, colors, DesignSystem.text(activity, "Görünüm", "Appearance", "Darstellung"));
        note(body, colors, DesignSystem.text(activity,
                "Sade açık ve koyu renkler. Bu ekran uygulamanın mevcut temasını izler.",
                "Quiet light and dark colors. This screen follows the app's current theme.",
                "Ruhige helle und dunkle Farben. Diese Seite folgt dem aktuellen App-Design."));
        section(body, colors, DesignSystem.text(activity, "Hesap ve gizlilik", "Account and privacy", "Konto und Datenschutz"));
        note(body, colors, DesignSystem.text(activity,
                "Hesabın ve mesajların Instagram altyapısında kalır. Friends Only ayrı bir sosyal ağ değildir. Giriş ve güvenlik kontrolleri Instagram tarafından yürütülür.",
                "Your account and messages remain on Instagram's infrastructure. Friends Only is not a separate social network. Instagram handles sign-in and security checks.",
                "Konto und Nachrichten bleiben auf Instagrams Infrastruktur. Friends Only ist kein separates soziales Netzwerk. Instagram übernimmt Anmeldung und Sicherheitsprüfungen."));
        TextView account = DesignSystem.button(activity, DesignSystem.text(activity,
                "Hesabı tarayıcıda yönet", "Manage account in browser", "Konto im Browser verwalten"), colors, false);
        account.setOnClickListener(view -> openBrowser(activity, "https://accountscenter.instagram.com/"));
        DesignSystem.add(body, account, 16);
        TextView privacy = DesignSystem.button(activity, DesignSystem.text(activity,
                "Instagram gizlilik bilgisi", "Instagram privacy information", "Instagram-Datenschutzinformationen"), colors, false);
        privacy.setOnClickListener(view -> openBrowser(activity, "https://privacycenter.instagram.com/"));
        DesignSystem.add(body, privacy, 8);
        section(body, colors, DesignSystem.text(activity, "Bu sürüm", "This build", "Diese Version"));
        note(body, colors, "Friends Only " + version(activity));
        TextView report = DesignSystem.button(activity, DesignSystem.text(activity,
                "Teknik bilgileri kopyala", "Copy technical information", "Technische Angaben kopieren"), colors, false);
        report.setOnClickListener(view -> {
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Friends Only", "Friends Only " + version(activity)
                        + "\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL
                        + "\nAndroid: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")"));
                Toast.makeText(activity, DesignSystem.text(activity, "Kopyalandı", "Copied", "Kopiert"), Toast.LENGTH_SHORT).show();
            }
        });
        DesignSystem.add(body, report, 12);
        note(body, colors, DesignSystem.text(activity, "Yalnızca sürüm ve cihaz bilgisi kopyalanır. Hesap ve sohbet içeriği eklenmez.",
                "Copies only build and device information. Account and conversation content are not included.",
                "Kopiert nur Versions- und Geräteangaben. Keine Konto- oder Gesprächsinhalte."));
        TextView done = DesignSystem.button(activity, DesignSystem.text(activity, "Geri dön", "Go back", "Zurück"), colors, true);
        DesignSystem.add(body, done, 28);
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(colors.canvas);
        scroll.addView(body);
        AlertDialog dialog = new AlertDialog.Builder(activity).create();
        dialog.setView(scroll, 0, 0, 0, 0);
        done.setOnClickListener(view -> dialog.dismiss());
        return dialog;
    }

    static void onShown(AlertDialog dialog, Activity activity) {
        Window window = dialog.getWindow();
        if (window == null) return;
        DesignSystem.Colors colors = new DesignSystem.Colors(QuietChrome.isDarkTheme(activity.getWindow().getDecorView()));
        window.setBackgroundDrawable(DesignSystem.surface(activity, colors.canvas, 0));
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setGravity(Gravity.CENTER);
        DesignSystem.window(window, colors);
        if (Build.VERSION.SDK_INT >= 30) {
            View root = window.getDecorView();
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
            root.requestApplyInsets();
        }
    }

    private static void section(LinearLayout parent, DesignSystem.Colors colors, String value) {
        TextView title = DesignSystem.label(parent.getContext(), value, 18, colors.text, true);
        DesignSystem.heading(title);
        DesignSystem.add(parent, title, 28);
    }

    private static void note(LinearLayout parent, DesignSystem.Colors colors, String value) {
        DesignSystem.add(parent, DesignSystem.label(parent.getContext(), value, 14, colors.secondary, false), 8);
    }

    private static String version(Activity activity) {
        try { return DesignSystem.VERSION + " (" + activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionCode + ")"; }
        catch (android.content.pm.PackageManager.NameNotFoundException ignored) { return "unknown"; }
    }

    private static void openBrowser(Activity activity, String address) {
        try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(address)).addCategory(Intent.CATEGORY_BROWSABLE)); }
        catch (RuntimeException failure) {
            Toast.makeText(activity, DesignSystem.text(activity, "Tarayıcı açılamadı.", "Could not open browser.", "Browser konnte nicht geöffnet werden."), Toast.LENGTH_LONG).show();
        }
    }
}
