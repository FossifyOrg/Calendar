package org.fossify.calendar.services

import android.content.Intent
import android.widget.RemoteViewsService
import org.fossify.calendar.adapters.EventListWidgetAdapterEmpty

class WidgetServiceEmpty : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent) = EventListWidgetAdapterEmpty(applicationContext)
}
