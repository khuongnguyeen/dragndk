package io.ndk.dev.dragndk.sample

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.util.Pair
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import io.ndk.dev.dragndk.R
import io.ndk.dev.dragndk.lib.BoardView
import io.ndk.dev.dragndk.lib.ColumnProperties

class BoardFragment : Fragment() {
    private var boardView: BoardView? = null
    private var columns = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.board_layout, container, false)
        boardView = view.findViewById(R.id.board_view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        resetBoard()
    }

    private fun resetBoard() {
        boardView?.apply {
            clearBoard()
            setCustomDragItem(null)
            setCustomColumnDragItem(null)
        }
        addColumn()
        addColumn()
    }

    private fun addColumn() {
        val mItemArray = ArrayList<Any?>()
        val addItems = 10
        for (i in 0 until addItems) {
            val id = sCreatedItems++.toLong()
            mItemArray.add(Pair(id, "Item $id"))
        }
        val listAdapter = ItemAdapter(mItemArray, R.layout.grid_item, R.id.item_layout, true)

        val layoutManager = GridLayoutManager(context, 5)
        val columnProperties = ColumnProperties.Builder.newBuilder(listAdapter)
            .setLayoutManager(layoutManager)
            .build()
        boardView?.addColumn(columnProperties)
        columns++
    }

    companion object {
        private var sCreatedItems = 0
        fun newInstance(): BoardFragment {
            return BoardFragment()
        }
    }
}
