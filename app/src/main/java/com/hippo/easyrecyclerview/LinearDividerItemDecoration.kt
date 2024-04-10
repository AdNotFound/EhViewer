/*
 * Copyright (C) 2015 Hippo Seven
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

package com.hippo.easyrecyclerview;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Only work for {@link androidx.recyclerview.widget.LinearLayoutManager}.
 * Show divider between item, just like
 * {@link android.widget.ListView#setDivider(android.graphics.drawable.Drawable)}
 */
public class LinearDividerItemDecoration extends RecyclerView.ItemDecoration {
    public static final int HORIZONTAL = LinearLayoutManager.HORIZONTAL;
    public static final int VERTICAL = LinearLayoutManager.VERTICAL;
    private final Rect mRect;
    private final Paint mPaint;
    private boolean mShowFirstDivider = false;
    private boolean mShowLastDivider = false;
    private int mOrientation;
    private int mThickness;
    private int mPaddingStart = 0;
    private int mPaddingEnd = 0;

    private boolean mOverlap = false;

    private ShowDividerHelper mShowDividerHelper;

    public LinearDividerItemDecoration(int orientation, int color, int thickness) {
        mRect = new Rect();
        mPaint = new Paint();
        mPaint.setStyle(Paint.Style.FILL);
        setOrientation(orientation);
        setColor(color);
        setThickness(thickness);
    }

    public void setShowDividerHelper(ShowDividerHelper showDividerHelper) {
        mShowDividerHelper = showDividerHelper;
    }

    public void setOrientation(int orientation) {
        if (orientation != HORIZONTAL && orientation != VERTICAL) {
            throw new IllegalArgumentException("invalid orientation");
        }
        mOrientation = orientation;
    }

    public void setColor(int color) {
        mPaint.setColor(color);
    }

    public void setThickness(int thickness) {
        mThickness = thickness;
    }

    public void setShowFirstDivider(boolean showFirstDivider) {
        mShowFirstDivider = showFirstDivider;
    }

    public void setShowLastDivider(boolean showLastDivider) {
        mShowLastDivider = showLastDivider;
    }

    public void setPadding(int padding) {
        setPaddingStart(padding);
        setPaddingEnd(padding);
    }

    public void setPaddingStart(int paddingStart) {
        mPaddingStart = paddingStart;
    }

    public void setPaddingEnd(int paddingEnd) {
        mPaddingEnd = paddingEnd;
    }

    public void setOverlap(boolean overlap) {
        mOverlap = overlap;
    }

    @Override
    public void getItemOffsets(Rect outRect, View view,
                               RecyclerView parent, RecyclerView.State state) {
        if (parent.getAdapter() == null) {
            outRect.set(0, 0, 0, 0);
            return;
        }

        if (mOverlap) {
            outRect.set(0, 0, 0, 0);
            return;
        }

        final int position = parent.getChildLayoutPosition(view);
        final int itemCount = parent.getAdapter().getItemCount();

        if (mShowDividerHelper != null) {
            if (mOrientation == VERTICAL) {
                if (position == 0 && mShowDividerHelper.showDivider(0)) {
                    outRect.top = mThickness;
                }
                if (mShowDividerHelper.showDivider(position + 1)) {
                    outRect.bottom = mThickness;
                }
            } else {
                if (position == 0 && mShowDividerHelper.showDivider(0)) {
                    outRect.left = mThickness;
                }
                if (mShowDividerHelper.showDivider(position + 1)) {
                    outRect.right = mThickness;
                }
            }
        } else {
            if (mOrientation == VERTICAL) {
                if (position == 0 && mShowFirstDivider) {
                    outRect.top = mThickness;
                }
                outRect.bottom = mThickness;
                if (position == itemCount - 1 && !mShowLastDivider) {
                    outRect.bottom = 0;
                }
            } else {
                if (position == 0 && mShowFirstDivider) {
                    outRect.left = mThickness;
                }
                outRect.right = mThickness;
                if (position == itemCount - 1 && !mShowLastDivider) {
                    outRect.right = 0;
                }
            }
        }
    }

    @Override
    public void onDrawOver(Canvas c, RecyclerView parent,
                           RecyclerView.State state) {
        RecyclerView.Adapter adapter = parent.getAdapter();
        if (adapter == null) {
            return;
        }

        int itemCount = adapter.getItemCount();
        boolean overlap = mOverlap;

        if (mOrientation == VERTICAL) {
            final boolean isRtl = ViewCompat.getLayoutDirection(parent) == ViewCompat.LAYOUT_DIRECTION_RTL;
            int mPaddingLeft;
            int mPaddingRight;
            if (isRtl) {
                mPaddingLeft = mPaddingEnd;
                mPaddingRight = mPaddingStart;
            } else {
                mPaddingLeft = mPaddingStart;
                mPaddingRight = mPaddingEnd;
            }

            final int left = parent.getPaddingLeft() + mPaddingLeft;
            final int right = parent.getWidth() - parent.getPaddingRight() - mPaddingRight;
            final int childCount = parent.getChildCount();

            for (int i = 0; i < childCount; i++) {
                final View child = parent.getChildAt(i);
                final RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) child.getLayoutParams();
                final int position = parent.getChildLayoutPosition(child);

                boolean show;
                if (mShowDividerHelper != null) {
                    show = mShowDividerHelper.showDivider(position + 1);
                } else {
                    show = position != itemCount - 1 || mShowLastDivider;
                }
                if (show) {
                    int top = child.getBottom() + lp.bottomMargin;
                    if (overlap) {
                        top -= mThickness;
                    }
                    final int bottom = top + mThickness;
                    mRect.set(left, top, right, bottom);
                    c.drawRect(mRect, mPaint);
                }

                if (position == 0) {
                    if (mShowDividerHelper != null) {
                        show = mShowDividerHelper.showDivider(0);
                    } else {
                        show = mShowFirstDivider;
                    }
                    if (show) {
                        int bottom = child.getTop() + lp.topMargin;
                        if (overlap) {
                            bottom += mThickness;
                        }
                        final int top = bottom - mThickness;
                        mRect.set(left, top, right, bottom);
                        c.drawRect(mRect, mPaint);
                    }
                }
            }
        } else {
            final int top = parent.getPaddingTop() + mPaddingStart;
            final int bottom = parent.getHeight() - parent.getPaddingBottom() - mPaddingEnd;
            final int childCount = parent.getChildCount();

            for (int i = 0; i < childCount; i++) {
                final View child = parent.getChildAt(i);
                final RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) child.getLayoutParams();
                final int position = parent.getChildLayoutPosition(child);

                boolean show;
                if (mShowDividerHelper != null) {
                    show = mShowDividerHelper.showDivider(position + 1);
                } else {
                    show = position != itemCount - 1 || mShowLastDivider;
                }
                if (show) {
                    int left = child.getRight() + lp.rightMargin;
                    if (overlap) {
                        left -= mThickness;
                    }
                    final int right = left + mThickness;
                    mRect.set(left, top, right, bottom);
                    c.drawRect(mRect, mPaint);
                }

                if (position == 0) {
                    if (mShowDividerHelper != null) {
                        show = mShowDividerHelper.showDivider(0);
                    } else {
                        show = mShowFirstDivider;
                    }
                    if (show) {
                        int right = child.getLeft() + lp.leftMargin;
                        if (overlap) {
                            right += mThickness;
                        }
                        final int left = right - mThickness;
                        mRect.set(left, top, right, bottom);
                        c.drawRect(mRect, mPaint);
                    }
                }
            }
        }
    }

    public interface ShowDividerHelper {
        boolean showDivider(int index);
    }
}
