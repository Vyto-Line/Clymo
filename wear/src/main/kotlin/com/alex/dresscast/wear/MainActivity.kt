package com.alex.dresscast.wear

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable

/** A simple screen with the same summary as the tiles. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        show()
        // Pick up the phone's latest summary, in case it arrived before this app was installed.
        Wearable.getDataClient(this).dataItems.addOnSuccessListener { items ->
            var found = false
            for (item in items) if (item.uri.path == "/dresscast") {
                Store(this).save(DataMapItem.fromDataItem(item).dataMap)
                found = true
            }
            items.release()
            if (found) { Store.refreshAll(this); show() }
        }
    }

    private fun tv(s: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
        text = s
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun show() {
        val st = Store(this)
        val pad = (resources.displayMetrics.widthPixels * 0.14f).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad / 2 + 16, pad, pad)
        }
        val muted = Color.parseColor("#B3BDCA")
        if (!st.has) {
            col.addView(tv(st.s("labelOpen").ifBlank { "Open DressCast on your phone" }, 15f, muted))
        } else {
            col.addView(tv(st.s("city"), 14f, muted))
            col.addView(tv(st.s("temp"), 42f, bold = true))
            col.addView(tv(st.s("feel"), 13f, muted))
            col.addView(tv(st.s("sky"), 14f))
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, 16, 0, 8) }
            st.icons.take(3).forEach { n ->
                row.addView(ImageView(this).apply {
                    setImageResource(Store.iconRes(n))
                    setColorFilter(Color.parseColor("#8AB4F8"))
                    layoutParams = LinearLayout.LayoutParams(64, 64).apply { setMargins(10, 0, 10, 0) }
                })
            }
            col.addView(row)
            col.addView(tv(st.s("outfit"), 16f, bold = true))
            col.addView(tv(st.s("rain"), 13f, if (st.s("wet") == "1") Color.parseColor("#FDD663") else Color.parseColor("#8AB4F8")))
            if (st.s("planTitle").isNotBlank()) {
                col.addView(tv("\n" + st.s("labelNext"), 13f, muted))
                col.addView(tv(listOf(st.s("planTime"), st.s("planTitle")).filter { it.isNotBlank() }.joinToString("  "), 16f, bold = true))
                if (st.s("planWeather").isNotBlank()) col.addView(tv(st.s("planWeather"), 13f, muted))
                if (st.s("planTip").isNotBlank()) col.addView(tv(st.s("planTip"), 14f))
            }
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.BLACK); addView(col) })
    }
}
