/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.glgallery;

import android.graphics.Rect;
import android.graphics.RectF;

import com.hippo.glview.anim.AlphaAnimation;
import com.hippo.glview.glrenderer.GLCanvas;
import com.hippo.glview.glrenderer.Texture;
import com.hippo.glview.image.ImageTexture;
import com.hippo.glview.view.GLView;
import com.hippo.yorozuya.AnimationUtils;
import com.hippo.yorozuya.MathUtils;

import java.util.Arrays;

class ImageView extends GLView implements ImageTexture.Callback {
    public static final int SCALE_ORIGIN = 0;
    public static final int SCALE_FIT_WIDTH = 1;
    public static final int SCALE_FIT_HEIGHT = 2;
    public static final int SCALE_FIT = 3;
    public static final int SCALE_FIXED = 4;
    public static final int START_POSITION_TOP_LEFT = 0;
    public static final int START_POSITION_TOP_RIGHT = 1;
    public static final int START_POSITION_BOTTOM_LEFT = 2;
    public static final int START_POSITION_BOTTOM_RIGHT = 3;
    public static final int START_POSITION_CENTER = 4;
    private static final long ALPHA_ANIMATION_DURING = 300L;
    private final RectF mDst = new RectF();
    private final RectF mSrcActual = new RectF();
    private final RectF mDstActual = new RectF();
    private final Rect mValidRect = new Rect();
    private final AlphaAnimation mAlphaAnimation;
    private ImageTexture mImageTexture;
    private int mTextureWidth;
    private int mTextureHeight;
    private int mScaleMode = SCALE_FIT;
    private int mStartPosition = START_POSITION_TOP_RIGHT;
    private float mScaleValue = 1.0f;
    private float mScale = 1.0f;
    private float mScaleMin = 0.1f;
    private float mScaleMax = 10.0f;
    private boolean mScaleOffsetDirty = true;
    private boolean mPositionInRootDirty = true;
    private boolean mEnableCustomPlace = false;

    public ImageView() {
        mAlphaAnimation = new AlphaAnimation(0.0f, 1.0f);
        mAlphaAnimation.setDuration(ALPHA_ANIMATION_DURING);
        mAlphaAnimation.setInterpolator(AnimationUtils.FAST_SLOW_INTERPOLATOR);
    }

    @Override
    protected int getSuggestedMinimumWidth() {
        return Math.max(super.getSuggestedMinimumWidth(),
                mImageTexture == null ? 0 : mTextureWidth);
    }

    @Override
    protected int getSuggestedMinimumHeight() {
        return Math.max(super.getSuggestedMinimumHeight(),
                mImageTexture == null ? 0 : mTextureHeight);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        if (mImageTexture == null) {
            super.onMeasure(widthSpec, heightSpec);
        } else {
            float ratio = (float) mTextureWidth / mTextureHeight;
            int widthSize = MeasureSpec.getSize(widthSpec);
            int heightSize = MeasureSpec.getSize(heightSpec);
            int widthMode = MeasureSpec.getMode(widthSpec);
            int heightMode = MeasureSpec.getMode(heightSpec);
            int measureWidth = -1;
            int measureHeight = -1;

            if (widthMode == MeasureSpec.EXACTLY) {
                measureWidth = widthSize;
                if (heightMode == MeasureSpec.EXACTLY) {
                    measureHeight = heightSize;
                } else {
                    measureHeight = (int) (widthSize / ratio);
                    if (heightMode == MeasureSpec.AT_MOST) {
                        measureHeight = Math.min(measureHeight, heightSize);
                    }
                }
            } else if (heightMode == MeasureSpec.EXACTLY) {
                measureHeight = heightSize;
                measureWidth = (int) (heightSize * ratio);
                if (widthMode == MeasureSpec.AT_MOST) {
                    measureWidth = Math.min(measureWidth, widthSize);
                }
            }

            if (measureWidth == -1 || measureHeight == -1) {
                super.onMeasure(widthSpec, heightSpec);
            } else {
                setMeasuredSize(measureWidth, measureHeight);
            }
        }
    }

    @Override
    protected void onSizeChanged(int newW, int newH, int oldW, int oldH) {
        mScaleOffsetDirty = true;
        mPositionInRootDirty = true;
        updateScaleLimits();
    }

    private void updateScaleLimits() {
        if (mImageTexture == null) return;
        int viewW = getWidth();
        int viewH = getHeight();
        if (viewW <= 0 || viewH <= 0) return;

        float fitScale = Math.min((float) viewW / mTextureWidth, (float) viewH / mTextureHeight);
        mScaleMin = Math.min(0.5f * fitScale, 0.2f);
        mScaleMax = Math.max(4.0f * fitScale, 2.0f);
    }

    @Override
    protected void onPositionInRootChanged(int x, int y, int oldX, int oldY) {
        mPositionInRootDirty = true;

        if (mImageTexture != null) {
            getValidRect(mValidRect);
            if (!mValidRect.isEmpty()) {
                mImageTexture.start();
            } else {
                mImageTexture.stop();
            }
        }
    }

    public void getScaleDefault(float[] scaleDefault) {
        if (mImageTexture == null) {
            return;
        }

        scaleDefault[0] = 1.0f;
        scaleDefault[1] = (float) getWidth() / mTextureWidth;
        scaleDefault[2] = (float) getHeight() / mTextureHeight;
        scaleDefault[3] = Math.max(scaleDefault[1], scaleDefault[2]) * 2;

        scaleDefault[0] = MathUtils.clamp(scaleDefault[0], mScaleMin, mScaleMax);
        scaleDefault[1] = MathUtils.clamp(scaleDefault[1], mScaleMin, mScaleMax);
        scaleDefault[2] = MathUtils.clamp(scaleDefault[2], mScaleMin, mScaleMax);
        scaleDefault[3] = MathUtils.clamp(scaleDefault[3], mScaleMin, mScaleMax);

        Arrays.sort(scaleDefault);
    }

    public ImageTexture getImageTexture() {
        return mImageTexture;
    }

    public void setImageTexture(ImageTexture imageTexture) {
        // Remove callback
        if (mImageTexture != null) {
            mImageTexture.setCallback(null);
            mImageTexture.stop();
        }

        int oldTextureWidth = mTextureWidth;
        int oldTextureHeight = mTextureHeight;

        mImageTexture = imageTexture;

        if (imageTexture != null) {
            imageTexture.setCallback(this);
            mTextureWidth = imageTexture.getWidth();
            mTextureHeight = imageTexture.getHeight();
            // Avoid zero and negative
            if (mTextureWidth <= 0) {
                mTextureWidth = 1;
            }
            if (mTextureHeight <= 0) {
                mTextureHeight = 1;
            }
            updateScaleLimits();

            // Start alpha animation, do not show animation for image has no valid rect
            getValidRect(mValidRect);
            if (!mValidRect.isEmpty()) {
                startAnimation(mAlphaAnimation, true);
                mImageTexture.start();
            }
        } else {
            mTextureWidth = 1;
            mTextureHeight = 1;
        }

        mScaleOffsetDirty = true;
        mPositionInRootDirty = true;

        if (oldTextureWidth != mTextureWidth || oldTextureHeight != mTextureHeight) {
            requestLayout();
        }
    }

    public boolean isLoaded() {
        return mImageTexture != null;
    }

    public boolean canFling() {
        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
            if (mScaleOffsetDirty) {
                return false;
            }
        }

        return mDst.left < 0.0f || mDst.top < 0.0f || mDst.right > getWidth() || mDst.bottom > getHeight();
    }

    public int getMaxDx() {
        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
            if (mScaleOffsetDirty) {
                return 0;
            }
        }
        return Math.max(0, -(int) mDst.left);
    }

    public int getMinDx() {
        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
            if (mScaleOffsetDirty) {
                return 0;
            }
        }
        return Math.min(0, getWidth() - (int) mDst.right);
    }

    public int getMaxDy() {
        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
            if (mScaleOffsetDirty) {
                return 0;
            }
        }
        return Math.max(0, -(int) mDst.top);
    }

    public int getMinDy() {
        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
            if (mScaleOffsetDirty) {
                return 0;
            }
        }
        return Math.min(0, getHeight() - (int) mDst.bottom);
    }

    public float getScale() {
        return mScale;
    }

    /**
     * If target is shorter then screen, make it in screen center. If target is
     * longer then parent, make sure target fill parent over
     */
    private void adjustPosition() {
        RectF dst = mDst;
        int screenWidth = getWidth();
        int screenHeight = getHeight();
        float targetWidth = dst.width();
        float targetHeight = dst.height();

        if (targetWidth > screenWidth) {
            float fixXOffset = dst.left;
            if (fixXOffset > 0) {
                dst.left -= fixXOffset;
                dst.right -= fixXOffset;
            } else if ((fixXOffset = screenWidth - dst.right) > 0) {
                dst.left += fixXOffset;
                dst.right += fixXOffset;
            }
        } else {
            float left = (screenWidth - targetWidth) / 2;
            dst.offsetTo(left, dst.top);
        }
        if (targetHeight > screenHeight) {
            float fixYOffset = dst.top;
            if (fixYOffset > 0) {
                dst.top -= fixYOffset;
                dst.bottom -= fixYOffset;
            } else if ((fixYOffset = screenHeight - dst.bottom) > 0) {
                dst.top += fixYOffset;
                dst.bottom += fixYOffset;
            }
        } else {
            float top = (screenHeight - targetHeight) / 2;
            dst.offsetTo(dst.left, top);
        }
    }

    public void setScaleOffset(int scaleMode, int startPosition, float scaleValue) {
        mScaleMode = scaleMode;
        mStartPosition = startPosition;
        mScaleValue = scaleValue;

        int screenWidth = getWidth();
        int screenHeight = getHeight();

        if (mImageTexture == null || screenWidth == 0 || screenHeight == 0) {
            mScaleOffsetDirty = true;
            return;
        }

        if (mEnableCustomPlace) {
            mScaleOffsetDirty = false;
            return;
        }

        int textureWidth = mTextureWidth;
        int textureHeight = mTextureHeight;

        // Set scale
        float targetWidth;
        float targetHeight;
        switch (scaleMode) {
            case SCALE_ORIGIN -> {
                mScale = 1.0f;
                targetWidth = textureWidth;
                targetHeight = textureHeight;
            }
            case SCALE_FIT_WIDTH -> {
                mScale = (float) screenWidth / textureWidth;
                targetWidth = screenWidth;
                targetHeight = textureHeight * mScale;
            }
            case SCALE_FIT_HEIGHT -> {
                mScale = (float) screenHeight / textureHeight;
                targetWidth = textureWidth * mScale;
                targetHeight = screenHeight;
            }
            case SCALE_FIT -> {
                float scaleX = (float) screenWidth / textureWidth;
                float scaleY = (float) screenHeight / textureHeight;
                if (scaleX < scaleY) {
                    mScale = scaleX;
                    targetWidth = screenWidth;
                    targetHeight = textureHeight * scaleX;
                } else {
                    mScale = scaleY;
                    targetWidth = textureWidth * scaleY;
                    targetHeight = screenHeight;
                }
            }
            default -> {
                mScale = scaleValue;
                targetWidth = textureWidth * scaleValue;
                targetHeight = textureHeight * scaleValue;
            }
        }

        // adjust scale, not too big, not too small
        if (mScale < mScaleMin) {
            mScale = mScaleMin;
            targetWidth = textureWidth * mScaleMin;
            targetHeight = textureHeight * mScaleMin;
        } else if (mScale > mScaleMax) {
            mScale = mScaleMax;
            targetWidth = textureWidth * mScaleMax;
            targetHeight = textureHeight * mScaleMax;
        }

        // Set mDst.left and mDst.right
        RectF dst = mDst;
        switch (startPosition) {
            case START_POSITION_TOP_LEFT -> {
                dst.left = 0;
                dst.top = 0;
            }
            case START_POSITION_TOP_RIGHT -> {
                dst.left = screenWidth - targetWidth;
                dst.top = 0;
            }
            case START_POSITION_BOTTOM_LEFT -> {
                dst.left = 0;
                dst.top = screenHeight - targetHeight;
            }
            case START_POSITION_BOTTOM_RIGHT -> {
                dst.left = screenWidth - targetWidth;
                dst.top = screenHeight - targetHeight;
            }
            default -> {
                dst.left = (screenWidth - targetWidth) / 2;
                dst.top = (screenHeight - targetHeight) / 2;
            }
        }

        // Set mDst.right and mDst.bottom
        dst.right = dst.left + targetWidth;
        dst.bottom = dst.top + targetHeight;

        // adjust position
        adjustPosition();

        mScaleOffsetDirty = false;
        mPositionInRootDirty = true;
    }

    public void scroll(int dx, int dy, int[] remain) {
        // Only work after layout
        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        }
        if (mScaleOffsetDirty) {
            remain[0] = dx;
            remain[1] = dy;
            return;
        }

        RectF dst = mDst;
        int screenWidth = getWidth();
        int screenHeight = getHeight();
        float targetWidth = dst.width();
        float targetHeight = dst.height();

        if (targetWidth > screenWidth) {
            dst.left -= dx;
            dst.right -= dx;

            float fixXOffset = dst.left;
            if (fixXOffset > 0) {
                dst.left -= fixXOffset;
                dst.right -= fixXOffset;
                remain[0] = -(int) fixXOffset;
            } else if ((fixXOffset = screenWidth - dst.right) > 0) {
                dst.left += fixXOffset;
                dst.right += fixXOffset;
                remain[0] = (int) fixXOffset;
            } else {
                remain[0] = 0;
            }
        } else {
            remain[0] = dx;
        }
        if (targetHeight > screenHeight) {
            dst.top -= dy;
            dst.bottom -= dy;

            float fixYOffset = dst.top;
            if (fixYOffset > 0) {
                dst.top -= fixYOffset;
                dst.bottom -= fixYOffset;
                remain[1] = -(int) fixYOffset;
            } else if ((fixYOffset = screenHeight - dst.bottom) > 0) {
                dst.top += fixYOffset;
                dst.bottom += fixYOffset;
                remain[1] = (int) fixYOffset;
            } else {
                remain[1] = 0;
            }
        } else {
            remain[1] = dy;
        }

        if (dx != remain[0] || dy != remain[1]) {
            mPositionInRootDirty = true;
            invalidate();
        }
    }

    public void scale(float focusX, float focusY, float scale) {
        // Only work after layout
        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        }
        if (mScaleOffsetDirty) {
            return;
        }

        if ((mScale == mScaleMax && scale >= 1.0f) || (mScale == mScaleMin && scale < 1.0f)) {
            return;
        }

        float newScale = mScale * scale;
        newScale = MathUtils.clamp(newScale, mScaleMin, mScaleMax);
        mScale = newScale;
        RectF dst = mDst;
        float left = (focusX - ((focusX - dst.left) * scale));
        float top = (focusY - ((focusY - dst.top) * scale));
        dst.set(left, top,
                (left + (mImageTexture.getWidth() * newScale)),
                (top + (mImageTexture.getHeight() * newScale)));

        // adjust position
        adjustPosition();

        mPositionInRootDirty = true;
        invalidate();
    }

    private void applyPositionInRoot() {
        int width = mImageTexture.getWidth();
        int height = mImageTexture.getHeight();
        RectF dst = mDst;
        RectF dstActual = mDstActual;
        RectF srcActual = mSrcActual;

        dstActual.set(dst);
        getValidRect(mValidRect);
        float vL = mValidRect.left;
        float vT = mValidRect.top;
        float vR = mValidRect.right;
        float vB = mValidRect.bottom;

        if (dst.left >= vL && dst.right <= vR && dst.top >= vT && dst.bottom <= vB) {
            // Fast Path: fully visible
            srcActual.set(0, 0, width, height);
            // dstActual is already set to dst
        } else if (dstActual.intersect(vL, vT, vR, vB)) {
            float dW = dst.width();
            float dH = dst.height();
            srcActual.left = (dstActual.left - dst.left) * width / dW;
            srcActual.right = (dstActual.right - dst.left) * width / dW;
            srcActual.top = (dstActual.top - dst.top) * height / dH;
            srcActual.bottom = (dstActual.bottom - dst.top) * height / dH;
        } else {
            // Can't be seen, set src and dst empty
            srcActual.setEmpty();
            dstActual.setEmpty();
        }

        mPositionInRootDirty = false;
    }

    @Override
    public void onRender(GLCanvas canvas) {
        Texture texture = mImageTexture;
        if (texture == null) {
            return;
        }

        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        }

        if (mPositionInRootDirty) {
            applyPositionInRoot();
        }

        if (!mSrcActual.isEmpty()) {
            texture.draw(canvas, mSrcActual, mDstActual);
        }
    }

    @Override
    public void invalidateImageTexture(ImageTexture who) {
        invalidate();
    }

    public void setCustomPlace(RectF dst) {
        mEnableCustomPlace = true;
        mDst.set(dst);
        mScaleOffsetDirty = false;
        mPositionInRootDirty = true;
        invalidate();
    }

    public void disableCustomPlace() {
        if (mEnableCustomPlace) {
            mEnableCustomPlace = false;
            mScaleOffsetDirty = true;
            requestLayout();
        }
    }
}
