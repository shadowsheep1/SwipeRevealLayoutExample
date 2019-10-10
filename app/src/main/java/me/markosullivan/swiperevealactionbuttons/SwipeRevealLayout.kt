package me.markosullivan.swiperevealactionbuttons

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.customview.widget.ViewDragHelper
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Converted and Maintain by shadowsheep on October 2019.
 */

class SwipeRevealLayout : ViewGroup {

    /**
     * Main view is the view which is shown when the layout is closed.
     */
    private lateinit var mainView: View

    /**
     * The view which is shown when the layout is opened from right to left
     */
    private lateinit var rightView: View

    /**
     * The view which is shown when the layout is opened from left to right
     */
    private lateinit var leftView: View

    /**
     * The rectangle position of the main view when the layout is closed.
     */
    private val rectMainClose = Rect()

    /**
     * The rectangle position of the main view when the layout is opened.
     */
    private val rectMainOpen = Rect()

    /**
     * The rectangle position of the right view when the layout is closed.
     */
    private val rectRightClose = Rect()

    /**
     * The rectangle position of the right view when the layout is opened.
     */
    private val rectRightOpen = Rect()

    /**
     * The rectangle position of the left view when the layout is closed.
     */
    private val rectLeftClose = Rect()

    /**
     * The rectangle position of the left view when the layout is opened.
     */
    private val rectLeftOpen = Rect()

    /**
     * The minimum distance (px) to the closest drag edge that the SwipeRevealLayout
     * will disallow the parent to intercept touch event.
     */
    private var minDistRequestDisallowParent = 0

    private var isOpen = false
    @Volatile
    private var isScrolling = false
    /**
     * @return true if the drag/swipe motion is currently locked.
     */
    @Volatile
    @get:Synchronized
    var isDragLocked = false
        private set

    private var minFlingVelocity = DEFAULT_MIN_FLING_VELOCITY

    private var dragEdge = DRAG_EDGE_LEFT

    private var dragDist = 0f
    private var prevX = -1f

    private lateinit var dragHelper: ViewDragHelper
    private lateinit var gestureDetector: GestureDetectorCompat

    //region mainOpenLeft
    private val mainOpenLeft: Int
        get() {
            return when (dragEdge) {
                DRAG_EDGE_LEFT or DRAG_EDGE_RIGHT ->
                    if (draggingFromRightToLeft()) {
                        mainOpenLeftForRightEdge()
                    } else {
                        mainOpenLeftForLeftEdge(leftView.width)
                    }
                DRAG_EDGE_LEFT -> mainOpenLeftForLeftEdge(rightView.width)

                DRAG_EDGE_RIGHT -> mainOpenLeftForRightEdge()

                else -> 0
            }
        }

    private fun mainOpenLeftForRightEdge() = rectMainClose.left - rightView.width
    private fun mainOpenLeftForLeftEdge(leftWidth: Int) = rectMainClose.left + leftWidth
    //endregion

    private val mainOpenTop: Int
        get() {
            return when (dragEdge) {
                DRAG_EDGE_LEFT,
                DRAG_EDGE_RIGHT,
                DRAG_EDGE_LEFT or DRAG_EDGE_RIGHT -> rectMainClose.top

                else -> 0
            }
        }

    private val rightOpenLeft: Int
        get() = rectRightClose.left

    private val leftOpenRight: Int
        get() = rectLeftClose.left

    private val secOpenTop: Int
        get() = rectRightClose.top

    private val draggedDistance: Float
        get() = abs(dragDist)

    //region Gesture Detector
    private val mGestureListener = object : GestureDetector.SimpleOnGestureListener() {
        var hasDisallowed = false

        override fun onDown(e: MotionEvent): Boolean {
            isScrolling = false
            hasDisallowed = false
            return true
        }

        override fun onFling(e1: MotionEvent,
                             e2: MotionEvent,
                             velocityX: Float,
                             velocityY: Float): Boolean {
            isScrolling = true
            return false
        }

        override fun onScroll(e1: MotionEvent,
                              e2: MotionEvent,
                              distanceX: Float,
                              distanceY: Float): Boolean {
            isScrolling = true

            if (parent != null) {
                val shouldDisallow: Boolean

                if (!hasDisallowed) {
                    shouldDisallow = distToClosestEdge >= minDistRequestDisallowParent
                    if (shouldDisallow) {
                        hasDisallowed = true
                    }
                } else {
                    shouldDisallow = true
                }

                // disallow parent to intercept touch event so that the layout will work
                // properly on RecyclerView or view that handles scroll gesture.
                parent.requestDisallowInterceptTouchEvent(shouldDisallow)
            }

            return false
        }
    }
    //endregion

    //region distToClosestEdge
    private val distToClosestEdge: Int
        get() {
            when (dragEdge) {
                DRAG_EDGE_LEFT or DRAG_EDGE_RIGHT ->
                    return if (draggingFromRightToLeft()) {
                        distToClosestEdgeForRightEdge()
                    } else {
                        distToClosestEdgeForLeftEdge(leftView.width)
                    }

                DRAG_EDGE_LEFT -> {
                    distToClosestEdgeForLeftEdge(rightView.width)
                }

                DRAG_EDGE_RIGHT -> {
                    return distToClosestEdgeForRightEdge()
                }
            }

            return 0
        }

    private fun distToClosestEdgeForRightEdge(): Int {
        val pivotLeft = rectMainClose.right - rightView.width
        return min(
                mainView.right - pivotLeft,
                rectMainClose.right - mainView.right
        )
    }

    private fun distToClosestEdgeForLeftEdge(leftWidth: Int): Int {
        val pivotRight = rectMainClose.left + leftWidth
        return min(
                mainView.left - rectMainClose.left,
                pivotRight - mainView.left
        )
    }
    //endregion

    //region halfwayPivotHorizontal
    private val halfwayPivotHorizontal: Int
        get() = if (dragEdge == DRAG_EDGE_LEFT) {
            halfwayPivotHorizontalForLeftEdge(rightView.width)
        } else if (dragEdge == DRAG_EDGE_RIGHT) {
            halfwayPivotHorizontalForRightEdge()
        } else {
            if (draggingFromRightToLeft()) {
                halfwayPivotHorizontalForRightEdge()
            } else {
                halfwayPivotHorizontalForLeftEdge(leftView.width)
            }
        }

    private fun halfwayPivotHorizontalForRightEdge() = rectMainClose.right - rightView.width / 2
    private fun halfwayPivotHorizontalForLeftEdge(leftWidth: Int) = rectMainClose.right + leftWidth / 2
    //endregion

    //region reset layout
    @Suppress("unused")
    fun resetLayout() {
        if (isOpen) {
            close(false)
        }
    }
    //endregion

    //region Drag Helper
    private val dragHelperCallback = object : ViewDragHelper.Callback() {
        override fun tryCaptureView(child: View, pointerId: Int): Boolean {

            if (isDragLocked)
                return false

            dragHelper.captureChildView(mainView, pointerId)
            return false
        }

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int {
            when (dragEdge) {
                DRAG_EDGE_RIGHT -> return clampViewPositionHorizontalForRightEdge(left)

                DRAG_EDGE_LEFT -> return clampViewPositionHorizontalForLeftEdge(
                        left,
                        rightView.width
                )

                DRAG_EDGE_LEFT or DRAG_EDGE_RIGHT ->
                    return if (draggingFromRightToLeft()) {
                        clampViewPositionHorizontalForRightEdge(left)
                    } else {
                        clampViewPositionHorizontalForLeftEdge(left, leftView.width)
                    }

                else -> return child.left
            }
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            val velRightExceeded = pxToDp(xvel.toInt()) >= minFlingVelocity
            val velLeftExceeded = pxToDp(xvel.toInt()) <= -minFlingVelocity

            // OKAY - here we release the view
            val pivotHorizontal = halfwayPivotHorizontal

            // Update rectMainOpen
            // open position of the main view
            rectMainOpen.set(
                    mainOpenLeft,
                    mainOpenTop,
                    mainOpenLeft + mainView.width,
                    mainOpenTop + mainView.height
            )

            when (dragEdge) {
                DRAG_EDGE_LEFT or DRAG_EDGE_RIGHT ->
                    if (draggingFromRightToLeft()) {
                        onViewReleasedForRightEdge(
                                velRightExceeded,
                                velLeftExceeded,
                                pivotHorizontal
                        )
                    } else {
                        onViewReleasedForLeftEdge(
                                velRightExceeded,
                                velLeftExceeded,
                                pivotHorizontal
                        )
                    }
                DRAG_EDGE_RIGHT ->
                    onViewReleasedForRightEdge(velRightExceeded, velLeftExceeded, pivotHorizontal)

                DRAG_EDGE_LEFT ->
                    onViewReleasedForLeftEdge(velRightExceeded, velLeftExceeded, pivotHorizontal)
            }
        }

        override fun onEdgeDragStarted(edgeFlags: Int, pointerId: Int) {
            super.onEdgeDragStarted(edgeFlags, pointerId)

            if (isDragLocked) {
                return
            }

            val edgeStartLeft = dragEdge == DRAG_EDGE_RIGHT && edgeFlags == ViewDragHelper.EDGE_LEFT

            val edgeStartRight = dragEdge == DRAG_EDGE_LEFT && edgeFlags == ViewDragHelper.EDGE_RIGHT

            if (edgeStartLeft || edgeStartRight) {
                dragHelper.captureChildView(mainView, pointerId)
            }
        }

        override fun onViewPositionChanged(changedView: View,
                                           left: Int,
                                           top: Int,
                                           dx: Int,
                                           dy: Int) {
            super.onViewPositionChanged(changedView, left, top, dx, dy)
            ViewCompat.postInvalidateOnAnimation(this@SwipeRevealLayout)
        }
    }

    private fun onViewReleasedForLeftEdge(velRightExceeded: Boolean,
                                          velLeftExceeded: Boolean,
                                          pivotHorizontal: Int) {
        if (velRightExceeded) {
            open(true)
        } else if (velLeftExceeded) {
            close(true)
        } else {
            if (mainView.left < pivotHorizontal) {
                close(true)
            } else {
                open(true)
            }
        }
    }

    private fun onViewReleasedForRightEdge(velRightExceeded: Boolean,
                                           velLeftExceeded: Boolean,
                                           pivotHorizontal: Int) {
        if (velRightExceeded) {
            close(true)
        } else if (velLeftExceeded) {
            open(true)
        } else {
            if (mainView.right < pivotHorizontal) {
                open(true)
            } else {
                close(true)
            }
        }
    }

    private fun clampViewPositionHorizontalForRightEdge(left: Int): Int {
        return max(
                min(left, rectMainClose.left),
                rectMainClose.left - rightView.width
        )
    }

    private fun clampViewPositionHorizontalForLeftEdge(left: Int, leftWidth: Int): Int {
        return max(
                min(left, rectMainClose.left + leftWidth),
                rectMainClose.left
        )
    }
    //endregion

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context, attrs)
    }

    //region Save Instance State
    override fun onSaveInstanceState(): Parcelable? {
        val bundle = Bundle()
        bundle.putParcelable(SUPER_INSTANCE_STATE, super.onSaveInstanceState())
        return super.onSaveInstanceState()
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        val bundle = state as Bundle?
        val myState = bundle?.getParcelable(SUPER_INSTANCE_STATE) as Parcelable?
        super.onRestoreInstanceState(myState)
    }
    //endregion

    // TODO - Manage accessibility!
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        dragHelper.processTouchEvent(event)
        return true
    }

    // https://developer.android.com/training/gestures/viewgroup
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isDragLocked && isInMainView(ev) && isOpen) {
            close(true)
            return super.onInterceptTouchEvent(ev)
        }

        dragHelper.processTouchEvent(ev)
        gestureDetector.onTouchEvent(ev)
        accumulateDragDist(ev)

        val couldBecomeClick = couldBecomeClick(ev)
        val settling = dragHelper.viewDragState == ViewDragHelper.STATE_SETTLING
        val idleAfterScrolled = dragHelper.viewDragState == ViewDragHelper.STATE_IDLE && isScrolling

        // must be placed as the last statement
        prevX = ev.x

        // return true => intercept, cannot trigger onClick event
        return !couldBecomeClick && (settling || idleAfterScrolled)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        // get views
        when (childCount) {
            3 -> {
                rightView = getChildAt(0)
                leftView = getChildAt(1)
                mainView = getChildAt(2)
            }
            2 -> {
                rightView = getChildAt(0)
                mainView = getChildAt(1)
            }
            1 -> mainView = getChildAt(0)
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (index in 0 until childCount) {
            val child = getChildAt(index)

            var left: Int
            var right: Int
            var top: Int
            var bottom: Int
            bottom = 0
            top = bottom
            right = top
            left = right

            val minLeft = paddingLeft
            val maxRight = max(r - paddingRight - l, 0)
            val minTop = paddingTop
            val maxBottom = max(b - paddingBottom - t, 0)

            var measuredChildHeight = child.measuredHeight
            var measuredChildWidth = child.measuredWidth

            // need to take account if child size is match_parent
            val childParams = child.layoutParams
            var matchParentHeight = false
            var matchParentWidth = false

            if (childParams != null) {
                matchParentHeight = childParams.height == LayoutParams.MATCH_PARENT
                matchParentWidth = childParams.width == LayoutParams.MATCH_PARENT
            }

            if (matchParentHeight) {
                measuredChildHeight = maxBottom - minTop
                childParams!!.height = measuredChildHeight
            }

            if (matchParentWidth) {
                measuredChildWidth = maxRight - minLeft
                childParams!!.width = measuredChildWidth
            }

            when (dragEdge) {
                DRAG_EDGE_LEFT or DRAG_EDGE_RIGHT ->
                    if (0 == index) { // right view
                        left = max(r - measuredChildWidth - paddingRight - l, minLeft)
                        top = min(paddingTop, maxBottom)
                        right = max(r - paddingRight - l, minLeft)
                        bottom = min(measuredChildHeight + paddingTop, maxBottom)
                    } else { // left view
                        left = min(paddingLeft, maxRight)
                        top = min(paddingTop, maxBottom)
                        right = min(measuredChildWidth + paddingLeft, maxRight)
                        bottom = min(measuredChildHeight + paddingTop, maxBottom)
                    }

                DRAG_EDGE_RIGHT -> {
                    left = max(r - measuredChildWidth - paddingRight - l, minLeft)
                    top = min(paddingTop, maxBottom)
                    right = max(r - paddingRight - l, minLeft)
                    bottom = min(measuredChildHeight + paddingTop, maxBottom)
                }

                DRAG_EDGE_LEFT -> {
                    left = min(paddingLeft, maxRight)
                    top = min(paddingTop, maxBottom)
                    right = min(measuredChildWidth + paddingLeft, maxRight)
                    bottom = min(measuredChildHeight + paddingTop, maxBottom)
                }
            }

            child.layout(left, top, right, bottom)
        }

        initRects()

        if (isOpen) {
            open(false)
        } else {
            close(false)
        }

    }

    /**
     * {@inheritDoc}
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var myWidthMeasureSpec = widthMeasureSpec
        var myHeightMeasureSpec = heightMeasureSpec
        if (childCount < 2) {
            throw RuntimeException("Layout must have two children")
        }

        val params = layoutParams

        val widthMode = MeasureSpec.getMode(myWidthMeasureSpec)
        val heightMode = MeasureSpec.getMode(myHeightMeasureSpec)

        var desiredWidth = 0
        var desiredHeight = 0

        // first find the largest child
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            measureChild(child, myWidthMeasureSpec, myHeightMeasureSpec)
            desiredWidth = max(child.measuredWidth, desiredWidth)
            desiredHeight = max(child.measuredHeight, desiredHeight)
        }
        // create new measure spec using the largest child width
        myWidthMeasureSpec = MeasureSpec.makeMeasureSpec(desiredWidth, widthMode)
        myHeightMeasureSpec = MeasureSpec.makeMeasureSpec(desiredHeight, heightMode)

        val measuredWidth = MeasureSpec.getSize(myWidthMeasureSpec)
        val measuredHeight = MeasureSpec.getSize(myHeightMeasureSpec)

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val childParams = child.layoutParams

            if (childParams != null) {
                if (childParams.height == LayoutParams.MATCH_PARENT) {
                    child.minimumHeight = measuredHeight
                }

                if (childParams.width == LayoutParams.MATCH_PARENT) {
                    child.minimumWidth = measuredWidth
                }
            }

            measureChild(child, myWidthMeasureSpec, myHeightMeasureSpec)
            desiredWidth = max(child.measuredWidth, desiredWidth)
            desiredHeight = max(child.measuredHeight, desiredHeight)
        }

        // taking accounts of padding
        desiredWidth += paddingLeft + paddingRight
        desiredHeight += paddingTop + paddingBottom

        // adjust desired width
        if (widthMode == MeasureSpec.EXACTLY) {
            desiredWidth = measuredWidth
        } else {
            if (params.width == LayoutParams.MATCH_PARENT) {
                desiredWidth = measuredWidth
            }

            if (widthMode == MeasureSpec.AT_MOST) {
                desiredWidth = if (desiredWidth > measuredWidth) measuredWidth else desiredWidth
            }
        }

        // adjust desired height
        if (heightMode == MeasureSpec.EXACTLY) {
            desiredHeight = measuredHeight
        } else {
            if (params.height == LayoutParams.MATCH_PARENT) {
                desiredHeight = measuredHeight
            }

            if (heightMode == MeasureSpec.AT_MOST) {
                desiredHeight = if (desiredHeight > measuredHeight) measuredHeight else desiredHeight
            }
        }

        setMeasuredDimension(desiredWidth, desiredHeight)
    }

    override fun computeScroll() {
        if (dragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    /**
     * Open the panel to show the secondary view
     */
    private fun open(animation: Boolean) {
        // OKAY - here we open the menu
        isOpen = true

        if (animation) {
            dragHelper.smoothSlideViewTo(mainView, rectMainOpen.left, rectMainOpen.top)
        } else {
            dragHelper.abort()

            mainView.layout(
                    rectMainOpen.left,
                    rectMainOpen.top,
                    rectMainOpen.right,
                    rectMainOpen.bottom
            )

            if ((dragEdge and DRAG_EDGE_RIGHT) != 0) {
                rightView.layout(
                        rectRightOpen.left,
                        rectRightOpen.top,
                        rectRightOpen.right,
                        rectRightOpen.bottom
                )
            }

            if (dragEdge == DRAG_EDGE_LEFT and DRAG_EDGE_RIGHT) {
                leftView.layout(
                        rectLeftOpen.left,
                        rectLeftOpen.top,
                        rectLeftOpen.right,
                        rectLeftOpen.bottom
                )
            }
        }

        ViewCompat.postInvalidateOnAnimation(this)
        isDragLocked = true
    }

    /**
     * Close the panel to hide the secondary view
     */
    private fun close(animation: Boolean) {
        // OKAY - here we hide the menu
        isOpen = false

        if (animation) {
            dragHelper.smoothSlideViewTo(mainView, rectMainClose.left, rectMainClose.top)
        } else {
            dragHelper.abort()

            mainView.layout(
                    rectMainClose.left,
                    rectMainClose.top,
                    rectMainClose.right,
                    rectMainClose.bottom
            )

            if ((dragEdge and DRAG_EDGE_RIGHT) != 0) {
                rightView.layout(
                        rectRightClose.left,
                        rectRightClose.top,
                        rectRightClose.right,
                        rectRightClose.bottom
                )
            }

            if (dragEdge == DRAG_EDGE_LEFT and DRAG_EDGE_RIGHT) {
                leftView.layout(
                        rectLeftClose.left,
                        rectLeftClose.top,
                        rectLeftClose.right,
                        rectLeftClose.bottom
                )
            }
        }

        ViewCompat.postInvalidateOnAnimation(this)
        isDragLocked = false
    }

    private fun initRects() {
        // close position of main view
        rectMainClose.set(
                mainView.left,
                mainView.top,
                mainView.right,
                mainView.bottom
        )

        // open position of the main view
        rectMainOpen.set(
                mainOpenLeft,
                mainOpenTop,
                mainOpenLeft + mainView.width,
                mainOpenTop + mainView.height
        )

        if ((dragEdge and DRAG_EDGE_RIGHT) != 0) {
            // close position of right view
            rectRightClose.set(
                    rightView.left,
                    rightView.top,
                    rightView.right,
                    rightView.bottom
            )

            // open position of the right view
            rectRightOpen.set(
                    rightOpenLeft,
                    secOpenTop,
                    rightOpenLeft + rightView.width,
                    secOpenTop + rightView.height
            )
        }

        if (dragEdge == DRAG_EDGE_LEFT and DRAG_EDGE_RIGHT) {
            // close position of left view
            rectLeftClose.set(
                    leftView.left,
                    leftView.top,
                    leftView.right,
                    leftView.bottom
            )

            // open position of the left view
            rectLeftOpen.set(
                    leftOpenRight,
                    secOpenTop,
                    leftOpenRight + leftView.width,
                    secOpenTop + leftView.height
            )
        }
    }

    private fun couldBecomeClick(ev: MotionEvent): Boolean {
        return isInMainView(ev) && !shouldInitiateADrag()
    }

    private fun isInMainView(ev: MotionEvent): Boolean {
        val x = ev.x
        val y = ev.y

        val withinVertical = mainView.top <= y && y <= mainView.bottom
        val withinHorizontal = mainView.left <= x && x <= mainView.right

        return withinVertical && withinHorizontal
    }

    private fun shouldInitiateADrag(): Boolean {
        val minDistToInitiateDrag = dragHelper.touchSlop.toFloat()
        return draggedDistance >= minDistToInitiateDrag
    }

    private fun accumulateDragDist(ev: MotionEvent) {
        val action = ev.action
        if (action == MotionEvent.ACTION_DOWN) {
            dragDist = 0f
            return
        }

        val signedDraggedDistance = ev.x - prevX
        dragDist += signedDraggedDistance
        Timber.i("Drag distance %s", signedDraggedDistance)
        Timber.i("Total drag distance %s", dragDist)
    }

    private fun draggingFromRightToLeft(): Boolean {
        return dragDist < 0
    }

    private fun init(context: Context?, attrs: AttributeSet?) {
        if (attrs != null && context != null) {
            val a = context.theme.obtainStyledAttributes(
                    attrs,
                    R.styleable.SwipeRevealLayout,
                    0,
                    0
            )

            dragEdge = a.getInteger(R.styleable.SwipeRevealLayout_dragFromEdge, DRAG_EDGE_LEFT)
            minFlingVelocity = DEFAULT_MIN_FLING_VELOCITY
            minDistRequestDisallowParent = DEFAULT_MIN_DIST_REQUEST_DISALLOW_PARENT
        }

        dragHelper = ViewDragHelper.create(this, 1.0f, dragHelperCallback)
        dragHelper.setEdgeTrackingEnabled(ViewDragHelper.EDGE_ALL)

        gestureDetector = GestureDetectorCompat(context, mGestureListener)
    }

    private fun pxToDp(px: Int): Int {
        val resources = context.resources
        val metrics = resources.displayMetrics
        return (px / (metrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)).toInt()
    }

    companion object {
        private val SUPER_INSTANCE_STATE =
                SwipeRevealLayout::class.java.name + "saved_instance_state_parcelable"

        private const val DEFAULT_MIN_FLING_VELOCITY = 300 // dp per second
        private const val DEFAULT_MIN_DIST_REQUEST_DISALLOW_PARENT = 1 // dp

        const val DRAG_EDGE_LEFT = 0x1 // -->
        const val DRAG_EDGE_RIGHT = 0x1 shl 1 // <--
    }
}