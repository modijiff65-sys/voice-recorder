package com.nauz.voicerecorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** A lightweight live waveform / idle animation strip. */
public class WaveformView extends View {

    private static final int BARS = 48;

    private final float[] levels = new float[BARS];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;

    private boolean live = false;
    private float phase = 0f;

    private final Runnable idleTick = new Runnable() {
        @Override
        public void run() {
            phase += 0.055f;
            invalidate();
            if (!live) postOnAnimation(this);
        }
    };

    public WaveformView(Context context) {
        this(context, null);
    }

    public WaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.FILL);
    }

    public void setLive(boolean isLive) {
        this.live = isLive;
        removeCallbacks(idleTick);
        if (isLive) {
            for (int i = 0; i < BARS; i++) levels[i] = 0f;
        } else {
            postOnAnimation(idleTick);
        }
        invalidate();
    }

    public void pushLevel(float value) {
        if (!live) return;
        float v = Math.max(0f, Math.min(1f, value));
        System.arraycopy(levels, 1, levels, 0, BARS - 1);
        levels[BARS - 1] = v;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!live) postOnAnimation(idleTick);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(idleTick);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0f || h <= 0f) return;

        float gap = 3f * density;
        float barW = (w - gap * (BARS - 1)) / BARS;
        if (barW <= 0f) return;

        float mid = h / 2f;
        float radius = barW / 2f;

        for (int i = 0; i < BARS; i++) {
            float bh;
            int color;
            if (live) {
                bh = Math.max(3f * density, levels[i] * h * 0.92f);
                int alpha = 140 + (int) (levels[i] * 115f);
                color = Color.argb(alpha, 124, 92, 255);
            } else {
                float wob = (float) (Math.sin(phase + i * 0.42f) * 0.5f + 0.5f);
                bh = 2f * density + wob * 5f * density;
                color = Color.argb(0x22, 255, 255, 255);
            }
            paint.setColor(color);
            float x = i * (barW + gap);
            rect.set(x, mid - bh / 2f, x + barW, mid + bh / 2f);
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
    }
}
