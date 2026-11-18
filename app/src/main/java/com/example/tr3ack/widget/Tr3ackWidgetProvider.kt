package com.example.tr3ack.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/**
 * Home-screen widget: today's set count and volume, tap to open the app.
 * Data is pushed by [WidgetUpdater]; onUpdate only kicks off a refresh.
 */
class Tr3ackWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater.update(context)
    }

    override fun onEnabled(context: Context) {
        WidgetUpdater.update(context)
    }
}
