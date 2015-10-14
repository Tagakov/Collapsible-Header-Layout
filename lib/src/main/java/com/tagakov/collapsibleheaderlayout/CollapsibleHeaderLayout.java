package com.tagakov.collapsibleheaderlayout;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Created by tagakov on 27.09.15.
 * vladimir@tagakov.com
 */
public class CollapsibleHeaderLayout extends FrameLayout {

    public interface CollapseListener {
        void onCollapsing(int currentHeight, float closingProgress);
    }

    public static final int INITIAL_STATE_COLLAPSED = 0;
    public static final int INITIAL_STATE_EXPANDED = 1;

    private static final int CUSTOM_HEADER_VIEW_INITIAL_POSITION = 1;

    private static final int SCRIM_STRATEGY_BEHIND = 0;
    private static final int SCRIM_STRATEGY_IN_FRONT = 1;

    private float parallaxMultiplier = .5f;
    private float customViewParallaxMultiplier = 0f;
    private int headerHeight = 0;
    private int minHeaderHeight = 0;
    private int initialState = INITIAL_STATE_EXPANDED;
    private int floatViewId = -1;
    private float floatViewScaleSpeed = 1f;
    private ColorDrawable scrimColor;
    private float scrimColorSpeed = 1f;
    private int scrimStrategy = SCRIM_STRATEGY_BEHIND;

    private CollapseListener collapseListener;

    private FrameLayout headerContainer;
    private Drawable headerDrawable;
    private ImageView headerImageView;
    private View customHeaderView;
    private ViewGroup floatView;
    private View contentView;

    public CollapsibleHeaderLayout(Context context) {
        this(context, null);
    }

    public CollapsibleHeaderLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CollapsibleHeaderLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        parseAttributes(attrs);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public CollapsibleHeaderLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        parseAttributes(attrs);
        init();
    }

    private void parseAttributes(AttributeSet attrs) {
        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.CollapsibleHeaderLayout);
        headerDrawable = a.getDrawable(R.styleable.CollapsibleHeaderLayout_headerImage);
        parallaxMultiplier = a.getFloat(R.styleable.CollapsibleHeaderLayout_parallaxMultiplier, parallaxMultiplier);
        customViewParallaxMultiplier = a.getFloat(R.styleable.CollapsibleHeaderLayout_customViewParallaxMultiplier, customViewParallaxMultiplier);
        headerHeight = a.getDimensionPixelSize(R.styleable.CollapsibleHeaderLayout_headerHeight, headerHeight);
        minHeaderHeight = a.getDimensionPixelSize(R.styleable.CollapsibleHeaderLayout_headerMinHeight, minHeaderHeight);
        initialState = a.getInt(R.styleable.CollapsibleHeaderLayout_headerInitialState, initialState);
        floatViewId = a.getResourceId(R.styleable.CollapsibleHeaderLayout_floatingViewId, floatViewId);
        floatViewScaleSpeed = a.getFloat(R.styleable.CollapsibleHeaderLayout_floatingViewScaleSpeed, floatViewScaleSpeed);
        scrimColor = new ColorDrawable(a.getColor(R.styleable.CollapsibleHeaderLayout_scrimColor, Color.TRANSPARENT));
        scrimColorSpeed = a.getFloat(R.styleable.CollapsibleHeaderLayout_scrimColorSpeed, scrimColorSpeed);
        scrimStrategy = a.getInt(R.styleable.CollapsibleHeaderLayout_scrimStrategy, scrimStrategy);
        a.recycle();
    }


    private void init() {
        headerImageView = new ImageView(getContext());
        headerImageView.setImageDrawable(headerDrawable);
        headerImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);

        headerContainer = new FrameLayout(getContext());
        headerContainer.addView(headerImageView);

        LayoutParams lp = generateDefaultLayoutParams();
        if (headerHeight != 0) {
            lp.height = headerHeight;
        } else {
            lp.height = LayoutParams.WRAP_CONTENT;
        }
        addView(headerContainer, lp);
        setOverScrollMode(OVER_SCROLL_ALWAYS);
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        headerHeight = headerContainer.getHeight();
        updateFloatingViewMargin();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        switch (getChildCount()) {
            case 2:
                initContentView();
                break;
            case 3:
                if (!initFloatView()) { //we have not float view
                    initCustomHeaderView();
                }
                initContentView();
                break;
            case 4:
                if (!initFloatView()) { // we have not float view
                    throw new IllegalStateException("Floating view id must be passed to the floatingViewId attribute of CollapsibleHeaderLayout");
                }
                initCustomHeaderView();
                initContentView();
                break;
            default:
                throw new IllegalStateException("Collapsible Header Layout should have from 1 to 3 direct children");
        }
        arrangeZLevels();
    }

    private void arrangeZLevels() {
        headerContainer.bringToFront();
        if (floatView != null) {
            floatView.bringToFront();
        }
    }

    private void initContentView() {
        for (int index = getChildCount() - 1; index > 0; index--) {
            View tmpView = getChildAt(index);
            if (tmpView != floatView) {
                contentView = tmpView;
                break;
            }
        }

        if (contentView instanceof RecyclerView) {
            RecyclerView rv = (RecyclerView) contentView;
            rv.addOnScrollListener(new ContentScrollListener());
            contentView.setPadding(
                    contentView.getPaddingLeft(),
                    contentView.getPaddingTop() + headerHeight,
                    contentView.getPaddingRight(),
                    contentView.getPaddingBottom()
            );
            rv.setClipToPadding(false);
            rv.setOverScrollMode(OVER_SCROLL_NEVER);
        } else if (contentView != null) {
            LayoutParams lp = (LayoutParams) contentView.getLayoutParams();
            lp.topMargin += headerHeight;
        } else {
            throw new IllegalStateException("Content view must be presented in the hierarchy");
        }
    }

    private boolean initFloatView() {
        if (floatViewId == -1) {
            return false;
        }
        View tmpFloatingView = findViewById(floatViewId);
        if (tmpFloatingView == null) {
            throw new IllegalStateException("Floating view id is presented but cannot be found!");
        }

        if (!(tmpFloatingView instanceof ViewGroup)) {
            throw new IllegalStateException("Floating was found but it is not ViewGroup!");
        }
        floatView = (ViewGroup) tmpFloatingView;
        return true;
    }

    private void updateFloatingViewMargin() {
        if (floatView == null) {
            return;
        }
        LayoutParams lp = (LayoutParams) floatView.getLayoutParams();
        if (lp.topMargin - headerHeight == floatView.getHeight() / 2) {
            return;
        }

        lp.topMargin = headerHeight - floatView.getHeight() / 2;
    }

    private void initCustomHeaderView() {
        for (int index = 1; index < getChildCount(); index++) {
            View tmpView = getChildAt(index);
            if (tmpView != floatView) {
                customHeaderView = tmpView;
                break;
            }
        }
        removeView(customHeaderView);

        LayoutParams lp = (LayoutParams) customHeaderView.getLayoutParams();
        lp.gravity = Gravity.BOTTOM;
        headerContainer.addView(customHeaderView, lp);
    }

    private class ContentScrollListener extends RecyclerView.OnScrollListener {
        int   maxTranslation = headerHeight - minHeaderHeight;
        float accumulatedTranslation = 0;
        float actualTranslation = 0;

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            accumulatedTranslation += dy;
            accumulatedTranslation = Math.max(0, accumulatedTranslation);

            actualTranslation = Math.min(maxTranslation, accumulatedTranslation);

            if (headerContainer.getTranslationY() + actualTranslation != 0) {
                float progress = actualTranslation / (maxTranslation != 0 ? maxTranslation : 1);
                if (floatView != null) {
                    float floatViewScale = 1f - actualTranslation * floatViewScaleSpeed / (maxTranslation != 0 ? maxTranslation : 1);
                    floatViewScale = Math.max(0, floatViewScale);
                    for (int i = 0; i < floatView.getChildCount(); i++) {
                        View child = floatView.getChildAt(i);
                        child.setScaleY(floatViewScale);
                        child.setScaleX(floatViewScale);
                    }
                    floatView.setTranslationY(-actualTranslation);
                }
                if (scrimColor.getColor() != Color.TRANSPARENT) {
                    float alpha = progress * scrimColorSpeed;
                    switch (scrimStrategy) {
                        case SCRIM_STRATEGY_BEHIND:
                            headerImageView.setColorFilter(adjustAlpha(scrimColor.getColor(), alpha));
                            break;
                        case SCRIM_STRATEGY_IN_FRONT:
                            scrimColor.setAlpha((int) (255 * alpha));
                            headerContainer.setForeground(scrimColor);
                            break;
                    }
                }

                if (customHeaderView != null) {
                    customHeaderView.setTranslationY(actualTranslation * customViewParallaxMultiplier);
                }
                headerContainer.setTranslationY(-actualTranslation);
                headerImageView.setTranslationY(actualTranslation * parallaxMultiplier);

                if (collapseListener != null) {
                    collapseListener.onCollapsing((int) (maxTranslation - actualTranslation), progress);
                }
            }
        }
    }

    private int adjustAlpha(int color, float factor) {
        return Color.argb(
                Math.max(0, Math.min(255, Math.round(Color.alpha(color) * factor))),
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        );
    }

}
