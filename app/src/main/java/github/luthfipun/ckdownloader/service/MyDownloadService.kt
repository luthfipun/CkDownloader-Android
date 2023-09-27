package github.luthfipun.ckdownloader.service

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import github.luthfipun.ck_downloader_core.core.CkDownloadManager
import github.luthfipun.ck_downloader_core.core.CkDownloadNotificationHelper
import github.luthfipun.ck_downloader_core.service.CkDownloadService
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Named

@AndroidEntryPoint
class MyDownloadService: CkDownloadService() {

	@Inject
	@ApplicationContext
	lateinit var context: Context

	@Inject
	lateinit var ckManager: CkDownloadManager

	@Inject
	lateinit var okhttp: OkHttpClient

	override fun getCkDownloadManager(): CkDownloadManager {
		return ckManager
	}

	override fun getOkHttpClient(): OkHttpClient {
		return okhttp
	}

	override fun buildNotification(): Pair<NotificationCompat.Builder, NotificationManagerCompat> {
		return CkDownloadNotificationHelper(
			context, "download_1234", "Download Test"
		).buildProgressNotification(
			null, null, "Downloading Content"
		)
	}
}