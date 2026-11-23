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
import com.example.tr3ack.data.entity.Exercise
import com.example.tr3ack.data.entity.ExerciseIds
import com.example.tr3ack.data.entity.ExerciseStatsEntity
import com.example.tr3ack.viewmodel.RankLadderRules
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
 * mirroring the Stats "PERSONAL RECORD" card (gradient, rank chip, big e1RM,
 * detail line and three stat tiles). The current page index is stored in
 * SharedPreferences so it survives data refreshes.
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
                    val exercise = exercises[exerciseId]
                    val row = stats[exerciseId]

                    views.setTextViewText(
                        nameId(index),
                        exercise?.name?.let(::shortName)?.uppercase() ?: "EXERCISE"
                    )

                    val tier = exercise?.let { rankTier(it, row) }
                    if (tier != null) {
                        views.setTextViewText(tierId(index), "● " + tier.replaceFirstChar { it.uppercase() })
                        views.setTextColor(tierId(index), tierColor(tier))
                    } else {
                        views.setTextViewText(tierId(index), "")
                    }

                    if (row != null && row.bestE1RM > 0.0) {
                        views.setTextViewText(valueId(index), "%.1f".format(row.bestE1RM))
                        views.setTextViewText(detailId(index), detailText(row))
                    } else {
                        views.setTextViewText(valueId(index), "—")
                        views.setTextViewText(detailId(index), "No PR yet")
                    }

                    val tiles = if (row != null && row.bestE1RM > 0.0) {
                        tileData(row, exercise?.isBodyweightBased == true)
                    } else {
                        listOf("" to "", "" to "", "" to "")
                    }
                    tiles.forEachIndexed { tileIndex, (label, value) ->
                        views.setTextViewText(tileLabelId(index, tileIndex), label)
                        views.setTextViewText(tileValueId(index, tileIndex), value)
                    }
                }

                val current = currentPage(app)
                for (i in 0 until PAGE_COUNT) {
                    views.setViewVisibility(pageId(i), if (i == current) View.VISIBLE else View.GONE)
                }
                views.setTextViewText(R.id.widget_indicator, "${current + 1} / $PAGE_COUNT")

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

    private fun rankTier(exercise: Exercise, stats: ExerciseStatsEntity?): String? {
        if (stats == null) return null
        val thresholds = RankLadderRules.thresholdsFor(exercise)
        if (thresholds.isEmpty()) return null
        val best = if (exercise.isBodyweightBased) stats.maxBodyweightRatio else stats.maxAddedWeightGte6
        return RankLadderRules.evaluate(thresholds, best).currentTier
    }

    private fun tierColor(tier: String): Int = when (tier) {
        "iron" -> 0xFFB9C0C8.toInt()
        "copper" -> 0xFFD07A2E.toInt()
        "silver" -> 0xFFAFBAC4.toInt()
        "gold" -> 0xFFD9A92C.toInt()
        "emerald" -> 0xFF22B573.toInt()
        "diamond" -> 0xFF4BB8E8.toInt()
        else -> 0xFFB9C0C8.toInt()
    }

    private fun detailText(row: ExerciseStatsEntity): String {
        val date = runCatching {
            LocalDate.parse(row.bestE1RMDate).format(dateFormat)
        }.getOrDefault("")
        val suffix = if (date.isNotEmpty()) " · $date" else ""
        return "%+.1f kg × %d$suffix".format(row.bestE1RMAddedWeight, row.bestE1RMReps)
    }

    /** (label, value) for the three stat tiles, mirroring the hero card. */
    private fun tileData(row: ExerciseStatsEntity, isBodyweight: Boolean): List<Pair<String, String>> {
        return if (isBodyweight) {
            listOf(
                "% BW" to "%.1f%%".format(row.bestE1RMPercentBodyWeight),
                "Added" to "%.1f kg".format(row.bestE1RMAddedWeight),
                "System" to "%.1f kg".format(row.bestE1RMTotalSystemWeight)
            )
        } else {
            listOf(
                "Added" to "%.1f kg".format(row.bestE1RMAddedWeight),
                "Reps" to row.bestE1RMReps.toString(),
                "e1RM" to "%.1f kg".format(row.bestE1RM)
            )
        }
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

    private fun nameId(index: Int): Int = when (index) {
        0 -> R.id.widget_name_0
        1 -> R.id.widget_name_1
        2 -> R.id.widget_name_2
        else -> R.id.widget_name_3
    }

    private fun tierId(index: Int): Int = when (index) {
        0 -> R.id.widget_tier_0
        1 -> R.id.widget_tier_1
        2 -> R.id.widget_tier_2
        else -> R.id.widget_tier_3
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

    private fun tileLabelId(index: Int, tile: Int): Int = when (index) {
        0 -> when (tile) { 0 -> R.id.widget_tlabel_0_0; 1 -> R.id.widget_tlabel_0_1; else -> R.id.widget_tlabel_0_2 }
        1 -> when (tile) { 0 -> R.id.widget_tlabel_1_0; 1 -> R.id.widget_tlabel_1_1; else -> R.id.widget_tlabel_1_2 }
        2 -> when (tile) { 0 -> R.id.widget_tlabel_2_0; 1 -> R.id.widget_tlabel_2_1; else -> R.id.widget_tlabel_2_2 }
        else -> when (tile) { 0 -> R.id.widget_tlabel_3_0; 1 -> R.id.widget_tlabel_3_1; else -> R.id.widget_tlabel_3_2 }
    }

    private fun tileValueId(index: Int, tile: Int): Int = when (index) {
        0 -> when (tile) { 0 -> R.id.widget_tvalue_0_0; 1 -> R.id.widget_tvalue_0_1; else -> R.id.widget_tvalue_0_2 }
        1 -> when (tile) { 0 -> R.id.widget_tvalue_1_0; 1 -> R.id.widget_tvalue_1_1; else -> R.id.widget_tvalue_1_2 }
        2 -> when (tile) { 0 -> R.id.widget_tvalue_2_0; 1 -> R.id.widget_tvalue_2_1; else -> R.id.widget_tvalue_2_2 }
        else -> when (tile) { 0 -> R.id.widget_tvalue_3_0; 1 -> R.id.widget_tvalue_3_1; else -> R.id.widget_tvalue_3_2 }
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
