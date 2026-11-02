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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap

abstract class CkDownloadService : Service() {

	private var manager: CkDownloadManager? = null
	private var okhttp: OkHttpClient? = null
	private var notificationManager: NotificationManagerCompat? = null
	private var notificationBuilder: NotificationCompat.Builder? = null

	private val exceptionHandler = CoroutineExceptionHandler { _, e ->
		Log.e(CkDownloadService::class.java.name, e.message.orEmpty(), e)
	}
	private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

	private val jobDownloads = ConcurrentHashMap<String, Job>()
	private val activeCalls = ConcurrentHashMap<String, Call>()
	private val downloadSemaphore by lazy { Semaphore(getMaxParallelDownloads().coerceAtLeast(1)) }

	@Volatile
	private var lastStartId = 0

	companion object {
		const val PARAM_STATE = "PARAM_STATE"
		const val PARAM_ID = "PARAM_ID"
		const val PARAM_PATH = "PARAM_PATH"
		const val PARAM_URL = "PARAM_URL"
		const val PARAM_LENGTH = "PARAM_LENGTH"
		private const val NOTIFICATION_ID = 2024
		private const val DEFAULT_MAX_PARALLEL_DOWNLOADS = 3
	}

	protected abstract fun getCkDownloadManager(): CkDownloadManager
	protected abstract fun getOkHttpClient(): OkHttpClient
	protected abstract fun buildNotification(): Pair<NotificationCompat.Builder, NotificationManagerCompat>

	/** Downloads beyond this limit wait in queue until a running one finishes. */
	protected open fun getMaxParallelDownloads(): Int = DEFAULT_MAX_PARALLEL_DOWNLOADS

	/** How many times a failed chunk is requested again, with exponential backoff, before the download fails. */
	protected open fun getMaxRetryPerChunk(): Int = CkChunkDownloader.DEFAULT_MAX_RETRY_PER_CHUNK

	override fun onCreate() {
		super.onCreate()
		manager = getCkDownloadManager()
		okhttp = getOkHttpClient()
		val (builder, managerCompat) = buildNotification()
		notificationBuilder = builder
		notificationManager = managerCompat
		startForegroundCompat()
		observeProgressFlow(manager, notificationManager, notificationBuilder)
	}

	private fun startForegroundCompat() {
		notificationBuilder?.build()?.let {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				startForeground(NOTIFICATION_ID, it, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
			} else {
				startForeground(NOTIFICATION_ID, it)
			}
		}
	}

	private fun observeProgressFlow(
		manager: CkDownloadManager?,
		notificationManager: NotificationManagerCompat?,
		notificationBuilder: NotificationCompat.Builder?
	) {
		serviceScope.launch {
			try {
				manager?.getTotalProgress()?.collect { progress ->
					if (progress == null) {
						stopIfIdle(manager)
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
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Log.e(CkDownloadService::class.java.name, e.message.orEmpty())
			}
		}
	}

	/**
	 * The emitted "no downloads" value may be stale when a new download was just added,
	 * so re-check the database and only stop if no newer start command has arrived since.
	 */
	private suspend fun stopIfIdle(manager: CkDownloadManager) {
		val startIdAtCheck = lastStartId
		if (manager.getProgress().isNotEmpty()) return
		withContext(Dispatchers.Main) {
			if (stopSelfResult(startIdAtCheck)) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
					stopForeground(STOP_FOREGROUND_REMOVE)
				} else {
					@Suppress("DEPRECATION")
					stopForeground(true)
				}
			}
		}
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		lastStartId = startId
		// Every startForegroundService() call must be followed by startForeground(),
		// including when the service is still alive after stopForeground()
		startForegroundCompat()

		// Commands that don't start a download may never change the database,
		// so check here whether the service still has work to do
		if (!handleCommand(intent)) {
			serviceScope.launch { manager?.let { stopIfIdle(it) } }
		}
		return START_NOT_STICKY
	}

	/** Returns true when a download job was launched. */
	private fun handleCommand(intent: Intent?): Boolean {
		val state = intent?.getStringExtra(PARAM_STATE).orEmpty()
		val uniqueId = intent?.getStringExtra(PARAM_ID).orEmpty()
		val filePath = intent?.getStringExtra(PARAM_PATH).orEmpty()
		val url = intent?.getStringExtra(PARAM_URL).orEmpty()
		val contentLength = intent?.getLongExtra(PARAM_LENGTH, 0L) ?: 0L

		val action = CkDownloadAction.entries.firstOrNull { it.name == state }
			?: return false

		if (action == CkDownloadAction.ACTION_STOP_ALL) {
			jobDownloads.keys.toList().forEach { stopJob(it) }
			return false
		}

		if (uniqueId.isEmpty() || filePath.isEmpty()) {
			return false
		}

		if (action == CkDownloadAction.ACTION_STOP) {
			stopJob(uniqueId)
			return false
		}

		if (url.isEmpty()) {
			serviceScope.launch { onError(uniqueId) }
			return false
		}

		if (jobDownloads[uniqueId]?.isActive == true) {
			return false
		}

		val outputFile = File(filePath)
		deleteFile(outputFile)

		jobDownloads[uniqueId] = serviceScope.launch {
			runDownloadJob(uniqueId, outputFile) {
				downloadSemaphore.withPermit {
					downloadInChunks(uniqueId, url, contentLength, outputFile)
				}
			}
		}

		return true
	}

	/**
	 * Runs [download] and maps its outcome to the download state.
	 * [download] returns true when the file is complete, false on a server error.
	 */
	private suspend fun runDownloadJob(
		uniqueId: String,
		outputFile: File,
		download: suspend () -> Boolean
	) {
		val job = currentCoroutineContext()[Job]
		try {
			if (download()) {
				onSuccess(uniqueId)
			} else {
				deleteFile(outputFile)
				onError(uniqueId)
			}
		} catch (e: CancellationException) {
			deleteFile(outputFile)
			throw e
		} catch (e: Exception) {
			deleteFile(outputFile)
			// Cancelling the OkHttp call on stop surfaces as an IOException, not an error
			if (currentCoroutineContext().isActive) {
				Log.e(CkDownloadService::class.java.name, e.message.orEmpty(), e)
				onError(uniqueId)
			}
		} finally {
			activeCalls.remove(uniqueId)
			if (job != null) jobDownloads.remove(uniqueId, job)
		}
	}

	/** Returns true when the file is complete, false when it failed after retries. */
	private suspend fun downloadInChunks(
		uniqueId: String,
		url: String,
		contentLength: Long,
		outputFile: File
	): Boolean {
		val client = okhttp ?: throw IllegalStateException("Please implement okhttp client!")
		val downloader = CkChunkDownloader(
			client = client,
			maxRetryPerChunk = getMaxRetryPerChunk().coerceAtLeast(0),
			onCallCreated = { activeCalls[uniqueId] = it },
			onProgress = { updateProgress(uniqueId, it) }
		)
		return when (val result = downloader.download(url, contentLength, outputFile)) {
			CkChunkDownloader.Result.Success -> true
			is CkChunkDownloader.Result.Failed -> {
				Log.e(CkDownloadService::class.java.name, "Download $uniqueId failed: ${result.reason}")
				false
			}
		}
	}

	private fun stopJob(uniqueId: String) {
		jobDownloads[uniqueId]?.cancel()
		// execute() and stream reads are blocking, cancelling the call aborts them
		activeCalls[uniqueId]?.cancel()
	}

	private suspend fun updateProgress(uniqueId: String, progress: Int) {
		try {
			manager?.updateProgress(uniqueId, progress)
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Log.e(CkDownloadService::class.java.name, e.message.orEmpty())
		}
	}

	private suspend fun onError(uniqueId: String) {
		try {
			manager?.updateState(uniqueId, CkDownloadState.STATE_ERROR)
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Log.e(CkDownloadService::class.java.name, e.message.orEmpty())
		}
	}

	private suspend fun onSuccess(uniqueId: String) {
		try {
			manager?.updateState(uniqueId, CkDownloadState.STATE_DONE)
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Log.e(CkDownloadService::class.java.name, e.message.orEmpty())
		}
	}

	private fun deleteFile(file: File) {
		try {
			if (file.exists()) {
				file.delete()
			}
		} catch (e: Exception) {
			Log.e(CkDownloadService::class.java.name, e.message.orEmpty())
		}
	}

	override fun onBind(intent: Intent?): IBinder? {
		return null
	}

	override fun onDestroy() {
		super.onDestroy()
		val interrupted = jobDownloads.keys.toList()
		activeCalls.values.forEach { it.cancel() }
		activeCalls.clear()
		jobDownloads.clear()
		serviceScope.cancel()

		// serviceScope is gone, use a short-lived scope so cut-off downloads
		// don't stay in STATE_DOWNLOADING and can be retried
		val manager = manager ?: return
		if (interrupted.isNotEmpty()) {
			CoroutineScope(Dispatchers.IO + exceptionHandler).launch {
				interrupted.forEach { manager.markInterrupted(it) }
			}
		}
	}
}
