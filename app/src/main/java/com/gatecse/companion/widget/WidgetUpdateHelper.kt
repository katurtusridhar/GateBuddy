package com.gatecse.companion.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WidgetUpdateHelper {

    suspend fun updateAllWidgets(context: Context) = withContext(Dispatchers.IO) {
        try {
            val manager = GlanceAppWidgetManager(context)
            
            CountdownWidget().apply {
                val glanceIds = manager.getGlanceIds(CountdownWidget::class.java)
                glanceIds.forEach { update(context, it) }
            }

            TodayTasksWidget().apply {
                val glanceIds = manager.getGlanceIds(TodayTasksWidget::class.java)
                glanceIds.forEach { update(context, it) }
            }

            PrepProgressWidget().apply {
                val glanceIds = manager.getGlanceIds(PrepProgressWidget::class.java)
                glanceIds.forEach { update(context, it) }
            }

            UpcomingTestWidget().apply {
                val glanceIds = manager.getGlanceIds(UpcomingTestWidget::class.java)
                glanceIds.forEach { update(context, it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
