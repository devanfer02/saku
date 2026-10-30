package com.dvnnfrr.saku.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.ChartStyle
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.ui.shared.EmptyState
import com.dvnnfrr.saku.ui.shared.MoneyText
import com.dvnnfrr.saku.ui.shared.RankedRow
import com.dvnnfrr.saku.ui.shared.TransactionRow
import com.dvnnfrr.saku.ui.shared.periodLabel
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.forSlotOrNeutral
import com.dvnnfrr.saku.ui.theme.formatIdr
import com.dvnnfrr.saku.ui.transactions.AccountRef
import com.dvnnfrr.saku.ui.transactions.TransactionRowUi
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.datetime.LocalDate

/**
 * What the user drilled into, over the transactions behind it.
 *
 * Rendered in place of the composition rather than as a new destination: the period
 * header, the mode selector and the side tabs stay put, so stepping a month while inside
 * a category keeps the comparison going instead of dropping the user back out.
 */
@Composable
fun DrillPane(
    drill: DrillUiState,
    range: DateRange,
    side: StatsSide,
    onBack: () -> Unit,
    onOpenCategory: (Long) -> Unit,
    modifier: Modifier = Modifier,
    chartStyle: ChartStyle = ChartStyle.BARS,
    onSelectPeriod: (DateRange) -> Unit = {},
) {
    val otherLabel = stringResource(R.string.stats_other)

    Column(modifier = modifier.fillMaxSize()) {
        DrillHeader(
            title = drill.title ?: otherLabel,
            range = range,
            total = drill.total,
            side = side,
            onBack = onBack,
        )
        when (drill) {
            is DrillUiState.Other -> OtherBody(drill, otherLabel, onOpenCategory)
            is DrillUiState.Category -> CategoryBody(drill, side, chartStyle, onSelectPeriod)
        }
    }
}

@Composable
private fun DrillHeader(
    title: String,
    range: DateRange,
    total: Money,
    side: StatsSide,
    onBack: () -> Unit,
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceAlt)
            .defaultMinSize(minHeight = spacing.touchTarget)
            .padding(end = spacing.screen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.stats_back),
                tint = colors.ink2,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = SakuTheme.typography.heading,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = periodLabel(range),
                style = SakuTheme.typography.caption,
                color = colors.ink2,
                maxLines = 1,
            )
        }
        MoneyText(
            money = total,
            type = side.txnType,
            style = SakuTheme.typography.amountMedium,
        )
    }
    HorizontalDivider(thickness = 1.dp, color = SakuTheme.colors.line)
}

/**
 * The categories "Other" combines, each with its own figure.
 *
 * No transaction list. The spec is explicit that the constituents are listed before any
 * transactions open, and merging nine categories' rows into one list would show a total
 * that is not any category's.
 */
@Composable
private fun OtherBody(
    drill: DrillUiState.Other,
    otherLabel: String,
    onOpenCategory: (Long) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "other-heading") {
            SectionHeader(stringResource(R.string.stats_other_heading))
        }
        items(
            count = drill.members.size,
            key = { index -> drill.members[index].categoryId ?: index.toLong() },
        ) { index ->
            val member = drill.members[index]
            CategoryRow(
                row = member,
                otherLabel = otherLabel,
                onClick = { member.categoryId?.let(onOpenCategory) },
            )
        }
    }
}

/**
 * One category, top to bottom: its share, count and average under the total the header
 * already shows; its own strip across all history; its subcategory split in the chosen
 * chart style; then its transactions.
 *
 * The split is the reason this screen exists. Pemberian is 50% of a year of
 * spending and the export carried no subcategory on any of its rows, so "how much of
 * this is the recurring family obligation" is a question the old app could not answer.
 * The strip comes before it because the owner asked to see the trend first: whether a
 * category is growing is read before what it is made of.
 */
@Composable
private fun CategoryBody(
    drill: DrillUiState.Category,
    side: StatsSide,
    chartStyle: ChartStyle,
    onSelectPeriod: (DateRange) -> Unit,
) {
    val colors = SakuTheme.colors
    val unassigned = stringResource(R.string.stats_no_subcategory)
    val sliceColors = drill.subcategories.map { slice ->
        slice.strengthPercent?.let { colors.forSlotOrNeutral(drill.colorSlot).copy(alpha = it / 100f) }
            ?: colors.forSlotOrNeutral(null)
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "summary") { CategorySummary(drill, side) }

        if (drill.strip.isNotEmpty()) {
            item(key = "strip") {
                SectionHeader(stringResource(stripTitleRes(drill.strip.first().range.mode)))
                PeriodStrip(columns = drill.strip, onSelectPeriod = onSelectPeriod, side = side)
                HorizontalDivider(thickness = 1.dp, color = colors.line)
            }
        }

        if (drill.rows.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    message = stringResource(
                        when (side) {
                            StatsSide.EXPENSES -> R.string.stats_empty_expenses
                            StatsSide.INCOME -> R.string.stats_empty_income
                        },
                        drill.title.orEmpty(),
                    ),
                )
            }
            return@LazyColumn
        }

        // A single slice is the whole category by definition, so the split says nothing
        // — except when that slice is the unassigned one, which is the finding.
        if (drill.subcategories.size > 1 || drill.subcategories.any { it.isUnassigned }) {
            item(key = "sub-heading") {
                SectionHeader(stringResource(R.string.stats_subcategory_heading))
            }
            if (chartStyle == ChartStyle.PIE) {
                item(key = "sub-pie") {
                    StatsPie(
                        slices = drill.subcategories.mapIndexed { index, slice ->
                            PieSlice(sliceColors[index], slice.sweepTenths, slice.name ?: unassigned, slice.percent, slice.total)
                        },
                        centerLabel = drill.title.orEmpty(),
                        centerValue = formatIdr(drill.total),
                    )
                }
            }
            items(
                count = drill.subcategories.size,
                key = { index -> drill.subcategories[index].subcategoryId ?: UNASSIGNED_KEY },
            ) { index ->
                val slice = drill.subcategories[index]
                RankedRow(
                    colorSlot = drill.colorSlot,
                    color = sliceColors[index],
                    name = slice.name ?: unassigned,
                    value = slice.total,
                    sharePercent = slice.percent,
                    barPermille = slice.barPermille,
                )
            }
            item(key = "sub-divider") {
                HorizontalDivider(thickness = 1.dp, color = colors.line)
            }
        }

        item(key = "txn-heading") {
            SectionHeader(stringResource(R.string.stats_transactions_heading))
        }
        items(count = drill.rows.size, key = { index -> drill.rows[index].id }) { index ->
            val row = drill.rows[index]
            TransactionRow(
                amount = row.amount,
                type = row.type,
                accountName = row.account.name,
                categorySlot = row.categorySlot,
                category = row.categoryName,
                note = row.note,
                toAccountName = row.toAccount?.name,
            )
        }
    }
}

/** "34% of spending", then "12 transactions · Rp 401.800 on average". */
@Composable
private fun CategorySummary(drill: DrillUiState.Category, side: StatsSide) {
    val colors = SakuTheme.colors
    val share = stringResource(
        when (side) {
            StatsSide.EXPENSES -> R.string.stats_category_share_spending
            StatsSide.INCOME -> R.string.stats_category_share_income
        },
        drill.percent,
    )
    val count = pluralStringResource(R.plurals.categories_transaction_count, drill.count, drill.count)
    val average = stringResource(R.string.stats_category_average, formatIdr(drill.average))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SakuTheme.spacing.screen, vertical = SakuTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(SakuTheme.spacing.xs),
    ) {
        Text(text = share, style = SakuTheme.typography.bodyStrong, color = colors.ink)
        Text(
            text = if (drill.count > 0) "$count · $average" else count,
            style = SakuTheme.typography.caption,
            color = colors.ink2,
        )
    }
}

/** No subcategory id can be negative, so this cannot collide with a real key. */
private const val UNASSIGNED_KEY = -1L

@SakuPreview
@Composable
private fun CategoryDrillPreview() {
    SakuPreviewSurface(padded = false) {
        DrillPane(
            drill = DrillUiState.Category(
                categoryId = 1,
                title = "Pemberian",
                colorSlot = ColorSlot(2),
                total = Money(1_050_000),
                percent = 34,
                count = 2,
                average = Money(525_000),
                strip = foldStrip(previewHistory(), DateRange.ofMonth(LocalDate(2026, 8, 1))),
                subcategories = listOf(
                    SubcategorySlice(1, "Rutin keluarga", Money(800_000), 76, 1_000, 2_743, 100),
                    SubcategorySlice(null, null, Money(250_000), 24, 313, 857, null),
                ),
                rows = listOf(
                    TransactionRowUi(
                        id = 1,
                        type = TxnType.EXPENSE,
                        amount = Money(800_000),
                        categoryName = "Pemberian",
                        subcategoryName = "Rutin keluarga",
                        note = null,
                        account = AccountRef(1, "BCA", ColorSlot(0)),
                        toAccount = null,
                    ),
                    TransactionRowUi(
                        id = 2,
                        type = TxnType.EXPENSE,
                        amount = Money(250_000),
                        categoryName = "Pemberian",
                        subcategoryName = null,
                        note = null,
                        account = AccountRef(1, "BCA", ColorSlot(0)),
                        toAccount = null,
                    ),
                ),
            ),
            range = DateRange.ofMonth(LocalDate(2026, 8, 1)),
            side = StatsSide.EXPENSES,
            onBack = {},
            onOpenCategory = {},
        )
    }
}
