package org.fossify.musicplayer.adapters

import android.text.TextUtils
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.highlightTextPart
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.commons.views.MyRecyclerView
import org.fossify.musicplayer.R
import org.fossify.musicplayer.databinding.ItemFolderBinding
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.extensions.config
import org.fossify.musicplayer.models.Events
import org.fossify.musicplayer.models.Folder
import org.fossify.musicplayer.models.Track
import org.greenrobot.eventbus.EventBus

class FoldersAdapter(
    activity: BaseSimpleActivity, items: ArrayList<Folder>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit
) : BaseMusicAdapter<Folder>(items, activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate {

    override fun getActionMenuId() = R.menu.cab_folders

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFolderBinding.inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val folder = items.getOrNull(position) ?: return
        holder.bindView(folder, allowSingleClick = true, allowLongClick = true) { itemView, _ ->
            setupView(itemView, folder)
        }
        bindViewHolder(holder)
    }

    override fun prepareActionMode(menu: Menu) {
        val selected = getSelectedItems()
        menu.findItem(R.id.cab_make_markable).isVisible = selected.any { !context.config.isFolderMarkable(it.path) }
        menu.findItem(R.id.cab_make_unmarkable).isVisible = selected.any { it.path.removeSuffix("/") in context.config.markableFolders }
    }

    override fun actionItemPressed(id: Int) {
        when (id) {
            R.id.cab_exclude_folders -> excludeFolders()
            R.id.cab_share -> shareFiles()
            R.id.cab_make_markable -> setMarkable(true)
            R.id.cab_make_unmarkable -> setMarkable(false)
        }
    }

    private fun setMarkable(markable: Boolean) {
        val paths = getSelectedItems().map { it.path }
        if (markable) {
            context.config.addMarkableFolders(paths)
        } else {
            context.config.removeMarkableFolders(paths)
        }

        finishActMode()
        EventBus.getDefault().post(Events.RefreshFragments())
        notifyDataSetChanged()
    }

    private fun excludeFolders() {
        getSelectedItems().forEach {
            context.config.addExcludedFolder(it.path)
        }

        finishActMode()
        EventBus.getDefault().post(Events.RefreshFragments())
    }

    override fun getSelectedTracks(): List<Track> {
        val tracks = arrayListOf<Track>()
        getSelectedItems().forEach {
            tracks += context.audioHelper.getFolderTracks(it.title)
        }

        return tracks
    }

    private fun setupView(view: View, folder: Folder) {
        ItemFolderBinding.bind(view).apply {
            root.setupViewBackground(context)
            folderFrame.isSelected = selectedKeys.contains(folder.hashCode())
            val title: CharSequence = if (textToHighlight.isEmpty()) folder.title else folder.title.highlightTextPart(textToHighlight, properPrimaryColor)
            folderTitle.text = if (context.config.isFolderMarkable(folder.path)) TextUtils.concat("🔖 ", title) else title
            folderTitle.setTextColor(textColor)

            val tracks = resources.getQuantityString(R.plurals.tracks_plural, folder.trackCount, folder.trackCount)
            folderTracks.text = tracks
            folderTracks.setTextColor(textColor)
        }
    }

    override fun onChange(position: Int) = items.getOrNull(position)?.getBubbleText(context.config.folderSorting) ?: ""
}
