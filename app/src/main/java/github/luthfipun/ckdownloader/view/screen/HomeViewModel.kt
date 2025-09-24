package github.luthfipun.ckdownloader.view.screen

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.luthfipun.ck_downloader_core.core.CkDownloadManager
import github.luthfipun.ck_downloader_core.core.CkDownloadRequest
import github.luthfipun.ck_downloader_core.db.entity.CkDownloadEntity
import github.luthfipun.ckdownloader.service.MyDownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
	private val manager: CkDownloadManager
) : ViewModel() {

	private val _state = MutableStateFlow<List<CkDownloadEntity>>(emptyList())
	val state = _state.asStateFlow()

	companion object {
		private const val SAMPLE_URL = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4"
		private const val SAMPLE_CONTENT_LENGTH: Long = 48051822
		private const val SAMPLE_URL2 = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/1080/Big_Buck_Bunny_1080_10s_30MB.mp4"
		private const val SAMPLE_CONTENT_LENGTH2: Long = 0
	}

	init {
		observeProgressFlow()
		observeStateFlow()
		refreshData()
	}

	private fun refreshData() {
		viewModelScope.launch(Dispatchers.IO) {
			try {
				manager.getAllDownloads(isASC = false).also {
					_state.value = it
				}
			} catch (e: Exception) {
				Log.e(HomeViewModel::class.java.name, e.message.orEmpty())
			}
		}
	}

	private fun observeStateFlow() {
		viewModelScope.launch(Dispatchers.IO) {
			try {
				manager.getStateFlow().collect { stateEntities ->
					val updateState = state.value.map { value ->
						stateEntities.firstOrNull { it.uniqueId == value.uniqueId }?.let {
							value.copy(state = it.state)
						} ?: value
					}
					_state.update { updateState }
				}
			} catch (e: Exception) {
				Log.e(HomeViewModel::class.java.name, e.message.orEmpty())
			}
		}
	}

	private fun observeProgressFlow() {
		viewModelScope.launch(Dispatchers.IO) {
			try {
				manager.getProgressFlow()
					.collect { progressEntities ->
						val updateState = state.value.map { value ->
							progressEntities.firstOrNull { it.uniqueId == value.uniqueId }?.let {
								value.copy(progress = it.progress)
							} ?: value
						}
						_state.update { updateState }
					}
			} catch (e: Exception) {
				Log.e(HomeViewModel::class.java.name, e.message.orEmpty())
			}
		}
	}

	fun startDownload(useContentLength: Boolean) {
		viewModelScope.launch(Dispatchers.IO) {
			try {
				val randomUnix = System.currentTimeMillis()
				val request = CkDownloadRequest(
					"Sample Video - $randomUnix",
					"sample-video-$randomUnix",
					null,
					if (useContentLength) SAMPLE_URL else SAMPLE_URL2,
					if (useContentLength) SAMPLE_CONTENT_LENGTH else SAMPLE_CONTENT_LENGTH2,
					null
				)
				manager.sendAddDownload(request, MyDownloadService::class.java)
				refreshData()
			} catch (e: Exception) {
				Log.e(HomeViewModel::class.java.name, e.message.orEmpty())
			}
		}
	}

	fun stopDownload(entity: CkDownloadEntity) {
		viewModelScope.launch(Dispatchers.IO) {
			try {
				manager.sendRemoveDownload(entity.uniqueId, MyDownloadService::class.java)
				refreshData()
			} catch (e: Exception) {
				Log.e(HomeViewModel::class.java.name, e.message.orEmpty())
			}
		}
	}
}