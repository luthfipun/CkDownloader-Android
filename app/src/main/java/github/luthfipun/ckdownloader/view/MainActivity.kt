package github.luthfipun.ckdownloader.view

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import github.luthfipun.ckdownloader.ui.theme.CkDownloaderTheme
import github.luthfipun.ckdownloader.view.screen.HomeScreen
import github.luthfipun.ckdownloader.view.screen.HomeViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

	private val notificationPermission = registerForActivityResult(
		ActivityResultContracts.RequestPermission()
	) { isGranted: Boolean ->
		if (isGranted) {
			Toast.makeText(
				this,
				"Request notification permission granted",
				Toast.LENGTH_SHORT
			).show()
		}else {
			Toast.makeText(
				this,
				"Please allow permission to get download notification",
				Toast.LENGTH_SHORT
			).show()
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			CkDownloaderTheme {
				val navController = rememberNavController()
				val context = LocalContext.current

				if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
						notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
					}
				}
				// A surface container using the 'background' color from the theme
				Surface(
					modifier = Modifier.fillMaxSize(),
					color = MaterialTheme.colorScheme.background
				) {
					NavHost(navController = navController, startDestination = "home") {
						composable(route = "home") {
							val homeViewModel = hiltViewModel<HomeViewModel>()
							val state = homeViewModel.state.collectAsState()
							HomeScreen(
								entities = state.value,
								onDownload = homeViewModel::startDownload,
								onDelete = homeViewModel::stopDownload,
								onPlay = {
									navController.navigate("player")
								}
							)
						}

						composable(route = "player") {

						}
					}
				}
			}
		}
	}
}