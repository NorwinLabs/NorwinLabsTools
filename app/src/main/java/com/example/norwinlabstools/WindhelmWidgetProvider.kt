package com.example.norwinlabstools

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

class WindhelmWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.windhelm_widget)

        // Main action: Open Windhelm Website
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://windhelmthegame.ddns.net"))
        val webPendingIntent = PendingIntent.getActivity(context, 0, webIntent, PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_main_action, webPendingIntent)

        // Shortcut: Calculator (Tool ID 1)
        views.setOnClickPendingIntent(R.id.btn_calc, getAppPendingIntent(context, "LAUNCH_TOOL_1", 1))

        // Shortcut: Notes (Tool ID 3)
        views.setOnClickPendingIntent(R.id.btn_notes, getAppPendingIntent(context, "LAUNCH_TOOL_3", 3))

        // Shortcut: Settings (Tool ID 4)
        views.setOnClickPendingIntent(R.id.btn_settings, getAppPendingIntent(context, "LAUNCH_TOOL_4", 4))

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun getAppPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}