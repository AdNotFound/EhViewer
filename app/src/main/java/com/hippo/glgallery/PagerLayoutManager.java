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

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.animation.Interpolator;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.util.BitSet;

import com.hippo.glview.anim.Animation;
import com.hippo.glview.view.GLView;
import com.hippo.glview.widget.GLProgressView;
import com.hippo.glview.widget.GLTextureView;
import com.hippo.yorozuya.AnimationUtils;
import com.hippo.yorozuya.AssertUtils;
import com.hippo.yorozuya.MathUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

class PagerLayoutManager extends GalleryView.LayoutManager implements GalleryPageView.OnLoadedListener {
    public static final int MODE_LEFT_TO_RIGHT = 0;
    public static final int MODE_RIGHT_TO_LEFT = 1;
    private static final String TAG = PagerLayoutManager.class.getSimpleName();
    private static final Interpolator SMOOTH_SCROLLER_INTERPOLATOR = t -> {
        t -= 1.0f;
        return t * t * t * t * t + 1.0f;
    };
    private final int[] mScrollRemain = new int[2];
    private final float[] mScaleDefault = new float[4];
    private final SmoothScroller mSmoothScroller;
    private final PageFling mPageFling;
    private final SmoothScaler mSmoothScaler;
    private final BitSet mSpreads = new BitSet();
    private final Rect mTempRect = new Rect();
    private final android.graphics.Matrix mPairMatrix = new android.graphics.Matrix();
    private final android.graphics.RectF mBaseRectPrimary = new android.graphics.RectF();
    private final android.graphics.RectF mBaseRectSecondary = new android.graphics.RectF();
    private final android.graphics.RectF mDstRectPrimary = new android.graphics.RectF();
    private final android.graphics.RectF mDstRectSecondary = new android.graphics.RectF();
    private GalleryView.Adapter mAdapter;
    private GLProgressView mProgress;
    private String mErrorStr;
    private GLTextureView mErrorView;
    private GalleryPageView mPrevious;
    private GalleryPageView mCurrent;
    private GalleryPageView mNext;
    private GalleryPageView mPreviousSecondary;
    private GalleryPageView mCurrentSecondary;
    private GalleryPageView mNextSecondary;
    private boolean mDoublePageMode = false;
    private boolean mDoublePageOffset = false;
    @Mode
    private int mMode = MODE_RIGHT_TO_LEFT;
    private int mScaleMode;
    private int mStartPosition;
    private float mScaleValue;
    private int mOffset;
    private boolean mCanScrollBetweenPages = false;
    private boolean mStopAnimationFinger;
    private int mInterval;
    // Current index
    private int mIndex;

    public PagerLayoutManager(Context context, @NonNull GalleryView galleryView,
            int scaleMode, int startPoint, float scaleValue, int interval) {
        super(galleryView);

        mScaleMode = scaleMode;
        mStartPosition = startPoint;
        mScaleValue = scaleValue;

        mInterval = interval;
        mSmoothScroller = new SmoothScroller();
        mPageFling = new PageFling(context);
        mSmoothScaler = new SmoothScaler();
    }

    public void setDoublePageMode(boolean enabled) {
        if (mDoublePageMode == enabled) {
            return;
        }
        mDoublePageMode = enabled;
        if (mAdapter != null) {
            cancelAllAnimations();
            removeProgress();
            removeErrorView();
            removeAllPages();
            resetParameters();
            mGalleryView.requestFill();
        }
    }

    public void setDoublePageOffset(boolean enabled) {
        if (mDoublePageOffset == enabled) {
            return;
        }
        mDoublePageOffset = enabled;
        if (mAdapter != null) {
            cancelAllAnimations();
            removeProgress();
            removeErrorView();
            removeAllPages();
            resetParameters();
            mGalleryView.requestFill();
        }
    }

    public void setInterval(int interval) {
        if (mInterval == interval) {
            return;
        }
        mInterval = interval;

        if (mAdapter != null) {
            int index = getInternalCurrentIndex();
            GalleryView.Adapter adapter = onDetach();
            onAttach(adapter);
            setCurrentIndex(index);
            mGalleryView.requestFill();
        }
    }

    private void resetParameters() {
        mOffset = 0;
        mCanScrollBetweenPages = false;
        mStopAnimationFinger = false;
    }

    // Helper: get the slot number for an index
    // With offset ON: slot 0 = index 0, slot 1 = indices 1-2, slot 2 = indices
    // 3-4...
    // With offset OFF: slot 0 = indices 0-1, slot 1 = indices 2-3...
    private int getSlotForIndex(int index) {
        if (mDoublePageOffset) {
            if (index == 0)
                return 0;
            return (index + 1) / 2; // 1→1, 2→1, 3→2, 4→2...
        } else {
            return index / 2; // 0→0, 1→0, 2→1, 3→1...
        }
    }

    // Helper: get the start index of a slot
    private int getSlotStart(int slot) {
        if (mDoublePageOffset) {
            if (slot == 0)
                return 0;
            return slot * 2 - 1; // slot 1→1, slot 2→3, slot 3→5...
        } else {
            return slot * 2; // slot 0→0, slot 1→2, slot 2→4...
        }
    }

    private int getPairStart(int index) {
        if (!mDoublePageMode)
            return index;

        // If the index itself is a spread, it's always its own start
        if (mSpreads.get(index))
            return index;

        int slot = getSlotForIndex(index);
        int slotStart = getSlotStart(slot);

        // If the slot-start is a spread, the second page of the slot becomes its own
        // solo start
        if (slotStart != index && mSpreads.get(slotStart)) {
            return index;
        }
        return slotStart;
    }

    public int getPairSize(int index) {
        if (!mDoublePageMode)
            return 1;
        if (mSpreads.get(index))
            return 1;
        if (mDoublePageOffset && index == 0)
            return 1;
        if (index >= mAdapter.size() - 1)
            return 1;
        if (mSpreads.get(index + 1))
            return 1;

        // Check if this is a slot-start position
        int slot = getSlotForIndex(index);
        int slotStart = getSlotStart(slot);

        if (index == slotStart) {
            // This is a slot-start, can pair
            return 2;
        } else {
            // This is the second page of a slot
            // If the slot-start was a spread, this becomes solo
            if (mSpreads.get(slotStart)) {
                return 1;
            }
            // Otherwise, we shouldn't be called for non-start indices
            // (getPairStart should have returned slotStart)
            return 1;
        }
    }

    private boolean cancelAllAnimations() {
        boolean running = mSmoothScroller.isRunning() ||
                mPageFling.isRunning() ||
                mSmoothScaler.isRunning();
        mSmoothScroller.cancel();
        mPageFling.cancel();
        mSmoothScaler.cancel();
        return running;
    }

    public void setMode(@Mode int mode) {
        if (mMode == mode) {
            return;
        }

        mMode = mode;
        if (mAdapter != null) {
            // It is attached, refill
            // Cancel all animations
            cancelAllAnimations();
            // Remove all view
            removeProgress();
            removeErrorView();
            removeAllPages();
            // Reset parameters
            resetParameters();
            // Request fill
            mGalleryView.requestFill();
        }
    }

    public void setScaleMode(int scaleMode) {
        if (mScaleMode == scaleMode) {
            return;
        }
        mScaleMode = scaleMode;

        if (mCurrent != null) {
            mCurrent.getImageView().setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        }
        if (mPrevious != null) {
            mPrevious.getImageView().setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        }
        if (mNext != null) {
            mNext.getImageView().setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        }
        if (mPreviousSecondary != null) {
            mPreviousSecondary.getImageView().setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        }
        if (mCurrentSecondary != null) {
            mCurrentSecondary.getImageView().setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        }
        if (mNextSecondary != null) {
            mNextSecondary.getImageView().setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        }
    }

    public void setStartPosition(int startPosition) {
        if (mStartPosition == startPosition) {
            return;
        }
        mStartPosition = startPosition;

        if (mCurrent != null) {
            mCurrent.getImageView().setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        }
        if (mPrevious != null) {
            mPrevious.getImageView().setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        }
        if (mNext != null) {
            mNext.getImageView().setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        }
        if (mPreviousSecondary != null) {
            mPreviousSecondary.getImageView().setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        }
        if (mCurrentSecondary != null) {
            mCurrentSecondary.getImageView().setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        }
        if (mNextSecondary != null) {
            mNextSecondary.getImageView().setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        }
    }

    @Override
    public void onAttach(GalleryView.Adapter adapter) {
        AssertUtils.assertNull("The PagerLayoutManager is attached", mAdapter);
        AssertUtils.assertNotNull("The adapter is null", adapter);
        mAdapter = adapter;
        mSpreads.clear();
        // Reset parameters
        resetParameters();
    }

    private void removeProgress() {
        if (mProgress != null) {
            mGalleryView.removeComponent(mProgress);
            mProgress = null;
        }
    }

    private void removeErrorView() {
        if (mErrorView != null) {
            mGalleryView.removeComponent(mErrorView);
            mGalleryView.releaseErrorView(mErrorView);
            mErrorView = null;
            mErrorStr = null;
        }
    }

    private void removePage(@NonNull GalleryPageView page) {
        page.removeOnLoadedListener(this);
        page.getImageView().disableCustomPlace();
        mGalleryView.removeComponent(page);
        mAdapter.unbind(page);
        mGalleryView.releasePage(page);
    }

    private void removeAllPages() {
        // Remove gallery view
        if (mPrevious != null) {
            removePage(mPrevious);
            mPrevious = null;
        }
        if (mCurrent != null) {
            removePage(mCurrent);
            mCurrent = null;
        }
        if (mNext != null) {
            removePage(mNext);
            mNext = null;
        }
        if (mPreviousSecondary != null) {
            removePage(mPreviousSecondary);
            mPreviousSecondary = null;
        }
        if (mCurrentSecondary != null) {
            removePage(mCurrentSecondary);
            mCurrentSecondary = null;
        }
        if (mNextSecondary != null) {
            removePage(mNextSecondary);
            mNextSecondary = null;
        }
    }

    @Override
    public GalleryView.Adapter onDetach() {
        AssertUtils.assertNotNull("The PagerLayoutManager is not attached", mAdapter);

        // Cancel all animations
        cancelAllAnimations();

        // Remove all view
        removeProgress();
        removeErrorView();
        removeAllPages();

        // Clear iterator
        GalleryView.Adapter adapter = mAdapter;
        mAdapter = null;

        return adapter;
    }

    private GalleryPageView getLeftPage() {
        return mMode == MODE_LEFT_TO_RIGHT ? mPrevious : mNext;
    }

    private GalleryPageView getRightPage() {
        return mMode == MODE_LEFT_TO_RIGHT ? mNext : mPrevious;
    }

    private GalleryPageView obtainPage() {
        GalleryPageView page = mGalleryView.obtainPage();
        page.addOnLoadedListener(this);
        page.getImageView().setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        return page;
    }

    @Override
    public void onLoaded(GalleryPageView page) {
        if (mGalleryView == null)
            return;

        ImageView view = page.getImageView();
        if (view != null && view.isLoaded()) {
            int w = view.getImageTexture().getWidth();
            int h = view.getImageTexture().getHeight();
            if (w > h) {
                int index = page.getIndex();
                if (index != -1 && !mSpreads.get(index)) {
                    mSpreads.set(index);
                }
            }
        }
        mGalleryView.requestFill();
    }

    private void layoutPage(GalleryPageView page, int widthSpec, int heightSpec,
            int left, int right, int bottom) {
        Rect rect = mTempRect;
        page.getValidRect(rect);
        boolean oldValid = !rect.isEmpty();
        page.measure(widthSpec, heightSpec);
        page.layout(left, 0, right, bottom);
        page.getValidRect(rect);
        boolean newValid = !rect.isEmpty();
        if (!oldValid && newValid) {
            page.getImageView().setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        }
    }

    private void calculateDoublePageRects(GalleryPageView primary, GalleryPageView secondary, boolean isRTL,
            android.graphics.RectF rectPrimary, android.graphics.RectF rectSecondary) {
        int width = mGalleryView.getWidth();
        int height = mGalleryView.getHeight();

        ImageView primaryView = primary.getImageView();

        // Dimensions of primary
        int w1 = primaryView.getImageTexture().getWidth();
        int h1 = primaryView.getImageTexture().getHeight();

        // Spread Detection - mark if spread and nullify secondary
        if (w1 > h1) {
            int index = primary.getIndex();
            if (index != -1 && !mSpreads.get(index)) {
                mSpreads.set(index);
                // No need to requestFill here, onLoaded already does it
            }
            secondary = null;
        }

        ImageView secondaryView = secondary != null ? secondary.getImageView() : null;
        float w2;
        if (secondaryView != null && secondaryView.isLoaded()) {
            int w2Raw = secondaryView.getImageTexture().getWidth();
            int h2Raw = secondaryView.getImageTexture().getHeight();

            // Check if secondary is spread
            if (w2Raw > h2Raw) {
                int sIndex = secondary.getIndex();
                if (sIndex != -1 && !mSpreads.get(sIndex)) {
                    mSpreads.set(sIndex);
                    // No need to requestFill here, onLoaded already does it
                }
                secondaryView = null;
                w2 = 0;
            } else {
                if (h1 != h2Raw) {
                    float ratio = (float) h1 / h2Raw;
                    w2 = w2Raw * ratio;
                } else {
                    w2 = w2Raw;
                }
            }
        } else {
            w2 = 0;
        }

        // Total width at "native" height h1
        float totalWidth = w1 + w2;
        float totalHeight = h1;

        // Scale to fit screen
        float sH = (float) height / totalHeight;
        float sW = (float) width / totalWidth;
        float baseScale = Math.min(sH, sW);

        float displayW1 = w1 * baseScale;
        float displayW2 = w2 * baseScale;
        float displayH = totalHeight * baseScale;

        // Base Layout (Centered)
        float startX = (width - (displayW1 + displayW2)) / 2f;
        float startY = (height - displayH) / 2f;

        if (isRTL) {
            // [Secondary] [Primary]
            rectSecondary.set(startX, startY, startX + displayW2, startY + displayH);
            rectPrimary.set(startX + displayW2, startY, startX + displayW2 + displayW1, startY + displayH);
        } else {
            // [Primary] [Secondary]
            rectPrimary.set(startX, startY, startX + displayW1, startY + displayH);
            rectSecondary.set(startX + displayW1, startY, startX + displayW1 + displayW2, startY + displayH);
        }
    }

    private void updateDoublePageLayout() {
        if (!mDoublePageMode || mCurrent == null)
            return;

        ImageView primaryView = mCurrent.getImageView();
        if (!primaryView.isLoaded()) {
            primaryView.disableCustomPlace();
            if (mCurrentSecondary != null)
                mCurrentSecondary.getImageView().disableCustomPlace();
            return;
        }

        calculateDoublePageRects(mCurrent, mCurrentSecondary,
                mMode == MODE_RIGHT_TO_LEFT, mBaseRectPrimary, mBaseRectSecondary);

        // Apply Transformation Matrix
        mPairMatrix.mapRect(mDstRectPrimary, mBaseRectPrimary);
        if (mCurrentSecondary != null && mCurrentSecondary.getImageView().isLoaded()
                && mBaseRectSecondary.width() > 0) {
            mPairMatrix.mapRect(mDstRectSecondary, mBaseRectSecondary);
            mCurrentSecondary.getImageView().setCustomPlace(mDstRectSecondary);
        } else if (mCurrentSecondary != null) {
            mCurrentSecondary.getImageView().disableCustomPlace();
        }

        // Apply to Views
        updatePagePairLayouts(mCurrent, mCurrentSecondary);
        primaryView.setCustomPlace(mDstRectPrimary);
    }

    private void updatePagePairLayouts(GalleryPageView primary, GalleryPageView secondary) {
        if (primary == null)
            return;
        boolean pLoaded = primary.getImageView().isLoaded();
        boolean sLoaded = secondary != null && secondary.getImageView().isLoaded();
        boolean isRTL = mMode == MODE_RIGHT_TO_LEFT;

        if (pLoaded && !sLoaded && secondary != null) {
            primary.setVisibility(GLView.VISIBLE);
            secondary.setVisibility(GLView.VISIBLE);
            secondary.setPagePosition(isRTL ? 4 : 3); // Move to edge
            mGalleryView.removeComponent(primary);
            mGalleryView.addComponent(primary);
        } else if (!pLoaded && sLoaded && secondary != null) {
            primary.setVisibility(GLView.VISIBLE);
            secondary.setVisibility(GLView.VISIBLE);
            primary.setPagePosition(isRTL ? 3 : 4); // Move to edge
            mGalleryView.removeComponent(secondary);
            mGalleryView.addComponent(secondary);
        } else {
            primary.setVisibility(GLView.VISIBLE);
            if (secondary != null) {
                // Hide secondary if it's a spread being incorrectly paired (before re-fill)
                // or if it has no width in the calculated layout.
                boolean isSpread = secondary.getImageView().isLoaded() && (secondary.getImageView().getImageTexture()
                        .getWidth() > secondary.getImageView().getImageTexture().getHeight());
                if (isSpread) {
                    secondary.setVisibility(GLView.GONE);
                } else {
                    secondary.setVisibility(GLView.VISIBLE);
                }
            }
        }
    }

    // Helper method to apply unified seamless layout to any page pair (for
    // neighbors)
    private void applyUnifiedLayoutToPagePair(GalleryPageView primary, GalleryPageView secondary, boolean isRTL) {
        if (primary == null)
            return;

        ImageView primaryView = primary.getImageView();
        if (!primaryView.isLoaded()) {
            primaryView.disableCustomPlace();
            if (secondary != null)
                secondary.getImageView().disableCustomPlace();
            return;
        }

        android.graphics.RectF rectPrimary = new android.graphics.RectF();
        android.graphics.RectF rectSecondary = new android.graphics.RectF();

        calculateDoublePageRects(primary, secondary,
                isRTL, rectPrimary, rectSecondary);

        // Apply to Views
        updatePagePairLayouts(primary, secondary);
        primaryView.setCustomPlace(rectPrimary);
        if (secondary != null) {
            if (secondary.getImageView().isLoaded() && rectSecondary.width() > 0) {
                secondary.getImageView().setCustomPlace(rectSecondary);
            } else {
                secondary.getImageView().disableCustomPlace();
            }
        }
    }

    @Override
    public void onFill() {
        GalleryView.Adapter adapter = mAdapter;
        GalleryView galleryView = mGalleryView;
        AssertUtils.assertNotNull("The PagerLayoutManager is not attached", adapter);

        int width = galleryView.getWidth();
        int height = galleryView.getHeight();
        int size = adapter.size();

        if (size == 0) { // Get empty, show error text
            String errorStr = galleryView.getEmptyStr();
            removeProgress();
            removeAllPages();
            if (mErrorView == null) {
                mErrorView = galleryView.obtainErrorView();
                galleryView.addComponent(mErrorView);
            }
            if (!errorStr.equals(mErrorStr)) {
                mErrorStr = errorStr;
                galleryView.bindErrorView(mErrorView, errorStr);
            }
            placeCenter(mErrorView);
            return;
        }

        removeProgress();
        removeErrorView();

        int index = mIndex;
        if (index < 0) {
            index = 0;
        } else if (index >= size) {
            index = size - 1;
        }
        if (mIndex != index) {
            mIndex = index;
            removeAllPages();
        }

        int widthSpec = GLView.MeasureSpec.makeMeasureSpec(width, GLView.MeasureSpec.EXACTLY);
        int heightSpec = GLView.MeasureSpec.makeMeasureSpec(height, GLView.MeasureSpec.EXACTLY);
        boolean isRTL = mMode == MODE_RIGHT_TO_LEFT;

        if (mDoublePageMode) {
            index = getPairStart(index);
            mIndex = index;

            // Previous
            if (index > 0) {
                int previousIndex = getPairStart(index - 1);
                if (mPrevious == null) {
                    mPrevious = obtainPage();
                    galleryView.addComponent(mPrevious);
                    adapter.bind(mPrevious, previousIndex);
                } else if (mPrevious.getIndex() != previousIndex) {
                    removePage(mPrevious);
                    mPrevious = obtainPage();
                    galleryView.addComponent(mPrevious);
                    adapter.bind(mPrevious, previousIndex);
                }
                if (getPairSize(previousIndex) > 1) {
                    if (mPreviousSecondary == null) {
                        mPreviousSecondary = obtainPage();
                        galleryView.addComponent(mPreviousSecondary);
                        adapter.bind(mPreviousSecondary, previousIndex + 1);
                    } else if (mPreviousSecondary.getIndex() != previousIndex + 1) {
                        removePage(mPreviousSecondary);
                        mPreviousSecondary = obtainPage();
                        galleryView.addComponent(mPreviousSecondary);
                        adapter.bind(mPreviousSecondary, previousIndex + 1);
                    }
                } else if (mPreviousSecondary != null) {
                    removePage(mPreviousSecondary);
                    mPreviousSecondary = null;
                }
            } else {
                if (mPrevious != null) {
                    removePage(mPrevious);
                    mPrevious = null;
                }
                if (mPreviousSecondary != null) {
                    removePage(mPreviousSecondary);
                    mPreviousSecondary = null;
                }
            }

            // Current
            if (mCurrent == null) {
                mCurrent = obtainPage();
                galleryView.addComponent(mCurrent);
                adapter.bind(mCurrent, index);
            } else if (mCurrent.getIndex() != index) {
                // Index mismatch due to spread status change - rebind
                removePage(mCurrent);
                mCurrent = obtainPage();
                galleryView.addComponent(mCurrent);
                adapter.bind(mCurrent, index);
            }
            if (getPairSize(index) > 1) {
                if (mCurrentSecondary == null) {
                    mCurrentSecondary = obtainPage();
                    galleryView.addComponent(mCurrentSecondary);
                    adapter.bind(mCurrentSecondary, index + 1);
                } else if (mCurrentSecondary.getIndex() != index + 1) {
                    removePage(mCurrentSecondary);
                    mCurrentSecondary = obtainPage();
                    galleryView.addComponent(mCurrentSecondary);
                    adapter.bind(mCurrentSecondary, index + 1);
                }
            } else if (mCurrentSecondary != null) {
                removePage(mCurrentSecondary);
                mCurrentSecondary = null;
            }

            // Next
            int nextIndex = index + getPairSize(index);
            if (nextIndex < size) {
                if (mNext == null) {
                    mNext = obtainPage();
                    galleryView.addComponent(mNext);
                    adapter.bind(mNext, nextIndex);
                } else if (mNext.getIndex() != nextIndex) {
                    removePage(mNext);
                    mNext = obtainPage();
                    galleryView.addComponent(mNext);
                    adapter.bind(mNext, nextIndex);
                }
                if (getPairSize(nextIndex) > 1) {
                    if (mNextSecondary == null) {
                        mNextSecondary = obtainPage();
                        galleryView.addComponent(mNextSecondary);
                        adapter.bind(mNextSecondary, nextIndex + 1);
                    } else if (mNextSecondary.getIndex() != nextIndex + 1) {
                        removePage(mNextSecondary);
                        mNextSecondary = obtainPage();
                        galleryView.addComponent(mNextSecondary);
                        adapter.bind(mNextSecondary, nextIndex + 1);
                    }
                } else if (mNextSecondary != null) {
                    removePage(mNextSecondary);
                    mNextSecondary = null;
                }
            } else {
                if (mNext != null) {
                    removePage(mNext);
                    mNext = null;
                }
                if (mNextSecondary != null) {
                    removePage(mNextSecondary);
                    mNextSecondary = null;
                }
            }

            // Set page info positions to avoid overlap
            if (mCurrent != null)
                mCurrent.setPagePosition(getPairSize(index) > 1 ? (isRTL ? 2 : 1) : 0);
            if (mCurrentSecondary != null)
                mCurrentSecondary.setPagePosition(isRTL ? 1 : 2);

            if (mPrevious != null) {
                int prevIndex = getPairStart(index - 1);
                mPrevious.setPagePosition(getPairSize(prevIndex) > 1 ? (isRTL ? 2 : 1) : 0);
            }
            if (mPreviousSecondary != null)
                mPreviousSecondary.setPagePosition(isRTL ? 1 : 2);

            if (mNext != null) {
                mNext.setPagePosition(getPairSize(nextIndex) > 1 ? (isRTL ? 2 : 1) : 0);
            }
            if (mNextSecondary != null)
                mNextSecondary.setPagePosition(isRTL ? 1 : 2);

            // Layout!
            GalleryPageView leftPage = getLeftPage();
            GalleryPageView rightPage = getRightPage();
            int min = rightPage == null ? 0 : -width - mInterval + 1;
            int max = leftPage == null ? 0 : width + mInterval - 1;
            mOffset = MathUtils.clamp(mOffset, min, max);
            int offset = mOffset;

            // Current (Double Page Unified)
            if (mCurrent != null) {
                // We layout both views to FULL SCREEN
                layoutPage(mCurrent, widthSpec, heightSpec, offset, offset + width, height);
                if (mCurrentSecondary != null) {
                    layoutPage(mCurrentSecondary, widthSpec, heightSpec, offset, offset + width, height);
                }
                // Apply Unified Layout
                updateDoublePageLayout();
            }

            // Neighbors - use unified seamless layout
            int prevOffset = isRTL ? offset + width + mInterval : offset - width - mInterval;
            int nextOffset = isRTL ? offset - width - mInterval : offset + width + mInterval;

            // Previous
            if (mPrevious != null) {
                layoutPage(mPrevious, widthSpec, heightSpec, prevOffset, prevOffset + width, height);
                if (mPreviousSecondary != null) {
                    layoutPage(mPreviousSecondary, widthSpec, heightSpec, prevOffset, prevOffset + width, height);
                }
                applyUnifiedLayoutToPagePair(mPrevious, mPreviousSecondary, isRTL);
            }

            // Next
            if (mNext != null) {
                layoutPage(mNext, widthSpec, heightSpec, nextOffset, nextOffset + width, height);
                if (mNextSecondary != null) {
                    layoutPage(mNextSecondary, widthSpec, heightSpec, nextOffset, nextOffset + width, height);
                }
                applyUnifiedLayoutToPagePair(mNext, mNextSecondary, isRTL);
            }

        } else {
            // Single Page Mode
            if (mPreviousSecondary != null) {
                removePage(mPreviousSecondary);
                mPreviousSecondary = null;
            }
            if (mCurrentSecondary != null) {
                removePage(mCurrentSecondary);
                mCurrentSecondary = null;
            }
            if (mNextSecondary != null) {
                removePage(mNextSecondary);
                mNextSecondary = null;
            }

            if (mCurrent == null) {
                mCurrent = obtainPage();
                galleryView.addComponent(mCurrent);
                adapter.bind(mCurrent, index);
            }
            if (mPrevious == null && index > 0) {
                mPrevious = obtainPage();
                galleryView.addComponent(mPrevious);
                adapter.bind(mPrevious, index - 1);
            } else if (mPrevious != null && index == 0) {
                removePage(mPrevious);
                mPrevious = null;
            }
            if (mNext == null && index < size - 1) {
                mNext = obtainPage();
                galleryView.addComponent(mNext);
                adapter.bind(mNext, index + 1);
            } else if (mNext != null && index == size - 1) {
                removePage(mNext);
                mNext = null;
            }

            GalleryPageView leftPage = getLeftPage();
            GalleryPageView rightPage = getRightPage();
            int min = rightPage == null ? 0 : -width - mInterval + 1;
            int max = leftPage == null ? 0 : width + mInterval - 1;
            mOffset = MathUtils.clamp(mOffset, min, max);
            int offset = mOffset;

            if (mCurrent != null) {
                mCurrent.setPagePosition(0);
                layoutPage(mCurrent, widthSpec, heightSpec, offset, width + offset, height);
            }
            if (leftPage != null) {
                leftPage.setPagePosition(0);
                layoutPage(leftPage, widthSpec, heightSpec, -mInterval - width + offset, -mInterval + offset,
                        height);
            }
            if (rightPage != null) {
                rightPage.setPagePosition(0);
                layoutPage(rightPage, widthSpec, heightSpec, width + mInterval + offset,
                        width + mInterval + width + offset, height);
            }
        }
    }

    @Override
    public void onDown() {
        mStopAnimationFinger = cancelAllAnimations();
    }

    @Override
    public void onUp() {
        if (mCurrent == null) {
            return;
        }

        // Scroll
        if (mOffset != 0) {
            int width = mGalleryView.getWidth();
            int dx;
            if (mOffset >= mInterval && getLeftPage() != null) {
                dx = mOffset - width - mInterval;
            } else if (mOffset <= -mInterval && getRightPage() != null) {
                dx = mOffset + width + mInterval;
            } else {
                dx = mOffset;
            }
            final float pageDelta = 7 * (float) Math.abs(mOffset) / (width + mInterval);
            int duration = (int) ((pageDelta + 1) * 100);
            mSmoothScroller.startSmoothScroll(dx, duration);
        }
    }

    // Helper to find page under point since getIndexUnder returns index
    private GalleryPageView findPageUnder(float x, float y) {
        int index = getIndexUnder(x, y);
        return findPageByIndex(index);
    }

    private void pagePrevious() {
        if (mIndex <= 0) {
            return;
        }

        if (mDoublePageMode) {
            mPairMatrix.reset();
            int jump = getPairSize(getPairStart(mIndex - 1));
            mIndex -= jump;
            if (mIndex < 0) {
                mIndex = 0;
            }

            if (mNextSecondary != null) {
                removePage(mNextSecondary);
            }
            if (mNext != null) {
                removePage(mNext);
            }
            mNext = mCurrent;
            mNextSecondary = mCurrentSecondary;

            mCurrent = mPrevious;
            mCurrentSecondary = mPreviousSecondary;

            mPrevious = null;
            mPreviousSecondary = null;

            if (mIndex > 0) {
                // Determine previous pair
                int prevIndex = getPairStart(mIndex - 1);
                if (prevIndex >= 0) {
                    mPrevious = obtainPage();
                    mGalleryView.addComponent(mPrevious);
                    mAdapter.bind(mPrevious, prevIndex);

                    if (getPairSize(prevIndex) > 1) {
                        mPreviousSecondary = obtainPage();
                        mGalleryView.addComponent(mPreviousSecondary);
                        mAdapter.bind(mPreviousSecondary, prevIndex + 1);
                    }
                }
            }
        } else {
            mIndex--;

            if (mNext != null) {
                removePage(mNext);
            }
            mNext = mCurrent;
            mCurrent = mPrevious;
            mPrevious = null;

            if (mIndex > 0) {
                mPrevious = obtainPage();
                mGalleryView.addComponent(mPrevious);
                mAdapter.bind(mPrevious, mIndex - 1);
            }
        }
    }

    private void pageNext() {
        GalleryView.Adapter adapter = mAdapter;
        int size = adapter.size();
        if (mIndex >= size - 1) {
            return;
        }

        if (mDoublePageMode) {
            mPairMatrix.reset();
            int jump = getPairSize(mIndex);
            mIndex += jump;
            if (mIndex >= size) {
                mIndex = getPairStart(size - 1);
            }

            // Check if we actually advanced?
            // To simplify: Just Re-Fill if logic is complex.
            // But let's support shifting for animation.

            if (mPrevious != null) {
                removePage(mPrevious);
            }
            if (mPreviousSecondary != null) {
                removePage(mPreviousSecondary);
            }

            mPrevious = mCurrent;
            mPreviousSecondary = mCurrentSecondary;

            mCurrent = mNext;
            mCurrentSecondary = mNextSecondary;
            mNext = null;
            mNextSecondary = null;

            int nextIndex = mIndex + jump; // Current index was already updated by jump
            if (mIndex + getPairSize(mIndex) < size) {
                nextIndex = mIndex + getPairSize(mIndex);
                mNext = obtainPage();
                mGalleryView.addComponent(mNext);
                adapter.bind(mNext, nextIndex);

                if (nextIndex + 1 < size && getPairSize(nextIndex) > 1) {
                    mNextSecondary = obtainPage();
                    mGalleryView.addComponent(mNextSecondary);
                    adapter.bind(mNextSecondary, nextIndex + 1);
                }
            }
        } else {
            mIndex++;

            if (mPrevious != null) {
                removePage(mPrevious);
            }
            mPrevious = mCurrent;
            mCurrent = mNext;
            mNext = null;

            if (mIndex < size - 1) {
                mNext = obtainPage();
                mGalleryView.addComponent(mNext);
                adapter.bind(mNext, mIndex + 1);
            }
        }
    }

    private void pageLeft() {
        if (mMode == MODE_LEFT_TO_RIGHT) {
            pagePrevious();
        } else {
            pageNext();
        }
    }

    private void pageRight() {
        if (mMode == MODE_LEFT_TO_RIGHT) {
            pageNext();
        } else {
            pagePrevious();
        }
    }

    private int scrollBetweenPages(int dx) {
        GalleryPageView leftPage = getLeftPage();
        GalleryPageView rightPage = getRightPage();
        int width = mGalleryView.getWidth();

        int remain;
        if (dx < 0) { // Try to show left
            int limit;
            if (leftPage == null) {
                limit = 0;
            } else {
                limit = width + mInterval;
            }

            if (dx > mOffset - limit) {
                remain = 0;
                mOffset -= dx;
            } else {
                // Go to left page if left page not null
                if (leftPage != null) {
                    pageLeft();
                }
                remain = dx + limit - mOffset;
                mOffset = 0;
            }
        } else { // Try to show right
            int limit;
            if (rightPage == null) {
                limit = 0;
            } else {
                limit = -width - mInterval;
            }

            if (dx < mOffset - limit) {
                remain = 0;
                mOffset -= dx;
            } else {
                // Go to right page if right page not null
                if (rightPage != null) {
                    pageRight();
                }
                remain = dx + limit - mOffset;
                mOffset = 0;
            }
        }

        return remain;
    }

    public void scrollInternal(float dx, float dy) {
        if (mCurrent == null) {
            return;
        }

        boolean needFill = false;
        boolean canImageScroll = true;
        int remainX = (int) dx;
        int remainY = (int) dy;

        if (mGalleryView.isFirstScroll()) {
            mCanScrollBetweenPages = Math.abs(dx) > Math.abs(dy) * 1.5;
        }

        while (remainX != 0 || remainY != 0) {
            if (mOffset == 0 && canImageScroll) {
                if (mDoublePageMode) {
                    // Unified Double Page Scroll
                    float dxF = remainX;
                    float dyF = remainY;
                    mPairMatrix.postTranslate(-dxF, -dyF);

                    // Calc Union Rect
                    android.graphics.RectF union = new android.graphics.RectF();
                    mPairMatrix.mapRect(mDstRectPrimary, mBaseRectPrimary);
                    union.set(mDstRectPrimary);
                    // Check if secondary has valid base rect (width > 0)
                    if (mBaseRectSecondary.width() > 0) {
                        mPairMatrix.mapRect(mDstRectSecondary, mBaseRectSecondary);
                        union.union(mDstRectSecondary);
                    }

                    int width = mGalleryView.getWidth();
                    int height = mGalleryView.getHeight();

                    // Fix X
                    float fixX = 0;
                    if (union.width() > width) {
                        if (union.left > 0)
                            fixX = -union.left;
                        else if (union.right < width)
                            fixX = width - union.right;
                    } else {
                        // Center
                        float cx = (width - union.width()) / 2f;
                        fixX = cx - union.left;
                    }

                    // Fix Y
                    float fixY = 0;
                    if (union.height() > height) {
                        if (union.top > 0)
                            fixY = -union.top;
                        else if (union.bottom < height)
                            fixY = height - union.bottom;
                    } else {
                        // Center
                        float cy = (height - union.height()) / 2f;
                        fixY = cy - union.top;
                    }

                    if (fixX != 0 || fixY != 0) {
                        mPairMatrix.postTranslate(fixX, fixY);
                    }

                    updateDoublePageLayout();

                    // Update remainX/Y
                    // If content is smaller than screen, we force centered (fixX undoes movement).
                    // So we consumed 0. remainX stays same.
                    // If content > screen, and we hit wall (fixX != 0).
                    // We consumed (dx - fixX?).
                    // remainX should be what's left.
                    // If fixX == dx (full revert), remainX = original.
                    // If fixX == 0, remainX = 0.
                    // So remainX = (int) -fixX.
                    // Note: fixX matches delta direction processing.

                    if (union.width() <= width) {
                        // If we didn't actually scroll image (because it fits),
                        // we shouldn't claim to have consumed it IF we want page turn.
                        // remainX is unchanged.
                    } else {
                        // We consumed all except fix part
                        remainX = (int) fixX;
                    }

                    if (union.height() <= height) {
                        remainY = (int) dy; // Unconsumed? Or just 0? Vertical scroll usually doesn't change page.
                        // If fits vertically, we usually ignore vertical drag or overscroll?
                        // Let's set 0 to avoid getting stuck in loop if vertical doesn't trigger
                        // anything.
                        remainY = 0;
                    } else {
                        remainY = (int) fixY;
                    }

                } else {
                    ImageView image = mCurrent.getImageView();
                    image.scroll(remainX, remainY, mScrollRemain);
                    remainX = mScrollRemain[0];
                    remainY = mScrollRemain[1];
                }
                canImageScroll = false;
            } else if (remainX == 0 ||
                    (getLeftPage() == null && mOffset == 0 && remainX < 0) ||
                    (getRightPage() == null && mOffset == 0 && remainX > 0)) {
                // On edge
                remainX = 0;
                remainY = 0;
            } else if (mCanScrollBetweenPages) {
                remainX = scrollBetweenPages(remainX);
                canImageScroll = true;
                needFill = true;
            } else {
                remainX = 0;
                remainY = 0;
            }
        }

        if (needFill) {
            mGalleryView.requestFill();
        }
    }

    @Override
    public void onScroll(float dx, float dy, float totalX, float totalY, float x, float y) {
        scrollInternal(dx, dy);
    }

    @Override
    public void onFling(float velocityX, float velocityY) {
        // Double Page Fling?
        // Standard fling relies on ImageView.
        // If we are in "Custom Place" mode, ImageView.fling might not work as expected
        // via standard calls.

        if (mCurrent == null || mOffset != 0 || !mCurrent.getImageView().isLoaded() ||
                !mCurrent.getImageView().canFling()) {
            return;
        }

        // If Double Page, disable fling for now or implement unified fling?
        // Disabling fling to avoid glitches during test.
        if (mDoublePageMode)
            return;

        ImageView image = mCurrent.getImageView();
        mPageFling.startFling((int) velocityX, image.getMinDx(), image.getMaxDx(),
                (int) velocityY, image.getMinDy(), image.getMaxDy());
    }

    @Override
    public boolean canScale() {
        if (mDoublePageMode)
            return mCurrent != null && mOffset == 0;
        return mCurrent != null && mOffset == 0 && mCurrent.getImageView().isLoaded();
    }

    @Override
    public void onScale(float focusX, float focusY, float scale) {
        if (mDoublePageMode) {
            mPairMatrix.postScale(scale, scale, focusX, focusY);
            updateDoublePageLayout();
            // Need to update mScaleValue for consistency?
            // mScaleValue = ... from box?
            return;
        }

        GalleryPageView page = findPageUnder(focusX, focusY);
        if (page == null || !page.getImageView().isLoaded()) {
            // Fallback to mCurrent if focused page is invalid/null to avoid crash
            if (mCurrent == null || !mCurrent.getImageView().isLoaded())
                return;
            page = mCurrent;
        }

        page.getImageView().scale(focusX, focusY, scale);
        mScaleValue = page.getImageView().getScale();
    }

    @Override
    public boolean onUpdateAnimation(long time) {
        boolean invalidate = mSmoothScroller.calculate(time);
        invalidate |= mPageFling.calculate(time);
        invalidate |= mSmoothScaler.calculate(time);
        if (invalidate) {
            mGalleryView.requestFill();
        }
        return invalidate;
    }

    @Override
    public void onDataChanged() {
        AssertUtils.assertNotNull("The PagerLayoutManager is not attached", mAdapter);

        // Cancel all animations
        cancelAllAnimations();
        // Remove all views
        removeProgress();
        removeErrorView();
        removeAllPages();
        mSpreads.clear();
        // Reset parameters
        resetParameters();
        mGalleryView.requestFill();
    }

    @Override
    public void onPageLeft() {
        pageLeft();
    }

    @Override
    public void onPageRight() {
        pageRight();
    }

    @Override
    public boolean isTapOrPressDisable() {
        return mStopAnimationFinger;
    }

    @Override
    public GalleryPageView findPageByIndex(int index) {
        if (mCurrent != null && mCurrent.getIndex() == index) {
            return mCurrent;
        }
        if (mPrevious != null && mPrevious.getIndex() == index) {
            return mPrevious;
        }
        if (mNext != null && mNext.getIndex() == index) {
            return mNext;
        }
        if (mCurrentSecondary != null && mCurrentSecondary.getIndex() == index) {
            return mCurrentSecondary;
        }
        if (mPreviousSecondary != null && mPreviousSecondary.getIndex() == index) {
            return mPreviousSecondary;
        }
        if (mNextSecondary != null && mNextSecondary.getIndex() == index) {
            return mNextSecondary;
        }
        return null;
    }

    @Override
    public int getCurrentIndex() {
        if (mCurrent != null) {
            return mCurrent.getIndex();
        } else {
            return GalleryPageView.INVALID_INDEX;
        }
    }

    @Override
    public void setCurrentIndex(int index) {
        GalleryView galleryView = mGalleryView;
        int size = mAdapter.size();
        if (size <= 0) {
            return;
        }
        if (index == mIndex || index < 0 || index >= size) {
            return;
        }

        if (mDoublePageMode) {
            mPairMatrix.reset();
            // Align to start of pair
            index = getPairStart(index);
        }

        if (index == mIndex)
            return;

        boolean isConnectedJump;
        if (mDoublePageMode) {
            isConnectedJump = (index == mIndex + getPairSize(mIndex) || index == getPairStart(mIndex - 1));
        } else {
            isConnectedJump = (index == mIndex - 1 || index == mIndex + 1);
        }

        cancelAllAnimations();
        resetParameters();

        if (isConnectedJump && mCurrent != null) {
            if (index < mIndex) {
                pagePrevious();
            } else {
                pageNext();
            }
        } else {
            mIndex = index;
            removeProgress();
            removeErrorView();
            removeAllPages();
        }
        galleryView.requestFill();
    }

    @Override
    public int getIndexUnder(float x, float y) {
        if (mDoublePageMode) {
            if (mCurrent != null) {
                if (mDstRectPrimary.contains((int) x, (int) y)) {
                    return mCurrent.getIndex();
                }
                if (mCurrentSecondary != null && mDstRectSecondary.contains((int) x, (int) y)) {
                    return mCurrentSecondary.getIndex();
                }
            }
            // In double page mode, we don't care about neighbors or raw bounds
            return GalleryPageView.INVALID_INDEX;
        }

        int intX = (int) x;
        int intY = (int) y;
        if (mCurrent != null && mCurrent.bounds().contains(intX, intY)) {
            return mCurrent.getIndex();
        } else if (mPrevious != null && mPrevious.bounds().contains(intX, intY)) {
            return mPrevious.getIndex();
        } else if (mNext != null && mNext.bounds().contains(intX, intY)) {
            return mNext.getIndex();
        } else {
            return GalleryPageView.INVALID_INDEX;
        }
    }

    public void onDoubleTapConfirmed(float x, float y) {
        if (mDoublePageMode) {
            // Zoom logic for Double Page
            // Use mPairMatrix scale
            // We can get current scale from matrix?
            // Matrix.getValues()
            float[] values = new float[9];
            mPairMatrix.getValues(values);
            float currentScale = values[android.graphics.Matrix.MSCALE_X];

            // Define jump stops?
            // Standard: 1.0 -> Fit Width/Height -> Max
            // Simplified: 1.0 -> 2.0 -> 1.0
            float endScale;
            float endTx = 0, endTy = 0;
            float startTx = values[android.graphics.Matrix.MTRANS_X];
            float startTy = values[android.graphics.Matrix.MTRANS_Y];

            if (currentScale < 1.5f) { // Zoom In
                endScale = 2.0f;
                // When zooming in, we keep translation (focus logic handles it)
                endTx = startTx;
                endTy = startTy;
            } else { // Zoom Out
                endScale = 1.0f;
                // When zooming out to 1.0, we want to reset translation to 0
                endTx = 0;
                endTy = 0;
            }

            mSmoothScaler.startSmoothScaler(x, y, currentScale, endScale, startTx, endTx, startTy, endTy, 300, null); // Target
                                                                                                                      // null
                                                                                                                      // for
                                                                                                                      // DoublePage
            return;
        }

        int index = getIndexUnder(x, y);
        GalleryPageView page = findPageByIndex(index);

        if (page == null || !page.getImageView().isLoaded()) {
            return;
        }

        float[] scales = mScaleDefault;
        ImageView image = page.getImageView();
        image.getScaleDefault(scales);
        float scale = image.getScale();
        float endScale = scales[0];
        for (float value : scales) {
            if (scale < value - 0.01f) {
                endScale = value;
                break;
            }
        }

        mSmoothScaler.startSmoothScaler(x, y, scale, endScale, 0, 0, 0, 0, 300, page);
    }

    @Override
    int getInternalCurrentIndex() {
        int currentIndex = getCurrentIndex();
        if (currentIndex == GalleryPageView.INVALID_INDEX) {
            currentIndex = mIndex;
        }
        return currentIndex;
    }

    @IntDef({ MODE_LEFT_TO_RIGHT, MODE_RIGHT_TO_LEFT })
    @Retention(RetentionPolicy.SOURCE)
    private @interface Mode {
    }

    private class SmoothScroller extends Animation {
        private int mDx;
        private int mLastX;

        public SmoothScroller() {
            setInterpolator(SMOOTH_SCROLLER_INTERPOLATOR);
        }

        public void startSmoothScroll(int dx, int duration) {
            mDx = dx;
            mLastX = 0;
            setDuration(duration);
            start();
            mGalleryView.invalidate();
        }

        @Override
        protected void onCalculate(float progress) {
            int x = (int) (mDx * progress);
            int offsetX = x - mLastX;
            while (offsetX != 0) {
                int oldOffsetX = offsetX;
                offsetX = scrollBetweenPages(offsetX);
                // Avoid loop infinitely
                if (offsetX == oldOffsetX) {
                    break;
                } else {
                    mGalleryView.requestFill();
                }
            }
            mLastX = x;
        }
    }

    private class PageFling extends Fling {
        private final int[] mTemp = new int[2];
        private int mDx;
        private int mDy;
        private int mLastX;
        private int mLastY;

        public PageFling(Context context) {
            super(context);
        }

        public void startFling(int velocityX, int minX, int maxX,
                int velocityY, int minY, int maxY) {
            mDx = (int) (getSplineFlingDistance(velocityX) * Math.signum(velocityX));
            mDy = (int) (getSplineFlingDistance(velocityY) * Math.signum(velocityY));
            mLastX = 0;
            mLastY = 0;
            int durationX = getSplineFlingDuration(velocityX);
            int durationY = getSplineFlingDuration(velocityY);

            if (mDx < minX) {
                durationX = adjustDuration(mDx, minX, durationX);
                mDx = minX;
            }
            if (mDx > maxX) {
                durationX = adjustDuration(mDx, maxX, durationX);
                mDx = maxX;
            }
            if (mDy < minY) {
                durationY = adjustDuration(mDy, minY, durationY);
                mDy = minY;
            }
            if (mDy > maxY) {
                durationY = adjustDuration(mDy, maxY, durationY);
                mDy = maxY;
            }

            if (mDx == 0 && mDy == 0) {
                return;
            }

            setDuration(Math.max(durationX, durationY));
            start();
            mGalleryView.invalidate();
        }

        @Override
        protected void onCalculate(float progress) {
            int x = (int) (mDx * progress);
            int y = (int) (mDy * progress);
            int offsetX = x - mLastX;
            int offsetY = y - mLastY;
            if (mCurrent != null && (offsetX != 0 || offsetY != 0)) {
                mCurrent.getImageView().scroll(-offsetX, -offsetY, mTemp);
            }
            mLastX = x;
            mLastY = y;
        }
    }

    private class SmoothScaler extends Animation {
        private float mFocusX;
        private float mFocusY;
        private float mStartScale;
        private float mEndScale;
        private float mStartTx;
        private float mEndTx;
        private float mStartTy;
        private float mEndTy;
        private float mLastScale;
        private GalleryPageView mTarget;

        public SmoothScaler() {
            setInterpolator(AnimationUtils.FAST_SLOW_INTERPOLATOR);
        }

        public void startSmoothScaler(float focusX, float focusY,
                float startScale, float endScale, float startTx, float endTx, float startTy, float endTy, int duration,
                GalleryPageView target) {
            mFocusX = focusX;
            mFocusY = focusY;
            mStartScale = startScale;
            mEndScale = endScale;
            mStartTx = startTx;
            mEndTx = endTx;
            mStartTy = startTy;
            mEndTy = endTy;
            mLastScale = startScale;
            // Target is optional for Double Page Mode
            mTarget = target;
            setDuration(duration);
            start();
            mGalleryView.invalidate();
        }

        @Override
        protected void onCalculate(float progress) {
            float scale = MathUtils.lerp(mStartScale, mEndScale, progress);

            if (mDoublePageMode) {
                float tx = MathUtils.lerp(mStartTx, mEndTx, progress);
                float ty = MathUtils.lerp(mStartTy, mEndTy, progress);

                // Set matrix with new scale and translation
                // To avoid complex math of relative postScale with relative translation,
                // we can just rebuild or use setValues if we track total state.
                // However, postScale is focus-point based.
                // Let's use a simpler approach:
                float dScale = scale / mLastScale;
                mPairMatrix.postScale(dScale, dScale, mFocusX, mFocusY);

                // If we are zooming out to 1.0, we also nudge translation towards target 0
                if (mEndScale == 1.0f) {
                    float[] curValues = new float[9];
                    mPairMatrix.getValues(curValues);
                    float curTx = curValues[android.graphics.Matrix.MTRANS_X];
                    float curTy = curValues[android.graphics.Matrix.MTRANS_Y];
                    float targetTx = MathUtils.lerp(mStartTx, mEndTx, progress);
                    float targetTy = MathUtils.lerp(mStartTy, mEndTy, progress);
                    mPairMatrix.postTranslate(targetTx - curTx, targetTy - curTy);
                }

                updateDoublePageLayout();
                mLastScale = scale;
                return;
            }

            if (mTarget == null) {
                return;
            }

            mTarget.getImageView().scale(mFocusX, mFocusY, scale / mLastScale);
            mLastScale = scale;
            mScaleValue = scale;
        }
    }
}
