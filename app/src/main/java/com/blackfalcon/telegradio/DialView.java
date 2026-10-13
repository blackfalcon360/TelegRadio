package com.blackfalcon.telegradio;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

/** A radio dial: drag it sideways to tune through the stations. */
public class DialView extends View {

    public interface Listener {
        /** pos = position in the station list (fractional); speed = how fast the dial is turning. */
        void onMove(float pos, float speed);
        void onSettle(int index);
    }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Station> pool = new ArrayList<>();
    private float pos = 0f;
    private float lastX, lastMoveMs;
    private boolean dragging = false;
    private VelocityTracker vt;
    private ValueAnimator anim;
    private Listener listener;

    public DialView(Context c) {
        super(c);
        tp.setTextAlign(Paint.Align.CENTER);
    }

    public void setListener(Listener l) { listener = l; }
    public float getPos() { return pos; }

    public void setPool(List<Station> list, int startIndex) {
        pool = list;
        pos = Math.max(0, Math.min(Math.max(0, pool.size() - 1), startIndex));
        invalidate();
    }

    public void setPos(float v) {
        pos = Math.max(0f, Math.min(Math.max(0, pool.size() - 1), v));
        invalidate();
    }

    private float spacing() { return Math.min(getWidth(), getHeight() * 3.2f) * 0.085f + 30f; }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight(), cx = w / 2f;
        // glass panel
        p.setShader(new LinearGradient(0, 0, 0, h, 0xFF16233B, 0xFF0A1220, Shader.TileMode.CLAMP));
        p.setStyle(Paint.Style.FILL);
        c.drawRoundRect(0, 0, w, h, h * 0.12f, h * 0.12f, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setColor(0xFF2A3B5E);
        c.drawRoundRect(1, 1, w - 1, h - 1, h * 0.12f, h * 0.12f, p);

        int n = pool.size();
        if (n == 0) {
            tp.setColor(0xFF8A9BB8);
            tp.setTextSize(h * 0.15f);
            tp.setTypeface(Typeface.DEFAULT);
            c.drawText("Loading stations\u2026", cx, h * 0.55f, tp);
            return;
        }
        float sp = spacing();
        int from = Math.max(0, (int) Math.floor(pos - cx / sp) - 1), to = Math.min(n - 1, (int) Math.ceil(pos + cx / sp) + 1);
        float baseY = h * 0.72f;
        for (int i = from; i <= to; i++) {
            float x = cx + (i - pos) * sp;
            boolean major = i % 5 == 0;
            float len = major ? h * 0.30f : h * 0.18f;
            float dist = Math.abs(i - pos);
            int alpha = (int) Math.max(40, 255 - dist * 28);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(major ? 3.2f : 2f);
            p.setColor(Color.argb(alpha, 190, 215, 255));
            c.drawLine(x, baseY, x, baseY - len, p);
            if (dist < 2.6f) {
                tp.setTypeface(dist < 0.5f ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                tp.setColor(Color.argb(alpha, 255, 255, 255));
                tp.setTextSize(h * 0.115f);
                String nm = pool.get(i).name;
                if (nm.length() > 14) nm = nm.substring(0, 13) + "\u2026";
                c.drawText(nm, x, baseY + h * 0.17f, tp);
            }
        }
        // station number
        int near = Math.round(pos);
        tp.setTypeface(Typeface.DEFAULT_BOLD);
        tp.setColor(0xFFFF9800);
        tp.setTextSize(h * 0.15f);
        c.drawText((near + 1) + " / " + n, cx, h * 0.2f, tp);

        // needle
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5f);
        p.setColor(0xFFFF9800);
        c.drawLine(cx, h * 0.26f, cx, h * 0.94f, p);
        p.setStrokeWidth(14f);
        p.setColor(0x33FF9800);
        c.drawLine(cx, h * 0.26f, cx, h * 0.94f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFF9800);
        c.drawCircle(cx, h * 0.26f, 8f, p);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (pool.isEmpty()) return true;
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (anim != null) anim.cancel();
                getParent().requestDisallowInterceptTouchEvent(true);
                dragging = true;
                lastX = e.getX();
                lastMoveMs = e.getEventTime();
                vt = VelocityTracker.obtain();
                vt.addMovement(e);
                return true;
            case MotionEvent.ACTION_MOVE: {
                vt.addMovement(e);
                float dx = e.getX() - lastX;
                float dt = Math.max(1f, e.getEventTime() - lastMoveMs);
                lastX = e.getX();
                lastMoveMs = e.getEventTime();
                float delta = -dx / spacing();
                pos = Math.max(0f, Math.min(pool.size() - 1, pos + delta));
                invalidate();
                if (listener != null) listener.onMove(pos, Math.abs(delta) / dt * 40f);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                dragging = false;
                vt.addMovement(e);
                vt.computeCurrentVelocity(1000);
                float vx = vt.getXVelocity();
                vt.recycle();
                vt = null;
                float target = pos - vx / spacing() * 0.28f;
                settleTo(Math.max(0, Math.min(pool.size() - 1, Math.round(target))));
                return true;
            }
        }
        return true;
    }

    /** Glide the dial to a station and tell the listener. */
    public void settleTo(final int index) {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(pos, index);
        anim.setDuration(Math.min(900, 200 + (long) (Math.abs(index - pos) * 60)));
        anim.setInterpolator(new DecelerateInterpolator(1.6f));
        final boolean[] cancelled = {false};
        anim.addUpdateListener(a -> {
            pos = (float) a.getAnimatedValue();
            invalidate();
            if (listener != null) listener.onMove(pos, 0.5f);
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationCancel(android.animation.Animator a) { cancelled[0] = true; }
            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (cancelled[0]) return;
                pos = index;
                invalidate();
                if (listener != null) listener.onSettle(index);
            }
        });
        anim.start();
    }
}
