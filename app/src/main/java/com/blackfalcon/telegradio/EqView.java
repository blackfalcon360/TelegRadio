package com.blackfalcon.telegradio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.util.Random;

/** Little dancing bars while a station plays. */
public class EqView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random();
    private final float[] h = new float[16];
    private boolean on = false;

    public EqView(Context c) {
        super(c);
        for (int i = 0; i < h.length; i++) h[i] = 0.1f;
    }

    public void setActive(boolean a) {
        on = a;
        if (a) postInvalidateOnAnimation(); else invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), hh = getHeight();
        float bw = w / (h.length * 1.6f);
        for (int i = 0; i < h.length; i++) {
            float target = on ? 0.12f + rnd.nextFloat() * 0.88f : 0.08f;
            h[i] += (target - h[i]) * (on ? 0.28f : 0.2f);
            float x = (i + 0.3f) * (w / h.length);
            p.setColor(0xFF29B6F6);
            p.setAlpha(on ? 230 : 90);
            c.drawRoundRect(x, hh - h[i] * hh, x + bw, hh, bw / 2, bw / 2, p);
        }
        if (on) postInvalidateDelayed(70);
    }
}
