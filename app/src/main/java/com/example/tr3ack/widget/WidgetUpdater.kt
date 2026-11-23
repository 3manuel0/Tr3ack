package com.example.tr3ack.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.tr3ack.MainActivity
import com.example.tr3ack.R
import com.example.tr3ack.Tr3ackApplication
import com.example.tr3ack.data.entity.ExerciseIds
import com.example.tr3ack.data.entity.ExerciseStatsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Renders the widget as a browsable carousel of the four personal records,
 * with left/right arrows and a tap-to-open action. The current page index is
 * stored in SharedPreferences so it survives data refreshes.
 */
object WidgetUpdater {

    const val PAGE_COUNT = 4

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dateFormat = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

    private const val PREFS = "tr3ack_widget"
    private const val KEY_INDEX = "page_index"

    fun currentPage(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_INDEX, 0)
            .coerceIn(0, PAGE_COUNT - 1)

    fun moveTo(context: Context, index: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_INDEX, index.coerceIn(0, PAGE_COUNT - 1))
            .apply()
    }

    fun update(context: Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching {
                val repository = (app as Tr3ackApplication).repository
                val stats = repository.exerciseStats.first().associateBy { it.exerciseId }
                val exercises = repository.allExercises.first().associateBy { it.id }

                val exerciseIds = listOf(
                    ExerciseIds.WEIGHTED_PULL_UPS,
                    ExerciseIds.WEIGHTED_DIPS,
                    ExerciseIds.BICEP_CURLS,
                    ExerciseIds.LATERAL_RAISES
                )

                val views = RemoteViews(app.packageName, R.layout.tr3ack_widget)

                exerciseIds.forEachIndexed { index, exerciseId ->
                    val name = exercises[exerciseId]?.name?.let(::shortName) ?: "Exercise"
                    val row = stats[exerciseId]
                    val (value, detail) = if (row != null && row.bestE1RM > 0.0) {
                        "%.1f kg".format(row.bestE1RM) to detailText(row)
                    } else {
                        "—" to "No PR yet"
                    }
                    views.setImageViewResource(iconId(index), iconRes(exerciseId))
                    views.setTextViewText(nameId(index), name)
                    views.setTextViewText(valueId(index), value)
                    views.setTextViewText(detailId(index), detail)
                }

                val current = currentPage(app)
                for (i in 0 until PAGE_COUNT) {
                    views.setViewVisibility(pageId(i), if (i == current) View.VISIBLE else View.GONE)
                }
                views.setTextViewText(
                    R.id.widget_indicator,
                    "${current + 1} / $PAGE_COUNT"
                )
                views.setOnClickPendingIntent(
                    R.id.widget_prev,
                    flipIntent(app, Tr3ackWidgetProvider.ACTION_PREV)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_next,
                    flipIntent(app, Tr3ackWidgetProvider.ACTION_NEXT)
                )

                val openIntent = Intent(app, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val openPending = PendingIntent.getActivity(
                    app,
                    0,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, openPending)

                val manager = AppWidgetManager.getInstance(app)
                val ids = manager.getAppWidgetIds(ComponentName(app, Tr3ackWidgetProvider::class.java))
                if (ids.isNotEmpty()) {
                    manager.updateAppWidget(ids, views)
                }
            }
        }
    }

    private fun detailText(row: ExerciseStatsEntity): String {
        val date = runCatching {
            LocalDate.parse(row.bestE1RMDate).format(dateFormat)
        }.getOrDefault("")
        val pct = "%.1f%% BW".format(row.bestE1RMPercentBodyWeight)
        return if (date.isNotEmpty()) "$pct · $date" else pct
    }

    private fun shortName(name: String): String = name
        .replace("Weighted ", "")
        .removePrefix("Bicep ")
        .removeSuffix(" Raises")

    private fun pageId(index: Int): Int = when (index) {
        0 -> R.id.widget_page_0
        1 -> R.id.widget_page_1
        2 -> R.id.widget_page_2
        else -> R.id.widget_page_3
    }

    private fun iconId(index: Int): Int = when (index) {
        0 -> R.id.widget_icon_0
        1 -> R.id.widget_icon_1
        2 -> R.id.widget_icon_2
        else -> R.id.widget_icon_3
    }

    private fun iconRes(exerciseId: Long): Int = when (exerciseId) {
        ExerciseIds.WEIGHTED_PULL_UPS -> R.drawable.ic_ach_pullup
        ExerciseIds.WEIGHTED_DIPS -> R.drawable.ic_ex_dips
        ExerciseIds.BICEP_CURLS -> R.drawable.ic_ach_bicep
        ExerciseIds.LATERAL_RAISES -> R.drawable.ic_ach_lateral
        else -> R.drawable.ic_ach_weight
    }

    private fun nameId(index: Int): Int = when (index) {
        0 -> R.id.widget_name_0
        1 -> R.id.widget_name_1
        2 -> R.id.widget_name_2
        else -> R.id.widget_name_3
    }

    private fun valueId(index: Int): Int = when (index) {
        0 -> R.id.widget_value_0
        1 -> R.id.widget_value_1
        2 -> R.id.widget_value_2
        else -> R.id.widget_value_3
    }

    private fun detailId(index: Int): Int = when (index) {
        0 -> R.id.widget_detail_0
        1 -> R.id.widget_detail_1
        2 -> R.id.widget_detail_2
        else -> R.id.widget_detail_3
    }

    private fun flipIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, Tr3ackWidgetProvider::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
