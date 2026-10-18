package it.armando.appgroups

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

class GroupWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetHelper.update(context, manager, it) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        GroupRepository(context).removeWidgets(appWidgetIds)
    }
}
