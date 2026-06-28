package android.keyboard.template.dictionary.ui

import android.keyboard.template.databinding.ItemWordBinding
import android.keyboard.template.dictionary.data.WordEntity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class WordAdapter(
    private val onItemClick: (WordEntity) -> Unit,
    private val onDeleteClick: (WordEntity) -> Unit,
) : ListAdapter<WordEntity, WordAdapter.ViewHolder>(DIFF) {

    class ViewHolder(val binding: ItemWordBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val word = getItem(position)
        holder.binding.wordText.text = "${word.reading} → ${word.target}"
        holder.binding.root.setOnClickListener { onItemClick(word) }
        holder.binding.deleteButton.setOnClickListener { onDeleteClick(word) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<WordEntity>() {
            override fun areItemsTheSame(oldItem: WordEntity, newItem: WordEntity) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: WordEntity, newItem: WordEntity) = oldItem == newItem
        }
    }
}
