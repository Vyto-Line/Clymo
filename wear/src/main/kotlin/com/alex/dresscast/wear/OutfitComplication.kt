package com.alex.dresscast.wear

import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

/** A watch-face complication: today's main garment and the temperature now. */
class OutfitComplication : SuspendingComplicationDataSourceService() {

    private fun data(temp: String, icon: String, desc: String): ComplicationData =
        ShortTextComplicationData.Builder(
            PlainComplicationText.Builder(temp).build(),
            PlainComplicationText.Builder(desc).build(),
        ).setMonochromaticImage(
            MonochromaticImage.Builder(Icon.createWithResource(this, Store.iconRes(icon))).build()
        ).build()

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return null
        val st = Store(this)
        if (!st.has) return data("--", "Shirt", "DressCast")
        return data(st.s("temp"), st.s("mainIcon"), st.s("outfit") + ", " + st.s("temp"))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.SHORT_TEXT) data("21°", "Jacket", "Light jacket, 21°") else null
}
