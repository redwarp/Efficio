package fr.geobert.efficio.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import fr.geobert.efficio.OnRefreshReceiver
import fr.geobert.efficio.R
import fr.geobert.efficio.db.TaskTable

/**
 * Implementation of App Widget functionality.
 */
class TaskListWidget : AppWidgetProvider() {
    val TAG = "TaskListWidget"

    companion object {
        val ACTION_CHECKBOX_CHANGED = "fr.geobert.efficio.action_checkbox_changed"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // There may be multiple widgets active, so update all of them
        Log.d(TAG, "onUpdate")
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: ${intent.action}")
        if (intent.action.equals(ACTION_CHECKBOX_CHANGED)) {
            val extras = intent.extras
            val appWidgetManager = AppWidgetManager.getInstance(context)
            TaskTable.updateDoneState(context, extras.getLong("taskId"), true)
            appWidgetManager.notifyAppWidgetViewDataChanged(
                    extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                            AppWidgetManager.INVALID_APPWIDGET_ID), R.id.tasks_list_widget)
            val intent = Intent(OnRefreshReceiver.REFRESH_ACTION)
            //intent.putExtra("storeId", ) // TODO get storeId according to widgetId
            context.sendBroadcast(intent)
        }
        super.onReceive(context, intent)
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        Log.d(TAG, "updateAppWidget $appWidgetId")

        val intent = Intent(context, TaskListWidgetService::class.java)
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        intent.data = Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME))

        // Construct the RemoteViews object
        val views = RemoteViews(context.packageName, R.layout.task_list_widget)
        views.setTextViewText(R.id.appwidget_text, context.getString(R.string.app_name))
        views.setRemoteAdapter(R.id.tasks_list_widget, intent)
        views.setEmptyView(R.id.tasks_list_widget, R.id.empty_text_widget)

        val onClickIntent = Intent(context, TaskListWidget::class.java)
        onClickIntent.action = ACTION_CHECKBOX_CHANGED
        onClickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

        val pIntent = PendingIntent.getBroadcast(context, 0, onClickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT)
        views.setPendingIntentTemplate(R.id.tasks_list_widget, pIntent)

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
