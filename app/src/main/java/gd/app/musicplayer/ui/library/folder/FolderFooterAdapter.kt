package gd.app.musicplayer.ui.library.folder

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.databinding.FragmentFolderFooterBinding

class FolderFooterAdapter(
    private val applyTheme: (android.view.View) -> Unit,
    private val onScanClick: () -> Unit
) : RecyclerView.Adapter<FolderFooterAdapter.FolderFooterViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderFooterViewHolder {
        val binding = FragmentFolderFooterBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        applyTheme(binding.root)
        return FolderFooterViewHolder(binding, onScanClick)
    }

    override fun onBindViewHolder(holder: FolderFooterViewHolder, position: Int) = Unit

    override fun getItemCount(): Int = 1

    class FolderFooterViewHolder(
        binding: FragmentFolderFooterBinding,
        onScanClick: () -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.folderFooterScan.setOnClickListener { onScanClick() }
        }
    }
}
