/*
 * Copyright 2014 Magnus Woxblom
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ndk.dev.dragndk.lib

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.res.Configuration
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Scroller
import androidx.core.util.Pair
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.ndk.dev.dragndk.R
import io.ndk.dev.dragndk.lib.AutoScroller.AutoScrollListener
import io.ndk.dev.dragndk.lib.AutoScroller.AutoScrollMode
import io.ndk.dev.dragndk.lib.AutoScroller.ScrollDirection
import io.ndk.dev.dragndk.lib.DragItemAdapter.DragStartCallback
import io.ndk.dev.dragndk.lib.DragItemRecyclerView.DragItemCallback
import io.ndk.dev.dragndk.lib.DragItemRecyclerView.DragItemListener
import kotlin.math.abs

class BoardView : HorizontalScrollView, AutoScrollListener {
    interface BoardCallback {
        fun canDragItemAtPosition(column: Int, row: Int): Boolean
        fun canDropItemAtPosition(oldColumn: Int, oldRow: Int, newColumn: Int, newRow: Int): Boolean
        fun canDragColumnAtPosition(index: Int): Boolean
        fun canDropColumnAtPosition(oldIndex: Int, newIndex: Int): Boolean
    }

    interface BoardListener {
        fun onItemDragStarted(column: Int, row: Int)
        fun onItemDragEnded(fromColumn: Int, fromRow: Int, toColumn: Int, toRow: Int)
        fun onItemChangedPosition(oldColumn: Int, oldRow: Int, newColumn: Int, newRow: Int)
        fun onFocusedColumnChanged(oldColumn: Int, newColumn: Int)
        fun onColumnDragStarted(position: Int)
        fun onColumnDragChangedPosition(oldPosition: Int, newPosition: Int)
        fun onColumnDragEnded(fromPosition: Int, toPosition: Int)
    }

    enum class ColumnSnapPosition {
        LEFT,
        CENTER,
        RIGHT
    }

    private var mScroller: Scroller? = null
    private var mAutoScroller: AutoScroller? = null
    private var mGestureDetector: GestureDetector? = null
    private var mRootLayout: FrameLayout? = null
    private var mColumnLayout: LinearLayout? = null
    private val mLists = ArrayList<DragItemRecyclerView>()
    private val mHeaders = ArrayList<View>()
    private val mFooters = ArrayList<View>()
    private var mCurrentRecyclerView: DragItemRecyclerView? = null
    private var mDragItem: DragItem? = null
    private var mDragColumn: DragItem? = null
    private var mBoardListener: BoardListener? = null
    private var mBoardCallback: BoardCallback? = null
    private var mSnapToColumnWhenScrolling = true
    private var mSnapToColumnWhenDragging = true
    private var mSnapToColumnInLandscape = false
    private var mSnapPosition = ColumnSnapPosition.CENTER
    private var mCurrentColumn = 0
    private var mTouchX = 0f
    private var mTouchY = 0f
    private var mDragColumnStartScrollX = 0f
    private var mColumnWidth = 0
    private var mColumnSpacing = 0
    private var mBoardEdge = 0
    private var mDragStartColumn = 0
    private var mDragStartRow = 0
    private var mHasLaidOut = false
    private var mDragEnabled = true
    private var mLastDragColumn = RecyclerView.NO_POSITION
    private var mLastDragRow = RecyclerView.NO_POSITION
    private var mDragColumnStartPosition = 0
    private var mSavedState: SavedState? = null

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet) : super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(attrs)
    }

    private fun init(attrs: AttributeSet) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.BoardView)
        mColumnSpacing = a.getDimensionPixelSize(R.styleable.BoardView_columnSpacing, 0)
        mBoardEdge = a.getDimensionPixelSize(R.styleable.BoardView_boardEdges, 0)
        a.recycle()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        val res = resources
        val isPortrait = res.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        mColumnWidth = if (isPortrait) {
            (res.displayMetrics.widthPixels * 0.87).toInt()
        } else {
            (res.displayMetrics.density * 320).toInt()
        }
        mGestureDetector = GestureDetector(context, GestureListener())
        mScroller = Scroller(context, DecelerateInterpolator(1.1f))
        mAutoScroller = AutoScroller(context, this)
        mAutoScroller?.setAutoScrollMode(if (snapToColumnWhenDragging()) AutoScrollMode.COLUMN else AutoScrollMode.POSITION)
        mDragItem = DragItem(context)
        mDragColumn = DragItem(context)
        mDragColumn?.isSnapToTouch = false
        mRootLayout = FrameLayout(context)
        mRootLayout?.layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.MATCH_PARENT
        )
        mColumnLayout = LinearLayout(context)
        mColumnLayout?.orientation = LinearLayout.HORIZONTAL
        mColumnLayout?.layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.MATCH_PARENT
        )
        mColumnLayout?.isMotionEventSplittingEnabled = false
        mRootLayout?.addView(mColumnLayout)
        mRootLayout?.addView(mDragItem?.dragItemView)
        addView(mRootLayout)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        updateBoardSpaces()
        // Snap to closes column after first layout.
        // This is needed so correct column is scrolled to after a rotation.
        if (!mHasLaidOut && mSavedState != null) {
            mCurrentColumn = mSavedState!!.currentColumn
            mSavedState = null
            post { scrollToColumn(mCurrentColumn, false) }
        }
        mHasLaidOut = true
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        val ss = state as SavedState
        super.onRestoreInstanceState(ss.superState)
        mSavedState = ss
        requestLayout()
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        return SavedState(
            superState,
            if (snapToColumnWhenScrolling()) mCurrentColumn else closestSnapColumn
        )
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        val retValue = handleTouchEvent(event)
        return retValue || super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val retValue = handleTouchEvent(event)
        return retValue || super.onTouchEvent(event)
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        if (mLists.size == 0) {
            return false
        }
        mTouchX = event.x
        mTouchY = event.y
        return if (isDragging) {
            when (event.action) {
                MotionEvent.ACTION_MOVE -> if (!mAutoScroller!!.isAutoScrolling) {
                    updateScrollPosition()
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mAutoScroller!!.stopAutoScroll()
                    if (isDraggingColumn) {
                        endDragColumn()
                    } else {
                        mCurrentRecyclerView!!.onDragEnded()
                    }
                    if (snapToColumnWhenScrolling()) {
                        scrollToColumn(getColumnOfList(mCurrentRecyclerView), true)
                    }
                    invalidate()
                }
            }
            true
        } else {
            if (snapToColumnWhenScrolling() && mGestureDetector!!.onTouchEvent(event)) {
                // A page fling occurred, consume event
                return true
            }
            when (event.action) {
                MotionEvent.ACTION_DOWN -> if (!mScroller!!.isFinished) {
                    // View was grabbed during animation
                    mScroller!!.forceFinished(true)
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (snapToColumnWhenScrolling()) {
                    scrollToColumn(closestSnapColumn, true)
                }
            }
            false
        }
    }

    override fun computeScroll() {
        if (!mScroller!!.isFinished && mScroller!!.computeScrollOffset()) {
            val x = mScroller!!.currX
            val y = mScroller!!.currY
            if (scrollX != x || scrollY != y) {
                scrollTo(x, y)
            }

            // If auto scrolling at the same time as the scroller is running,
            // then update the drag item position to prevent stuttering item
            if (mAutoScroller!!.isAutoScrolling && isDragging) {
                if (isDraggingColumn) {
                    mDragColumn!!.setPosition(mTouchX + scrollX - mDragColumnStartScrollX, mTouchY)
                } else {
                    mDragItem!!.setPosition(
                        getRelativeViewTouchX(mCurrentRecyclerView!!.parent as View),
                        getRelativeViewTouchY(mCurrentRecyclerView)
                    )
                }
            }
            ViewCompat.postInvalidateOnAnimation(this)
        } else if (!snapToColumnWhenScrolling()) {
            super.computeScroll()
        }
    }

    override fun onAutoScrollPositionBy(dx: Int, dy: Int) {
        if (isDragging) {
            scrollBy(dx, dy)
            updateScrollPosition()
        } else {
            mAutoScroller!!.stopAutoScroll()
        }
    }

    override fun onAutoScrollColumnBy(columns: Int) {
        if (isDragging) {
            val newColumn = mCurrentColumn + columns
            if (columns != 0 && newColumn >= 0 && newColumn < mLists.size) {
                scrollToColumn(newColumn, true)
            }
            updateScrollPosition()
        } else {
            mAutoScroller!!.stopAutoScroll()
        }
    }

    private fun updateScrollPosition() {
        if (isDraggingColumn) {
            val currentList = getCurrentRecyclerView(mTouchX + scrollX)
            if (mCurrentRecyclerView !== currentList) {
                val oldIndex = getColumnOfList(mCurrentRecyclerView)
                val newIndex = getColumnOfList(currentList)
                if (mBoardCallback == null || mBoardCallback!!.canDropColumnAtPosition(
                        oldIndex,
                        newIndex
                    )
                ) {
                    moveColumn(oldIndex, newIndex)
                }
            }
            // Need to subtract with scrollX at the beginning of the column drag because of how drag item position is calculated
            mDragColumn!!.setPosition(mTouchX + scrollX - mDragColumnStartScrollX, mTouchY)
        } else {
            // Updated event to scrollview coordinates
            val currentList = getCurrentRecyclerView(mTouchX + scrollX)
            if (mCurrentRecyclerView != currentList) {
                getColumnOfList(mCurrentRecyclerView)
                val newColumn = getColumnOfList(currentList)
                val itemId = mCurrentRecyclerView?.dragItemId

                // Check if it is ok to drop the item in the new column first
                val newPosition =
                    currentList!!.getDragPositionForY(getRelativeViewTouchY(currentList))
                if (mBoardCallback == null || mBoardCallback!!.canDropItemAtPosition(
                        mDragStartColumn,
                        mDragStartRow,
                        newColumn,
                        newPosition
                    )
                ) {
                    val item = mCurrentRecyclerView?.removeDragItemAndEnd()
                    if (item != null) {
                        mCurrentRecyclerView = currentList
                        mCurrentRecyclerView?.addDragItemAndStart(
                            getRelativeViewTouchY(
                                mCurrentRecyclerView
                            ), item, itemId
                        )
                        mDragItem?.setOffset(
                            (mCurrentRecyclerView?.parent as? View)?.left?.toFloat(),
                            mCurrentRecyclerView?.top?.toFloat()
                        )
                    }
                }
            }

            // Updated event to list coordinates
            mCurrentRecyclerView?.onDragging(
                getRelativeViewTouchX(mCurrentRecyclerView!!.parent as View),
                getRelativeViewTouchY(mCurrentRecyclerView)
            )
        }
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val scrollEdge = resources.displayMetrics.widthPixels * if (isPortrait) 0.06f else 0.14f
        if (mTouchX > width - scrollEdge && scrollX < mColumnLayout!!.width) {
            mAutoScroller!!.startAutoScroll(ScrollDirection.LEFT)
        } else if (mTouchX < scrollEdge && scrollX > 0) {
            mAutoScroller!!.startAutoScroll(ScrollDirection.RIGHT)
        } else {
            mAutoScroller!!.stopAutoScroll()
        }
        invalidate()
    }

    private fun getRelativeViewTouchX(view: View): Float {
        return mTouchX + scrollX - view.left
    }

    private fun getRelativeViewTouchY(view: View?): Float {
        return mTouchY - view!!.top
    }

    private fun getCurrentRecyclerView(x: Float): DragItemRecyclerView? {
        for (list in mLists) {
            val parent = list.parent as View
            if (parent.left <= x && parent.right > x) {
                return list
            }
        }
        return mCurrentRecyclerView
    }

    private fun getColumnOfList(list: DragItemRecyclerView?): Int {
        var column = 0
        for (i in mLists.indices) {
            val tmpList: RecyclerView = mLists[i]
            if (tmpList === list) {
                column = i
            }
        }
        return column
    }

    private val closestSnapColumn: Int
        get() {
            var column = 0
            var minDiffX = Int.MAX_VALUE
            for (i in mLists.indices) {
                val listParent = mLists[i].parent as View
                val diffX: Int = when (mSnapPosition) {
                    ColumnSnapPosition.LEFT -> {
                        val leftPosX = scrollX
                        abs(listParent.left - leftPosX)
                    }

                    ColumnSnapPosition.CENTER -> {
                        val middlePosX = scrollX + measuredWidth / 2
                        abs(listParent.left + mColumnWidth / 2 - middlePosX)
                    }

                    ColumnSnapPosition.RIGHT -> {
                        val rightPosX = scrollX + measuredWidth
                        abs(listParent.right - rightPosX)
                    }
                }
                if (diffX < minDiffX) {
                    minDiffX = diffX
                    column = i
                }
            }
            return column
        }

    private fun snapToColumnWhenScrolling(): Boolean {
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        return mSnapToColumnWhenScrolling && (isPortrait || mSnapToColumnInLandscape)
    }

    private fun snapToColumnWhenDragging(): Boolean {
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        return mSnapToColumnWhenDragging && (isPortrait || mSnapToColumnInLandscape)
    }

    private val isDraggingColumn: Boolean
        get() = mCurrentRecyclerView != null && mDragColumn!!.isDragging
    private val isDragging: Boolean
        get() = mCurrentRecyclerView != null && (mCurrentRecyclerView!!.isDragging || isDraggingColumn)

    fun getRecyclerView(column: Int): RecyclerView? {
        return if (column >= 0 && column < mLists.size) {
            mLists[column]
        } else null
    }

    fun getAdapter(column: Int): DragItemAdapter<*, *>? {
        return if (column >= 0 && column < mLists.size) {
            mLists[column].adapter as DragItemAdapter<*, *>?
        } else null
    }

    val itemCount: Int
        get() {
            var count = 0
            for (list in mLists) {
                count += list.adapter!!.itemCount
            }
            return count
        }

    fun getItemCount(column: Int): Int {
        return if (mLists.size > column) {
            mLists[column].adapter!!.itemCount
        } else 0
    }

    val columnCount: Int
        get() = mLists.size

    fun getHeaderView(column: Int): View {
        return mHeaders[column]
    }

    fun getFooterView(column: Int): View {
        return mFooters[column]
    }

    /**
     * @return The index of the column with a specific header. If the header can't be found -1 is returned.
     */
    fun getColumnOfHeader(header: View): Int {
        for (i in mHeaders.indices) {
            if (mHeaders[i] == header) {
                return i
            }
        }
        return -1
    }

    /**
     * @return The index of the column with a specific footer. If the footer can't be found -1 is returned.
     */
    fun getColumnOfFooter(footer: View): Int {
        for (i in mFooters.indices) {
            if (mFooters[i] == footer) {
                return i
            }
        }
        return -1
    }

    fun removeItem(column: Int, row: Int) {
        if (!isDragging && mLists.size > column && mLists[column].adapter!!.itemCount > row) {
            val adapter = mLists[column].adapter as DragItemAdapter<*, *>?
            adapter!!.removeItem(row)
        }
    }

    fun addItem(column: Int, row: Int, item: Pair<Long, String>?, scrollToItem: Boolean) {
        if (!isDragging && mLists.size > column && mLists[column].adapter!!.itemCount >= row) {
            val adapter = mLists[column].adapter as DragItemAdapter<*, *>?
            adapter?.addItem(row, item)
            if (scrollToItem) {
                scrollToItem(column, row, false)
            }
        }
    }

    fun moveItem(fromColumn: Int, fromRow: Int, toColumn: Int, toRow: Int, scrollToItem: Boolean) {
        if (!isDragging && mLists.size > fromColumn && mLists[fromColumn].adapter!!
                .itemCount > fromRow && mLists.size > toColumn && mLists[toColumn].adapter!!.itemCount >= toRow
        ) {
            var adapter = mLists[fromColumn].adapter as DragItemAdapter<*, *>?
            val item = adapter!!.removeItem(fromRow)
            adapter = mLists[toColumn].adapter as DragItemAdapter<*, *>?
            adapter!!.addItem(toRow, item)
            if (scrollToItem) {
                scrollToItem(toColumn, toRow, false)
            }
        }
    }

    fun moveItem(itemId: Long, toColumn: Int, toRow: Int, scrollToItem: Boolean) {
        for (i in mLists.indices) {
            val adapter = mLists[i].adapter
            val count = adapter!!.itemCount
            for (j in 0 until count) {
                val id = adapter.getItemId(j)
                if (id == itemId) {
                    moveItem(i, j, toColumn, toRow, scrollToItem)
                    return
                }
            }
        }
    }

    fun replaceItem(column: Int, row: Int, item: Pair<Long, String>?, scrollToItem: Boolean) {
        if (!isDragging && mLists.size > column && mLists[column].adapter!!.itemCount > row) {
            val adapter = mLists[column].adapter as DragItemAdapter<*, *>?
            adapter!!.removeItem(row)
            adapter.addItem(row, item)
            if (scrollToItem) {
                scrollToItem(column, row, false)
            }
        }
    }

    fun scrollToItem(column: Int, row: Int, animate: Boolean) {
        if (!isDragging && mLists.size > column && mLists[column].adapter!!.itemCount > row) {
            mScroller!!.forceFinished(true)
            scrollToColumn(column, animate)
            if (animate) {
                mLists[column].smoothScrollToPosition(row)
            } else {
                mLists[column].scrollToPosition(row)
            }
        }
    }

    fun scrollToColumn(column: Int, animate: Boolean) {
        if (mLists.size <= column) {
            return
        }
        val parent = mLists[column].parent as View
        val parentLayoutParams = parent.layoutParams as MarginLayoutParams
        var newX: Int = when (mSnapPosition) {
            ColumnSnapPosition.LEFT -> parent.left - parentLayoutParams.leftMargin
            ColumnSnapPosition.CENTER -> {
                val indent =
                    (measuredWidth - parent.measuredWidth - parentLayoutParams.leftMargin - parentLayoutParams.rightMargin) / 2
                parent.left - parentLayoutParams.leftMargin - indent
            }

            ColumnSnapPosition.RIGHT -> parent.right + parentLayoutParams.rightMargin - measuredWidth
        }
        val maxScroll = mRootLayout!!.measuredWidth - measuredWidth
        newX = if (newX < 0) 0 else newX
        newX = if (newX > maxScroll) maxScroll else newX
        if (scrollX != newX) {
            mScroller!!.forceFinished(true)
            if (animate) {
                mScroller!!.startScroll(
                    scrollX,
                    scrollY,
                    newX - scrollX,
                    0,
                    SCROLL_ANIMATION_DURATION
                )
                ViewCompat.postInvalidateOnAnimation(this)
            } else {
                scrollTo(newX, scrollY)
            }
        }
        val oldColumn = mCurrentColumn
        mCurrentColumn = column
        if (mBoardListener != null && oldColumn != mCurrentColumn) {
            mBoardListener!!.onFocusedColumnChanged(oldColumn, mCurrentColumn)
        }
    }

    fun clearBoard() {
        val count = mLists.size
        for (i in count - 1 downTo 0) {
            mColumnLayout!!.removeViewAt(i)
            mHeaders.removeAt(i)
            mFooters.removeAt(i)
            mLists.removeAt(i)
        }
    }

    fun removeColumn(column: Int) {
        if (column >= 0 && mLists.size > column) {
            mColumnLayout!!.removeViewAt(column)
            mHeaders.removeAt(column)
            mFooters.removeAt(column)
            mLists.removeAt(column)
            updateBoardSpaces()
        }
    }

    var isDragEnabled: Boolean
        get() = mDragEnabled
        set(enabled) {
            mDragEnabled = enabled
            if (mLists.size > 0) {
                for (list in mLists) {
                    list.isDragEnabled = mDragEnabled
                }
            }
        }
    val focusedColumn: Int
        /**
         * @return The index of the currently focused column. If column snapping is not enabled this will always return 0.
         */
        get() = if (!snapToColumnWhenScrolling()) {
            0
        } else mCurrentColumn

    /**
     * @param width the width of columns in both portrait and landscape. This must be called before [.addColumn] is
     * called for the width to take effect.
     */
    fun setColumnWidth(width: Int) {
        mColumnWidth = width
    }

    /**
     * @param columnSpacing the interval between columns in pixels.
     */
    fun setColumnSpacing(columnSpacing: Int) {
        mColumnSpacing = columnSpacing
        updateBoardSpaces()
    }

    /**
     * @param boardEdge the extra space in pixels on the start and on the end of columns.
     */
    fun setBoardEdge(boardEdge: Int) {
        mBoardEdge = boardEdge
        updateBoardSpaces()
    }

    /**
     * @param snapToColumn true if scrolling should snap to columns. Only applies to portrait mode.
     */
    fun setSnapToColumnsWhenScrolling(snapToColumn: Boolean) {
        mSnapToColumnWhenScrolling = snapToColumn
    }

    /**
     * @param snapToColumn true if dragging should snap to columns when dragging towards the edge. Only applies to portrait mode.
     */
    fun setSnapToColumnWhenDragging(snapToColumn: Boolean) {
        mSnapToColumnWhenDragging = snapToColumn
        mAutoScroller!!.setAutoScrollMode(if (snapToColumnWhenDragging()) AutoScrollMode.COLUMN else AutoScrollMode.POSITION)
    }

    /**
     * @param snapToColumnInLandscape true if dragging should snap to columns when dragging towards the edge also in landscape mode.
     */
    fun setSnapToColumnInLandscape(snapToColumnInLandscape: Boolean) {
        mSnapToColumnInLandscape = snapToColumnInLandscape
        mAutoScroller!!.setAutoScrollMode(if (snapToColumnWhenDragging()) AutoScrollMode.COLUMN else AutoScrollMode.POSITION)
    }

    /**
     * @param snapPosition determines what position a column will snap to. LEFT, CENTER or RIGHT.
     */
    fun setColumnSnapPosition(snapPosition: ColumnSnapPosition) {
        mSnapPosition = snapPosition
    }

    /**
     * @param snapToTouch true if the drag item should snap to touch position when a drag is started.
     */
    fun setSnapDragItemToTouch(snapToTouch: Boolean) {
        mDragItem?.isSnapToTouch = snapToTouch
    }

    fun setBoardListener(listener: BoardListener?) {
        mBoardListener = listener
    }

    fun setBoardCallback(callback: BoardCallback?) {
        mBoardCallback = callback
    }

    /**
     * Set a custom drag item to control the visuals and animations when dragging a list item.
     */
    fun setCustomDragItem(dragItem: DragItem?) {
        val newDragItem = dragItem ?: DragItem(context)
        if (dragItem == null) {
            newDragItem.isSnapToTouch = true
        }
        mDragItem = newDragItem
        mRootLayout?.removeViewAt(1)
        mRootLayout?.addView(mDragItem?.dragItemView)
    }

    /**
     * Set a custom drag item to control the visuals and animations when dragging a column.
     */
    fun setCustomColumnDragItem(dragItem: DragItem?) {
        val newDragItem = dragItem ?: DragItem(context)
        if (dragItem == null) {
            newDragItem.isSnapToTouch = false
        }
        mDragColumn = newDragItem
    }

    private fun startDragColumn(recyclerView: DragItemRecyclerView, posX: Float, posY: Float) {
        mDragColumnStartScrollX = scrollX.toFloat()
        mCurrentRecyclerView = recyclerView
        val columnView = mColumnLayout!!.getChildAt(getColumnOfList(recyclerView))
        mDragColumn?.startDrag(columnView, posX, posY)
        mRootLayout?.addView(mDragColumn?.dragItemView)
        columnView.alpha = 0f
        if (mBoardListener != null) {
            mDragColumnStartPosition = getColumnOfList(mCurrentRecyclerView)
            mBoardListener?.onColumnDragStarted(mDragColumnStartPosition)
        }
    }

    private fun endDragColumn() {
        mDragColumn?.endDrag(mDragColumn?.realDragView, object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                mDragColumn?.realDragView?.alpha = 1f
                mDragColumn?.hide()
                mRootLayout?.removeView(mDragColumn?.dragItemView)
                if (mBoardListener != null) {
                    mBoardListener?.onColumnDragEnded(
                        mDragColumnStartPosition,
                        getColumnOfList(mCurrentRecyclerView)
                    )
                }
            }
        })
    }

    private fun moveColumn(fromIndex: Int, toIndex: Int) {
        val list = mLists.removeAt(fromIndex)
        mLists.add(toIndex, list)
        val header = mHeaders.removeAt(fromIndex)
        mHeaders.add(toIndex, header)
        val footer = mFooters.removeAt(fromIndex)
        mFooters.add(toIndex, footer)
        val column1 = mColumnLayout?.getChildAt(fromIndex)
        val column2 = mColumnLayout?.getChildAt(toIndex)
        mColumnLayout?.removeViewAt(fromIndex)
        mColumnLayout?.addView(column1, toIndex)
        updateBoardSpaces()
        mColumnLayout?.addOnLayoutChangeListener(object : OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int
            ) {
                mColumnLayout!!.removeOnLayoutChangeListener(this)
                column2?.translationX = column2?.translationX!! + column1?.left!! - column2.left
                column2.animate()?.translationX(0f)?.setDuration(350)?.start()
            }
        })
        if (mBoardListener != null) {
            mBoardListener?.onColumnDragChangedPosition(fromIndex, toIndex)
        }
    }

    fun insertColumn(
        adapter: DragItemAdapter<*, *>,
        index: Int,
        header: View?,
        columnDragView: View?,
        hasFixedItemSize: Boolean
    ): DragItemRecyclerView {
        return insertColumn(
            adapter, index, header, columnDragView, hasFixedItemSize, LinearLayoutManager(
                context
            )
        )
    }

    fun insertColumn(
        adapter: DragItemAdapter<*, *>,
        index: Int,
        header: View?,
        columnDragView: View?,
        hasFixedItemSize: Boolean,
        layoutManager: RecyclerView.LayoutManager
    ): DragItemRecyclerView {
        val columnProperties: ColumnProperties =
            ColumnProperties.Builder.Companion.newBuilder(adapter)
                .setHeader(header)
                .setColumnDragView(columnDragView)
                .setHasFixedItemSize(hasFixedItemSize)
                .setLayoutManager(layoutManager)
                .build()
        return addColumnTo(index, columnProperties)
    }

    /**
     * Inserts a column to the board at a specific index.
     *
     * @param index            Index where on the board to add the column.
     * @param columnProperties Properties of the column to be added.
     */
    fun insertColumn(index: Int, columnProperties: ColumnProperties) {
        addColumnTo(index, columnProperties)
    }

    fun addColumn(
        adapter: DragItemAdapter<*, *>,
        header: View?,
        columnDragView: View?,
        hasFixedItemSize: Boolean
    ): DragItemRecyclerView {
        return addColumn(
            adapter, header, columnDragView, hasFixedItemSize, LinearLayoutManager(
                context
            )
        )
    }

    fun addColumn(
        adapter: DragItemAdapter<*, *>,
        header: View?,
        columnDragView: View?,
        hasFixedItemSize: Boolean,
        layoutManager: RecyclerView.LayoutManager
    ): DragItemRecyclerView {
        val columnProperties: ColumnProperties =
            ColumnProperties.Builder.Companion.newBuilder(adapter)
                .setHeader(header)
                .setColumnDragView(columnDragView)
                .setHasFixedItemSize(hasFixedItemSize)
                .setLayoutManager(layoutManager)
                .build()
        return addColumnTo(columnCount, columnProperties)
    }

    /**
     * Adds a column at the last index of the board.
     *
     * @param columnProperties Properties of the column to be added.
     */
    fun addColumn(columnProperties: ColumnProperties) {
        addColumnTo(columnCount, columnProperties)
    }

    private fun addColumnTo(index: Int, columnProperties: ColumnProperties): DragItemRecyclerView {
        require(index <= columnCount) { "Index is out of bounds" }
        val recyclerView = LayoutInflater.from(context)
            .inflate(R.layout.drag_item_recycler_view, this, false) as DragItemRecyclerView
        recyclerView.id = columnCount
        recyclerView.isHorizontalScrollBarEnabled = false
        recyclerView.isVerticalScrollBarEnabled = false
        recyclerView.isMotionEventSplittingEnabled = false
        recyclerView.setDragItem(mDragItem)
        recyclerView.layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        val layoutManager = columnProperties.layoutManager
        recyclerView.layoutManager = layoutManager ?: LinearLayoutManager(
            context
        )
        recyclerView.setBackgroundColor(columnProperties.itemsSectionBackgroundColor)
        recyclerView.setHasFixedSize(columnProperties.hasFixedItemSize())
        val itemDecorations = columnProperties.itemDecorations
        for (i in itemDecorations.indices) {
            recyclerView.addItemDecoration(itemDecorations[i])
        }
        recyclerView.itemAnimator = DefaultItemAnimator()
        recyclerView.setDragItemListener(object : DragItemListener {
            override fun onDragStarted(itemPosition: Int, x: Float, y: Float) {
                mDragStartColumn = getColumnOfList(recyclerView)
                mDragStartRow = itemPosition
                mCurrentRecyclerView = recyclerView
                mDragItem!!.setOffset(
                    (mCurrentRecyclerView!!.parent as View).x,
                    mCurrentRecyclerView!!.y
                )
                if (mBoardListener != null) {
                    mBoardListener!!.onItemDragStarted(mDragStartColumn, mDragStartRow)
                }
                invalidate()
            }

            override fun onDragging(itemPosition: Int, x: Float, y: Float) {
                val column = getColumnOfList(recyclerView)
                val positionChanged = column != mLastDragColumn || itemPosition != mLastDragRow
                if (mBoardListener != null && positionChanged) {
                    mLastDragColumn = column
                    mLastDragRow = itemPosition
                    mBoardListener!!.onItemChangedPosition(
                        mDragStartColumn,
                        mDragStartRow,
                        column,
                        itemPosition
                    )
                }
            }

            override fun onDragEnded(newItemPosition: Int) {
                mLastDragColumn = RecyclerView.NO_POSITION
                mLastDragRow = RecyclerView.NO_POSITION
                if (mBoardListener != null) {
                    mBoardListener!!.onItemDragEnded(
                        mDragStartColumn,
                        mDragStartRow,
                        getColumnOfList(recyclerView),
                        newItemPosition
                    )
                }
            }
        })
        recyclerView.setDragItemCallback(object : DragItemCallback {
            override fun canDragItemAtPosition(dragPosition: Int): Boolean {
                val column = getColumnOfList(recyclerView)
                return mBoardCallback == null || mBoardCallback!!.canDragItemAtPosition(
                    column,
                    dragPosition
                )
            }

            override fun canDropItemAtPosition(dropPosition: Int): Boolean {
                val column = getColumnOfList(recyclerView)
                return mBoardCallback == null || mBoardCallback!!.canDropItemAtPosition(
                    mDragStartColumn,
                    mDragStartRow,
                    column,
                    dropPosition
                )
            }
        })
        val adapter = columnProperties.dragItemAdapter
        adapter.setDragStartedListener(object : DragStartCallback {
            override fun startDrag(itemView: View, itemId: Long): Boolean {
                return recyclerView.startDrag(
                    itemView,
                    itemId,
                    getRelativeViewTouchX(recyclerView.parent as View),
                    getRelativeViewTouchY(recyclerView)
                )
            }

            override val isDragging: Boolean
                get() = recyclerView.isDragging
        })
        recyclerView.adapter = adapter
        recyclerView.isDragEnabled = mDragEnabled
        val columnBackgroundDrawable = columnProperties.columnBackgroundDrawable
        recyclerView.setBackgroundDrawable(columnBackgroundDrawable)
        var specificColumnWidth = columnProperties.columnWidth
        specificColumnWidth = specificColumnWidth ?: mColumnWidth
        val layout = LinearLayout(context)
        layout.setBackgroundColor(columnProperties.columnBackgroundColor)
        layout.orientation = LinearLayout.VERTICAL
        layout.layoutParams = LayoutParams(specificColumnWidth, LayoutParams.MATCH_PARENT)
        var columnHeader = columnProperties.header
        if (columnHeader == null) {
            columnHeader = View(context)
            columnHeader.visibility = GONE
        }
        layout.addView(columnHeader)
        mHeaders.add(columnHeader)
        layout.addView(recyclerView)
        mLists.add(index, recyclerView)
        var columnFooter = columnProperties.footer
        if (columnFooter == null) {
            columnFooter = View(context)
            columnFooter.visibility = GONE
        }
        layout.addView(columnFooter)
        mFooters.add(columnFooter)
        mColumnLayout!!.addView(layout, index)
        updateBoardSpaces()
        setupColumnDragListener(columnProperties.columnDragView, recyclerView)
        return recyclerView
    }

    private fun setupColumnDragListener(columnDragView: View?, recyclerView: DragItemRecyclerView) {
        columnDragView?.setOnLongClickListener {
            if (mBoardCallback == null || mBoardCallback!!.canDragColumnAtPosition(
                    getColumnOfList(
                        recyclerView
                    )
                )
            ) {
                startDragColumn(recyclerView, mTouchX, mTouchY)
                true
            } else {
                false
            }
        }
    }

    private fun updateBoardSpaces() {
        val columnCount = columnCount
        val oneSideIntervalSpace = mColumnSpacing / 2
        for (childPosition in 0 until columnCount) {
            val child = mColumnLayout!!.getChildAt(childPosition)
            val layoutParams: MarginLayoutParams = child.layoutParams as LinearLayout.LayoutParams
            when (childPosition) {
                0 -> {
                    layoutParams.leftMargin = mBoardEdge
                    layoutParams.rightMargin = oneSideIntervalSpace
                }
                columnCount - 1 -> {
                    layoutParams.leftMargin = oneSideIntervalSpace
                    layoutParams.rightMargin = mBoardEdge
                }
                else -> {
                    layoutParams.leftMargin = oneSideIntervalSpace
                    layoutParams.rightMargin = oneSideIntervalSpace
                }
            }
        }
    }

    private inner class GestureListener : SimpleOnGestureListener() {
        private var mStartScrollX = 0f
        private var mStartColumn = 0
        override fun onDown(e: MotionEvent): Boolean {
            mStartScrollX = scrollX.toFloat()
            mStartColumn = mCurrentColumn
            return super.onDown(e)
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            // Calc new column to scroll to
            val closestColumn: Int = closestSnapColumn
            var newColumn = closestColumn

            // This can happen if you start to drag in one direction and then fling in the other direction.
            // We should then switch column in the fling direction.
            val wrongSnapDirection =
                newColumn > mStartColumn && velocityX > 0 || newColumn < mStartColumn && velocityX < 0
            if (mStartScrollX == scrollX.toFloat()) {
                newColumn = mStartColumn
            } else if (mStartColumn == closestColumn || wrongSnapDirection) {
                newColumn = if (velocityX < 0) {
                    closestColumn + 1
                } else {
                    closestColumn - 1
                }
            }
            if (newColumn < 0 || newColumn > mLists.size - 1) {
                newColumn = if (newColumn < 0) 0 else mLists.size - 1
            }

            // Calc new scrollX position
            scrollToColumn(newColumn, true)
            return true
        }
    }

    internal class SavedState : BaseSavedState {
        var currentColumn: Int

        constructor(superState: Parcelable?, currentColumn: Int) : super(superState) {
            this.currentColumn = currentColumn
        }

        constructor(source: Parcel) : super(source) {
            currentColumn = source.readInt()
        }

        override fun describeContents(): Int {
            return 0
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeInt(currentColumn)
        }

    }

    companion object {
        private const val SCROLL_ANIMATION_DURATION = 325
    }
}
