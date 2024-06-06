package com.simprints.feature.selectagegroup.screen

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.simprints.feature.selectagegroup.R
import com.simprints.infra.resources.R as IDR


// The age groups should be sorted as follows: Newborn, Baby, Child, Adult
// because the icons are in that order
internal class AgeGroupAdapter(
    private val ageGroups: List<AgeGroupDisplayModel>,
    private val onClick: (AgeGroupDisplayModel) -> Unit
) : RecyclerView.Adapter<AgeGroupAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val view = layoutInflater.inflate(R.layout.age_group_item, parent, false)
        return ViewHolder(view)

    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ageGroup = ageGroups[position]
        holder.bind(ageGroup, position)
    }

    override fun getItemCount() = ageGroups.size

    private val icons = intArrayOf(
        IDR.drawable.ic_age_group_selection_new_born,
        IDR.drawable.ic_age_group_selection_baby,
        IDR.drawable.ic_age_group_selection_child,
        IDR.drawable.ic_age_group_selection_adult
    )

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ageGroupTextView: TextView = itemView.findViewById(R.id.item_label)
        private val ageGroupIcon: ImageView = itemView.findViewById(R.id.item_icon)
        fun bind(ageGroupDisplayModel: AgeGroupDisplayModel, position: Int) {
            ageGroupTextView.text = ageGroupDisplayModel.displayString
            // if the position is greater than the number of icons, use the last icon
            ageGroupIcon.setImageResource(icons.getOrNull(position) ?: icons.last())

            itemView.setOnClickListener {
                onClick(ageGroupDisplayModel)
            }
        }
    }
}
