package fr.geobert.efficio.adapter

import android.support.v7.widget.*
import android.view.View
import android.widget.*
import fr.geobert.efficio.R
import fr.geobert.efficio.data.Task
import kotlin.properties.Delegates

class TaskViewHolder(val view: View, isHeader: Boolean, val listener: TaskViewHolderListener) :
        RecyclerView.ViewHolder(view), CompoundButton.OnCheckedChangeListener {
    interface TaskViewHolderListener {
        fun onDoneStateChanged(task: Task)
        fun onItemClicked(task: Task)
        fun onQtyClicked(task: Task)
    }

    var name: TextView by Delegates.notNull()
    var depName: TextView by Delegates.notNull()
    var checkbox: CheckBox by Delegates.notNull()
    var cardView: CardView by Delegates.notNull()
    var content: View by Delegates.notNull()
    var task: Task by Delegates.notNull()
    var qty: Button by Delegates.notNull()

    private var isListenerActive: Boolean = true

    init {
        if (!isHeader) {
            name = view.findViewById(R.id.name) as TextView
            checkbox = view.findViewById(R.id.task_checkbox) as CheckBox
            cardView = view.findViewById(R.id.item_row_layout) as CardView
            content = view.findViewById(R.id.card_content)
            depName = view.findViewById(R.id.dep_name) as TextView
            qty = view.findViewById(R.id.qty_btn) as Button
            checkbox.setOnCheckedChangeListener(this)
            content.setOnClickListener { v -> onClicked(v) }

        }
    }

    private fun onClicked(v: View) {
        listener.onItemClicked(task)
    }

    override fun onCheckedChanged(p0: CompoundButton?, isChecked: Boolean) {
        if (isListenerActive) {
            task.isDone = isChecked
            listener.onDoneStateChanged(task)
            cardView.isSelected = isChecked
            setBgColor(isChecked)
        }
    }

    fun setBgColor(isChecked: Boolean) {
        cardView.alpha = if (isChecked) 0.5f else 1.0f
    }

    fun bind(task: Task) {
        synchronized(this) {
            this.task = task
            if (task.type == TaskAdapter.VIEW_TYPES.Normal) {
                isListenerActive = false
                checkbox.isChecked = task.isDone
                name.text = task.item.name
                depName.text = view.context.getString(R.string.dep_name).
                        format(task.item.department.name)
                cardView.isSelected = task.isDone
                qty.text = task.qty.toString()
                qty.setOnClickListener { v ->
                    listener.onQtyClicked(task)
                }
                setBgColor(task.isDone)
                isListenerActive = true
            }
        }
    }
}