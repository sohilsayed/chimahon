package eu.kanade.tachiyomi.ui.download

import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.data.animedownload.model.AnimeDownload
import eu.kanade.tachiyomi.databinding.DownloadItemBinding

class AnimeDownloadHolder(view: View, val adapter: DownloadAdapter) :
    FlexibleViewHolder(view, adapter) {

    private val binding = DownloadItemBinding.bind(view)

    fun bind(download: AnimeDownload) {
        binding.chapterTitle.text = download.episode.name
        binding.mangaFullTitle.text = download.anime.title
        binding.reorder.isVisible = false
        binding.menu.isVisible = false
        (binding.menu.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            it.width = 0
        }
        updateProgress(download)
    }

    fun updateProgress(download: AnimeDownload) {
        when (download.status) {
            AnimeDownload.State.DOWNLOADING -> {
                binding.downloadProgress.max = 100
                binding.downloadProgress.progress = download.progress.coerceIn(0, 100)
                binding.downloadProgressText.text = "${download.progress}%"
                binding.downloadProgress.isVisible = true
                binding.downloadProgressText.isVisible = true
            }
            AnimeDownload.State.QUEUE -> {
                binding.downloadProgress.max = 1
                binding.downloadProgress.progress = 0
                binding.downloadProgressText.text = ""
                binding.downloadProgress.isVisible = true
                binding.downloadProgressText.isVisible = false
            }
            AnimeDownload.State.DOWNLOADED -> {
                binding.downloadProgress.max = 1
                binding.downloadProgress.progress = 0
                binding.downloadProgressText.text = ""
                binding.downloadProgress.isVisible = false
                binding.downloadProgressText.isVisible = false
            }
            AnimeDownload.State.ERROR -> {
                binding.downloadProgress.max = 1
                binding.downloadProgress.progress = 0
                binding.downloadProgressText.text = ""
                binding.downloadProgress.isVisible = false
                binding.downloadProgressText.isVisible = false
            }
            else -> {
                binding.downloadProgress.isVisible = false
                binding.downloadProgressText.isVisible = false
            }
        }
    }
}
