package io.ndk.dev.dragndk.lib

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import java.util.Collections

class ColumnProperties private constructor(
    val dragItemAdapter: DragItemAdapter<*, *>,
    val layoutManager: RecyclerView.LayoutManager?,
    val itemDecorations: List<ItemDecoration>,
    private val mHasFixedItemSize: Boolean,
    @get:ColorInt val columnBackgroundColor: Int,
    @get:ColorInt val itemsSectionBackgroundColor: Int,
    val columnDragView: View?,
    val header: View?,
    val footer: View?,
    val columnWidth: Int?,
    val columnBackgroundDrawable: Drawable?
) {

    fun hasFixedItemSize(): Boolean {
        return mHasFixedItemSize
    }

    /**
     * Builder for [ColumnProperties].
     */
    class Builder private constructor(private val mDragItemAdapter: DragItemAdapter<*, *>) {
        private var mLayoutManager: RecyclerView.LayoutManager? = null
        private val mItemDecoration = ArrayList<ItemDecoration>()
        private var mHasFixedItemSize = false
        private var mColumnBackgroundColor = Color.TRANSPARENT
        private var mItemsSectionBackgroundColor = Color.TRANSPARENT
        private var mHeader: View? = null
        private var mFooter: View? = null
        private var mColumnDragView: View? = null
        private var mColumnWidth: Int? = null
        private var mColumnBackgroundDrawable: Drawable? = null

        fun setLayoutManager(layoutManager: RecyclerView.LayoutManager?): Builder {
            mLayoutManager = layoutManager
            return this
        }

        fun addItemDecorations(vararg itemDecorations: ItemDecoration): Builder {
            Collections.addAll(mItemDecoration, *itemDecorations)
            return this
        }

        /**
         * This method is used for [RecyclerView.setHasFixedSize] of items'.
         * Set true if the items will have a fixed size and false if dynamic.
         *
         * @param hasFixedItemSize If the items will have a fixed or dynamic size. Default value is false.
         *
         * @return instance of the [Builder]
         */
        fun setHasFixedItemSize(hasFixedItemSize: Boolean): Builder {
            mHasFixedItemSize = hasFixedItemSize
            return this
        }

        /**
         * Sets background color to whole the column.
         *
         * @param backgroundColor Color int value. Default value is [Color.TRANSPARENT].
         *
         * @return instance of the [Builder]
         */
        fun setColumnBackgroundColor(@ColorInt backgroundColor: Int): Builder {
            mColumnBackgroundColor = backgroundColor
            return this
        }

        /**
         * Sets background color to the items area only.
         *
         * @param backgroundColor Color int value. Default value is [Color.TRANSPARENT].
         *
         * @return instance of the [Builder]
         */
        fun setItemsSectionBackgroundColor(@ColorInt backgroundColor: Int): Builder {
            mItemsSectionBackgroundColor = backgroundColor
            return this
        }

        /**
         * Sets header view that will be positioned above the column
         *
         * @param header View that will be positioned above the column. Default value is null.
         *
         * @return instance of the [Builder]
         */
        fun setHeader(header: View?): Builder {
            mHeader = header
            return this
        }

        /**
         * Sets footer view that will be positioned below the column
         *
         * @param footer View that will be positioned below the column. Default value is null.
         *
         * @return instance of the [Builder]
         */
        fun setFooter(footer: View?): Builder {
            mFooter = footer
            return this
        }

        /**
         * Sets View that will act as handle to drag and drop columns. Can be null.
         *
         * @param columnDragView View that will act as handle to drag and drop columns. Default value is null.
         *
         * @return instance of the [Builder]
         */
        fun setColumnDragView(columnDragView: View?): Builder {
            mColumnDragView = columnDragView
            return this
        }

        /**
         * Sets the width for this specific column.
         *
         * @param columnWidth Width for this specific column.
         *
         * @return instance of the [Builder]
         */
        fun setColumnWidth(columnWidth: Int?): Builder {
            mColumnWidth = columnWidth
            return this
        }

        /**
         * Sets background drawable to the column.
         *
         * @param backgroundDrawable Drawable to set as background.
         *
         * @return instance of the [Builder]
         */
        fun setColumnBackgroundDrawable(backgroundDrawable: Drawable?): Builder {
            mColumnBackgroundDrawable = backgroundDrawable
            return this
        }

        /**
         * Builds a [ColumnProperties] with the settled parameters
         *
         * @return the [ColumnProperties] instance
         */
        fun build(): ColumnProperties {
            return ColumnProperties(
                mDragItemAdapter,
                mLayoutManager,
                mItemDecoration,
                mHasFixedItemSize,
                mColumnBackgroundColor,
                mItemsSectionBackgroundColor,
                mColumnDragView,
                mHeader,
                mFooter,
                mColumnWidth,
                mColumnBackgroundDrawable
            )
        }

        companion object {
            /**
             * Create the [Builder] instance with the items' adapter [DragItemAdapter]
             *
             * @param adapter Adapter with the items for the column.
             *
             * @return instance of the [Builder]
             */
            fun newBuilder(adapter: DragItemAdapter<*, *>): Builder {
                return Builder(adapter)
            }
        }
    }
}
