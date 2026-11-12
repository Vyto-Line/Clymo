package com.alex.dresscast.wear

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

private const val RES = "1"
private const val WHITE = 0xFFFFFFFF.toInt()
private const val MUTED = 0xFFB3BDCA.toInt()
private const val ACCENT = 0xFF8AB4F8.toInt()
private const val AMBER = 0xFFFDD663.toInt()

internal fun text(s: String, size: Float, color: Int = WHITE, bold: Boolean = false, lines: Int = 1) =
    Text.Builder().setText(s).setMaxLines(lines)
        .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
        .setFontStyle(
            FontStyle.Builder().setSize(sp(size)).setColor(argb(color))
                .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL).build()
        ).build()

internal fun gap(h: Float) = Spacer.Builder().setHeight(dp(h)).build()

internal fun icon(name: String, size: Float, color: Int = ACCENT) = Image.Builder().setResourceId(name)
    .setWidth(dp(size)).setHeight(dp(size))
    .setColorFilter(LayoutElementBuilders.ColorFilter.Builder().setTint(argb(color)).build()).build()

/** Tapping a tile opens the watch app. */
internal fun openApp(ctx: Context) = ModifiersBuilders.Modifiers.Builder().setClickable(
    ModifiersBuilders.Clickable.Builder().setId("open").setOnClick(
        ActionBuilders.LaunchAction.Builder().setAndroidActivity(
            ActionBuilders.AndroidActivity.Builder().setPackageName(ctx.packageName).setClassName(MainActivity::class.java.name).build()
        ).build()
    ).build()
).build()

internal fun tile(root: LayoutElementBuilders.LayoutElement): TileBuilders.Tile = TileBuilders.Tile.Builder()
    .setResourcesVersion(RES)
    .setFreshnessIntervalMillis(30 * 60 * 1000L)
    .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
    .build()

internal fun resources(): ResourceBuilders.Resources {
    val b = ResourceBuilders.Resources.Builder().setVersion(RES)
    listOf("Jacket", "Shirt", "Umbrella", "Glasses", "Sun", "Shorts", "Shoe", "Scarf").forEach { n ->
        b.addIdToImageMapping(n, ResourceBuilders.ImageResource.Builder().setAndroidResourceByResId(
            ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(Store.iconRes(n)).build()
        ).build())
    }
    return b.build()
}

private fun box(ctx: Context, inner: LayoutElementBuilders.LayoutElement) = LayoutElementBuilders.Box.Builder()
    .setWidth(expand()).setHeight(expand())
    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
    .setModifiers(openApp(ctx))
    .addContent(inner).build()

private fun noData(ctx: Context, st: Store) = box(ctx, Column.Builder().setWidth(dp(150f))
    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
    .addContent(icon("Jacket", 28f)).addContent(gap(8f))
    .addContent(text(st.s("labelOpen").ifBlank { "Open Clymo on your phone" }, 14f, MUTED, lines = 3)).build())

/** "Today": temperature, feels like, what to wear and the rain outlook. */
class TodayTile : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val st = Store(this)
        if (!st.has) return Futures.immediateFuture(tile(noData(this, st)))
        val icons = Row.Builder().setWidth(wrap()).setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        st.icons.take(3).forEachIndexed { i, n ->
            if (i > 0) icons.addContent(Spacer.Builder().setWidth(dp(8f)).build())
            icons.addContent(icon(n, 24f))
        }
        val inner = Column.Builder().setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text(st.s("city"), 13f, MUTED))
            .addContent(text(st.s("temp"), 40f, WHITE, bold = true))
            .addContent(text(st.s("feel"), 12f, MUTED))
            .addContent(gap(8f))
            .addContent(icons.build())
            .addContent(gap(4f))
            .addContent(text(st.s("outfit"), 14f, WHITE, bold = true, lines = 2))
            .addContent(gap(4f))
            .addContent(text(st.s("rain"), 12f, if (st.s("wet") == "1") AMBER else ACCENT))
            .build()
        return Futures.immediateFuture(tile(box(this, inner)))
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(resources())
}

/** "Next plan": time, title, the weather then and what to take. */
class PlanTile : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val st = Store(this)
        if (!st.has) return Futures.immediateFuture(tile(noData(this, st)))
        val c = Column.Builder().setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text(st.s("labelNext"), 13f, MUTED))
        if (st.s("planTime").isNotBlank()) c.addContent(text(st.s("planTime"), 30f, WHITE, bold = true))
        c.addContent(text(st.s("planTitle"), 16f, WHITE, bold = true, lines = 2))
        if (st.s("planWeather").isNotBlank()) c.addContent(gap(2f)).addContent(text(st.s("planWeather"), 13f, MUTED))
        if (st.s("planTip").isNotBlank()) {
            c.addContent(gap(8f))
            c.addContent(
                Row.Builder().setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    .setModifiers(ModifiersBuilders.Modifiers.Builder().setBackground(
                        ModifiersBuilders.Background.Builder().setColor(argb(0xFF1E2A3D.toInt()))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(16f)).build()).build()
                    ).setPadding(ModifiersBuilders.Padding.Builder().setStart(dp(10f)).setEnd(dp(12f)).setTop(dp(6f)).setBottom(dp(6f)).build()).build())
                    .addContent(icon(st.s("planIcon").ifBlank { "Jacket" }, 18f))
                    .addContent(Spacer.Builder().setWidth(dp(6f)).build())
                    .addContent(text(st.s("planTip"), 13f, WHITE, lines = 2))
                    .build()
            )
        }
        return Futures.immediateFuture(tile(box(this, c.build())))
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(resources())
}
