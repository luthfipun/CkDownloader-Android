package github.luthfipun.ck_downloader_core.core

import android.content.Context
import android.content.Intent
import github.luthfipun.ck_downloader_core.db.entity.CkDownloadEntity
import github.luthfipun.ck_downloader_core.service.CkDownloadService
import github.luthfipun.ck_downloader_core.service.CkDownloadService.Companion.PARAM_ID
import github.luthfipun.ck_downloader_core.service.CkDownloadService.Companion.PARAM_LENGTH
import github.luthfipun.ck_downloader_core.service.CkDownloadService.Companion.PARAM_PATH
import github.luthfipun.ck_downloader_core.service.CkDownloadService.Companion.PARAM_STATE
import github.luthfipun.ck_downloader_core.service.CkDownloadService.Companion.PARAM_URL
import github.luthfipun.ck_downloader_core.util.CkDownloadAction.ACTION_QUEUE
import github.luthfipun.ck_downloader_core.util.CkDownloadAction.ACTION_STOP
import github.luthfipun.ck_downloader_core.util.CkDownloadState
import java.io.File

class CkDownloadManager(
	private val context: Context,
	ckDownloadStandaloneDatabase: CkDownloadStandaloneDatabase
) {

	private val dao = ckDownloadStandaloneDatabase.db?.ckDownloadDao()
	private val defaultPath = context.cacheDir.path + "/offline"

	@Throws(Exception::class)
	suspend fun sendAddDownload(
		request: CkDownloadRequest,
		clazz: Class<out CkDownloadService>?,
	) {
		try {
			val userPath = request.path?.path ?: defaultPath
			if (File(userPath).exists().not()) {
				File(userPath).mkdir()
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
			if (! checkUniqueId(entity.uniqueId)) {
				insertOnce(entity).also {
					val intent = Intent(context, clazz).apply {
						putExtra(PARAM_STATE, ACTION_QUEUE.name)
						putExtra(PARAM_ID, entity.uniqueId)
						putExtra(PARAM_PATH, entity.filePath)
						putExtra(PARAM_URL, entity.url)
						putExtra(PARAM_LENGTH, entity.contentLength)
					}
					context.startService(intent)
				}
			} else {
				throw Exception("Download file is already!")
			}
		} catch (e: Exception) {
			throw e
		}
	}

	@Throws(Exception::class)
	suspend fun sendRemoveDownload(
		uniqueId: String,
		clazz: Class<out CkDownloadService>?
	) {
		try {
			getByUniqueId(uniqueId)?.let {
				val intent = Intent(context, clazz).apply {
					putExtra(PARAM_STATE, ACTION_STOP.name)
					putExtra(PARAM_ID, it.uniqueId)
					putExtra(PARAM_PATH, it.filePath)
				}
				context.startService(intent)

				deleteDownload(uniqueId)
				if (File(it.filePath).exists()) {
					File(it.filePath).delete()
				}
			}
		} catch (e: Exception) {
			throw e
		}
	}

	@Throws(Exception::class)
	suspend fun getProgress() =
		dao?.getProgress() ?: throw Exception("CkDownload manager not initialize")

	@Throws(Exception::class)
	suspend fun getAllDownloads(isASC: Boolean = true) =
		dao?.getAllDownloads(isASC) ?: throw Exception("CkDownload manager not initialize")

	@Throws(Exception::class)
	fun getProgressFlow() =
		dao?.getProgressFlow() ?: throw Exception("CkDownload manager not initialize")

	@Throws(Exception::class)
	fun getStateFlow() = dao?.getStateFlow() ?: throw Exception("CkDownload manager not initialize")

	@Throws(Exception::class)
	fun getTotalProgress() =
		dao?.getTotalProgress() ?: throw Exception("CkDownload manager not initialize")

	@Throws(Exception::class)
	suspend fun updateState(uniqueId: String, state: CkDownloadState) {
		try {
			dao?.updateState(uniqueId, state.name)
		} catch (e: Exception) {
			throw e
		}
	}

	@Throws(Exception::class)
	suspend fun updateProgress(uniqueId: String, progress: Int) {
		try {
			dao?.updateProgress(uniqueId, progress)
		} catch (e: Exception) {
			throw e
		}
	}

	@Throws(Exception::class)
	suspend fun clean() {
		try {
			dao?.clean()
		} catch (e: Exception) {
			throw e
		}
	}

	@Throws(Exception::class)
	suspend fun deleteDownload(uniqueId: String) {
		try {
			dao?.deleteByUniqueId(uniqueId)
		} catch (e: Exception) {
			throw e
		}
	}

	@Throws(Exception::class)
	private suspend fun insertOnce(entity: CkDownloadEntity) {
		try {
			dao?.insertOne(entity)
		} catch (e: Exception) {
			throw e
		}
	}

	@Throws(Exception::class)
	private suspend fun checkUniqueId(uniqueId: String): Boolean {
		return try {
			dao?.checkByUniqueId(uniqueId) !! > 0
		} catch (e: Exception) {
			throw e
		}
	}

	@Throws(Exception::class)
	suspend fun getByUniqueId(uniqueId: String): CkDownloadEntity? {
		return try {
			dao?.getByUniqueId(uniqueId)
		} catch (e: Exception) {
			throw e
		}
	}
}