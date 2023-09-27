package github.luthfipun.ckdownloader.view.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.luthfipun.ck_downloader_core.db.entity.CkDownloadEntity
import github.luthfipun.ck_downloader_core.util.CkDownloadState
import github.luthfipun.ckdownloader.R
import kotlin.random.Random

@Preview
@Composable
fun HomeScreen() {
	LazyColumn(
		modifier = Modifier.fillMaxSize(),
		state = rememberLazyListState(),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(8.dp),
		contentPadding = PaddingValues(8.dp)
	) {
		items(count = 10) {
			DownloadItemView(
				entity = CkDownloadEntity(
					it.toLong(),
					"Test $it",
					"",
					"",
					123L,
					Random.nextInt(0, 100),
					CkDownloadState.STATE_DOWNLOADING.name
				)
			)
		}
	}
}

@Composable
fun DownloadItemView(
	entity: CkDownloadEntity
) {
	Card(modifier = Modifier.fillMaxWidth()) {
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
						id = when (CkDownloadState.valueOf(entity.state)) {
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
				if (CkDownloadState.valueOf(entity.state) == CkDownloadState.STATE_DOWNLOADING) {
					LinearProgressIndicator(
						progress = entity.progress.toFloat().div(100f),
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
						text = when (CkDownloadState.valueOf(entity.state)) {
							CkDownloadState.STATE_DOWNLOADING -> "Downloading"
							CkDownloadState.STATE_ERROR -> "Download Failed"
							CkDownloadState.STATE_DONE -> "Completed"
						}
					)
					if (CkDownloadState.valueOf(entity.state) == CkDownloadState.STATE_DOWNLOADING) {
						Text(text = "${entity.progress}%")
					}
				}
			}
		}
	}
}