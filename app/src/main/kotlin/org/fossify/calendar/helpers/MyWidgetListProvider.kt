package org.fossify.calendar.helpers

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.calendar.R
import org.fossify.calendar.activities.SplashActivity
import org.fossify.calendar.databases.EventsDatabase
import org.fossify.calendar.activities.SettingsActivity
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.getWidgetFontSize
import org.fossify.calendar.extensions.launchNewEventOrTaskActivity
import org.fossify.calendar.extensions.widgetsDB
import org.fossify.calendar.services.WidgetService
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getColoredBitmap
import org.fossify.commons.extensions.getLaunchIntent
import org.fossify.commons.extensions.setTextSize
import org.fossify.commons.helpers.ensureBackgroundThread
import org.joda.time.DateTime
import kotlin.math.log
import org.fossify.calendar.models.Event as FossifyEvent


class MyWidgetListProvider : AppWidgetProvider() {
    private val NEW_EVENT = "new_event"
    private val LAUNCH_CAL = "launch_cal"
    private val GO_TO_TODAY = "go_to_today"

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        performUpdate(context)
    }

    fun performUpdate(context: Context) {
        val fontSize = context.getWidgetFontSize()
        val textColor = context.config.widgetTextColor

        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
        ensureBackgroundThread {
            appWidgetManager.getAppWidgetIds(getComponentName(context)).forEach {
                val widget = context.widgetsDB.getWidgetWithWidgetId(it)
                val views = RemoteViews(context.packageName, R.layout.widget_event_list).apply {
                    applyColorFilter(R.id.widget_event_list_background, context.config.widgetBgColor)
                    setTextColor(R.id.widget_event_list_empty, textColor)
                    setTextSize(R.id.widget_event_list_empty, fontSize)

                    setTextColor(R.id.widget_event_list_today, textColor)
                    setTextSize(R.id.widget_event_list_today, fontSize)
                }

                views.setImageViewBitmap(
                    R.id.widget_event_new_event, context.resources.getColoredBitmap(
                        resourceId = org.fossify.commons.R.drawable.ic_plus_vector,
                        newColor = textColor
                    )
                )
                setupIntent(context, views, NEW_EVENT, R.id.widget_event_new_event)
                setupIntent(context, views, LAUNCH_CAL, R.id.widget_event_list_today)

                views.setImageViewBitmap(R.id.widget_event_go_to_today, context.resources.getColoredBitmap(R.drawable.ic_today_vector, textColor))
                setupIntent(context, views, GO_TO_TODAY, R.id.widget_event_go_to_today)

                Intent(context, WidgetService::class.java).apply {
                    putExtra(EVENT_LIST_PERIOD, widget?.period)
                    data = Uri.parse(this.toUri(Intent.URI_INTENT_SCHEME))
                    views.setRemoteAdapter(R.id.widget_event_list, this)
                }

                val startActivityIntent = context.getLaunchIntent() ?: Intent(context, SplashActivity::class.java)
                val startActivityPendingIntent =
                    PendingIntent.getActivity(context, 0, startActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                views.setPendingIntentTemplate(R.id.widget_event_list, startActivityPendingIntent)
                views.setEmptyView(R.id.widget_event_list, R.id.widget_event_list_empty)

                appWidgetManager.updateAppWidget(it, views)
                appWidgetManager.notifyAppWidgetViewDataChanged(it, R.id.widget_event_list)
            }
        }
    }

    private fun getComponentName(context: Context) = ComponentName(context, MyWidgetListProvider::class.java)

    private fun setupIntent(context: Context, views: RemoteViews, action: String, id: Int) {
        Intent(context, MyWidgetListProvider::class.java).apply {
            this.action = action
            val pendingIntent = PendingIntent.getBroadcast(context, 0, this, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(id, pendingIntent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NEW_EVENT -> context.launchNewEventOrTaskActivity()
            LAUNCH_CAL -> launchCalenderInDefaultView(context)
            GO_TO_TODAY -> goToToday(context)
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        super.onDeleted(context, appWidgetIds)
        ensureBackgroundThread {
            appWidgetIds?.forEach {
                context?.widgetsDB?.deleteWidgetId(it)
            }
        }
    }

    private fun launchCalenderInDefaultView(context: Context) {
        (context.getLaunchIntent() ?: Intent(context, SplashActivity::class.java)).apply {
            putExtra(DAY_CODE, Formatter.getDayCodeFromDateTime(DateTime()))
            putExtra(VIEW_TO_OPEN, context.config.listWidgetViewToOpen)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(this)
        }
    }
    private val widgetUpdateScope = CoroutineScope(Dispatchers.Main)
    private var cachedEvents: List<FossifyEvent>? = null
    class DisplayPastEvents {
        companion object {
            var result: Int? = 1440
        }
    }




    private fun getCachedEvents(context: Context): List<FossifyEvent> {
        if (cachedEvents == null) {
            cachedEvents = EventsDatabase.getInstance(context).EventsDao().getAllEvents()
        }
        return cachedEvents ?: emptyList()
    }

    private fun updateWidgetData(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
        val valueResult = DisplayPastEvents.result
        widgetUpdateScope.launch(Dispatchers.IO) {
            val eventsList: List<FossifyEvent> = getCachedEvents(context)
            withContext(Dispatchers.Main) {
                var yesterdayEventCount = 0
                val now = DateTime.now()
                val startOfToday = now.withTimeAtStartOfDay().millis / 1000
                val startOfPeriod = valueResult?.let { now.minusMinutes(it).millis / 1000 }

                val limitedEvents = eventsList
                    .filter { it.startTS < startOfToday && it.startTS > startOfPeriod!! }
                    .take(100)
                    .sortedBy { it.startTS }
                limitedEvents.forEach { event ->
                    val eventDateTime = DateTime(event.startTS * 1000L).toLocalTime()
                    val isAfterNowOrMidnight = eventDateTime.isAfter(now.toLocalTime()) || !event.getIsAllDay()
                    if (isAfterNowOrMidnight) {
                        yesterdayEventCount += 1
                    }
                }
                if (yesterdayEventCount != 0) {
                    yesterdayEventCount += 3
                }
                val views = RemoteViews(context.packageName, R.layout.widget_event_list)
                views.setScrollPosition(R.id.widget_event_list, yesterdayEventCount)

                appWidgetManager.updateAppWidget(widgetId, views)
                appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_event_list)
            }
        }
    }

    fun DisplayPastEvents(value: Int) {
        DisplayPastEvents.result = value;
    }
    
    private fun goToToday(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
        val widgetIds = appWidgetManager.getAppWidgetIds(getComponentName(context))
        widgetIds.forEach { widgetId ->
            updateWidgetData(context, appWidgetManager, widgetId)
        }
        performUpdate(context)
    }
}
