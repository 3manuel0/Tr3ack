package com.example.tr3ack.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.tr3ack.MainActivity
import com.example.tr3ack.R
import com.example.tr3ack.Tr3ackApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Pushes today's totals into every instance of [Tr3ackWidgetProvider]. Called
 * from the widget host (onUpdate) and after every data change via the automatic
 * backup watcher, so the widget is never more than a few seconds stale.
 */
object WidgetUpdater {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun update(context: Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching {
                val repository = (app as Tr3ackApplication).repository
                val today = LocalDate.now().toString()
                val sets = repository.getSetsForDate(today).first()
                val exercises = repository.allExercises.first()
                val bodyWeight = repository.getEffectiveBodyWeight(today)

                val exerciseById = exercises.associateBy { it.id }
                val exerciseCount = sets.map { it.exerciseId }.distinct().size
                val volume = sets.sumOf { set ->
                    val isBodyweight = exerciseById[set.exerciseId]?.isBodyweightBased == true
                    val weight = if (isBodyweight && bodyWeight != null) {
                        bodyWeight + set.addedWeightKg
                    } else {
                        set.addedWeightKg
                    }
                    weight * set.reps
                }

                val views = RemoteViews(app.packageName, R.layout.tr3ack_widget)
                views.setTextViewText(
                    R.id.widget_today,
                    if (sets.isEmpty()) "No sets yet today" else "$sets.size sets · $exerciseCount exercises"
                )
                views.setTextViewText(
                    R.id.widget_volume,
                    "%.0f kg·reps".format(volume)
                )

                val openIntent = Intent(app, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val pending = PendingIntent.getActivity(
                    app,
                    0,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pending)

                val manager = AppWidgetManager.getInstance(app)
                val ids = manager.getAppWidgetIds(
                    ComponentName(app, Tr3ackWidgetProvider::class.java)
                )
                if (ids.isNotEmpty()) {
                    manager.updateAppWidget(ids, views)
                }
            }
        }
    }
}
