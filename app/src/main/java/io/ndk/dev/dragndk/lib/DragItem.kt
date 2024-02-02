package io.ndk.dev.dragndk.lib

import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout

open class DragItem internal constructor(context: Context?) {
    var dragItemView: View
        private set
    var realDragView: View? = null
        private set
    private var mRealStartX = 0f
    private var mRealStartY = 0f
    private var mOffsetX = 0f
    private var mOffsetY = 0f
    private var mPosX = 0f
    private var mPosY = 0f
    private var mPosTouchDx = 0f
    private var mPosTouchDy = 0f
    private var mAnimationDx = 0f
    private var mAnimationDy = 0f
    private var mCanDragHorizontally = true
    private var mCanDragVertically = true
    var isSnapToTouch = true

    open fun onBindDragView(clickedView: View, dragView: View) {
        val bitmap =
            Bitmap.createBitmap(clickedView.width, clickedView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        clickedView.draw(canvas)
        dragView.background = BitmapDrawable(clickedView.resources, bitmap)
    }

    open fun onMeasureDragView(clickedView: View, dragView: View) {
        dragView.layoutParams =
            FrameLayout.LayoutParams(clickedView.measuredWidth, clickedView.measuredHeight)
        val widthSpec =
            View.MeasureSpec.makeMeasureSpec(clickedView.measuredWidth, View.MeasureSpec.EXACTLY)
        val heightSpec =
            View.MeasureSpec.makeMeasureSpec(clickedView.measuredHeight, View.MeasureSpec.EXACTLY)
        dragView.measure(widthSpec, heightSpec)
    }

    open fun onStartDragAnimation(dragView: View?) {}
    open fun onEndDragAnimation(dragView: View?) {}
    fun canDragHorizontally(): Boolean {
        return mCanDragHorizontally
    }

    fun setCanDragHorizontally(canDragHorizontally: Boolean) {
        mCanDragHorizontally = canDragHorizontally
    }

    fun canDragVertically(): Boolean {
        return mCanDragVertically
    }

    fun setCanDragVertically(canDragVertically: Boolean) {
        mCanDragVertically = canDragVertically
    }

    private fun show() {
        dragItemView.visibility = View.VISIBLE
    }

    fun hide() {
        dragItemView.visibility = View.GONE
        realDragView = null
    }

    val isDragging: Boolean
        get() = dragItemView.visibility == View.VISIBLE

    fun startDrag(startFromView: View, touchX: Float, touchY: Float) {
        show()
        realDragView = startFromView
        onBindDragView(startFromView, dragItemView)
        onMeasureDragView(startFromView, dragItemView)
        onStartDragAnimation(dragItemView)
        mRealStartX =
            startFromView.x - (dragItemView.measuredWidth - startFromView.measuredWidth) / 2f + dragItemView
                .measuredWidth / 2f
        mRealStartY =
            startFromView.y - (dragItemView.measuredHeight - startFromView.measuredHeight) / 2f + dragItemView
                .measuredHeight / 2f
        if (isSnapToTouch) {
            mPosTouchDx = 0f
            mPosTouchDy = 0f
            setPosition(touchX, touchY)
            setAnimationDx(mRealStartX - touchX)
            setAnimationDY(mRealStartY - touchY)
            val pvhX = PropertyValuesHolder.ofFloat("AnimationDx", mAnimationDx, 0f)
            val pvhY = PropertyValuesHolder.ofFloat("AnimationDY", mAnimationDy, 0f)
            val anim = ObjectAnimator.ofPropertyValuesHolder(this, pvhX, pvhY)
            anim.interpolator = DecelerateInterpolator()
            anim.setDuration(ANIMATION_DURATION)
            anim.start()
        } else {
            mPosTouchDx = mRealStartX - touchX
            mPosTouchDy = mRealStartY - touchY
            setPosition(touchX, touchY)
        }
    }

    fun endDrag(endToView: View?, listener: AnimatorListenerAdapter?) {
        onEndDragAnimation(dragItemView)
        val endX =
            endToView!!.x - (dragItemView.measuredWidth - endToView.measuredWidth) / 2f + dragItemView
                .measuredWidth / 2f
        val endY =
            endToView.y - (dragItemView.measuredHeight - endToView.measuredHeight) / 2f + dragItemView
                .measuredHeight / 2f
        val pvhX = PropertyValuesHolder.ofFloat("X", mPosX, endX)
        val pvhY = PropertyValuesHolder.ofFloat("Y", mPosY, endY)
        val anim = ObjectAnimator.ofPropertyValuesHolder(this, pvhX, pvhY)
        anim.interpolator = DecelerateInterpolator()
        anim.setDuration(ANIMATION_DURATION)
        anim.addListener(listener)
        anim.start()
    }

    private fun setAnimationDx(x: Float) {
        mAnimationDx = x
        updatePosition()
    }

    private fun setAnimationDY(y: Float) {
        mAnimationDy = y
        updatePosition()
    }

    @set:Suppress("unused")
    var x: Float
        get() = mPosX
        set(x) {
            mPosX = x
            updatePosition()
        }

    @set:Suppress("unused")
    var y: Float
        get() = mPosY
        set(y) {
            mPosY = y
            updatePosition()
        }

    fun setPosition(touchX: Float, touchY: Float) {
        if (mCanDragHorizontally) {
            mPosX = touchX + mPosTouchDx
        } else {
            mPosX = mRealStartX
            dragItemView.x = mPosX - dragItemView.measuredWidth / 2f
        }
        if (mCanDragVertically) {
            mPosY = touchY + mPosTouchDy
        } else {
            mPosY = mRealStartY
            dragItemView.y = mPosY - dragItemView.measuredHeight / 2f
        }
        updatePosition()
    }

    fun setOffset(offsetX: Float?, offsetY: Float?) {
        mOffsetX = offsetX ?: 0f
        mOffsetY = offsetY ?: 0f
        updatePosition()
    }

    private fun updatePosition() {
        if (mCanDragHorizontally) {
            dragItemView.x = mPosX + mOffsetX + mAnimationDx - dragItemView.measuredWidth / 2f
        }
        if (mCanDragVertically) {
            dragItemView.y = mPosY + mOffsetY + mAnimationDy - dragItemView.measuredHeight / 2f
        }
        dragItemView.invalidate()
    }

    companion object {
        const val ANIMATION_DURATION = 250L
    }

    init {
        dragItemView = View(context)
        hide()
    }
}
