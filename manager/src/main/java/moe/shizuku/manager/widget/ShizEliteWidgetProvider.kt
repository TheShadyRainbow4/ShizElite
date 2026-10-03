package moe.shizuku.manager.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.receiver.ManualStartReceiver
import moe.shizuku.manager.receiver.ManualStopReceiver
import moe.shizuku.manager.utils.ShizukuStateMachine

class ShizEliteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "moe.shizuku.manager.widget.UPDATE_STATE") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, ShizEliteWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                onUpdate(context, appWidgetManager, appWidgetIds)
            }
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val isRunning = ShizukuStateMachine.isRunning()
            val views = RemoteViews(context.packageName, R.layout.widget_shizelite)

            val token = ShizukuSettings.getAuthToken()

            if (isRunning) {
                // Currently running -> "Disable"
                views.setTextViewText(R.id.widget_text, "Disable")
                
                // Set colored icon
                views.setImageViewResource(R.id.widget_icon, R.mipmap.ic_launcher)

                val stopIntent = Intent(context, ManualStopReceiver::class.java).apply {
                    action = "${BuildConfig.APPLICATION_ID}.STOP"
                    putExtra("auth", token)
                }
                val stopPendingIntent = PendingIntent.getBroadcast(
                    context, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_icon, stopPendingIntent)

            } else {
                // Not running -> "Enable"
                views.setTextViewText(R.id.widget_text, "Enable")
                
                // Create grayscale icon
                val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
                if (drawable != null) {
                    val bitmap = Bitmap.createBitmap(
                        drawable.intrinsicWidth,
                        drawable.intrinsicHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    
                    val matrix = ColorMatrix()
                    matrix.setSaturation(0f)
                    drawable.colorFilter = ColorMatrixColorFilter(matrix)
                    drawable.draw(canvas)
                    
                    views.setImageViewBitmap(R.id.widget_icon, bitmap)
                }

                val startIntent = Intent(context, ManualStartReceiver::class.java).apply {
                    action = "${BuildConfig.APPLICATION_ID}.START"
                    putExtra("auth", token)
                }
                val startPendingIntent = PendingIntent.getBroadcast(
                    context, 0, startIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_icon, startPendingIntent)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun broadcastUpdate(context: Context) {
            val intent = Intent(context, ShizEliteWidgetProvider::class.java)
            intent.action = "moe.shizuku.manager.widget.UPDATE_STATE"
            context.sendBroadcast(intent)
        }
    }
}
