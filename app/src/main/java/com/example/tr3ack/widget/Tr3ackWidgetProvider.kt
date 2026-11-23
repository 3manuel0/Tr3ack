package com.example.tr3ack.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

/**
 * Home-screen widget: a browsable carousel of the four personal records.
 * Left/right arrows advance the page; tapping a record opens the app.
 */
class Tr3ackWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_NEXT = "com.example.tr3ack.widget.NEXT"
        const val ACTION_PREV = "com.example.tr3ack.widget.PREV"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater.update(context)
    }

    override fun onEnabled(context: Context) {
        WidgetUpdater.update(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_NEXT -> {
                WidgetUpdater.moveTo(
                    context,
                    (WidgetUpdater.currentPage(context) + 1) % WidgetUpdater.PAGE_COUNT
                )
                WidgetUpdater.update(context)
            }
            ACTION_PREV -> {
                WidgetUpdater.moveTo(
                    context,
                    (WidgetUpdater.currentPage(context) - 1 + WidgetUpdater.PAGE_COUNT) % WidgetUpdater.PAGE_COUNT
                )
                WidgetUpdater.update(context)
            }
        }
    }
}
