package github.luthfipun.ck_downloader_core.core

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import github.luthfipun.ck_downloader_core.db.entity.CkDownloadEntity
import github.luthfipun.ck_downloader_core.service.CkDownloadService
import github.luthfipun.ck_downloader_core.service.CkDownloadService.Companion.PARAM_ID
import github.luthfipun.ck_downloader_core.service.CkDownloadService.Companion.PARAM_LENGTH
import github.luthfipun.ck_downloader_core.service.CkDownloadService.Companion.PARAM_PATH
import github.luthfipun.ck_downloader_core.service.CkDownloadService.Companion.PARAM_STATE
import github.luthfipun.ck_downloader_core.service.CkDownloadService.Companion.PARAM_URL
import github.luthfipun.ck_downloader_core.util.CkDownloadAction.ACTION_QUEUE
import github.luthfipun.ck_downloader_core.util.CkDownloadAction.ACTION_STOP
import github.luthfipun.ck_downloader_core.util.CkDownloadAction.ACTION_STOP_ALL
import github.luthfipun.ck_downloader_core.util.CkDownloadState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class CkDownloadManager(
	private val context: Context,
	private val ckDownloadStandaloneDatabase: CkDownloadStandaloneDatabase
) {

	private val dao by lazy { ckDownloadStandaloneDatabase.db.ckDownloadDao() }
	private val addMutex = Mutex()

	// filesDir instead of cacheDir, the system may evict cache files at any time
	private val defaultPath = File(context.filesDir, "offline").path

	@Throws(Exception::class)
	suspend fun sendAddDownload(
		request: CkDownloadRequest,
		clazz: Class<out CkDownloadService>?,
	) {
		val userPath = request.path?.path ?: defaultPath
		if (File(userPath).exists().not()) {
			File(userPath).mkdirs()
		}

		val fileExtension = if (request.extension != null) ".${request.extension}" else ""
		val filePath = File(userPath, request.fileName + fileExtension)

		val entity = CkDownloadEntity(
			uniqueId = request.uniqueId,
			url = request.url,
			filePath = filePath.path,
			contentLength = request.contentLength,
			progress = 0,
			state = CkDownloadState.STATE_DOWNLOADING.name
		)

		// Serialize check-then-insert so concurrent calls can't create duplicate unique_id rows
		val target = addMutex.withLock {
			val existEntity = getByUniqueId(entity.uniqueId)
			if (existEntity == null) {
				dao.insertOne(entity)
				entity
			} else {
				if (existEntity.state != CkDownloadState.STATE_ERROR.name) {
					return
				}
				updateState(existEntity.uniqueId, CkDownloadState.STATE_DOWNLOADING)
				updateProgress(existEntity.uniqueId, 0)
				existEntity
			}
		}

		val intent = Intent(context, clazz).apply {
			putExtra(PARAM_STATE, ACTION_QUEUE.name)
			putExtra(PARAM_ID, target.uniqueId)
			putExtra(PARAM_PATH, target.filePath)
			putExtra(PARAM_URL, target.url)
			putExtra(PARAM_LENGTH, target.contentLength)
		}
		try {
			startDownloadService(intent)
		} catch (e: Exception) {
			// Service could not start (e.g. app in background on Android 12+), allow retry
			updateState(target.uniqueId, CkDownloadState.STATE_ERROR)
			throw e
		}
	}

	private fun startDownloadService(intent: Intent) {
		// CkDownloadService runs in the foreground, startService() is rejected from background on Android 8+
		ContextCompat.startForegroundService(context, intent)
	}

	@Throws(Exception::class)
	suspend fun sendRemoveDownload(
		uniqueId: String,
		clazz: Class<out CkDownloadService>?
	) {
		getByUniqueId(uniqueId)?.let {
			val intent = Intent(context, clazz).apply {
				putExtra(PARAM_STATE, ACTION_STOP.name)
				putExtra(PARAM_ID, it.uniqueId)
				putExtra(PARAM_PATH, it.filePath)
			}
			startDownloadService(intent)

			dao.deleteByUniqueId(uniqueId)
			if (File(it.filePath).exists()) {
				File(it.filePath).delete()
			}
		}
	}

	@Throws(Exception::class)
	suspend fun sendStopRemoveAllDownloads(
		clazz: Class<out CkDownloadService>?
	) {
		val intent = Intent(context, clazz).apply {
			putExtra(PARAM_STATE, ACTION_STOP_ALL.name)
		}
		startDownloadService(intent)
		getAllDownloads().forEach {
			if (File(it.filePath).exists()) {
				File(it.filePath).delete()
			}
		}
		dao.clean()
	}

	@Throws(Exception::class)
	suspend fun getProgress() = dao.getProgress()

	@Throws(Exception::class)
	suspend fun getAllDownloads(isASC: Boolean = true) = dao.getAllDownloads(isASC)

	fun getProgressFlow() = dao.getProgressFlow()

	fun getStateFlow() = dao.getStateFlow()

	fun getTotalProgress() = dao.getTotalProgress()

	@Throws(Exception::class)
	suspend fun updateState(uniqueId: String, state: CkDownloadState) {
		dao.updateState(uniqueId, state.name)
	}

	/** Marks a download that was cut off before finishing as error, without touching finished ones. */
	internal suspend fun markInterrupted(uniqueId: String) {
		dao.updateStateIfCurrent(
			uniqueId,
			CkDownloadState.STATE_DOWNLOADING.name,
			CkDownloadState.STATE_ERROR.name
		)
	}

	@Throws(Exception::class)
	suspend fun updateProgress(uniqueId: String, progress: Int) {
		dao.updateProgress(uniqueId, progress)
	}

	@Throws(Exception::class)
	suspend fun getByUniqueId(uniqueId: String): CkDownloadEntity? {
		return dao.getByUniqueId(uniqueId)
	}
}
