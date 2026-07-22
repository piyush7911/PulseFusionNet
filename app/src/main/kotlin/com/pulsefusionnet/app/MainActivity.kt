package com.pulsefusionnet.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.pulsefusionnet.app.camera.CameraController
import com.pulsefusionnet.app.camera.getCameraProvider
import com.pulsefusionnet.app.ui.DetectingScreen
import com.pulsefusionnet.app.ui.FailedScreen
import com.pulsefusionnet.app.ui.HomeScreen
import com.pulsefusionnet.app.ui.LoadingScreen
import com.pulsefusionnet.app.ui.MeasuringScreen
import com.pulsefusionnet.app.ui.PermissionScreen
import com.pulsefusionnet.app.ui.PulseColors
import com.pulsefusionnet.app.ui.PulseFusionTheme
import com.pulsefusionnet.app.ui.ResultScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MeasurementViewModel by viewModels()
    private var permissionDenied by mutableStateOf(false)

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                viewModel.onPermissionGranted()
            } else {
                permissionDenied = true
                viewModel.onPermissionDenied()
            }
        }

        setContent {
            PulseFusionTheme {
                val lifecycleOwner = LocalLifecycleOwner.current

                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(900)
                    viewModel.finishLoading(hasCameraPermission())
                }

                val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
                val cameraController = remember {
                    CameraController(onFrame = { stats -> mainHandler.post { viewModel.onCameraFrame(stats) } })
                }

                // Bind/unbind the camera only while an active scan is running — saves battery
                // and avoids holding the sensor open on the home screen.
                DisposableEffect(viewModel.journey) {
                    val active = viewModel.journey == Journey.DETECTING || viewModel.journey == Journey.MEASURING
                    if (active && hasCameraPermission()) {
                        getCameraProvider(this@MainActivity) { provider ->
                            cameraController.start(lifecycleOwner, provider)
                        }
                    } else {
                        cameraController.stop()
                    }
                    onDispose {}
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PulseColors.Background)
                ) {
                    AnimatedContent(
                        targetState = viewModel.journey,
                        transitionSpec = {
                            (fadeIn(tween(280)) + slideInHorizontally(tween(280)) { it / 6 }) togetherWith
                                (fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { -it / 6 })
                        },
                        label = "journey"
                    ) { journey ->
                        when (journey) {
                            Journey.LOADING -> LoadingScreen()

                            Journey.PERMISSION -> PermissionScreen(
                                wasDenied = permissionDenied,
                                onRequestPermission = {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                },
                                onOpenSettings = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", packageName, null)
                                    }
                                    startActivity(intent)
                                }
                            )

                            Journey.HOME -> HomeScreen(onStart = { viewModel.startScan() })

                            Journey.DETECTING -> DetectingScreen(
                                fingerOnLens = viewModel.fingerOnLens,
                                stabilizationPct = viewModel.stabilizationPct,
                                waveformSamples = viewModel.waveformSamples,
                                cameraController = cameraController,
                                onCancel = { viewModel.cancelAndReturnHome() }
                            )

                            Journey.MEASURING -> MeasuringScreen(
                                secondsRemaining = viewModel.secondsRemaining,
                                liveBpmText = viewModel.liveBpmText,
                                signalQualityPct = viewModel.signalQualityPct,
                                fingerPresent = viewModel.fingerPresent,
                                movementWarning = viewModel.movementWarning,
                                isPaused = viewModel.isPaused,
                                waveformSamples = viewModel.waveformSamples,
                                cameraController = cameraController
                            )

                            Journey.FAILED -> FailedScreen(
                                reason = viewModel.failedReason,
                                onRetry = { viewModel.startScan() },
                                onCancel = { viewModel.cancelAndReturnHome() }
                            )

                            Journey.RESULT -> viewModel.result?.let { result ->
                                ResultScreen(
                                    result = result,
                                    onMeasureAgain = { viewModel.startScan() },
                                    onDone = { viewModel.cancelAndReturnHome() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
