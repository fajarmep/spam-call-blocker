package com.fajar.spamcallblocker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.fajar.spamcallblocker.R
import com.fajar.spamcallblocker.data.WhitelistItem
import com.google.android.material.button.MaterialButton

class WhitelistAdapter(
    private var items: List<WhitelistItem>,
    private val onDeleteClick: (WhitelistItem) -> Unit
) : RecyclerView.Adapter<WhitelistAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvWhiteNumber: TextView = view.findViewById(R.id.tvWhiteNumber)
        val tvWhiteNote: TextView = view.findViewById(R.id.tvWhiteNote)
        val btnDeleteWhite: MaterialButton = view.findViewById(R.id.btnDeleteWhite)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_whitelist, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvWhiteNumber.text = item.phoneNumber
        if (item.note.isNotBlank()) {
            holder.tvWhiteNote.visibility = View.VISIBLE
            holder.tvWhiteNote.text = item.note
        } else {
            holder.tvWhiteNote.visibility = View.GONE
        }
        holder.btnDeleteWhite.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<WhitelistItem>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(o: Int, n: Int) = items[o].id == newItems[n].id
            override fun areContentsTheSame(o: Int, n: Int) = items[o] == newItems[n]
        })
        items = newItems
        diff.dispatchUpdatesTo(this)
    }
}
