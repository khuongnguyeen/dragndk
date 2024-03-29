package io.ndk.dev.dragndk.lib

import android.view.MotionEvent
import android.view.View
import android.view.View.OnLongClickListener
import android.view.View.OnTouchListener
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

abstract class DragItemAdapter<T, VH : DragItemAdapter.ViewHolder?> : RecyclerView.Adapter<VH>() {
    interface DragStartCallback {
        fun startDrag(itemView: View, itemId: Long): Boolean
        val isDragging: Boolean
    }

    private var mDragStartCallback: DragStartCallback? = null
    private var mDragItemId = RecyclerView.NO_ID
    var dropTargetId = RecyclerView.NO_ID
    protected var mItemList: MutableList<Any?>? = null

    /**
     * @return a unique id for an item at the specific position.
     */
    abstract fun getUniqueItemId(position: Int): Long

    init {
        setHasStableIds(true)
    }

    var itemList: MutableList<Any?>?
        get() = mItemList
        set(itemList) {
            mItemList = itemList
            notifyDataSetChanged()
        }

    fun getPositionForItem(item: T): Int {
        val count = itemCount
        for (i in 0 until count) {
            if (mItemList!![i] == item) {
                return i
            }
        }
        return RecyclerView.NO_POSITION
    }

    fun removeItem(pos: Int): Any? {
        if (mItemList != null && mItemList!!.size > pos && pos >= 0) {
            val item: Any? = mItemList?.removeAt(pos)
            notifyItemRemoved(pos)
            return item
        }
        return null
    }

    fun addItem(pos: Int, item: Any?) {
        if (mItemList != null && mItemList!!.size >= pos) {
            mItemList?.add(pos, item)
            notifyItemInserted(pos)
        }
    }

    fun changeItemPosition(fromPos: Int, toPos: Int) {
        if (mItemList != null && mItemList!!.size > fromPos && mItemList!!.size > toPos) {
            val item: Any? = mItemList?.removeAt(fromPos)
            mItemList?.add(toPos, item)
            notifyItemMoved(fromPos, toPos)
        }
    }

    fun swapItems(pos1: Int, pos2: Int) {
        if (mItemList != null && mItemList!!.size > pos1 && mItemList!!.size > pos2) {
            Collections.swap(mItemList!!, pos1, pos2)
            notifyDataSetChanged()
        }
    }

    fun getPositionForItemId(id: Long): Int {
        val count = itemCount
        for (i in 0 until count) {
            if (id == getItemId(i)) {
                return i
            }
        }
        return RecyclerView.NO_POSITION
    }

    override fun getItemId(position: Int): Long {
        return getUniqueItemId(position)
    }

    override fun getItemCount(): Int {
        return if (mItemList == null) 0 else mItemList!!.size
    }

    override fun onBindViewHolder(holder: VH & Any, position: Int) {
        val itemId = getItemId(position)
        holder.mItemId = itemId
        holder.itemView.visibility = if (mDragItemId == itemId) View.INVISIBLE else View.VISIBLE
        holder.setDragStartCallback(mDragStartCallback)
    }

    override fun onViewRecycled(holder: VH & Any) {
        super.onViewRecycled(holder)
        holder.setDragStartCallback(null)
    }

    fun setDragStartedListener(dragStartedListener: DragStartCallback?) {
        mDragStartCallback = dragStartedListener
    }

    fun setDragItemId(dragItemId: Long) {
        mDragItemId = dragItemId
    }

    abstract class ViewHolder(itemView: View, handleResId: Int, dragOnLongPress: Boolean) :
        RecyclerView.ViewHolder(itemView) {
        private var mGrabView: View
        var mItemId: Long = 0
        private var mDragStartCallback: DragStartCallback? = null

        init {
            mGrabView = itemView.findViewById(handleResId)
            if (dragOnLongPress) {
                mGrabView.setOnLongClickListener(OnLongClickListener { view ->
                    if (mDragStartCallback == null) {
                        return@OnLongClickListener false
                    }
                    if (mDragStartCallback!!.startDrag(itemView, mItemId)) {
                        return@OnLongClickListener true
                    }
                    if (itemView === mGrabView) {
                        onItemLongClicked(view)
                    } else false
                })
            } else {
                mGrabView.setOnTouchListener(OnTouchListener { _, event ->
                    if (mDragStartCallback == null) {
                        return@OnTouchListener false
                    }
                    if (event.action == MotionEvent.ACTION_DOWN && mDragStartCallback!!.startDrag(
                            itemView,
                            mItemId
                        )
                    ) {
                        return@OnTouchListener true
                    }
                    if (!mDragStartCallback!!.isDragging && itemView == mGrabView) {
                        onItemTouch()
                    } else false
                })
            }
            itemView.setOnClickListener { view -> onItemClicked(view) }
            if (itemView !== mGrabView) {
                itemView.setOnLongClickListener { view -> onItemLongClicked(view) }
                itemView.setOnTouchListener { _, _ -> onItemTouch() }
            }
        }

        fun setDragStartCallback(dragStartedListener: DragStartCallback?) {
            mDragStartCallback = dragStartedListener
        }

        open fun onItemClicked(view: View?) {}
        open fun onItemLongClicked(view: View?): Boolean {
            return false
        }

        private fun onItemTouch(): Boolean {
            return false
        }
    }
}
