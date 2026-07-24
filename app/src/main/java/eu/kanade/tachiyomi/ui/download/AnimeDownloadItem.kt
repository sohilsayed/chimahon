package eu.kanade.tachiyomi.ui.download

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractSectionableItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.animedownload.model.AnimeDownload

class AnimeDownloadItem(
    val download: AnimeDownload,
    header: AnimeDownloadHeaderItem,
) : AbstractSectionableItem<AnimeDownloadHolder, AnimeDownloadHeaderItem>(header) {

    override fun getLayoutRes(): Int = R.layout.download_anime_item

    override fun createViewHolder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
    ): AnimeDownloadHolder {
        return AnimeDownloadHolder(view, adapter as DownloadAdapter)
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: AnimeDownloadHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        holder.bind(download)
    }

    override fun isDraggable(): Boolean = true

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is AnimeDownloadItem) {
            return download.episode.id == other.download.episode.id
        }
        return false
    }

    override fun hashCode(): Int = download.episode.id.toInt()
}
