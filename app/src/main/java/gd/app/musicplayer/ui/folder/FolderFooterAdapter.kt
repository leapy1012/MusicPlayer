
package gd.app.musicplayer.ui.folder

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import gd.app.musicplayer.databinding.FragmentFolderFooterBinding
import gd.app.musicplayer.ui.theme.applyCurrentTheme

class FolderFooterAdapter(
    private val onScanClick: () -> Unit
) : RecyclerView.Adapter<FolderFooterAdapter.FolderFooterViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderFooterViewHolder {
        val binding = FragmentFolderFooterBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        applyCurrentTheme(binding.root)
        return FolderFooterViewHolder(binding, onScanClick)
    }

    override fun onBindViewHolder(holder: FolderFooterViewHolder, position: Int) = Unit

    override fun getItemCount(): Int = 1

    class FolderFooterViewHolder(
        private val binding: FragmentFolderFooterBinding,
        onScanClick: () -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.folderFooterScan.setOnClickListener { onScanClick() }
        }
    }
}
