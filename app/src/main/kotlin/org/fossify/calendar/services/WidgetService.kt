package org.fossify.calendar.services

import android.content.Intent
import android.widget.RemoteViewsService
import org.fossify.calendar.adapters.EventListWidgetAdapter

class WidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent) = EventListWidgetAdapter(applicationContext, intent)
}
