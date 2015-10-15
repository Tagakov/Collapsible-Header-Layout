package com.tagakov.collapsibleheaderlayout;

import android.animation.Animator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.NestedScrollingChild;
import android.support.v4.view.NestedScrollingChildHelper;
import android.support.v4.view.NestedScrollingParent;
import android.support.v4.view.NestedScrollingParentHelper;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Created by tagakov on 27.09.15.
 * vladimir@tagakov.com
 */
public class CollapsibleHeaderLayout extends FrameLayout implements NestedScrollingParent,
        NestedScrollingChild {

    public interface CollapseListener {
        void onCollapse(int currentHeight, float collapseFraction);
        void onOverDrag(int currentHeight, float overDragFraction);
    }

    public static final int INITIAL_STATE_COLLAPSED = 0;
    public static final int INITIAL_STATE_EXPANDED = 1;

    public static final int SCRIM_STRATEGY_BEHIND = 0;
    public static final int SCRIM_STRATEGY_IN_FRONT = 1;

    public static final int OVERDRAG_STRATEGY_SCALE_OVER_BOUNDS = 0;
    public static final int OVERDRAG_STRATEGY_SCALE_IN_BOUNDS = 1;

    public static int CUSTOM_VIEW_OVERDRAG_BEHAVIOR_NONE = 0;
    public static int CUSTOM_VIEW_OVERDRAG_BEHAVIOR_WITH_IMAGE = 1;

    public static final int FLOAT_VIEW_OVERDRAG_BEHAVIOR_NONE = 0;
    public static final int FLOAT_VIEW_OVERDRAG_BEHAVIOR_SCALE = 1;

    public static final int HEADER_OPEN_STRATEGY_QUICK = 0;
    public static final int HEADER_OPEN_STRATEGY_TOP = 1;

    private static final int INVALID_POINTER = -1;
    private static final float DEFAULT_RETURN_DURATION = 300;
    private static final TimeInterpolator DECELERATE_INTERPOLATOR = new DecelerateInterpolator();

    private final int[] mParentScrollConsumed = new int[2];
    private final NestedScrollingParentHelper mNestedScrollingParentHelper = new NestedScrollingParentHelper(this);
    private final NestedScrollingChildHelper mNestedScrollingChildHelper = new NestedScrollingChildHelper(this);
    private final ValueAnimator overDragReturner = ValueAnimator.ofFloat(1f, 0f);

    private int activePointerId;
    private int touchSlop;
    private boolean isBeingDragged;
    private float prevMotionY;
    private float headerOverDrag;
    private float headerTranslation;
    private float accumulatedHeaderTranslation;
    private int minTranslation;
    private float maxOverDragDistance;
    private float maxOverDragScale;
    private boolean returningToStart;

    private float parallaxMultiplier = .5f;
    private float customViewParallaxMultiplier = 0f;
    private int headerHeight = 0;
    private int minHeaderHeight = 0;
    private int initialState = INITIAL_STATE_EXPANDED;
    private int floatViewId = -1;
    private float floatViewScaleSpeed = 1f;
    private ColorDrawable scrimColorDrawable;
    private float scrimColorSpeed = 1f;
    private int scrimStrategy = SCRIM_STRATEGY_BEHIND;
    private float overDragMultiplier = .2f;
    private float returningSpringForce = 1f;
    private int overDragStrategy = OVERDRAG_STRATEGY_SCALE_IN_BOUNDS;
    private int customViewOverDragBehavior = CUSTOM_VIEW_OVERDRAG_BEHAVIOR_NONE;
    private float overDragPivotY = .5f;
    private float overDragPivotX = .5f;
    private int floatViewOverDragBehavior = FLOAT_VIEW_OVERDRAG_BEHAVIOR_SCALE;
    private int headerOpenStrategy = HEADER_OPEN_STRATEGY_TOP;

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
        scrimColorDrawable = new ColorDrawable(a.getColor(R.styleable.CollapsibleHeaderLayout_scrimColor, Color.TRANSPARENT));
        scrimColorSpeed = a.getFloat(R.styleable.CollapsibleHeaderLayout_scrimColorSpeed, scrimColorSpeed);
        scrimStrategy = a.getInt(R.styleable.CollapsibleHeaderLayout_scrimStrategy, scrimStrategy);
        overDragMultiplier = a.getFloat(R.styleable.CollapsibleHeaderLayout_overDragMultiplier, overDragMultiplier);
        returningSpringForce = a.getFloat(R.styleable.CollapsibleHeaderLayout_returningSpringForce, returningSpringForce);
        overDragStrategy = a.getInt(R.styleable.CollapsibleHeaderLayout_overDragStrategy, overDragStrategy);
        customViewOverDragBehavior = a.getInt(R.styleable.CollapsibleHeaderLayout_customViewOverDragBehavior, customViewOverDragBehavior);
        floatViewOverDragBehavior = a.getInt(R.styleable.CollapsibleHeaderLayout_floatViewOverDragBehavior, floatViewOverDragBehavior);
        headerOpenStrategy = a.getInt(R.styleable.CollapsibleHeaderLayout_headerOpenStrategy, headerOpenStrategy);
        overDragPivotY = a.getFloat(R.styleable.CollapsibleHeaderLayout_overDragPivotY, overDragPivotY);
        overDragPivotX = a.getFloat(R.styleable.CollapsibleHeaderLayout_overDragPivotX, overDragPivotX);
        a.recycle();
    }

    public void setHeaderImage(Drawable image) {
        headerImageView.setImageDrawable(image);
    }

    public void setCollapseListener(CollapseListener listener) {
        this.collapseListener = listener;
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

        touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        setNestedScrollingEnabled(true);

        overDragReturner.setInterpolator(new AccelerateInterpolator());
        overDragReturner.addListener(new OverDragReturnerEndListener());
        overDragReturner.addUpdateListener(new OverDragReturnerUpdateListener());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (headerHeight == 0) {
            headerHeight = headerContainer.getHeight();
        }

        if (headerHeight != 0) {
            minTranslation = minHeaderHeight - headerHeight;
            maxOverDragDistance = headerHeight + headerHeight * overDragMultiplier;
            if (maxOverDragDistance > 0) {
                maxOverDragScale = 1f - headerHeight / maxOverDragDistance;
            }
        }

        headerContainer.setPivotX(headerContainer.getWidth() * overDragPivotX);
        headerContainer.setPivotY(headerContainer.getHeight() * overDragPivotY);

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

        if (initialState == INITIAL_STATE_COLLAPSED) {
            contentView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    contentView.getViewTreeObserver().removeOnPreDrawListener(this);
                    CollapseListener tmp = collapseListener;
                    collapseListener = null;
                    contentView.scrollBy(0, -minTranslation);
                    collapseListener = tmp;
                    return false;
                }
            });
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
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            translateHeader(-dy);
        }
    }

    private class OverDragReturnerEndListener implements ValueAnimator.AnimatorListener {
        @Override
        public void onAnimationStart(Animator animation) {
            returningToStart = true;
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            returningToStart = false;
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            returningToStart = false;
        }

        @Override
        public void onAnimationRepeat(Animator animation) {
            returningToStart = true;
        }
    }

    private class OverDragReturnerUpdateListener implements ValueAnimator.AnimatorUpdateListener {

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            overDragHeader((Float) animation.getAnimatedValue() - headerOverDrag);
        }
    }

    private void translateHeader(final float translationOffset) {
        if (minTranslation == 0) return;
        accumulatedHeaderTranslation = Math.min(0, accumulatedHeaderTranslation + translationOffset);
        if (headerOpenStrategy == HEADER_OPEN_STRATEGY_TOP && accumulatedHeaderTranslation < minTranslation && headerTranslation == minTranslation) {
            return;
        }

        float resultTranslation = Math.max(minTranslation, Math.min(0, headerTranslation + translationOffset));
        headerTranslation = resultTranslation;

        float visibleFraction = 1f - resultTranslation / minTranslation;

        onHeaderMoveUp(resultTranslation, visibleFraction);
        alignFloatView();

        if (collapseListener != null) {
            collapseListener.onCollapse((int) (headerHeight + resultTranslation), visibleFraction);
        }
    }

    private void overDragHeader(float overDragOffset) {
        headerOverDrag += overDragOffset;
        float overDragFraction = DECELERATE_INTERPOLATOR.getInterpolation(
                Math.max(
                        0f,
                        Math.min(1f, headerOverDrag / maxOverDragDistance)
                )
        ) * maxOverDragScale;

        float resultScale = 1f + overDragFraction;

        overDragTranslateContent(overDragFraction);

        switch (overDragStrategy) {
            case OVERDRAG_STRATEGY_SCALE_IN_BOUNDS:
                overDragScaleIn(resultScale);
                break;

            case OVERDRAG_STRATEGY_SCALE_OVER_BOUNDS:
                overDragOverBounds(resultScale);
                alignFloatView();
                break;
        }

        if (floatView != null && floatViewOverDragBehavior == FLOAT_VIEW_OVERDRAG_BEHAVIOR_SCALE) {
            scaleFloatView(1f + overDragFraction);
        }

        if (collapseListener != null) {
            collapseListener.onOverDrag((int) (headerHeight + contentView.getTranslationY()), overDragFraction);
        }
    }

    private void overDragTranslateContent(float overDragFraction) {
        contentView.setTranslationY(headerHeight * overDragFraction * (1 - overDragPivotY));
    }

    private void overDragOverBounds(float resultScale) {
        headerContainer.setScaleX(resultScale);
        headerContainer.setScaleY(resultScale);

        if (customViewOverDragBehavior == CUSTOM_VIEW_OVERDRAG_BEHAVIOR_NONE && customHeaderView != null) {
            float counterScale = 1f / resultScale;
            customHeaderView.setScaleX(counterScale);
            customHeaderView.setScaleY(counterScale);
        }

    }

    private void overDragScaleIn(float resultScale) {
        headerImageView.setScaleX(resultScale);
        headerImageView.setScaleY(resultScale);

        if (customViewOverDragBehavior == CUSTOM_VIEW_OVERDRAG_BEHAVIOR_WITH_IMAGE && customHeaderView != null) {
            customHeaderView.setScaleX(resultScale);
            customHeaderView.setScaleY(resultScale);
        }
    }

    private void onHeaderMoveUp(float translation, float headerVisibleFraction) {
        if (floatView != null) {
            scaleFloatView(headerVisibleFraction * floatViewScaleSpeed);
        }
        scrimHeader(headerVisibleFraction);
        moveHeader(translation);
    }

    private void moveHeader(float translation) {
        headerContainer.setTranslationY(translation);
        moveImage(translation);
        if (customHeaderView != null) {
            moveCustomView(translation);
        }
    }

    private void moveCustomView(float translation) {
        customHeaderView.setTranslationY(-translation * customViewParallaxMultiplier);
    }

    private void moveImage(float translation) {
        headerImageView.setTranslationY(-translation * parallaxMultiplier);
    }

    private void scrimHeader(float headerVisibleFraction) {
        if (scrimColorDrawable.getColor() != Color.TRANSPARENT) {
            float alpha = (1f - headerVisibleFraction) * scrimColorSpeed;
            switch (scrimStrategy) {
                case SCRIM_STRATEGY_BEHIND:
                    headerImageView.setColorFilter(adjustAlpha(scrimColorDrawable.getColor(), alpha));
                    break;
                case SCRIM_STRATEGY_IN_FRONT:
                    scrimColorDrawable.setAlpha((int) (255 * alpha));
                    headerContainer.setForeground(scrimColorDrawable);
                    break;
            }
        }
    }

    private void alignFloatView() {
        if (floatView == null) return;
        floatView.setTranslationY(-headerHeight * (1f - headerContainer.getScaleY()) * (1f - overDragPivotY) + headerContainer.getTranslationY());
    }

    private void scaleFloatView(float scale) {
        float floatViewScale = scale;
        floatViewScale = Math.max(0, floatViewScale);
        floatView.setScaleY(floatViewScale);
        for (int i = 0; i < floatView.getChildCount(); i++) {
            View child = floatView.getChildAt(i);
            if (!child.isShown()) continue;
            child.setScaleX(floatViewScale);
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

    /**
     * @return Whether it is possible for the child view of this layout to
     *         scroll up. Override this if the child view is a custom view.
     */
    protected boolean canChildScrollUp() {
        return ViewCompat.canScrollVertically(contentView, -1);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = MotionEventCompat.getActionMasked(ev);

        if (returningToStart && action == MotionEvent.ACTION_DOWN) {
            stopOverDragReturning();
        }

        if (!isEnabled() || returningToStart || canChildScrollUp()) {
            // Fail fast if we're not in a state where a swipe is possible
            return false;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                activePointerId = MotionEventCompat.getPointerId(ev, 0);
                isBeingDragged = false;
                final float initialDownY = getMotionEventY(ev, activePointerId);
                if (initialDownY == -1) {
                    break;
                }
                prevMotionY = initialDownY;
                break;

            case MotionEvent.ACTION_MOVE: {
                if (activePointerId == INVALID_POINTER) {
                    break;
                }

                final float y = getMotionEventY(ev, activePointerId);
                if (y == -1) {
                    break;
                }
                if (isBeingDragged) {
                    final float dY = y - prevMotionY;
                    prevMotionY = y;
                    stopOverDragReturning();
                    overDragHeader(dY);
                    isBeingDragged = headerOverDrag + dY >= 0;
                } else {
                    final float yDiff = y - prevMotionY;
                    if (yDiff > touchSlop) {
                        prevMotionY += yDiff;
                        isBeingDragged = true;
                    }
                }
                break;
            }
            case MotionEventCompat.ACTION_POINTER_DOWN: {
                final int index = MotionEventCompat.getActionIndex(ev);
                activePointerId = MotionEventCompat.getPointerId(ev, index);
                break;
            }

            case MotionEventCompat.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (activePointerId != INVALID_POINTER) {
                    final int pointerIndex = MotionEventCompat.findPointerIndex(ev, activePointerId);
                    final float y = MotionEventCompat.getY(ev, pointerIndex);
                    final float dY = y - prevMotionY;
                    isBeingDragged = false;
                    stopOverDragReturning();
                    overDragHeader(dY);
                    startOverDragReturning();
                    activePointerId = INVALID_POINTER;
                }
                break;
            }
        }
        return false;
    }

    private void stopOverDragReturning() {
        if (!returningToStart || headerOverDrag == 0) return;
        overDragReturner.cancel();
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = MotionEventCompat.getActionIndex(ev);
        final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex);
        if (pointerId == activePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            activePointerId = MotionEventCompat.getPointerId(ev, newPointerIndex);
        }
    }

    private float getMotionEventY(MotionEvent ev, int activePointerId) {
        final int index = MotionEventCompat.findPointerIndex(ev, activePointerId);
        if (index < 0) {
            return -1;
        }
        return MotionEventCompat.getY(ev, index);
    }

    private void startOverDragReturning() {
        if (returningToStart || headerOverDrag == 0) return;
        returningToStart = true;
        final long adjustedDuration = (long) ((headerOverDrag * DEFAULT_RETURN_DURATION / maxOverDragDistance) / returningSpringForce);
        overDragReturner.setDuration(adjustedDuration > 0 ? adjustedDuration : (long) DEFAULT_RETURN_DURATION);
        overDragReturner.setFloatValues(headerOverDrag, 0f);
        overDragReturner.start();
    }


    @Override
    public void requestDisallowInterceptTouchEvent(boolean b) {
        // if this is a List < L or another view that doesn't support nested
        // scrolling, ignore this request so that the vertical scroll event
        // isn't stolen
        if ((Build.VERSION.SDK_INT >= 21 || !(contentView instanceof AbsListView))
                && (ViewCompat.isNestedScrollingEnabled(contentView))) {
                    super.requestDisallowInterceptTouchEvent(b);
        }
    }

    // NestedScrollingParent

    @Override
    public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
        if (isEnabled() && (nestedScrollAxes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0) {
            // Dispatch up to the nested parent
            startNestedScroll(nestedScrollAxes & ViewCompat.SCROLL_AXIS_VERTICAL);
            return true;
        }
        return false;
    }

    @Override
    public void onNestedScrollAccepted(View child, View target, int axes) {
        mNestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes);
    }

    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        // If we are in the middle of consuming, a scroll, then we want to move the spinner back up
        // before allowing the list to scroll
        if (dy > 0 && headerOverDrag > 0) {
            stopOverDragReturning();
            if (dy > headerOverDrag) {
                consumed[1] = dy - (int) headerOverDrag;
                overDragHeader(-headerOverDrag);
            } else {
                consumed[1] = dy;
                overDragHeader(-dy);
            }
        }

        // Now let our nested parent consume the leftovers
        final int[] parentConsumed = mParentScrollConsumed;
        if (dispatchNestedPreScroll(dx - consumed[0], dy - consumed[1], parentConsumed, null)) {
            consumed[0] += parentConsumed[0];
            consumed[1] += parentConsumed[1];
        }
    }

    @Override
    public int getNestedScrollAxes() {
        return mNestedScrollingParentHelper.getNestedScrollAxes();
    }

    @Override
    public void onStopNestedScroll(View target) {
        mNestedScrollingParentHelper.onStopNestedScroll(target);
        // Finish the spinner for nested scrolling if we ever consumed any
        // unconsumed nested scroll
        if (headerOverDrag > 0) {
            startOverDragReturning();
        }
        // Dispatch up our nested parent
        stopNestedScroll();
    }

    @Override
    public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed,
                               int dyUnconsumed) {
        if (dyUnconsumed < 0) {
            dyUnconsumed = Math.abs(dyUnconsumed);
            stopOverDragReturning();
            overDragHeader(dyUnconsumed);
        }
        // Dispatch up to the nested parent
        dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dxConsumed, null);
    }

    // NestedScrollingChild

    @Override
    public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
        return false;
    }

    @Override
    public boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed) {
        return false;
    }

    @Override
    public void setNestedScrollingEnabled(boolean enabled) {
        mNestedScrollingChildHelper.setNestedScrollingEnabled(enabled);
    }

    @Override
    public boolean isNestedScrollingEnabled() {
        return mNestedScrollingChildHelper.isNestedScrollingEnabled();
    }

    @Override
    public boolean startNestedScroll(int axes) {
        return mNestedScrollingChildHelper.startNestedScroll(axes);
    }

    @Override
    public void stopNestedScroll() {
        mNestedScrollingChildHelper.stopNestedScroll();
    }

    @Override
    public boolean hasNestedScrollingParent() {
        return mNestedScrollingChildHelper.hasNestedScrollingParent();
    }

    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed,
                                        int dyUnconsumed, int[] offsetInWindow) {
        return mNestedScrollingChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed,
                dxUnconsumed, dyUnconsumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow) {
        return mNestedScrollingChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        return mNestedScrollingChildHelper.dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        return mNestedScrollingChildHelper.dispatchNestedPreFling(velocityX, velocityY);
    }
}
