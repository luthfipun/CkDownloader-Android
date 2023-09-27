package github.luthfipun.ckdownloader.view.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import github.luthfipun.ck_downloader_core.db.entity.CkDownloadEntity
import github.luthfipun.ck_downloader_core.util.CkDownloadState
import github.luthfipun.ckdownloader.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
	entities: List<CkDownloadEntity>,
	onDownload: () -> Unit,
	onDelete: (CkDownloadEntity) -> Unit,
	onPlay: (CkDownloadEntity) -> Unit
) {
	val dialogDelete = remember {
		mutableStateOf(false)
	}

	val selectedDelete = remember {
		mutableStateOf<CkDownloadEntity?>(null)
	}

	Surface {
		Column(modifier = Modifier.fillMaxSize()) {
			TopAppBar(
				modifier = Modifier.fillMaxWidth(),
				title = { Text(text = "Home") },
				actions = {
					IconButton(onClick = onDownload) {
						Icon(
							painter = painterResource(id = R.drawable.baseline_download_24),
							contentDescription = null
						)
					}
				}
			)
			LazyColumn(
				modifier = Modifier
					.fillMaxSize()
					.weight(1f, false),
				state = rememberLazyListState(),
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.spacedBy(8.dp),
				contentPadding = PaddingValues(8.dp)
			) {
				items(items = entities, key = { it.uniqueId }) { value ->
					DownloadItemView(
						entity = value,
						onPlay = onPlay,
						onDelete = { selected ->
							selectedDelete.value = selected
							dialogDelete.value = true
						}
					)
				}
			}
		}

		if (dialogDelete.value) {
			AlertDialog(
				title = { Text(text = "Delete Video!") },
				text = { Text(text = "Are you sure to delete this video?") },
				onDismissRequest = {
					selectedDelete.value = null
					dialogDelete.value = false
				},
				confirmButton = {
					Button(onClick = {
						selectedDelete.value?.let { onDelete(it) }
					}) {
						Text(text = "Yes, Delete")
					}
				},
				dismissButton = {
					Button(onClick = {
						selectedDelete.value = null
						dialogDelete.value = false
					}) {
						Text(text = "Cancel")
					}
				}
			)
		}
	}
}

@Composable
fun DownloadItemView(
	entity: CkDownloadEntity,
	onPlay: (CkDownloadEntity) -> Unit,
	onDelete: (CkDownloadEntity) -> Unit
) {
	val progress = remember(entity.progress) {
		mutableStateOf(entity.progress)
	}

	val state = remember(entity.state) {
		mutableStateOf(entity.state)
	}

	Card(modifier = Modifier
		.fillMaxWidth()
		.clickable {
			when (CkDownloadState.valueOf(state.value)) {
				CkDownloadState.STATE_DOWNLOADING, CkDownloadState.STATE_ERROR -> onDelete(
					entity
				)

				CkDownloadState.STATE_DONE -> onPlay(entity)
			}
		}
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(8.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp)
		) {
			FilledIconButton(onClick = { }) {
				Icon(
					painter = painterResource(
						id = when (CkDownloadState.valueOf(state.value)) {
							CkDownloadState.STATE_DOWNLOADING -> R.drawable.baseline_download_24
							CkDownloadState.STATE_ERROR -> R.drawable.baseline_file_download_off_24
							CkDownloadState.STATE_DONE -> R.drawable.baseline_file_download_done_24
						}
					),
					contentDescription = null
				)
			}
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.weight(1f, false),
				verticalArrangement = Arrangement.spacedBy(4.dp)
			) {
				Text(text = entity.uniqueId)
				if (CkDownloadState.valueOf(state.value) == CkDownloadState.STATE_DOWNLOADING) {
					LinearProgressIndicator(
						progress = progress.value.toFloat().div(100f),
						modifier = Modifier.fillMaxWidth(),
						trackColor = ProgressIndicatorDefaults.linearColor.copy(0.3f)
					)
				}
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween
				) {
					Text(
						text = when (CkDownloadState.valueOf(state.value)) {
							CkDownloadState.STATE_DOWNLOADING -> "Downloading"
							CkDownloadState.STATE_ERROR -> "Download Failed"
							CkDownloadState.STATE_DONE -> "Completed"
						}
					)
					if (CkDownloadState.valueOf(state.value) == CkDownloadState.STATE_DOWNLOADING) {
						Text(text = "${progress.value}%")
					}
				}
			}
		}
	}
}