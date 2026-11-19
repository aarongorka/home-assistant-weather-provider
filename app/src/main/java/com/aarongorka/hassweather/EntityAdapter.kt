package com.aarongorka.hassweather

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter

/** Matches anywhere in the entity ID, and lists everything when the field is empty or already a full match. */
class EntityAdapter(context: Context, private val all: List<String>) :
    ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line, all.toMutableList()) {

    override fun getFilter() = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val query = constraint?.toString()?.trim().orEmpty()
            val matches = when {
                query.isEmpty() || query in all -> all
                else -> all.filter { it.contains(query, ignoreCase = true) }
            }
            return FilterResults().apply {
                values = matches
                count = matches.size
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults) {
            clear()
            addAll(results.values as List<String>)
            notifyDataSetChanged()
        }
    }
}
