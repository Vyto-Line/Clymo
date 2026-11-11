package com.alex.dresscast.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/** Receives the summary the phone app sends after each forecast. */
class PhoneListener : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        var changed = false
        for (e in events) {
            if (e.type == DataEvent.TYPE_CHANGED && e.dataItem.uri.path == "/dresscast") {
                Store(this).save(DataMapItem.fromDataItem(e.dataItem).dataMap)
                changed = true
            }
        }
        if (changed) Store.refreshAll(this)
    }
}
