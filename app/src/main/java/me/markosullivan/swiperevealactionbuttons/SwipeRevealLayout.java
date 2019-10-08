package me.markosullivan.swiperevealactionbuttons;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.ViewCompat;
import androidx.customview.widget.ViewDragHelper;

import timber.log.Timber;

/**
 * Created by Mark O'Sullivan on 25th February 2018.
 */

public class SwipeRevealLayout extends ViewGroup {

    private static final String SUPER_INSTANCE_STATE = "saved_instance_state_parcelable";

    private static final int DEFAULT_MIN_FLING_VELOCITY = 300; // dp per second
    private static final int DEFAULT_MIN_DIST_REQUEST_DISALLOW_PARENT = 1; // dp

    public static final int DRAG_EDGE_LEFT = 0x1;
    public static final int DRAG_EDGE_RIGHT = 0x1 << 1;

    /**
     * Main view is the view which is shown when the layout is closed.
     */
    private View mainView;

    /**
     *  The view which is shown when the layout is opened from right to left
     */
    private View rightView;

    /**
     * The view which is shown when the layout is opened from left to right
     */
    private View leftView;

    /**
     * The rectangle position of the main view when the layout is closed.
     */
    private Rect rectMainClose = new Rect();

    /**
     * The rectangle position of the main view when the layout is opened.
     */
    private Rect rectMainOpen = new Rect();

    /**
     * The rectangle position of the right view when the layout is closed.
     */
    private Rect rectRightClose = new Rect();

    /**
     * The rectangle position of the right view when the layout is opened.
     */
    private Rect rectRightOpen = new Rect();

    /**
     * The rectangle position of the left view when the layout is closed.
     */
    private Rect rectLeftClose = new Rect();

    /**
     * The rectangle position of the left view when the layout is opened.
     */
    private Rect rectLeftOpen = new Rect();

    /**
     * The minimum distance (px) to the closest drag edge that the SwipeRevealLayout
     * will disallow the parent to intercept touch event.
     */
    private int minDistRequestDisallowParent = 0;

    private boolean isOpenBeforeInit = false;
    private volatile boolean isScrolling = false;
    private volatile boolean lockDrag = false;

    private int minFlingVelocity = DEFAULT_MIN_FLING_VELOCITY;

    private int dragEdge = DRAG_EDGE_LEFT;

    private float dragDist = 0;
    private float prevX = -1;

    private ViewDragHelper dragHelper;
    private GestureDetectorCompat gestureDetector;

    public SwipeRevealLayout(Context context) {
        super(context);
        init(context, null);
    }

    public SwipeRevealLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public SwipeRevealLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Nullable
    @Override
    protected Parcelable onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putParcelable(SUPER_INSTANCE_STATE, super.onSaveInstanceState());
        return super.onSaveInstanceState();
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        Bundle bundle = (Bundle) state;
        state = bundle.getParcelable(SUPER_INSTANCE_STATE);
        super.onRestoreInstanceState(state);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        dragHelper.processTouchEvent(event);
        return true;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (isDragLocked()) {
            return super.onInterceptTouchEvent(ev);
        }

        dragHelper.processTouchEvent(ev);
        gestureDetector.onTouchEvent(ev);
        accumulateDragDist(ev);

        boolean couldBecomeClick = couldBecomeClick(ev);
        boolean settling = dragHelper.getViewDragState() == ViewDragHelper.STATE_SETTLING;
        boolean idleAfterScrolled
                = dragHelper.getViewDragState() == ViewDragHelper.STATE_IDLE && isScrolling;

        // must be placed as the last statement
        prevX = ev.getX();

        // return true => intercept, cannot trigger onClick event
        return !couldBecomeClick && (settling || idleAfterScrolled);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // get views
        if (getChildCount() >= 2) {
            rightView = getChildAt(0);
            mainView = getChildAt(1);
        } else if (getChildCount() == 1) {
            mainView = getChildAt(0);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int index = 0; index < getChildCount(); index++) {
            final View child = getChildAt(index);

            int left, right, top, bottom;
            left = right = top = bottom = 0;

            final int minLeft = getPaddingLeft();
            final int maxRight = Math.max(r - getPaddingRight() - l, 0);
            final int minTop = getPaddingTop();
            final int maxBottom = Math.max(b - getPaddingBottom() - t, 0);

            int measuredChildHeight = child.getMeasuredHeight();
            int measuredChildWidth = child.getMeasuredWidth();

            // need to take account if child size is match_parent
            final LayoutParams childParams = child.getLayoutParams();
            boolean matchParentHeight = false;
            boolean matchParentWidth = false;

            if (childParams != null) {
                matchParentHeight = (childParams.height == LayoutParams.MATCH_PARENT);
                matchParentWidth = (childParams.width == LayoutParams.MATCH_PARENT);
            }

            if (matchParentHeight) {
                measuredChildHeight = maxBottom - minTop;
                childParams.height = measuredChildHeight;
            }

            if (matchParentWidth) {
                measuredChildWidth = maxRight - minLeft;
                childParams.width = measuredChildWidth;
            }

            switch (dragEdge) {
                case DRAG_EDGE_RIGHT:
                    left = Math.max(r - measuredChildWidth - getPaddingRight() - l, minLeft);
                    top = Math.min(getPaddingTop(), maxBottom);
                    right = Math.max(r - getPaddingRight() - l, minLeft);
                    bottom = Math.min(measuredChildHeight + getPaddingTop(), maxBottom);
                    break;

                case DRAG_EDGE_LEFT:
                    left = Math.min(getPaddingLeft(), maxRight);
                    top = Math.min(getPaddingTop(), maxBottom);
                    right = Math.min(measuredChildWidth + getPaddingLeft(), maxRight);
                    bottom = Math.min(measuredChildHeight + getPaddingTop(), maxBottom);
                    break;
            }

            child.layout(left, top, right, bottom);
        }

        initRects();

        if (isOpenBeforeInit) {
            open(false);
        } else {
            close(false);
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (getChildCount() < 2) {
            throw new RuntimeException("Layout must have two children");
        }

        final LayoutParams params = getLayoutParams();

        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);

        int desiredWidth = 0;
        int desiredHeight = 0;

        // first find the largest child
        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            desiredWidth = Math.max(child.getMeasuredWidth(), desiredWidth);
            desiredHeight = Math.max(child.getMeasuredHeight(), desiredHeight);
        }
        // create new measure spec using the largest child width
        widthMeasureSpec = MeasureSpec.makeMeasureSpec(desiredWidth, widthMode);
        heightMeasureSpec = MeasureSpec.makeMeasureSpec(desiredHeight, heightMode);

        final int measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
        final int measuredHeight = MeasureSpec.getSize(heightMeasureSpec);

        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            final LayoutParams childParams = child.getLayoutParams();

            if (childParams != null) {
                if (childParams.height == LayoutParams.MATCH_PARENT) {
                    child.setMinimumHeight(measuredHeight);
                }

                if (childParams.width == LayoutParams.MATCH_PARENT) {
                    child.setMinimumWidth(measuredWidth);
                }
            }

            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            desiredWidth = Math.max(child.getMeasuredWidth(), desiredWidth);
            desiredHeight = Math.max(child.getMeasuredHeight(), desiredHeight);
        }

        // taking accounts of padding
        desiredWidth += getPaddingLeft() + getPaddingRight();
        desiredHeight += getPaddingTop() + getPaddingBottom();

        // adjust desired width
        if (widthMode == MeasureSpec.EXACTLY) {
            desiredWidth = measuredWidth;
        } else {
            if (params.width == LayoutParams.MATCH_PARENT) {
                desiredWidth = measuredWidth;
            }

            if (widthMode == MeasureSpec.AT_MOST) {
                desiredWidth = (desiredWidth > measuredWidth) ? measuredWidth : desiredWidth;
            }
        }

        // adjust desired height
        if (heightMode == MeasureSpec.EXACTLY) {
            desiredHeight = measuredHeight;
        } else {
            if (params.height == LayoutParams.MATCH_PARENT) {
                desiredHeight = measuredHeight;
            }

            if (heightMode == MeasureSpec.AT_MOST) {
                desiredHeight = (desiredHeight > measuredHeight) ? measuredHeight : desiredHeight;
            }
        }

        setMeasuredDimension(desiredWidth, desiredHeight);
    }

    @Override
    public void computeScroll() {
        if (dragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    /**
     * Open the panel to show the secondary view
     */
    public void open(boolean animation) {
        // TODO - here we open the menu
        isOpenBeforeInit = true;

        if (animation) {
            dragHelper.smoothSlideViewTo(mainView, rectMainOpen.left, rectMainOpen.top);
        } else {
            dragHelper.abort();

            mainView.layout(
                    rectMainOpen.left,
                    rectMainOpen.top,
                    rectMainOpen.right,
                    rectMainOpen.bottom
            );

            rightView.layout(
                    rectRightOpen.left,
                    rectRightOpen.top,
                    rectRightOpen.right,
                    rectRightOpen.bottom
            );
        }

        ViewCompat.postInvalidateOnAnimation(this);
    }

    /**
     * Close the panel to hide the secondary view
     */
    public void close(boolean animation) {
        // TODO - here we hide the menu
        isOpenBeforeInit = false;

        if (animation) {
            dragHelper.smoothSlideViewTo(mainView, rectMainClose.left, rectMainClose.top);
        } else {
            dragHelper.abort();
            mainView.layout(
                    rectMainClose.left,
                    rectMainClose.top,
                    rectMainClose.right,
                    rectMainClose.bottom
            );
            rightView.layout(
                    rectRightClose.left,
                    rectRightClose.top,
                    rectRightClose.right,
                    rectRightClose.bottom
            );
        }

        ViewCompat.postInvalidateOnAnimation(this);
    }

    /**
     * @return true if the drag/swipe motion is currently locked.
     */
    public boolean isDragLocked() {
        return lockDrag;
    }

    private int getMainOpenLeft() {
        switch (dragEdge) {
            case DRAG_EDGE_LEFT:
                return rectMainClose.left + rightView.getWidth();

            case DRAG_EDGE_RIGHT:
                return rectMainClose.left - rightView.getWidth();


            default:
                return 0;
        }
    }

    private int getMainOpenTop() {
        switch (dragEdge) {
            case DRAG_EDGE_LEFT:
            case DRAG_EDGE_RIGHT:
                return rectMainClose.top;


            default:
                return 0;
        }
    }

    private int getSecOpenLeft() {
        return rectRightClose.left;
    }

    private int getSecOpenTop() {
        return rectRightClose.top;
    }

    private void initRects() {
        // close position of main view
        rectMainClose.set(
                mainView.getLeft(),
                mainView.getTop(),
                mainView.getRight(),
                mainView.getBottom()
        );

        // close position of secondary view
        rectRightClose.set(
                rightView.getLeft(),
                rightView.getTop(),
                rightView.getRight(),
                rightView.getBottom()
        );

        // open position of the main view
        rectMainOpen.set(
                getMainOpenLeft(),
                getMainOpenTop(),
                getMainOpenLeft() + mainView.getWidth(),
                getMainOpenTop() + mainView.getHeight()
        );

        // open position of the secondary view
        rectRightOpen.set(
                getSecOpenLeft(),
                getSecOpenTop(),
                getSecOpenLeft() + rightView.getWidth(),
                getSecOpenTop() + rightView.getHeight()
        );
    }

    private boolean couldBecomeClick(MotionEvent ev) {
        return isInMainView(ev) && !shouldInitiateADrag();
    }

    private boolean isInMainView(MotionEvent ev) {
        float x = ev.getX();
        float y = ev.getY();

        boolean withinVertical = mainView.getTop() <= y && y <= mainView.getBottom();
        boolean withinHorizontal = mainView.getLeft() <= x && x <= mainView.getRight();

        return withinVertical && withinHorizontal;
    }

    private boolean shouldInitiateADrag() {
        float minDistToInitiateDrag = dragHelper.getTouchSlop();
        return getDraggedDistance() >= minDistToInitiateDrag;
    }

    private void accumulateDragDist(MotionEvent ev) {
        final int action = ev.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            dragDist = 0;
            return;
        }

        float signedDraggedDistance = ev.getX() - prevX;
        dragDist += signedDraggedDistance;
        Timber.i("Drag distance " + signedDraggedDistance);
        Timber.i("Total drag distance " + dragDist);
    }

    private float getDraggedDistance() {
        return Math.abs(dragDist);
    }

    private boolean draggingFromLeftToRight() {
        return dragDist > 0;
    }

    private void init(Context context, AttributeSet attrs) {
        if (attrs != null && context != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(
                    attrs,
                    R.styleable.SwipeRevealLayout,
                    0,
                    0
            );

            dragEdge = a.getInteger(R.styleable.SwipeRevealLayout_dragFromEdge, DRAG_EDGE_LEFT);
            minFlingVelocity = DEFAULT_MIN_FLING_VELOCITY;
            minDistRequestDisallowParent = DEFAULT_MIN_DIST_REQUEST_DISALLOW_PARENT;
        }

        dragHelper = ViewDragHelper.create(this, 1.0f, mDragHelperCallback);
        dragHelper.setEdgeTrackingEnabled(ViewDragHelper.EDGE_ALL);

        gestureDetector = new GestureDetectorCompat(context, mGestureListener);
    }

    private final GestureDetector.OnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener() {
        boolean hasDisallowed = false;

        @Override
        public boolean onDown(MotionEvent e) {
            isScrolling = false;
            hasDisallowed = false;
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            isScrolling = true;
            return false;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            isScrolling = true;

            if (getParent() != null) {
                boolean shouldDisallow;

                if (!hasDisallowed) {
                    shouldDisallow = getDistToClosestEdge() >= minDistRequestDisallowParent;
                    if (shouldDisallow) {
                        hasDisallowed = true;
                    }
                } else {
                    shouldDisallow = true;
                }

                // disallow parent to intercept touch event so that the layout will work
                // properly on RecyclerView or view that handles scroll gesture.
                getParent().requestDisallowInterceptTouchEvent(shouldDisallow);
            }

            return false;
        }
    };

    private int getDistToClosestEdge() {
        switch (dragEdge) {
            case DRAG_EDGE_LEFT:
                final int pivotRight = rectMainClose.left + rightView.getWidth();

                return Math.min(
                        mainView.getLeft() - rectMainClose.left,
                        pivotRight - mainView.getLeft()
                );

            case DRAG_EDGE_RIGHT:
                final int pivotLeft = rectMainClose.right - rightView.getWidth();

                return Math.min(
                        mainView.getRight() - pivotLeft,
                        rectMainClose.right - mainView.getRight()
                );
        }

        return 0;
    }

    private int getHalfwayPivotHorizontal() {
        if (dragEdge == DRAG_EDGE_LEFT) {
            return rectMainClose.left + rightView.getWidth() / 2;
        } else {
            return rectMainClose.right - rightView.getWidth() / 2;
        }
    }

    private final ViewDragHelper.Callback mDragHelperCallback = new ViewDragHelper.Callback() {
        @Override
        public boolean tryCaptureView(@NonNull View child, int pointerId) {

            if (lockDrag)
                return false;

            dragHelper.captureChildView(mainView, pointerId);
            return false;
        }

        @Override
        public int clampViewPositionHorizontal(@NonNull View child, int left, int dx) {
            switch (dragEdge) {
                case DRAG_EDGE_RIGHT:
                    return Math.max(
                            Math.min(left, rectMainClose.left),
                            rectMainClose.left - rightView.getWidth()
                    );

                case DRAG_EDGE_LEFT:
                    return Math.max(
                            Math.min(left, rectMainClose.left + rightView.getWidth()),
                            rectMainClose.left
                    );

                default:
                    return child.getLeft();
            }
        }

        @Override
        public void onViewReleased(@NonNull View releasedChild, float xvel, float yvel) {
            final boolean velRightExceeded = pxToDp((int) xvel) >= minFlingVelocity;
            final boolean velLeftExceeded = pxToDp((int) xvel) <= -minFlingVelocity;

            // TODO - here we release the view
            final int pivotHorizontal = getHalfwayPivotHorizontal();

            switch (dragEdge) {
                case DRAG_EDGE_RIGHT:
                    if (velRightExceeded) {
                        close(true);
                    } else if (velLeftExceeded) {
                        open(true);
                    } else {
                        if (mainView.getRight() < pivotHorizontal) {
                            open(true);
                        } else {
                            close(true);
                        }
                    }
                    break;

                case DRAG_EDGE_LEFT:
                    if (velRightExceeded) {
                        open(true);
                    } else if (velLeftExceeded) {
                        close(true);
                    } else {
                        if (mainView.getLeft() < pivotHorizontal) {
                            close(true);
                        } else {
                            open(true);
                        }
                    }
                    break;
            }
        }

        @Override
        public void onEdgeDragStarted(int edgeFlags, int pointerId) {
            super.onEdgeDragStarted(edgeFlags, pointerId);

            if (lockDrag) {
                return;
            }

            boolean edgeStartLeft = (dragEdge == DRAG_EDGE_RIGHT)
                    && edgeFlags == ViewDragHelper.EDGE_LEFT;

            boolean edgeStartRight = (dragEdge == DRAG_EDGE_LEFT)
                    && edgeFlags == ViewDragHelper.EDGE_RIGHT;

            if (edgeStartLeft || edgeStartRight) {
                dragHelper.captureChildView(mainView, pointerId);
            }
        }

        @Override
        public void onViewPositionChanged(@NonNull View changedView, int left, int top, int dx, int dy) {
            super.onViewPositionChanged(changedView, left, top, dx, dy);
            ViewCompat.postInvalidateOnAnimation(SwipeRevealLayout.this);
        }
    };

    private int pxToDp(int px) {
        Resources resources = getContext().getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        return (int) (px / ((float) metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT));
    }
}