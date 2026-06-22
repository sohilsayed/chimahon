package eu.kanade.tachiyomi.ui.entries.anime

import android.content.Context
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import coil3.load
import coil3.request.transformations
import coil3.transform.RoundedCornersTransformation
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.EditAnimeDialogBinding
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.widget.materialdialogs.binding
import eu.kanade.tachiyomi.widget.materialdialogs.dismissDialog
import eu.kanade.tachiyomi.widget.materialdialogs.setNegativeButton
import eu.kanade.tachiyomi.widget.materialdialogs.setPositiveButton
import eu.kanade.tachiyomi.widget.materialdialogs.setTextEdit
import eu.kanade.tachiyomi.widget.materialdialogs.setTitle
import exh.ui.metadata.adapters.MetadataUIUtil.getResourceColor
import exh.util.dropBlank
import exh.util.trimOrNull
import kotlinx.coroutines.CoroutineScope
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.source.local.entries.anime.isLocal

@Composable
@Suppress("MagicNumber", "LongMethod")
fun EditAnimeDialog(
    anime: Anime,
    onDismissRequest: () -> Unit,
    onPositiveClick: (
        title: String?,
        author: String?,
        artist: String?,
        thumbnailUrl: String?,
        description: String?,
        tags: List<String>?,
        status: Long?,
    ) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var binding by remember {
        mutableStateOf<EditAnimeDialogBinding?>(null)
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    val binding = binding ?: return@TextButton
                    onPositiveClick(
                        binding.title.text.toString(),
                        binding.mangaAuthor.text.toString(),
                        binding.mangaArtist.text.toString(),
                        binding.thumbnailUrl.text.toString(),
                        binding.mangaDescription.text.toString(),
                        binding.mangaGenresTags.getTextStrings(),
                        binding.status.selectedItemPosition.let {
                            when (it) {
                                1 -> SAnime.ONGOING
                                2 -> SAnime.COMPLETED
                                3 -> SAnime.LICENSED
                                4 -> SAnime.PUBLISHING_FINISHED
                                5 -> SAnime.CANCELLED
                                6 -> SAnime.ON_HIATUS
                                else -> null
                            }
                        }?.toLong(),
                    )
                    onDismissRequest()
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                AndroidView(
                    factory = { factoryContext ->
                        EditAnimeDialogBinding.inflate(LayoutInflater.from(factoryContext))
                            .also { binding = it }
                            .apply {
                                onViewCreated(anime, factoryContext, this, scope)
                            }
                            .root
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Suppress("MagicNumber", "LongMethod", "CyclomaticComplexMethod")
private fun onViewCreated(
    anime: Anime,
    context: Context,
    binding: EditAnimeDialogBinding,
    scope: CoroutineScope,
) {
    loadCover(anime, binding)

    val statusAdapter: ArrayAdapter<String> = ArrayAdapter(
        context,
        android.R.layout.simple_spinner_dropdown_item,
        listOf(
            R.string.label_default,
            R.string.ongoing,
            R.string.completed,
            R.string.licensed,
            R.string.publishing_finished,
            R.string.cancelled,
            R.string.on_hiatus,
        ).map { context.getString(it) },
    )

    binding.status.adapter = statusAdapter
    if (anime.status != anime.ogStatus) {
        binding.status.setSelection(
            when (anime.status.toInt()) {
                SAnime.UNKNOWN -> 0
                SAnime.ONGOING -> 1
                SAnime.COMPLETED -> 2
                SAnime.LICENSED -> 3
                SAnime.PUBLISHING_FINISHED, 61 -> 4
                SAnime.CANCELLED, 62 -> 5
                SAnime.ON_HIATUS, 63 -> 6
                else -> 0
            },
        )
    }

    if (anime.isLocal()) {
        if (anime.title != anime.url) {
            binding.title.setText(anime.title)
        }

        binding.title.hint = context.getString(R.string.title_hint, anime.url)
        binding.mangaAuthor.setText(anime.author.orEmpty())
        binding.mangaArtist.setText(anime.artist.orEmpty())
        binding.mangaDescription.setText(anime.description.orEmpty())
        binding.mangaGenresTags.setChips(anime.genre.orEmpty().dropBlank(), scope)
    } else {
        if (anime.title != anime.ogTitle) {
            binding.title.append(anime.title)
        }
        if (anime.author != anime.ogAuthor) {
            binding.mangaAuthor.append(anime.author.orEmpty())
        }
        if (anime.artist != anime.ogArtist) {
            binding.mangaArtist.append(anime.artist.orEmpty())
        }
        if (anime.description != anime.ogDescription) {
            binding.mangaDescription.append(anime.description.orEmpty())
        }
        binding.mangaGenresTags.setChips(anime.genre.orEmpty().dropBlank(), scope)

        binding.title.hint = context.getString(R.string.title_hint, anime.ogTitle)
        binding.mangaAuthor.hint = context.getString(R.string.author_hint, anime.ogAuthor ?: "")
        binding.mangaArtist.hint = context.getString(R.string.artist_hint, anime.ogArtist ?: "")
        binding.mangaDescription.hint =
            context.getString(
                R.string.description_hint,
                anime.ogDescription?.takeIf { it.isNotBlank() }?.replace("\n", " ")?.chop(20) ?: "",
            )
    }
    binding.mangaGenresTags.clearFocus()

    binding.resetTags.setOnClickListener { resetTags(anime, binding, scope) }
    // SY -->
    binding.resetInfo.setOnClickListener { resetInfo(anime, binding, scope) }
    // SY <--
}

private fun resetTags(anime: Anime, binding: EditAnimeDialogBinding, scope: CoroutineScope) {
    if (anime.genre.isNullOrEmpty() || anime.isLocal()) {
        binding.mangaGenresTags.setChips(emptyList(), scope)
    } else {
        binding.mangaGenresTags.setChips(anime.ogGenre.orEmpty(), scope)
    }
}

private fun resetInfo(anime: Anime, binding: EditAnimeDialogBinding, scope: CoroutineScope) {
    binding.title.setText("")
    binding.mangaAuthor.setText("")
    binding.mangaArtist.setText("")
    binding.mangaDescription.setText("")
    resetTags(anime, binding, scope)
}

private fun loadCover(anime: Anime, binding: EditAnimeDialogBinding) {
    binding.mangaCover.load(anime) {
        transformations(RoundedCornersTransformation(4.dpToPx.toFloat()))
    }
}

private fun ChipGroup.setChips(items: List<String>, scope: CoroutineScope) {
    removeAllViews()

    items.asSequence().map { item ->
        Chip(context).apply {
            text = item

            isCloseIconVisible = true
            closeIcon?.setTint(context.getResourceColor(R.attr.colorAccent))
            setOnCloseIconClickListener {
                removeView(this)
            }
        }
    }.forEach {
        addView(it)
    }

    val addTagChip = Chip(context).apply {
        setText(R.string.add_tag)

        chipIcon = ContextCompat.getDrawable(context, R.drawable.ic_add_24dp)?.apply {
            isChipIconVisible = true
            setTint(context.getResourceColor(R.attr.colorAccent))
        }

        setOnClickListener {
            var dialog: AlertDialog? = null
            val builder = MaterialAlertDialogBuilder(context)
            val inputBinding = builder.binding(context)
                .setTitle(context.getString(R.string.add_tag))
                .setPositiveButton(context.getString(R.string.action_ok)) {
                    dialog?.dismissDialog()
                    val newTag = it.trimOrNull()
                    if (newTag != null) setChips(items + listOf(newTag), scope)
                }
                .setNegativeButton(context.getString(android.R.string.cancel)) {
                    dialog?.dismissDialog()
                }
                .setTextEdit()

            dialog = builder.create()
            dialog.setView(inputBinding.root)
            dialog.show()
        }
    }
    addView(addTagChip)
}

private fun ChipGroup.getTextStrings(): List<String> = children.mapNotNull {
    if (it is Chip &&
        !it.text.toString().contains(
            context.getString(R.string.add_tag),
            ignoreCase = true,
        )
    ) {
        it.text.toString()
    } else {
        null
    }
}.toList()
