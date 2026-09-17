package com.fajar.spamcallblocker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fajar.spamcallblocker.R
import com.fajar.spamcallblocker.data.BlockRule
import com.google.android.material.button.MaterialButton

class RulesAdapter(
    private var items: List<BlockRule>,
    private val onDeleteClick: (BlockRule) -> Unit
) : RecyclerView.Adapter<RulesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRuleType: TextView = view.findViewById(R.id.tvRuleType)
        val tvRulePattern: TextView = view.findViewById(R.id.tvRulePattern)
        val tvRuleNote: TextView = view.findViewById(R.id.tvRuleNote)
        val btnDeleteRule: MaterialButton = view.findViewById(R.id.btnDeleteRule)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rule, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvRuleType.text = item.ruleType.displayName.uppercase()
        holder.tvRulePattern.text = item.pattern

        if (item.note.isNotBlank()) {
            holder.tvRuleNote.visibility = View.VISIBLE
            holder.tvRuleNote.text = item.note
        } else {
            holder.tvRuleNote.visibility = View.GONE
        }

        holder.btnDeleteRule.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<BlockRule>) {
        items = newItems
        notifyDataSetChanged()
    }
}
