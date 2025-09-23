package github.luthfipun.ck_downloader_core.service

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import github.luthfipun.ck_downloader_core.core.CkDownloadManager
import github.luthfipun.ck_downloader_core.util.CkDownloadAction
import github.luthfipun.ck_downloader_core.util.CkDownloadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

abstract class CkDownloadService : Service() {

	private var manager: CkDownloadManager? = null
	private var okhttp: OkHttpClient? = null
	private var notificationManager: NotificationManagerCompat? = null
	private var notificationBuilder: NotificationCompat.Builder? = null

	private val jobDownloads = mutableMapOf<String, Job>()

	companion object {
		const val PARAM_STATE = "PARAM_STATE"
		const val PARAM_ID = "PARAM_ID"
		const val PARAM_PATH = "PARAM_PATH"
		const val PARAM_URL = "PARAM_URL"
		const val PARAM_LENGTH = "PARAM_LENGTH"
		private const val NOTIFICATION_ID = 2024
	}

	protected abstract fun getCkDownloadManager(): CkDownloadManager
	protected abstract fun getOkHttpClient(): OkHttpClient
	protected abstract fun buildNotification(): Pair<NotificationCompat.Builder, NotificationManagerCompat>

	override fun onCreate() {
		super.onCreate()
		manager = getCkDownloadManager()
		okhttp = getOkHttpClient()
		val (builder, managerCompat) = buildNotification()
		notificationBuilder = builder
		notificationManager = managerCompat
		notificationBuilder?.build()?.let {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				startForeground(NOTIFICATION_ID, it, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
			} else {
				startForeground(NOTIFICATION_ID, it)
			}
		}
		observeProgressFlow(manager, notificationManager, notificationBuilder)
	}

	private fun observeProgressFlow(
		manager: CkDownloadManager?,
		notificationManager: NotificationManagerCompat?,
		notificationBuilder: NotificationCompat.Builder?
	) {
		CoroutineScope(Dispatchers.IO).launch {
			try {
				manager?.getTotalProgress()?.collect { progress ->
					if (progress == null) {
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
							stopForeground(STOP_FOREGROUND_REMOVE)
						} else {
							@Suppress("DEPRECATION")
							stopForeground(true)
						}
						stopSelf()
						return@collect
					}
					withContext(Dispatchers.Main) {
						notificationBuilder?.setProgress(100, progress, false)
						notificationBuilder?.setContentText("$progress% Complete")
						notificationBuilder?.build()?.let {
							if (ActivityCompat.checkSelfPermission(
                                    application.applicationContext,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
								notificationManager?.notify(NOTIFICATION_ID, it)
							}
						}
					}
				}
			} catch (e: Exception) {
				Log.e(CkDownloadService::class.java.name, e.message.orEmpty())
			}
		}
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		val state = intent?.getStringExtra(PARAM_STATE).orEmpty()
		val uniqueId = intent?.getStringExtra(PARAM_ID).orEmpty()
		val filePath = intent?.getStringExtra(PARAM_PATH).orEmpty()
		val url = intent?.getStringExtra(PARAM_URL).orEmpty()
		val contentLength = intent?.getLongExtra(PARAM_LENGTH, 0L) ?: 0L

		if (state.isEmpty()) {
			return START_NOT_STICKY
		}

		if (CkDownloadAction.valueOf(state) == CkDownloadAction.ACTION_STOP_ALL) {
			jobDownloads.values.forEach { it.cancel() }
			jobDownloads.clear()
			return START_NOT_STICKY
		}

		if (uniqueId.isEmpty() || filePath.isEmpty()) {
			return START_NOT_STICKY
		}

		if (CkDownloadAction.valueOf(state) == CkDownloadAction.ACTION_STOP) {
			jobDownloads[uniqueId]?.cancel("STOP_JOB")
			return START_NOT_STICKY
		}

		if (url.isEmpty()) {
			CoroutineScope(Dispatchers.IO).launch { onError(uniqueId) }
			return START_NOT_STICKY
		}

		try {
			if (File(filePath).exists()) {
				File(filePath).delete()
			}
		} catch (e: Exception) {
			Log.e(CkDownloadService::class.java.name, e.message.orEmpty())
		}

		val outputFile = File(filePath)

		if (contentLength > 0) {
			jobDownloadWithContentLength(
				uniqueId = uniqueId,
				filePath = filePath,
				url = url,
				contentLength = contentLength,
				outputFile = outputFile
			)
		}else {
			jobDownloadWithoutContentLength(
				uniqueId = uniqueId,
				filePath = filePath,
				url = url,
				outputFile = outputFile
			)
		}

		return START_NOT_STICKY
	}

	private fun jobDownloadWithoutContentLength(
		uniqueId: String,
		filePath: String,
		url: String,
		outputFile: File
	) {
		jobDownloads[uniqueId] = CoroutineScope(Dispatchers.IO).launch {
			try {
				val client = okhttp ?: throw Exception("Please implement okhttp client!")
				val chunkSize = downloadMaxChunkLength(30_000_000)
				var pos = outputFile.length()
				var done = false

				while (!done && isActive) {
					val end = pos + chunkSize - 1L
					val rangeVal = "bytes=$pos-$end"
					val resp = requestRange(client, url, rangeVal)

					resp?.use { r ->
						val body = r.body
						when (r.code) {
							206 -> {
								if (body != null) {
									saveChunkToFile(body.byteStream(), outputFile)
									if (body.contentLength() < chunkSize) done = true
									pos += body.contentLength()
									try {
										manager?.updateProgress(uniqueId, (pos.div(1000_000).toDouble().toInt()))
									} catch (e: Exception) {
										Log.e(CkDownloadService::class.java.name, e.message.orEmpty())
									}
								}else {
									done = true
								}
							}
							200 -> {
								if (body != null) {
									saveChunkToFile(body.byteStream(), outputFile)
								}else {
									done = true
								}
							}
							else -> {
								done = true
								if (File(filePath).exists()) {
									File(filePath).delete()
								}
								onError(uniqueId)
							}
						}
					}
				}

				onSuccess(uniqueId)
			} catch (e: IOException) {
				Log.e(CkDownloadService::class.java.name, e.message.orEmpty())
				if (e.message == "STOP_JOB") {
					if (File(filePath).exists()) {
						File(filePath).delete()
					}
				} else {
					onError(uniqueId)
				}
			}
		}
	}

	private fun jobDownloadWithContentLength(
		uniqueId: String,
		filePath: String,
		url: String,
		contentLength: Long,
		outputFile: File
	) {
		jobDownloads[uniqueId] = CoroutineScope(Dispatchers.IO).launch {
			try {
				val client = okhttp ?: throw Exception("Please implement okhttp client!")
				val chunkSize = downloadMaxChunkLength(contentLength)
				var startByte = 0L
				var endByte = chunkSize - 1L

				while (startByte < contentLength && isActive) {
					val response = requestRange(client, url, "bytes=$startByte-$endByte")
					response?.body?.let { body ->
						saveChunkToFile(body.byteStream(), outputFile)
						startByte = endByte + 1L
						endByte += chunkSize

						if (endByte >= contentLength) {
							endByte = contentLength - 1
						}

						val progress = (endByte.times(100)).div(contentLength).toInt()
						try {
							manager?.updateProgress(uniqueId, progress)
						} catch (e: Exception) {
							Log.e(CkDownloadService::class.java.name, e.message.orEmpty())
						}
					}
				}

				onSuccess(uniqueId)
			} catch (e: IOException) {
				Log.e(CkDownloadService::class.java.name, e.message.orEmpty())
				if (e.message == "STOP_JOB") {
					if (File(filePath).exists()) {
						File(filePath).delete()
					}
				} else {
					onError(uniqueId)
				}
			}
		}
	}

	private fun requestRange(okhttp: OkHttpClient, url: String, range: String): Response? {
		val req = Request.Builder()
			.url(url)
			.header("Range", range)
			.build()
		return okhttp.newCall(req).execute()
	}

	private suspend fun onError(uniqueId: String) {
		try {
			manager?.updateState(uniqueId, CkDownloadState.STATE_ERROR).also {
				jobDownloads[uniqueId]?.cancel()
			}
		} catch (e: Exception) {
			Log.e(CkDownloadService::class.java.name, e.message.orEmpty())
		}
	}

	private suspend fun onSuccess(uniqueId: String) {
		try {
			manager?.updateState(uniqueId, CkDownloadState.STATE_DONE).also {
				jobDownloads[uniqueId]?.cancel()
			}
		} catch (e: Exception) {
			Log.e(CkDownloadService::class.java.name, e.message.orEmpty())
		}
	}

	override fun onBind(intent: Intent?): IBinder? {
		return null
	}

	private fun downloadMaxChunkLength(contentLength: Long): Long {
		val m = (1000.times(1000)).toLong()
		return when {
			contentLength <= 50.times(m) -> 2.times(m)
			contentLength <= 100.times(m) -> 4.times(m)
			contentLength <= 500.times(m) -> 5.times(m)
			else -> 8.times(m)
		}
	}

	private fun saveChunkToFile(inputStream: java.io.InputStream, outputFile: File) {
		val buffer = ByteArray(8192)
		val outputStream = FileOutputStream(outputFile, true)
		try {
			var bytesRead: Int
			while (inputStream.read(buffer).also { bytesRead = it } != - 1) {
				outputStream.write(buffer, 0, bytesRead)
			}
			outputStream.flush()
		} catch (e: IOException) {
			e.printStackTrace()
		} finally {
			try {
				outputStream.close()
			} catch (e: IOException) {
				e.printStackTrace()
			}
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		jobDownloads.values.forEach { it.cancel() }
		jobDownloads.clear()
	}
}