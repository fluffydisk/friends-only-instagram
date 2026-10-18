package dev.friendsonly.patch.runtime;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Text treatment over native feed buttons; original listeners, state and accessibility stay native. */
final class FeedActionLabel extends Drawable {
    private static final WeakHashMap<View, WeakReference<FeedActionLabel>> LABELS = new WeakHashMap<>();
    private final WeakReference<ImageView> button;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String text;
    private final float density, textSize;
    private DesignSystem.Colors colors;

    private FeedActionLabel(ImageView view, String text) {
        button = new WeakReference<>(view);
        this.text = text;
        density = view.getResources().getDisplayMetrics().density;
        textSize = 14 * view.getResources().getDisplayMetrics().scaledDensity;
        paint.setTypeface(DesignSystem.font(view.getContext(), true));
    }

    static void apply(View view, String name, DesignSystem.Colors colors) {
        if (!(view instanceof ImageView)) return;
        if (view.getResources().getConfiguration().fontScale > 1.3f) return;
        String label;
        if ("row_feed_button_like".equals(name)) label = DesignSystem.text(view.getContext(), "Beğen", "Like", "Mag ich");
        else if ("row_feed_button_comment".equals(name)) label = DesignSystem.text(view.getContext(), "Yorum", "Reply", "Antwort");
        else if ("row_feed_button_share".equals(name)) label = DesignSystem.text(view.getContext(), "Gönder", "Send", "Senden");
        else return;
        WeakReference<FeedActionLabel> reference = LABELS.get(view);
        FeedActionLabel drawable = reference == null ? null : reference.get();
        if (drawable == null) {
            drawable = new FeedActionLabel((ImageView) view, label);
            LABELS.put(view, new WeakReference<>(drawable));
            view.getOverlay().add(drawable);
        }
        // Keep four actions within the available row: three labels and the native Save button.
        int width = DesignSystem.dp(view.getContext(), 88);
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null && params.width > 0 && params.width < width) {
            params.width = width;
            view.setLayoutParams(params);
        }
        drawable.colors = colors;
        drawable.setBounds(0, 0, view.getWidth(), view.getHeight());
        drawable.invalidateSelf();
    }

    @Override public void draw(Canvas canvas) {
        ImageView view = button.get();
        if (view == null || colors == null) return;
        paint.setTextSize(textSize);
        // If a native/server layout refuses the wider button, leave its native icon intact.
        float icon = 16 * density, gap = 6 * density;
        float width = paint.measureText(text) + icon + gap;
        if (width + 8 * density > getBounds().width() || getBounds().height() < 24 * density) return;
        paint.setColor(colors.canvas);
        canvas.drawRect(getBounds(), paint);
        float left = getBounds().exactCenterX() - width / 2f;
        Drawable nativeIcon = view.getDrawable();
        if (nativeIcon != null) {
            Rect bounds = nativeIcon.getBounds();
            if (bounds.width() > 0 && bounds.height() > 0) {
                int save = canvas.save();
                canvas.translate(left, getBounds().exactCenterY() - icon / 2f);
                canvas.scale(icon / bounds.width(), icon / bounds.height());
                canvas.translate(-bounds.left, -bounds.top);
                nativeIcon.draw(canvas); // Preserve the actual native liked/unliked drawable state.
                canvas.restoreToCount(save);
            }
        }
        paint.setColor(colors.text);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        canvas.drawText(text, left + icon + gap,
                getBounds().exactCenterY() - (metrics.ascent + metrics.descent) / 2f, paint);
    }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
