package com.example.slimbrowser.ui.browser.actions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.slimbrowser.databinding.ItemBrowserMoreActionBinding

class BrowserMoreActionsAdapter(
    private val onActionSelected: (BrowserMoreAction) -> Unit,
) : ListAdapter<BrowserMoreActionItem, BrowserMoreActionsAdapter.ActionViewHolder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionViewHolder =
        ActionViewHolder(
            ItemBrowserMoreActionBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            ),
            onActionSelected,
        )

    override fun onBindViewHolder(holder: ActionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ActionViewHolder(
        private val binding: ItemBrowserMoreActionBinding,
        private val onActionSelected: (BrowserMoreAction) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: BrowserMoreActionItem) {
            val button = binding.root
            button.setText(item.labelRes)
            button.icon = AppCompatResources.getDrawable(button.context, item.iconRes)
            button.isEnabled = item.isEnabled
            button.isCheckable = item.action == BrowserMoreAction.TOGGLE_DESKTOP_MODE ||
                item.action == BrowserMoreAction.TOGGLE_BOOKMARK
            button.isChecked = item.isSelected
            button.contentDescription = button.text
            button.setOnClickListener { onActionSelected(item.action) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<BrowserMoreActionItem>() {
        override fun areItemsTheSame(
            oldItem: BrowserMoreActionItem,
            newItem: BrowserMoreActionItem,
        ): Boolean = oldItem.action == newItem.action

        override fun areContentsTheSame(
            oldItem: BrowserMoreActionItem,
            newItem: BrowserMoreActionItem,
        ): Boolean = oldItem == newItem
    }
}
