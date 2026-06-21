package com.hippo.glgallery;

import android.graphics.Color;
import android.graphics.RectF;
import android.text.TextPaint;

import com.hippo.glview.glrenderer.GLCanvas;
import com.hippo.glview.glrenderer.StringTexture;
import com.hippo.glview.view.GLView;

public class DebugHud extends GLView {
    private static final int MAX_LINES = 8;
    private static final int FONT_SIZE = 22;
    private static final int FONT_SIZE_HEADER = 24;
    private static final int PADDING_H = 12;
    private static final int PADDING_TOP = 8;
    private static final int PADDING_BOTTOM = 6;
    private static final int LINE_INTERVAL = 1;
    private static final int SECTION_GAP = 5;
    private static final int SEPARATOR_HEIGHT = 1;
    private static final int SEPARATOR_COLOR = 0x40FFFFFF;
    private static final int HEADER_LINE_INDEX = 0;

    private final StringTexture[] mTextures = new StringTexture[MAX_LINES];
    private int mLineCount;
    private int mWidthLimit;
    private final RectF mSrc = new RectF();
    private final RectF mDst = new RectF();

    public DebugHud() {
        setBackgroundColor(0xCC000000);
    }

    public void setWidthLimit(int limit) {
        mWidthLimit = limit;
    }

    public void update(String[] lines) {
        for (int i = 0; i < MAX_LINES; i++) {
            if (mTextures[i] != null) {
                mTextures[i].recycle();
                mTextures[i] = null;
            }
        }
        mLineCount = 0;

        if (lines == null || lines.length == 0) {
            requestLayout();
            invalidate();
            return;
        }

        // ShadowLayer(2f) adds ~4px to rendered texture width; compensate
        float limit = mWidthLimit > 0 ? (float) mWidthLimit - 4 : 1000f;

        for (int li = 0; li < lines.length && mLineCount < MAX_LINES; li++) {
            String text = lines[li];
            if (text == null) continue;

            float fontSize = (mLineCount == HEADER_LINE_INDEX) ? FONT_SIZE_HEADER : FONT_SIZE;
            TextPaint paint = StringTexture.getDefaultPaint(fontSize, Color.WHITE);
            float[] measuredWidth = new float[1];

            while (text.length() > 0 && mLineCount < MAX_LINES) {
                int visibleChars = paint.breakText(text, true, limit, measuredWidth);
                if (visibleChars <= 0) visibleChars = Math.min(1, text.length());

                String chunk = text.substring(0, visibleChars);
                StringTexture tex = StringTexture.newInstance(chunk, fontSize, Color.WHITE);
                mTextures[mLineCount] = tex;
                mLineCount++;

                text = text.substring(visibleChars);
                // Subsequent wrapped lines use normal size
                if (text.length() > 0) {
                    fontSize = FONT_SIZE;
                    paint = StringTexture.getDefaultPaint(FONT_SIZE, Color.WHITE);
                }
            }
        }

        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        if (mLineCount == 0) {
            setMeasuredSize(0, 0);
            return;
        }
        int maxW = 0;
        int totalH = PADDING_TOP + PADDING_BOTTOM;
        for (int i = 0; i < mLineCount; i++) {
            if (mTextures[i] != null) {
                int w = mTextures[i].getWidth();
                int h = mTextures[i].getHeight();
                if (w > maxW) maxW = w;
                totalH += h;
                if (i > 0) totalH += LINE_INTERVAL;
                if (i == HEADER_LINE_INDEX + 1) totalH += SECTION_GAP + SEPARATOR_HEIGHT;
            }
        }
        int measuredW = PADDING_H * 2 + maxW;
        int specMode = GLView.MeasureSpec.getMode(widthSpec);
        int specSize = GLView.MeasureSpec.getSize(widthSpec);
        if (specMode == GLView.MeasureSpec.AT_MOST && measuredW > specSize) {
            measuredW = specSize;
        }
        setMeasuredSize(measuredW, totalH);
    }

    @Override
    public void onRender(GLCanvas canvas) {
        if (mLineCount == 0 || getWidth() <= 0 || getHeight() <= 0) return;

        int contentWidth = getWidth() - PADDING_H * 2;
        float y = PADDING_TOP;

        for (int i = 0; i < mLineCount; i++) {
            if (i == HEADER_LINE_INDEX + 1) {
                // Section separator
                y += (SECTION_GAP - LINE_INTERVAL) / 2f;
                canvas.fillRect(PADDING_H, y, contentWidth, SEPARATOR_HEIGHT, SEPARATOR_COLOR);
                y += SEPARATOR_HEIGHT + (SECTION_GAP - LINE_INTERVAL + 1) / 2f;
            }

            StringTexture tex = mTextures[i];
            if (tex == null) continue;

            int w = tex.getWidth();
            int h = tex.getHeight();
            mSrc.set(0, 0, w, h);
            mDst.set(PADDING_H, y, PADDING_H + w, y + h);
            tex.draw(canvas, mSrc, mDst);
            y += h + LINE_INTERVAL;
        }
    }
}
