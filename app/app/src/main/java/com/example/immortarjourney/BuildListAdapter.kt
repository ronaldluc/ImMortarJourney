package com.example.immortarjourney

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BuildItem(val name: String)

class BuildListAdapter(private val dataset: Array<BuildItem>) :
    RecyclerView.Adapter<BuildListAdapter.BuildItemViewHolder>() {
    var onItemClick: ((Int, View?) -> Unit)? = null

    class BuildItemViewHolder(private val adapter: BuildListAdapter, myItemView: View) : RecyclerView.ViewHolder(myItemView),
        View.OnClickListener {
        val nameView: TextView = myItemView.findViewById(R.id.build_name)

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            adapter.onItemClick?.invoke(adapterPosition, v)
        }
    }


    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup,
                                    viewType: Int): BuildItemViewHolder {
        // create a new view
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.build_item_layout, parent, false)
        // set the view's size, margins, paddings and layout parameters
        return BuildItemViewHolder(this, itemView)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: BuildItemViewHolder, position: Int) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        holder.nameView.text = dataset[position].name
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = dataset.size
}
