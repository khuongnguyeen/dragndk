package io.ndk.dev.dragndk.sample

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.util.Pair
import io.ndk.dev.dragndk.R
import io.ndk.dev.dragndk.lib.DragItemAdapter

internal class ItemAdapter(
    list: MutableList<Any?>?,
    private val mLayoutId: Int,
    private val mGrabHandleId: Int,
    private val mDragOnLongPress: Boolean
) : DragItemAdapter<Pair<Long, String>, ItemAdapter.ViewHolder?>() {
    init {
        itemList = list
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(mLayoutId, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)
        val text = (mItemList?.get(position) as? Pair<Long, String>)?.second!!
        holder.mText.text = text
        holder.itemView.tag = mItemList?.get(position)
    }

    override fun getUniqueItemId(position: Int): Long {
        return (mItemList?.get(position) as? Pair<Long, String>)?.first!!
    }

    internal inner class ViewHolder(itemView: View) :
        DragItemAdapter.ViewHolder(itemView, mGrabHandleId, mDragOnLongPress) {
        var mText: TextView

        init {
            mText = itemView.findViewById<View>(R.id.text) as TextView
        }

        override fun onItemClicked(view: View?) {
            Toast.makeText(view?.context, "Item clicked", Toast.LENGTH_SHORT).show()
        }

        override fun onItemLongClicked(view: View?): Boolean {
            Toast.makeText(view?.context, "Item long clicked", Toast.LENGTH_SHORT).show()
            return true
        }
    }
}
