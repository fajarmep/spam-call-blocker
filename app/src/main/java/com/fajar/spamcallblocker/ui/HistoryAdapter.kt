package com.fajar.spamcallblocker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.fajar.spamcallblocker.R
import com.fajar.spamcallblocker.data.BlockedCall
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private var items: List<BlockedCall>,
    private val onUnblockClick: (BlockedCall) -> Unit,
    private val onWhitelistClick: (BlockedCall) -> Unit,
    private val onLongClick: (BlockedCall) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvPhoneNumber: TextView = view.findViewById(R.id.tvPhoneNumber)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val tvReason: TextView = view.findViewById(R.id.tvReason)
        val btnUnblock: MaterialButton = view.findViewById(R.id.btnUnblock)
        val btnWhitelist: MaterialButton = view.findViewById(R.id.btnWhitelist)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvPhoneNumber.text = item.phoneNumber
        holder.tvTime.text = dateFormat.format(Date(item.timestamp))
        holder.tvReason.text = item.reason
        holder.btnUnblock.setOnClickListener { onUnblockClick(item) }
        holder.btnWhitelist.setOnClickListener { onWhitelistClick(item) }
        holder.itemView.setOnLongClickListener { onLongClick(item); true }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<BlockedCall>) {
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
