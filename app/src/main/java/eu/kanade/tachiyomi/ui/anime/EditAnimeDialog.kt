package eu.kanade.tachiyomi.ui.anime

import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.LayoutRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
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
import eu.kanade.tachiyomi.databinding.EditMangaDialogBinding
import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.widget.materialdialogs.setTextInput
import exh.ui.metadata.adapters.MetadataUIUtil.getResourceColor
import exh.util.dropBlank
import exh.util.trimOrNull
import kotlinx.coroutines.CoroutineScope
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.anime.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.isLocal

@Composable
fun EditAnimeDialog(
    manga: Anime,
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
        mutableStateOf<EditMangaDialogBinding?>(null)
    }
    // KMK -->
    val colors = EditAnimeDialogColors(
        textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb(),
        textHighlightColor = MaterialTheme.colorScheme.inversePrimary.toArgb(),
        iconColor = MaterialTheme.colorScheme.primary.toArgb(),
        tagColor = MaterialTheme.colorScheme.outlineVariant.toArgb(),
        tagTextColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb(),
        btnTextColor = MaterialTheme.colorScheme.onPrimary.toArgb(),
        btnBgColor = MaterialTheme.colorScheme.surfaceTint.toArgb(),
        dropdownBgColor = MaterialTheme.colorScheme.surfaceVariant.toArgb(),
        dialogBgColor = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb(),
    )
    // KMK <--
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    @Suppress("NAME_SHADOWING")
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
                Text(stringResource(MR.strings.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
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
                        EditMangaDialogBinding.inflate(LayoutInflater.from(factoryContext))
                            .also { binding = it }
                            .apply {
                                onViewCreated(
                                    manga,
                                    factoryContext,
                                    this,
                                    scope,
                                    // KMK -->
                                    colors,
                                    // KMK <--
                                )
                            }
                            .root
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

// KMK -->
data class EditAnimeDialogColors(
    @ColorInt val textColor: Int,
    @ColorInt val textHighlightColor: Int,
    @ColorInt val iconColor: Int,
    @ColorInt val tagColor: Int,
    @ColorInt val tagTextColor: Int,
    @ColorInt val btnTextColor: Int,
    @ColorInt val btnBgColor: Int,
    @ColorInt val dropdownBgColor: Int,
    @ColorInt val dialogBgColor: Int,
)
// KMK <--

private fun onViewCreated(
    manga: Anime,
    context: Context,
    binding: EditMangaDialogBinding,
    scope: CoroutineScope,
    // KMK -->
    colors: EditAnimeDialogColors,
    // KMK <--
) {
    loadCover(manga, binding)

    // KMK -->
    // val statusAdapter: ArrayAdapter<String> = ArrayAdapter(
    val statusAdapter = SpinnerAdapter(
        // KMK <--
        context,
        android.R.layout.simple_spinner_dropdown_item,
        listOf(
            MR.strings.label_default,
            MR.strings.ongoing,
            MR.strings.completed,
            MR.strings.licensed,
            MR.strings.publishing_finished,
            MR.strings.cancelled,
            MR.strings.on_hiatus,
        ).map { context.stringResource(it) },
        // KMK -->
        colors,
        // KMK <--
    )

    binding.status.adapter = statusAdapter
    if (manga.status != manga.ogStatus) {
        binding.status.setSelection(
            when (manga.status.toInt()) {
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

    // KMK -->
    // Set Spinner's selected item's background color to transparent
    binding.status.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
            if (view != null) (view as TextView).setBackgroundColor(0x00000000)
        }
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }

    // Set Spinner's dropdown caret color
    binding.status.backgroundTintList = ColorStateList.valueOf(colors.iconColor)
    // KMK

    if (manga.isLocal()) {
        if (manga.title != manga.url) {
            binding.title.setText(manga.title)
        }

        binding.title.hint = context.stringResource(SYMR.strings.title_hint, manga.url)
        binding.mangaAuthor.setText(manga.author.orEmpty())
        binding.mangaArtist.setText(manga.artist.orEmpty())
        binding.thumbnailUrl.setText(manga.thumbnailUrl.orEmpty())
        binding.mangaDescription.setText(manga.description.orEmpty())
        binding.mangaGenresTags.setChips(manga.genre.orEmpty().dropBlank(), scope)
    } else {
        if (manga.title != manga.ogTitle) {
            binding.title.append(manga.title)
        }
        if (manga.author != manga.ogAuthor) {
            binding.mangaAuthor.append(manga.author.orEmpty())
        }
        if (manga.artist != manga.ogArtist) {
            binding.mangaArtist.append(manga.artist.orEmpty())
        }
        if (manga.thumbnailUrl != manga.ogThumbnailUrl) {
            binding.thumbnailUrl.append(manga.thumbnailUrl.orEmpty())
        }
        if (manga.description != manga.ogDescription) {
            binding.mangaDescription.append(manga.description.orEmpty())
        }
        binding.mangaGenresTags.setChips(manga.genre.orEmpty().dropBlank(), scope)

        binding.title.hint = context.stringResource(SYMR.strings.title_hint, manga.ogTitle)

        binding.mangaAuthor.hint = context.stringResource(SYMR.strings.author_hint, manga.ogAuthor ?: "")
        binding.mangaArtist.hint = context.stringResource(SYMR.strings.artist_hint, manga.ogArtist ?: "")
        binding.mangaDescription.hint =
            context.stringResource(
                SYMR.strings.description_hint,
                manga.ogDescription?.takeIf { it.isNotBlank() }?.replace("\n", " ")?.chop(20) ?: "",
            )
        binding.thumbnailUrl.hint =
            context.stringResource(
                SYMR.strings.thumbnail_url_hint,
                manga.ogThumbnailUrl?.let {
                    it.chop(40) + if (it.length > 46) "." + it.substringAfterLast(".").chop(6) else ""
                } ?: "",
            )
    }
    binding.mangaGenresTags.clearFocus()

    // KMK -->
    listOf(
        binding.title,
        binding.mangaAuthor,
        binding.mangaArtist,
        binding.thumbnailUrl,
        binding.mangaDescription,
    ).forEach {
        it.setTextColor(colors.textColor)
        it.highlightColor = colors.textHighlightColor

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            it.textSelectHandle?.let { drawable ->
                drawable.setTint(colors.iconColor)
                it.setTextSelectHandle(drawable)
            }
            it.textSelectHandleLeft?.let { drawable ->
                drawable.setTint(colors.iconColor)
                it.setTextSelectHandleLeft(drawable)
            }
            it.textSelectHandleRight?.let { drawable ->
                drawable.setTint(colors.iconColor)
                it.setTextSelectHandleRight(drawable)
            }
        }
    }
    listOf(
        binding.titleOutline,
        binding.mangaAuthorOutline,
        binding.mangaArtistOutline,
        binding.thumbnailUrlOutline,
        binding.mangaDescriptionOutline,
    ).forEach {
        it.boxStrokeColor = colors.iconColor

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            it.cursorColor = ColorStateList.valueOf(colors.iconColor)
        }
    }

    binding.autofillFromTracker.setTextColor(colors.btnTextColor)
    binding.autofillFromTracker.setBackgroundColor(colors.btnBgColor)
    binding.resetTags.setTextColor(colors.btnTextColor)
    binding.resetTags.setBackgroundColor(colors.btnBgColor)
    binding.resetInfo.setTextColor(colors.btnTextColor)
    binding.resetInfo.setBackgroundColor(colors.btnBgColor)
    // KMK <--

    binding.resetTags.setOnClickListener { resetTags(manga, binding, scope) }
    binding.resetInfo.setOnClickListener { resetInfo(manga, binding, scope) }
}

private fun resetTags(manga: Anime, binding: EditMangaDialogBinding, scope: CoroutineScope) {
    if (manga.genre.isNullOrEmpty() || manga.isLocal()) {
        binding.mangaGenresTags.setChips(emptyList(), scope)
    } else {
        binding.mangaGenresTags.setChips(manga.ogGenre.orEmpty(), scope)
    }
}

private fun resetInfo(manga: Anime, binding: EditMangaDialogBinding, scope: CoroutineScope) {
    binding.title.setText("")
    binding.mangaAuthor.setText("")
    binding.mangaArtist.setText("")
    binding.mangaDescription.setText("")
    resetTags(manga, binding, scope)
}

private fun loadCover(manga: Anime, binding: EditMangaDialogBinding) {
    binding.mangaCover.load(manga) {
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
            var newTag: String? = null
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.add_tag)
                .setTextInput {
                    newTag = it.trimOrNull()
                }
                .setPositiveButton(R.string.action_ok) { _, _ ->
                    if (newTag != null) setChips(items + listOfNotNull(newTag), scope)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
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

// KMK -->
private class SpinnerAdapter(
    context: Context,
    @LayoutRes val resource: Int,
    objects: List<String>,
    val colors: EditAnimeDialogColors,
) : ArrayAdapter<String>(context, resource, objects) {
    private val mInflater = LayoutInflater.from(context)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createViewFromResource(mInflater, position, convertView, parent, resource)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createViewFromResource(mInflater, position, convertView, parent, resource)
    }

    private fun createViewFromResource(
        inflater: LayoutInflater,
        position: Int,
        convertView: View?,
        parent: ViewGroup,
        resource: Int,
    ): View {
        val text: TextView

        val view = convertView ?: inflater.inflate(resource, parent, false)

        try {
            //  If no custom field is assigned, assume the whole resource is a TextView
            text = view as TextView
        } catch (e: ClassCastException) {
            throw IllegalStateException("ArrayAdapter requires the resource ID to be a TextView", e)
        }

        val item: String? = getItem(position)
        if (item != null) text.text = item

        text.setTextColor(colors.textColor)
        text.setBackgroundColor(colors.dropdownBgColor)

        return view
    }
}
// KMK <--
