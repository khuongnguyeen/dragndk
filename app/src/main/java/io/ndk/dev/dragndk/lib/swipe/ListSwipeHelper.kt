package io.ndk.dev.dragndk.lib.swipe

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnItemTouchListener
import io.ndk.dev.dragndk.lib.swipe.ListSwipeItem.SwipeDirection

class ListSwipeHelper(applicationContext: Context?, private var mSwipeListener: OnSwipeListener?) :
    RecyclerView.OnScrollListener(), OnItemTouchListener {

    interface OnSwipeListener {
        fun onItemSwipeStarted(item: ListSwipeItem?)
        fun onItemSwipeEnded(item: ListSwipeItem?, swipedDirection: SwipeDirection?)
        fun onItemSwiping(item: ListSwipeItem?, swipedDistanceX: Float)
    }

    private val mGestureListener: GestureListener
    private val mGestureDetector: GestureDetector
    private var mSwipeView: ListSwipeItem? = null
    private var mRecyclerView: RecyclerView? = null
    private var mTouchSlop = 0

    init {
        mGestureListener = GestureListener()
        mGestureDetector = GestureDetector(applicationContext, mGestureListener)
    }

    override fun onInterceptTouchEvent(rv: RecyclerView, event: MotionEvent): Boolean {
        handleTouch(rv, event)
        return mGestureListener.isSwipeStarted
    }

    override fun onTouchEvent(rv: RecyclerView, event: MotionEvent) {
        handleTouch(rv, event)
    }

    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
        resetSwipedViews(null)
    }

    fun resetSwipedViews(exceptionView: View?) {
        val childCount = mRecyclerView!!.childCount
        for (i in 0 until childCount) {
            val view = mRecyclerView!!.getChildAt(i)
            if (view is ListSwipeItem && view != exceptionView) {
                view.resetSwipe(true)
            }
        }
    }

    private fun handleTouch(rv: RecyclerView, event: MotionEvent) {
        mGestureDetector.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val swipeView = rv.findChildViewUnder(event.x, event.y)
                if (swipeView is ListSwipeItem &&
                    swipeView.supportedSwipeDirection != SwipeDirection.NONE
                ) {
                    mSwipeView = swipeView
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (mSwipeView != null) {
                    val endingSwipeView: ListSwipeItem? = mSwipeView
                    endingSwipeView?.handleSwipeUp(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            if (endingSwipeView.isSwipeStarted) {
                                resetSwipedViews(endingSwipeView)
                            }
                            if (mSwipeListener != null) {
                                mSwipeListener!!.onItemSwipeEnded(
                                    endingSwipeView,
                                    endingSwipeView.swipedDirection
                                )
                            }
                        }
                    })
                } else {
                    resetSwipedViews(null)
                }
                mSwipeView = null
                mRecyclerView!!.requestDisallowInterceptTouchEvent(false)
            }
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
    fun detachFromRecyclerView() {
        if (mRecyclerView != null) {
            mRecyclerView!!.removeOnItemTouchListener(this)
            mRecyclerView!!.removeOnScrollListener(this)
        }
        mRecyclerView = null
    }

    fun attachToRecyclerView(recyclerView: RecyclerView?) {
        mRecyclerView = recyclerView
        mRecyclerView!!.addOnItemTouchListener(this)
        mRecyclerView!!.addOnScrollListener(this)
        mTouchSlop = ViewConfiguration.get(mRecyclerView!!.context).scaledTouchSlop
    }

    fun setSwipeListener(listener: OnSwipeListener?) {
        mSwipeListener = listener
    }

    private inner class GestureListener : SimpleOnGestureListener() {
        var isSwipeStarted = false
            private set

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (e1 == null || e2 == null || mSwipeView == null || mRecyclerView!!.scrollState != RecyclerView.SCROLL_STATE_IDLE) {
                return false
            }
            val diffX = Math.abs(e2.x - e1.x)
            val diffY = Math.abs(e2.y - e1.y)
            if (!isSwipeStarted && diffX > mTouchSlop * 2 && diffX * 0.5f > diffY) {
                isSwipeStarted = true
                mRecyclerView!!.requestDisallowInterceptTouchEvent(true)
                mSwipeView!!.handleSwipeMoveStarted(mSwipeListener)
                if (mSwipeListener != null) {
                    mSwipeListener!!.onItemSwipeStarted(mSwipeView)
                }
            }
            if (isSwipeStarted) {
                mSwipeView!!.handleSwipeMove(
                    -distanceX, mRecyclerView!!.getChildViewHolder(
                        mSwipeView!!
                    )
                )
            }
            return isSwipeStarted
        }

        override fun onDown(e: MotionEvent): Boolean {
            isSwipeStarted = false
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (!canStartSwipe(e1, e2)) {
                return false
            }
            mSwipeView!!.setFlingSpeed(velocityX)
            return true
        }

        private fun canStartSwipe(e1: MotionEvent?, e2: MotionEvent?): Boolean {
            return !(e1 == null || e2 == null || mSwipeView == null || mRecyclerView!!.scrollState != RecyclerView.SCROLL_STATE_IDLE || mSwipeView?.supportedSwipeDirection == SwipeDirection.NONE)
        }
    }
}
