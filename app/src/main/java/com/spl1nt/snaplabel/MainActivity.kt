package com.spl1nt.snaplabel

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.spl1nt.snaplabel.editor.Layer
import com.spl1nt.snaplabel.printer.BlePrinterManager
import com.spl1nt.snaplabel.printer.PrinterRepository
import com.spl1nt.snaplabel.ui.CameraScreen
import com.spl1nt.snaplabel.ui.EditorScreen
import com.spl1nt.snaplabel.ui.HistoryScreen
import com.spl1nt.snaplabel.ui.PrintPreviewScreen
import com.spl1nt.snaplabel.ui.PrinterScreen
import com.spl1nt.snaplabel.ui.UpdateAvailableDialog
import com.spl1nt.snaplabel.ui.theme.SnapLabelTheme
import com.spl1nt.snaplabel.update.UpdateChecker
import com.spl1nt.snaplabel.update.UpdateInfo
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    private lateinit var repo: PrinterRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = PrinterRepository(applicationContext)

        setContent {
            SnapLabelTheme {
                val permissions = remember { requiredPermissions() }
                var granted by remember { mutableStateOf(false) }
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                    granted = result.values.all { it }
                }
                LaunchedEffect(Unit) { launcher.launch(permissions) }

                if (granted) {
                    SnapLabelApp(repo)
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Camera and Bluetooth permissions are needed to take and print photos.")
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { launcher.launch(permissions) }) { Text("Grant permissions") }
                        }
                    }
                }
            }
        }
    }

    private fun requiredPermissions(): Array<String> {
        val perms = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_SCAN
            perms += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            perms += Manifest.permission.ACCESS_FINE_LOCATION
        }
        return perms.toTypedArray()
    }
}

private object Routes {
    const val CAMERA = "camera"
    const val EDITOR = "editor"
    const val PRINT_PREVIEW = "print"
    const val PRINTER = "printer"
    const val HISTORY = "history"
}

@Composable
private fun SnapLabelApp(repo: PrinterRepository) {
    val navController = rememberNavController()
    val context = LocalContext.current
    var capturedPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var initialLayers by remember { mutableStateOf<List<Layer>>(emptyList()) }
    var flattenedForPrint by remember { mutableStateOf<Bitmap?>(null) }
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }

    // Silent check on launch — only surfaces anything if a genuinely newer
    // release is tagged "v<versionCode>" on GitHub; never nags if there's
    // nothing new or the request fails (offline, API hiccup, etc).
    LaunchedEffect(Unit) {
        val currentCode = context.packageManager.getPackageInfo(context.packageName, 0).let {
            @Suppress("DEPRECATION") it.versionCode
        }
        UpdateChecker.checkForUpdate(currentCode).getOrNull()?.let { availableUpdate = it }
    }

    availableUpdate?.let { info ->
        UpdateAvailableDialog(info = info, onDismiss = { availableUpdate = null })
    }

    // Auto-connect to whichever printer was used last, so the app is ready to
    // print without a trip through the printer screen every time it's opened.
    LaunchedEffect(Unit) {
        val savedAddress = repo.rememberedAddress.first()
        if (savedAddress != null && repo.ble.state.value == BlePrinterManager.State.Disconnected) {
            repo.connectToRemembered(savedAddress)
        }
    }

    NavHost(navController = navController, startDestination = Routes.CAMERA) {
        composable(Routes.CAMERA) {
            Box(Modifier.fillMaxSize()) {
                CameraScreen(
                    onPhotoReady = { bmp ->
                        capturedPhoto = bmp
                        initialLayers = emptyList()
                        navController.navigate(Routes.EDITOR)
                    },
                    onTemplateReady = { bmp, layer ->
                        capturedPhoto = bmp
                        initialLayers = listOf(layer)
                        navController.navigate(Routes.EDITOR)
                    },
                )
                val connState by repo.ble.state.collectAsState()
                IconButton(
                    onClick = { navController.navigate(Routes.PRINTER) },
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                ) {
                    Icon(
                        Icons.Default.Print,
                        contentDescription = "Printer settings",
                        tint = if (connState is BlePrinterManager.State.Connected) {
                            Color(0xFF4CAF50)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                IconButton(
                    onClick = { navController.navigate(Routes.HISTORY) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                ) { Icon(Icons.Default.History, contentDescription = "Print history") }
            }
        }
        composable(Routes.EDITOR) {
            capturedPhoto?.let { photo ->
                EditorScreen(
                    photo = photo,
                    initialLayers = initialLayers,
                    onPrint = { flattened ->
                        flattenedForPrint = flattened
                        navController.navigate(Routes.PRINT_PREVIEW)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(Routes.PRINT_PREVIEW) {
            flattenedForPrint?.let { flat ->
                PrintPreviewScreen(
                    photo = flat,
                    repo = repo,
                    onDone = { navController.popBackStack(Routes.CAMERA, inclusive = false) },
                    onConnectPrinter = { navController.navigate(Routes.PRINTER) },
                )
            }
        }
        composable(Routes.PRINTER) {
            PrinterScreen(repo = repo, onDone = { navController.popBackStack() })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(repo = repo, onBack = { navController.popBackStack() })
        }
    }
}
