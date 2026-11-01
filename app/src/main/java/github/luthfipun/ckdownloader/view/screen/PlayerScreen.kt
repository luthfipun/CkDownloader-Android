package github.luthfipun.ckdownloader.view.screen

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
	filePath: String,
	onBack: () -> Unit
) {
	val context = LocalContext.current
	val lifecycleOwner = LocalLifecycleOwner.current

	// Surfaces playback errors, e.g. a corrupted download that fails to parse
	val errorMessage = remember { mutableStateOf<String?>(null) }

	val player = remember(filePath) {
		ExoPlayer.Builder(context).build().apply {
			setMediaItem(MediaItem.fromUri(Uri.fromFile(File(filePath))))
			prepare()
			playWhenReady = true
		}
	}

	DisposableEffect(player, lifecycleOwner) {
		val listener = object : Player.Listener {
			override fun onPlayerError(error: PlaybackException) {
				errorMessage.value = error.cause?.message ?: error.message
			}
		}
		val observer = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_STOP) player.pause()
		}
		player.addListener(listener)
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose {
			lifecycleOwner.lifecycle.removeObserver(observer)
			player.removeListener(listener)
			player.release()
		}
	}

	Surface {
		Column(modifier = Modifier.fillMaxSize()) {
			TopAppBar(
				modifier = Modifier.fillMaxWidth(),
				title = { Text(text = File(filePath).name) },
				navigationIcon = {
					IconButton(onClick = onBack) {
						Icon(
							imageVector = Icons.AutoMirrored.Filled.ArrowBack,
							contentDescription = "Back"
						)
					}
				}
			)
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(Color.Black),
				contentAlignment = Alignment.Center
			) {
				AndroidView(
					modifier = Modifier.fillMaxSize(),
					factory = { PlayerView(it).apply { this.player = player } }
				)
				errorMessage.value?.let {
					Text(
						modifier = Modifier.padding(16.dp),
						text = "Failed to play video:\n$it",
						color = MaterialTheme.colorScheme.error,
						textAlign = TextAlign.Center
					)
				}
			}
		}
	}
}
