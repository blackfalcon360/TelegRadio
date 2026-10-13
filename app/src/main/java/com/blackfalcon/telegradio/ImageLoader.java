package com.blackfalcon.telegradio;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads small station logos. */
public final class ImageLoader {
    private ImageLoader() { }

    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(150);
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void round(ImageView iv) {
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setOval(0, 0, v.getWidth(), v.getHeight()); }
        });
        iv.setClipToOutline(true);
    }

    /** Shows the logo; keeps the view's current picture if there is no logo. */
    public static void load(final ImageView iv, final String url) {
        iv.setTag(url);
        iv.setImageDrawable(null);
        if (url == null || !url.startsWith("http")) return;
        Bitmap b = CACHE.get(url);
        if (b != null) { iv.setImageBitmap(b); return; }
        POOL.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(7000);
                c.setReadTimeout(7000);
                c.setInstanceFollowRedirects(true);
                InputStream in = c.getInputStream();
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n, total = 0;
                while ((n = in.read(buf)) > 0 && total < 600000) { bo.write(buf, 0, n); total += n; }
                in.close();
                c.disconnect();
                byte[] data = bo.toByteArray();
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, o);
                int sample = 1;
                while (o.outWidth / sample > 300 || o.outHeight / sample > 300) sample *= 2;
                o = new BitmapFactory.Options();
                o.inSampleSize = sample;
                Bitmap raw = BitmapFactory.decodeByteArray(data, 0, data.length, o);
                final Bitmap bm;
                if (raw != null) {
                    bm = Bitmap.createBitmap(raw.getWidth(), raw.getHeight(), Bitmap.Config.ARGB_8888);
                    Canvas cv = new Canvas(bm);
                    cv.drawColor(0xFFF2F6FC);
                    cv.drawBitmap(raw, 0, 0, null);
                } else {
                    bm = null;
                }
                if (bm != null) {
                    CACHE.put(url, bm);
                    MAIN.post(() -> { if (url.equals(iv.getTag())) iv.setImageBitmap(bm); });
                }
            } catch (Exception ignored) { }
        });
    }
}
