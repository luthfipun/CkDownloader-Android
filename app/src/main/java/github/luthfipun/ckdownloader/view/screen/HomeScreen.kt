package github.luthfipun.ckdownloader.view.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.draw.clip
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import github.luthfipun.ck_downloader_core.db.entity.CkDownloadEntity
import github.luthfipun.ck_downloader_core.util.CkDownloadState
import github.luthfipun.ckdownloader.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
	entities: List<CkDownloadEntity>,
	onDownload: (Boolean) -> Unit,
	onDelete: (CkDownloadEntity) -> Unit,
	onPlay: (CkDownloadEntity) -> Unit
) {
	val dialogDelete = remember {
		mutableStateOf(false)
	}

	val dialogChooseFormat = remember {
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
					IconButton(onClick = {
						dialogChooseFormat.value = true
					}) {
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
					SwipeToDeleteItem(
						onDelete = {
							selectedDelete.value = value
							dialogDelete.value = true
						}
					) {
						DownloadItemView(
							entity = value,
							onPlay = onPlay
						)
					}
				}
			}
		}

		if (dialogChooseFormat.value) {
			AlertDialog(
				title = {
					Text(text = "Video Format")
				},
				text = {
					Text(text = "Choose video format, with content length or without")
				},
				onDismissRequest = {
					dialogChooseFormat.value = false
				},
				confirmButton = {
					Button(onClick = {
						onDownload(true)
						dialogChooseFormat.value = false
					}) {
						Text(text = "Video with Content Length")
					}
				},
				dismissButton = {
					Button(onClick = {
						onDownload(false)
						dialogChooseFormat.value = false
					}) {
						Text(text = "Video without Content Length")
					}
				}
			)
		}

		if (dialogDelete.value) {
			AlertDialog(
				title = { Text(text = "Delete Video!") },
				text = {
					val selected = selectedDelete.value
					Text(
						text = if (selected?.state == CkDownloadState.STATE_DOWNLOADING.name) {
							"This download is still running. Stop and delete it?"
						} else {
							"Are you sure to delete this video?"
						}
					)
				},
				onDismissRequest = {
					selectedDelete.value = null
					dialogDelete.value = false
				},
				confirmButton = {
					Button(onClick = {
						selectedDelete.value?.let { onDelete(it) }
						selectedDelete.value = null
						dialogDelete.value = false
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
	onPlay: (CkDownloadEntity) -> Unit
) {
	val progress = remember(entity.progress) {
        mutableIntStateOf(entity.progress)
	}

	val state = remember(entity.state) {
		mutableStateOf(entity.state)
	}

	Card(modifier = Modifier
		.fillMaxWidth()
		.clickable(enabled = state.value == CkDownloadState.STATE_DONE.name) {
			onPlay(entity)
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
					if (entity.contentLength > 0 || progress.intValue > 0) {
						LinearProgressIndicator(
							progress = { progress.intValue.toFloat().div(100f) },
							modifier = Modifier.fillMaxWidth(),
							color = MaterialTheme.colorScheme.primary,
							trackColor = ProgressIndicatorDefaults.linearColor.copy(0.3f),
							strokeCap = StrokeCap.Round,
						)
					}else {
						LinearProgressIndicator()
					}
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
						Text(text = "${progress.intValue}%")
					}
				}
			}
		}
	}
}
/** Swiping from end to start asks for deletion, the item snaps back so the dialog can be cancelled. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteItem(
	onDelete: () -> Unit,
	content: @Composable () -> Unit
) {
	val swipeState = rememberSwipeToDismissBoxState()
	val scope = rememberCoroutineScope()

	SwipeToDismissBox(
		state = swipeState,
		enableDismissFromStartToEnd = false,
		onDismiss = {
			onDelete()
			scope.launch { swipeState.reset() }
		},
		backgroundContent = {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.clip(CardDefaults.shape)
					.background(MaterialTheme.colorScheme.errorContainer)
					.padding(horizontal = 16.dp),
				contentAlignment = Alignment.CenterEnd
			) {
				Icon(
					imageVector = Icons.Filled.Delete,
					contentDescription = "Delete",
					tint = MaterialTheme.colorScheme.onErrorContainer
				)
			}
		}
	) {
		content()
	}
}
