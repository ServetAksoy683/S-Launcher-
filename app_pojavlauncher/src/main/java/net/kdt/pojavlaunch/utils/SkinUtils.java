package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * S-Launcher skin helpers: stores imported skins and renders faces / full characters from a skin atlas.
 */
public final class SkinUtils {
    private static final int GOLD = 0xFFFFC400;

    private SkinUtils() {}

    // ------------------------------------------------------------------ storage

    public static File skinFile(String username) {
        String safe = username == null ? "unknown" : username.replaceAll("[^A-Za-z0-9_.-]", "_");
        return new File(new File(Tools.DIR_GAME_HOME, "S-Skins"), safe + ".png");
    }

    @Nullable
    public static Bitmap loadSkin(String username) {
        File f = skinFile(username);
        if (!f.exists()) return null;
        Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
        return isValidSkin(b) ? b : null;
    }

    public static boolean saveSkin(String username, Bitmap skin) {
        File f = skinFile(username);
        //noinspection ResultOfMethodCallIgnored
        f.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(f)) {
            return skin.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException e) {
            return false;
        }
    }

    public static void deleteSkin(String username) {
        //noinspection ResultOfMethodCallIgnored
        skinFile(username).delete();
    }

    /** Standard skins: 64x64 or legacy 64x32 (HD multiples of that are accepted too) */
    public static boolean isValidSkin(@Nullable Bitmap b) {
        if (b == null) return false;
        int w = b.getWidth(), h = b.getHeight();
        return w >= 64 && w % 64 == 0 && (h == w || h * 2 == w);
    }

    // ---------------------------------------------------------------- rendering

    private static void region(Canvas c, Bitmap skin, int s, int sx, int sy, int w, int h,
                               int dx, int dy, boolean mirror, Paint p) {
        Rect src = new Rect(sx * s, sy * s, (sx + w) * s, (sy + h) * s);
        RectF dst = new RectF(dx * s, dy * s, (dx + w) * s, (dy + h) * s);
        if (mirror) {
            c.save();
            c.scale(-1f, 1f, dst.centerX(), dst.centerY());
            c.drawBitmap(skin, src, dst, p);
            c.restore();
        } else {
            c.drawBitmap(skin, src, dst, p);
        }
    }

    /** Head (with hat layer) of the skin, scaled up without smoothing */
    public static Bitmap headFromSkin(Bitmap skin, int size) {
        int s = skin.getWidth() / 64;
        Bitmap head = Bitmap.createBitmap(8 * s, 8 * s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(head);
        Paint p = new Paint();
        region(c, skin, s, 8, 8, 8, 8, 0, 0, false, p);
        region(c, skin, s, 40, 8, 8, 8, 0, 0, false, p);
        return Bitmap.createScaledBitmap(head, size, size, false);
    }

    /** Front view of the whole character, height = targetHeight pixels */
    public static Bitmap characterFromSkin(Bitmap skin, int targetHeight) {
        int s = skin.getWidth() / 64;
        boolean modern = skin.getHeight() == skin.getWidth();
        Bitmap sprite = Bitmap.createBitmap(16 * s, 32 * s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(sprite);
        Paint p = new Paint();
        // base layer
        region(c, skin, s, 8, 8, 8, 8, 4, 0, false, p);          // head
        region(c, skin, s, 20, 20, 8, 12, 4, 8, false, p);       // body
        region(c, skin, s, 44, 20, 4, 12, 0, 8, false, p);       // right arm
        region(c, skin, s, 4, 20, 4, 12, 4, 20, false, p);       // right leg
        if (modern) {
            region(c, skin, s, 36, 52, 4, 12, 12, 8, false, p);  // left arm
            region(c, skin, s, 20, 52, 4, 12, 8, 20, false, p);  // left leg
            // outer layer
            region(c, skin, s, 40, 8, 8, 8, 4, 0, false, p);
            region(c, skin, s, 20, 36, 8, 12, 4, 8, false, p);
            region(c, skin, s, 44, 36, 4, 12, 0, 8, false, p);
            region(c, skin, s, 52, 52, 4, 12, 12, 8, false, p);
            region(c, skin, s, 4, 36, 4, 12, 4, 20, false, p);
            region(c, skin, s, 4, 52, 4, 12, 8, 20, false, p);
        } else {
            region(c, skin, s, 44, 20, 4, 12, 12, 8, true, p);   // legacy: mirrored right arm
            region(c, skin, s, 4, 20, 4, 12, 8, 20, true, p);    // legacy: mirrored right leg
            region(c, skin, s, 40, 8, 8, 8, 4, 0, false, p);
        }
        int w = Math.max(1, targetHeight / 2);
        return Bitmap.createScaledBitmap(sprite, w, targetHeight, false);
    }

    // ------------------------------------------------------------------ avatars

    public static Bitmap circle(Bitmap src, int size, boolean ring) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float ringW = ring ? size / 14f : 0f;
        float r = size / 2f - ringW;
        c.drawCircle(size / 2f, size / 2f, r, p);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        c.drawBitmap(Bitmap.createScaledBitmap(src, size, size, false), 0, 0, p);
        if (ring) {
            Paint rp = new Paint(Paint.ANTI_ALIAS_FLAG);
            rp.setStyle(Paint.Style.STROKE);
            rp.setStrokeWidth(ringW);
            rp.setColor(GOLD);
            c.drawCircle(size / 2f, size / 2f, size / 2f - ringW / 2f, rp);
        }
        return out;
    }

    /** Gold disc with the first letter, used when no skin is available */
    public static Bitmap placeholder(@Nullable String name, int size) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF141414);
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(size / 14f);
        p.setColor(GOLD);
        c.drawCircle(size / 2f, size / 2f, size / 2f - size / 28f, p);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(size * 0.5f);
        String letter = (name == null || name.isEmpty()) ? "?" : name.substring(0, 1).toUpperCase();
        c.drawText(letter, size / 2f, size / 2f - (p.descent() + p.ascent()) / 2f, p);
        return out;
    }

    /** The bundled default (Steve-like) skin, used when the player has no skin of their own */
    public static Bitmap defaultSkin(Context context) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        return BitmapFactory.decodeResource(context.getResources(), R.drawable.s_default_skin, options);
    }

    /** Imported skin if any, otherwise the default one */
    public static Bitmap skinOrDefault(Context context, String username) {
        Bitmap skin = loadSkin(username);
        return skin != null ? skin : defaultSkin(context);
    }

    /** Round avatar: imported skin, cached online head, then the default skin. Only "no account" shows a "?" */
    public static Bitmap avatarFor(Context context, @Nullable MinecraftAccount account, int size) {
        if (account == null) return placeholder(null, size);
        try {
            Bitmap skin = loadSkin(account.username);
            if (skin != null) return circle(headFromSkin(skin, 8 * 16), size, true);
            Bitmap head = account.getSkinFace();
            if (head != null) return circle(head, size, true);
            return circle(headFromSkin(defaultSkin(context), 8 * 16), size, true);
        } catch (Exception ignored) {
            return placeholder(account.username, size);
        }
    }
}
