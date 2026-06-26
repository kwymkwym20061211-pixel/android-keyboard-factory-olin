package android.keyboard.factory.olin.ui

import android.keyboard.factory.olin.data.KeyboardProjectEntity
import android.keyboard.factory.olin.databinding.ItemProjectBinding
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class ProjectListAdapter(
    private val onItemClick: (KeyboardProjectEntity) -> Unit,
    private val onDeleteClick: (KeyboardProjectEntity) -> Unit,
) : ListAdapter<KeyboardProjectEntity, ProjectListAdapter.ViewHolder>(DIFF) {

    class ViewHolder(val binding: ItemProjectBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProjectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val project = getItem(position)
        holder.binding.projectName.text = project.name
        holder.binding.root.setOnClickListener { onItemClick(project) }
        holder.binding.deleteButton.setOnClickListener { onDeleteClick(project) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<KeyboardProjectEntity>() {
            override fun areItemsTheSame(oldItem: KeyboardProjectEntity, newItem: KeyboardProjectEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: KeyboardProjectEntity, newItem: KeyboardProjectEntity) =
                oldItem == newItem
        }
    }
}
