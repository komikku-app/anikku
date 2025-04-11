package tachiyomi.source.local.filter

import android.content.Context
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import eu.kanade.tachiyomi.animesource.model.AnimeFilter as Filter

sealed class OrderBy(context: Context, selection: Selection) : Filter.Sort(
    context.stringResource(MR.strings.local_filter_order_by),
    arrayOf(context.stringResource(MR.strings.title), context.stringResource(MR.strings.date)),
    selection,
) {
    class Popular(context: Context) : OrderBy(context, Selection(0, true))
    class Latest(context: Context) : OrderBy(context, Selection(1, false))
}
