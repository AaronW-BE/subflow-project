package org.dpdns.alwaysup.subflow.ui.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

/** One rectangle of the map, in the same units as the box it was laid out in. */
data class TreemapRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

/** A labelled slice of spend. [value] must be positive; zeroes are dropped. */
data class TreemapItem(
    val label: String,
    /** The formatted amount, shown only on tiles tall enough to hold two lines. */
    val subtitle: String,
    val value: Double,
    val color: Color
)

/**
 * Squarified treemap layout (Bruls, Huizing and van Wijk).
 *
 * The naive alternative - slice the box in proportion, one strip per item -
 * produces long thin ribbons whose areas nobody can compare, which defeats the
 * point of encoding value as area. This packs each row against the shorter
 * side and only commits the row once adding the next item would make its worst
 * aspect ratio worse, so tiles stay close to square.
 *
 * [values] must be sorted descending; the algorithm's guarantee depends on it.
 * Rectangles come back in the same order as the values that produced them.
 */
internal fun squarify(values: List<Double>, width: Float, height: Float): List<TreemapRect> {
    if (width <= 0f || height <= 0f) return emptyList()
    val total = values.sumOf { max(it, 0.0) }
    if (total <= 0.0) return emptyList()

    // Areas rather than values, so a row's thickness is just its area over the
    // side it sits against.
    val areas = values.map { max(it, 0.0) / total * width.toDouble() * height.toDouble() }
    val out = MutableList(values.size) { TreemapRect(0f, 0f, 0f, 0f) }

    var x = 0.0
    var y = 0.0
    var boxWidth = width.toDouble()
    var boxHeight = height.toDouble()

    var i = 0
    while (i < areas.size) {
        val side = min(boxWidth, boxHeight)
        if (side <= 0.0) break

        var end = i + 1
        var sum = areas[i]
        var best = worstAspect(areas, i, end, sum, side)
        while (end < areas.size) {
            val grown = sum + areas[end]
            val candidate = worstAspect(areas, i, end + 1, grown, side)
            // Strictly worse: stop and lay out the row we have.
            if (candidate > best) break
            sum = grown
            best = candidate
            end++
        }

        val thickness = sum / side
        var along = 0.0
        for (k in i until end) {
            val length = if (thickness > 0.0) areas[k] / thickness else 0.0
            out[k] = if (boxWidth >= boxHeight) {
                // Short side is the height, so the row is a column on the left.
                TreemapRect((x).toFloat(), (y + along).toFloat(), thickness.toFloat(), length.toFloat())
            } else {
                TreemapRect((x + along).toFloat(), (y).toFloat(), length.toFloat(), thickness.toFloat())
            }
            along += length
        }

        if (boxWidth >= boxHeight) {
            x += thickness
            boxWidth -= thickness
        } else {
            y += thickness
            boxHeight -= thickness
        }
        i = end
    }
    return out
}

/**
 * The worst aspect ratio in the row [from, to), if it were laid against [side].
 *
 * Zero-area items would make this infinite, which is why the caller drops them
 * before laying anything out.
 */
private fun worstAspect(areas: List<Double>, from: Int, to: Int, sum: Double, side: Double): Double {
    if (sum <= 0.0) return Double.MAX_VALUE
    var lo = Double.MAX_VALUE
    var hi = 0.0
    for (k in from until to) {
        lo = min(lo, areas[k])
        hi = max(hi, areas[k])
    }
    if (lo <= 0.0) return Double.MAX_VALUE
    val s2 = side * side
    val sum2 = sum * sum
    return max(s2 * hi / sum2, sum2 / (s2 * lo))
}

/**
 * Spend as area.
 *
 * The category bar above this answers "which kind of thing do I spend on"; the
 * ranked list below answers "what are the five biggest". Neither shows one
 * subscription against all the others at once, which is the question a map
 * answers in a glance: whether the total is one large tile and some crumbs, or
 * genuinely spread out.
 *
 * Tiles carry each service's own brand colour rather than a chart palette, so
 * the map is recognisable rather than merely colourful.
 */
@Composable
internal fun SpendTreemap(
    items: List<TreemapItem>,
    modifier: Modifier = Modifier,
    summary: String? = null
) {
    val positive = remember(items) { items.filter { it.value > 0.0 } }
    if (positive.isEmpty()) return

    val density = LocalDensity.current

    BoxWithConstraints(
        contentAlignment = AbsoluteAlignment.TopLeft,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (summary != null) {
                    // One node for the whole map. Read tile by tile it is a
                    // list of numbers in an order nobody can see; the summary
                    // is the sentence the picture is making.
                    Modifier.semantics { contentDescription = summary }
                } else {
                    Modifier
                }
            )
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val rects = remember(positive, widthPx, heightPx) {
            squarify(positive.map { it.value }, widthPx, heightPx)
        }

        rects.forEachIndexed { index, rect ->
            val item = positive[index]
            val tileWidth = with(density) { rect.width.toDp() }
            val tileHeight = with(density) { rect.height.toDp() }
            if (tileWidth <= 0.dp || tileHeight <= 0.dp) return@forEachIndexed

            // Ink on the tile, chosen against the tile rather than the theme:
            // these are brand colours and half of them are light.
            val ink = if (item.color.luminance() > 0.5f) {
                Color.Black.copy(alpha = 0.78f)
            } else {
                Color.White
            }

            Box(
                modifier = Modifier
                    // absoluteOffset rather than offset, to match the
                    // coordinates: squarify works in absolute pixels from the
                    // top left, while offset() and Alignment.TopStart are both
                    // direction-aware and would reinterpret them under RTL.
                    // Untested there - no shipped locale is right-to-left and
                    // the emulator would not take the forced-RTL setting - so
                    // this is the API contract rather than an observation.
                    .absoluteOffset(
                        x = with(density) { rect.x.toDp() },
                        y = with(density) { rect.y.toDp() }
                    )
                    .size(tileWidth, tileHeight)
                    // The gap is padding inside the tile rather than a smaller
                    // rectangle: the layout's areas stay exactly proportional
                    // and only the paint is inset.
                    .padding(1.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(item.color)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .clearAndSetSemantics { }
            ) {
                // Anything smaller than this fits a truncated word at best,
                // and a tile labelled "Net..." is worse than one left plain -
                // the ranked list underneath already names everything.
                if (tileWidth >= 54.dp && tileHeight >= 26.dp) {
                    Column {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = FontWeight.Bold,
                            color = ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (tileHeight >= 46.dp) {
                            Text(
                                text = item.subtitle,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = ink.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
