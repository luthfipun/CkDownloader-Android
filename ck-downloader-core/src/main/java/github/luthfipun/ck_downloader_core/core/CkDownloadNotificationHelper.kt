package github.luthfipun.ck_downloader_core.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class CkDownloadNotificationHelper constructor(
	private val context: Context,
	private val channelId: String,
	private val channelName: String
) {
	fun buildProgressNotification(
		@DrawableRes notificationIcon: Int?,
		contentIntent: Intent?,
		message: String
	): Pair<NotificationCompat.Builder, NotificationManagerCompat> {

		val notificationManager = NotificationManagerCompat.from(context)
		createNotificationChannel(channelId, channelName, notificationManager)

		val notificationCompat = NotificationCompat.Builder(context, channelId)
			.setSmallIcon(notificationIcon ?: android.R.drawable.stat_sys_download)
			.setContentTitle(message)
			.setProgress(100, 0, false)
			.setOngoing(true)
		contentIntent?.let {
			notificationCompat.setContentIntent(createNotificationIntent(context, it))
		}
		return Pair(notificationCompat, notificationManager)
	}

	private fun createNotificationIntent(context: Context, intent: Intent): PendingIntent {
		return PendingIntent.getActivity(
			context,
			5000,
			intent,
			PendingIntent.FLAG_UPDATE_CURRENT
		)
	}

	private fun createNotificationChannel(
		channelId: String,
		channelName: String,
		notificationManager: NotificationManagerCompat
	) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val notificationChannel = NotificationChannel(
				channelId,
				channelName,
				NotificationManager.IMPORTANCE_LOW
			)
			notificationManager.createNotificationChannel(notificationChannel)
		}
	}
}