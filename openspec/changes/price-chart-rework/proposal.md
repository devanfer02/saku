## Why

The owner, 4 Nov 2026, on a phone search with 19 rows from Jul 2025 to Oct 2026: the line and dots are too heavy, ten years of data would squeeze into the card, tapping a dot shows nothing, and there is no Y axis.

## What Changes

- A thinner line (1.5 dp), small markers (3 dp), and markers dropped when points crowd.
- Range chips above the chart: 6M, 1Y, 3Y, All, only those that hold two points and fewer than the next longer one. Opens on 1Y when offered, else All. Every figure on the card follows the range.
- Over more than 18 months and 60 days with a price, the chart draws one point per month at its average.
- A Y axis with 3 or 4 round gridlines, and first, middle and last dates under the plot.
- Tap or drag selects the nearest point: a guide, a ringed marker and a label with its date and price. TalkBack steps through the points.
- The chart is 136 dp tall (was 88), plus its label band and dates.

## Capabilities

### Modified Capabilities
- `item-price-history`: the card's chart gains ranges, a Y axis and point selection.

## Impact

`ui/search/` (`PriceChartAssembly.kt` new, `PriceLineChart.kt` new, `PriceCard.kt`, `PriceHistoryAssembly.kt`, `SearchScreen.kt`), `ui/theme/Format.kt` (`formatIdrAxis` exact), strings. `ui/shared/PriceLine.kt` removed. No storage change.
