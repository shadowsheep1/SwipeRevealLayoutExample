package me.markosullivan.swiperevealactionbuttons

import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
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

/**
 * Created by Mark O'Sullivan on 25th February 2018.
 * Heavy modified by shadowsheep on October 2019.
 */

class SwipeRevealLayout : ViewGroup {

    /**
     * Main view is the view which is shown when the layout is closed.
     */
    private var mainView: View? = null

    /**
     * The view which is shown when the layout is opened from right to left
     */
    private var rightView: View? = null

    /**
     * The view which is shown when the layout is opened from left to right
     */
    private var leftView: View? = null

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

    private var isOpenBeforeInit = false
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

    private var dragHelper: ViewDragHelper? = null
    private var gestureDetector: GestureDetectorCompat? = null

    private val mainOpenLeft: Int
        get() {
            when (dragEdge) {
                DRAG_EDGE_LEFT or DRAG_EDGE_RIGHT -> return if (draggingFromRightToLeft()) {
                    rectMainClose.left - leftView!!.width
                } else {
                    rectMainClose.left + rightView!!.width
                }
                DRAG_EDGE_LEFT -> return rectMainClose.left + rightView!!.width

                DRAG_EDGE_RIGHT -> return rectMainClose.left - rightView!!.width


                else -> return 0
            }
        }

    private val mainOpenTop: Int
        get() {
            when (dragEdge) {
                DRAG_EDGE_LEFT, DRAG_EDGE_RIGHT -> return rectMainClose.top


                else -> return 0
            }
        }

    private val rightOpenLeft: Int
        get() = rectRightClose.left

    private val leftOpenRight: Int
        get() = rectLeftClose.left

    private val secOpenTop: Int
        get() = rectRightClose.top

    private val draggedDistance: Float
        get() = Math.abs(dragDist)

    private val mGestureListener = object : GestureDetector.SimpleOnGestureListener() {
        internal var hasDisallowed = false

        override fun onDown(e: MotionEvent): Boolean {
            isScrolling = false
            hasDisallowed = false
            return true
        }

        override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            isScrolling = true
            return false
        }

        override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
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

    private val distToClosestEdge: Int
        get() {
            when (dragEdge) {
                DRAG_EDGE_LEFT or DRAG_EDGE_RIGHT -> if (draggingFromRightToLeft()) {
                    val pivotLeft = rectMainClose.right - rightView!!.width

                    return Math.min(
                            mainView!!.right - pivotLeft,
                            rectMainClose.right - mainView!!.right
                    )
                } else {
                    val pivotRight = rectMainClose.left + leftView!!.width

                    return Math.min(
                            mainView!!.left - rectMainClose.left,
                            pivotRight - mainView!!.left
                    )
                }

                DRAG_EDGE_LEFT -> {
                    val pivotRight = rectMainClose.left + rightView!!.width

                    return Math.min(
                            mainView!!.left - rectMainClose.left,
                            pivotRight - mainView!!.left
                    )
                }

                DRAG_EDGE_RIGHT -> {
                    val pivotLeft = rectMainClose.right - rightView!!.width

                    return Math.min(
                            mainView!!.right - pivotLeft,
                            rectMainClose.right - mainView!!.right
                    )
                }
            }

            return 0
        }

    private//if (dragEdge == (DRAG_EDGE_RIGHT | DRAG_EDGE_LEFT)) {
    val halfwayPivotHorizontal: Int
        get() = if (dragEdge == DRAG_EDGE_LEFT) {
            rectMainClose.left + rightView!!.width / 2
        } else if (dragEdge == DRAG_EDGE_RIGHT) {
            rectMainClose.right - rightView!!.width / 2
        } else {
            if (draggingFromRightToLeft()) {
                rectMainClose.right - rightView!!.width / 2
            } else {
                rectMainClose.left + leftView!!.width / 2
            }
        }

    private val dragHelperCallback = object : ViewDragHelper.Callback() {
        override fun tryCaptureView(child: View, pointerId: Int): Boolean {

            if (isDragLocked)
                return false

            dragHelper!!.captureChildView(mainView!!, pointerId)
            return false
        }

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int {
            when (dragEdge) {
                DRAG_EDGE_RIGHT -> return Math.max(
                        Math.min(left, rectMainClose.left),
                        rectMainClose.left - rightView!!.width
                )

                DRAG_EDGE_LEFT -> return Math.max(
                        Math.min(left, rectMainClose.left + rightView!!.width),
                        rectMainClose.left
                )

                DRAG_EDGE_LEFT or DRAG_EDGE_RIGHT -> return if (draggingFromRightToLeft()) {
                    Math.max(
                            Math.min(left, rectMainClose.left),
                            rectMainClose.left - rightView!!.width
                    )
                } else {
                    Math.max(
                            Math.min(left, rectMainClose.left + leftView!!.width),
                            rectMainClose.left
                    )
                }

                else -> return child.left
            }
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            val velRightExceeded = pxToDp(xvel.toInt()) >= minFlingVelocity
            val velLeftExceeded = pxToDp(xvel.toInt()) <= -minFlingVelocity

            // TODO - here we release the view
            val pivotHorizontal = halfwayPivotHorizontal

            // Update rectMainOpen
            // open position of the main view
            rectMainOpen.set(
                    mainOpenLeft,
                    mainOpenTop,
                    mainOpenLeft + mainView!!.width,
                    mainOpenTop + mainView!!.height
            )

            when (dragEdge) {
                DRAG_EDGE_LEFT or DRAG_EDGE_RIGHT -> if (draggingFromRightToLeft()) {
                    if (velRightExceeded) {
                        close(true)
                    } else if (velLeftExceeded) {
                        open(true)
                    } else {
                        if (mainView!!.right < pivotHorizontal) {
                            open(true)
                        } else {
                            close(true)
                        }
                    }
                } else {
                    if (velRightExceeded) {
                        open(true)
                    } else if (velLeftExceeded) {
                        close(true)
                    } else {
                        if (mainView!!.left < pivotHorizontal) {
                            close(true)
                        } else {
                            open(true)
                        }
                    }
                }
                DRAG_EDGE_RIGHT -> if (velRightExceeded) {
                    close(true)
                } else if (velLeftExceeded) {
                    open(true)
                } else {
                    if (mainView!!.right < pivotHorizontal) {
                        open(true)
                    } else {
                        close(true)
                    }
                }

                DRAG_EDGE_LEFT -> if (velRightExceeded) {
                    open(true)
                } else if (velLeftExceeded) {
                    close(true)
                } else {
                    if (mainView!!.left < pivotHorizontal) {
                        close(true)
                    } else {
                        open(true)
                    }
                }
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
                dragHelper!!.captureChildView(mainView!!, pointerId)
            }
        }

        override fun onViewPositionChanged(changedView: View, left: Int, top: Int, dx: Int, dy: Int) {
            super.onViewPositionChanged(changedView, left, top, dx, dy)
            ViewCompat.postInvalidateOnAnimation(this@SwipeRevealLayout)
        }
    }

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {}

    override fun onSaveInstanceState(): Parcelable? {
        val bundle = Bundle()
        bundle.putParcelable(SUPER_INSTANCE_STATE, super.onSaveInstanceState())
        return super.onSaveInstanceState()
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        var state = state
        val bundle = state as Bundle?
        state = bundle!!.getParcelable(SUPER_INSTANCE_STATE)
        super.onRestoreInstanceState(state)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector!!.onTouchEvent(event)
        dragHelper!!.processTouchEvent(event)
        return true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isDragLocked && isInMainView(ev)) {
            close(true)
            return super.onInterceptTouchEvent(ev)
        }

        dragHelper!!.processTouchEvent(ev)
        gestureDetector!!.onTouchEvent(ev)
        accumulateDragDist(ev)

        val couldBecomeClick = couldBecomeClick(ev)
        val settling = dragHelper!!.viewDragState == ViewDragHelper.STATE_SETTLING
        val idleAfterScrolled = dragHelper!!.viewDragState == ViewDragHelper.STATE_IDLE && isScrolling

        // must be placed as the last statement
        prevX = ev.x

        // return true => intercept, cannot trigger onClick event
        return !couldBecomeClick && (settling || idleAfterScrolled)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        val children = childCount
        // get views
        when (children) {
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
            val maxRight = Math.max(r - paddingRight - l, 0)
            val minTop = paddingTop
            val maxBottom = Math.max(b - paddingBottom - t, 0)

            var measuredChildHeight = child.measuredHeight
            var measuredChildWidth = child.measuredWidth

            // need to take account if child size is match_parent
            val childParams = child.layoutParams
            var matchParentHeight = false
            var matchParentWidth = false

            if (childParams != null) {
                matchParentHeight = childParams.height == ViewGroup.LayoutParams.MATCH_PARENT
                matchParentWidth = childParams.width == ViewGroup.LayoutParams.MATCH_PARENT
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
                DRAG_EDGE_LEFT or DRAG_EDGE_RIGHT -> if (0 == index) { // right view
                    left = Math.max(r - measuredChildWidth - paddingRight - l, minLeft)
                    top = Math.min(paddingTop, maxBottom)
                    right = Math.max(r - paddingRight - l, minLeft)
                    bottom = Math.min(measuredChildHeight + paddingTop, maxBottom)
                } else { // left view
                    left = Math.min(paddingLeft, maxRight)
                    top = Math.min(paddingTop, maxBottom)
                    right = Math.min(measuredChildWidth + paddingLeft, maxRight)
                    bottom = Math.min(measuredChildHeight + paddingTop, maxBottom)
                }

                DRAG_EDGE_RIGHT -> {
                    left = Math.max(r - measuredChildWidth - paddingRight - l, minLeft)
                    top = Math.min(paddingTop, maxBottom)
                    right = Math.max(r - paddingRight - l, minLeft)
                    bottom = Math.min(measuredChildHeight + paddingTop, maxBottom)
                }

                DRAG_EDGE_LEFT -> {
                    left = Math.min(paddingLeft, maxRight)
                    top = Math.min(paddingTop, maxBottom)
                    right = Math.min(measuredChildWidth + paddingLeft, maxRight)
                    bottom = Math.min(measuredChildHeight + paddingTop, maxBottom)
                }
            }

            child.layout(left, top, right, bottom)
        }

        initRects()

        if (isOpenBeforeInit) {
            open(false)
        } else {
            close(false)
        }

    }

    /**
     * {@inheritDoc}
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var widthMeasureSpec = widthMeasureSpec
        var heightMeasureSpec = heightMeasureSpec
        if (childCount < 2) {
            throw RuntimeException("Layout must have two children")
        }

        val params = layoutParams

        val widthMode = View.MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = View.MeasureSpec.getMode(heightMeasureSpec)

        var desiredWidth = 0
        var desiredHeight = 0

        // first find the largest child
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            desiredWidth = Math.max(child.measuredWidth, desiredWidth)
            desiredHeight = Math.max(child.measuredHeight, desiredHeight)
        }
        // create new measure spec using the largest child width
        widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(desiredWidth, widthMode)
        heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(desiredHeight, heightMode)

        val measuredWidth = View.MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = View.MeasureSpec.getSize(heightMeasureSpec)

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val childParams = child.layoutParams

            if (childParams != null) {
                if (childParams.height == ViewGroup.LayoutParams.MATCH_PARENT) {
                    child.minimumHeight = measuredHeight
                }

                if (childParams.width == ViewGroup.LayoutParams.MATCH_PARENT) {
                    child.minimumWidth = measuredWidth
                }
            }

            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            desiredWidth = Math.max(child.measuredWidth, desiredWidth)
            desiredHeight = Math.max(child.measuredHeight, desiredHeight)
        }

        // taking accounts of padding
        desiredWidth += paddingLeft + paddingRight
        desiredHeight += paddingTop + paddingBottom

        // adjust desired width
        if (widthMode == View.MeasureSpec.EXACTLY) {
            desiredWidth = measuredWidth
        } else {
            if (params.width == ViewGroup.LayoutParams.MATCH_PARENT) {
                desiredWidth = measuredWidth
            }

            if (widthMode == View.MeasureSpec.AT_MOST) {
                desiredWidth = if (desiredWidth > measuredWidth) measuredWidth else desiredWidth
            }
        }

        // adjust desired height
        if (heightMode == View.MeasureSpec.EXACTLY) {
            desiredHeight = measuredHeight
        } else {
            if (params.height == ViewGroup.LayoutParams.MATCH_PARENT) {
                desiredHeight = measuredHeight
            }

            if (heightMode == View.MeasureSpec.AT_MOST) {
                desiredHeight = if (desiredHeight > measuredHeight) measuredHeight else desiredHeight
            }
        }

        setMeasuredDimension(desiredWidth, desiredHeight)
    }

    override fun computeScroll() {
        if (dragHelper!!.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    /**
     * Open the panel to show the secondary view
     */
    fun open(animation: Boolean) {
        // TODO - here we open the menu
        isOpenBeforeInit = true

        if (animation) {
            dragHelper!!.smoothSlideViewTo(mainView!!, rectMainOpen.left, rectMainOpen.top)
        } else {
            dragHelper!!.abort()

            mainView!!.layout(
                    rectMainOpen.left,
                    rectMainOpen.top,
                    rectMainOpen.right,
                    rectMainOpen.bottom
            )

            rightView!!.layout(
                    rectRightOpen.left,
                    rectRightOpen.top,
                    rectRightOpen.right,
                    rectRightOpen.bottom
            )

            leftView!!.layout(
                    rectLeftOpen.left,
                    rectLeftOpen.top,
                    rectLeftOpen.right,
                    rectLeftOpen.bottom
            )
        }

        ViewCompat.postInvalidateOnAnimation(this)
        isDragLocked = true
    }

    /**
     * Close the panel to hide the secondary view
     */
    fun close(animation: Boolean) {
        // TODO - here we hide the menu
        isOpenBeforeInit = false

        if (animation) {
            dragHelper!!.smoothSlideViewTo(mainView!!, rectMainClose.left, rectMainClose.top)
        } else {
            dragHelper!!.abort()

            mainView!!.layout(
                    rectMainClose.left,
                    rectMainClose.top,
                    rectMainClose.right,
                    rectMainClose.bottom
            )

            rightView!!.layout(
                    rectRightClose.left,
                    rectRightClose.top,
                    rectRightClose.right,
                    rectRightClose.bottom
            )

            leftView!!.layout(
                    rectLeftClose.left,
                    rectLeftClose.top,
                    rectLeftClose.right,
                    rectLeftClose.bottom
            )
        }

        ViewCompat.postInvalidateOnAnimation(this)
        isDragLocked = false
    }

    private fun initRects() {
        // close position of main view
        rectMainClose.set(
                mainView!!.left,
                mainView!!.top,
                mainView!!.right,
                mainView!!.bottom
        )

        // close position of right view
        rectRightClose.set(
                rightView!!.left,
                rightView!!.top,
                rightView!!.right,
                rightView!!.bottom
        )

        // close position of left view
        rectLeftClose.set(
                leftView!!.left,
                leftView!!.top,
                leftView!!.right,
                leftView!!.bottom
        )

        // open position of the main view
        rectMainOpen.set(
                mainOpenLeft,
                mainOpenTop,
                mainOpenLeft + mainView!!.width,
                mainOpenTop + mainView!!.height
        )

        // open position of the right view
        rectRightOpen.set(
                rightOpenLeft,
                secOpenTop,
                rightOpenLeft + rightView!!.width,
                secOpenTop + rightView!!.height
        )

        // open position of the left view
        rectLeftOpen.set(
                leftOpenRight,
                secOpenTop,
                leftOpenRight + leftView!!.width,
                secOpenTop + leftView!!.height
        )
    }

    private fun couldBecomeClick(ev: MotionEvent): Boolean {
        return isInMainView(ev) && !shouldInitiateADrag()
    }

    private fun isInMainView(ev: MotionEvent): Boolean {
        val x = ev.x
        val y = ev.y

        val withinVertical = mainView!!.top <= y && y <= mainView!!.bottom
        val withinHorizontal = mainView!!.left <= x && x <= mainView!!.right

        return withinVertical && withinHorizontal
    }

    private fun shouldInitiateADrag(): Boolean {
        val minDistToInitiateDrag = dragHelper!!.touchSlop.toFloat()
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
        dragHelper!!.setEdgeTrackingEnabled(ViewDragHelper.EDGE_ALL)

        gestureDetector = GestureDetectorCompat(context, mGestureListener)
    }

    private fun pxToDp(px: Int): Int {
        val resources = context.resources
        val metrics = resources.displayMetrics
        return (px / (metrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)).toInt()
    }

    companion object {

        private val SUPER_INSTANCE_STATE = "saved_instance_state_parcelable"

        private val DEFAULT_MIN_FLING_VELOCITY = 300 // dp per second
        private val DEFAULT_MIN_DIST_REQUEST_DISALLOW_PARENT = 1 // dp

        val DRAG_EDGE_LEFT = 0x1 // -->
        val DRAG_EDGE_RIGHT = 0x1 shl 1 // <--
    }
}