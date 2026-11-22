package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.doOnAttach
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import kotlinx.coroutines.delay

private const val CAMERA_PREVIEW_TAG = "CameraPreview"
private const val STREAM_START_TIMEOUT_MS = 3500L

/**
 * Reusable CameraX live preview composable with built-in runtime `CAMERA` permission handling,
 * lifecycle resume re-checks, PERFORMANCE -> COMPATIBLE fallback, front/rear lens switching,
 * stream-state & CameraState monitoring, and accessible fallback UI.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    cameraEnabled: Boolean = true,
    useFrontCamera: Boolean = true,
    autoRequestPermission: Boolean = true,
    scaleType: PreviewView.ScaleType = PreviewView.ScaleType.FILL_CENTER,
    onPermissionStateChanged: ((Boolean) -> Unit)? = null,
    onCameraError: ((String?) -> Unit)? = null,
    onLensFallback: ((Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { context.findActivity() }

    var hasCameraPermission by remember {
        mutableStateOf(context.hasCameraPermission())
    }
    var hasPromptedOnce by rememberSaveable { mutableStateOf(false) }
    var canShowRationale by remember { mutableStateOf(true) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var bindAttempt by remember { mutableIntStateOf(0) }

    // Track active lens and implementation mode with automatic fallback if stream stays IDLE
    var lastRequestedLens by remember { mutableStateOf(useFrontCamera) }
    var effectiveUseFront by remember { mutableStateOf(useFrontCamera) }
    var implementationMode by remember(bindAttempt) {
        mutableStateOf(PreviewView.ImplementationMode.PERFORMANCE)
    }
    var streamState by remember(bindAttempt, implementationMode, effectiveUseFront) {
        mutableStateOf(PreviewView.StreamState.IDLE)
    }
    var triedAlternateMode by remember(bindAttempt) { mutableStateOf(false) }
    var triedAlternateLens by remember(bindAttempt) { mutableStateOf(false) }

    LaunchedEffect(useFrontCamera) {
        if (useFrontCamera != lastRequestedLens) {
            lastRequestedLens = useFrontCamera
            effectiveUseFront = useFrontCamera
            cameraError = null
            triedAlternateMode = false
            triedAlternateLens = false
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        hasPromptedOnce = true
        canShowRationale = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
        } ?: true
        if (granted) {
            cameraError = null
        }
        onPermissionStateChanged?.invoke(granted)
    }

    // Re-check permission whenever the host returns to foreground (e.g., from App Settings)
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = context.hasCameraPermission()
                if (granted != hasCameraPermission) {
                    hasCameraPermission = granted
                    if (granted) {
                        cameraError = null
                    }
                    onPermissionStateChanged?.invoke(granted)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(hasCameraPermission) {
        onPermissionStateChanged?.invoke(hasCameraPermission)
    }

    LaunchedEffect(autoRequestPermission) {
        if (autoRequestPermission && !hasCameraPermission && !hasPromptedOnce) {
            hasPromptedOnce = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(cameraError) {
        onCameraError?.invoke(cameraError)
    }

    // Watchdog: if camera stays IDLE (blank colour) without streaming frames, automatically fall back
    // from SurfaceView (PERFORMANCE) -> TextureView (COMPATIBLE), then alternate lens, then show error.
    val stalledMessage = stringResource(R.string.camera_stream_stalled)
    LaunchedEffect(
        hasCameraPermission,
        cameraEnabled,
        cameraError,
        streamState,
        implementationMode,
        effectiveUseFront,
        bindAttempt,
    ) {
        if (!hasCameraPermission || !cameraEnabled || cameraError != null) return@LaunchedEffect
        if (streamState == PreviewView.StreamState.STREAMING) return@LaunchedEffect

        delay(STREAM_START_TIMEOUT_MS)
        if (streamState == PreviewView.StreamState.IDLE) {
            when {
                !triedAlternateMode -> {
                    Log.w(
                        CAMERA_PREVIEW_TAG,
                        "Stream stayed IDLE in $implementationMode mode; switching to COMPATIBLE",
                    )
                    triedAlternateMode = true
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
                !triedAlternateLens -> {
                    val nextFront = !effectiveUseFront
                    Log.w(
                        CAMERA_PREVIEW_TAG,
                        "Stream stayed IDLE on front=$effectiveUseFront; falling back to front=$nextFront",
                    )
                    triedAlternateLens = true
                    triedAlternateMode = false
                    lastRequestedLens = nextFront
                    effectiveUseFront = nextFront
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    onLensFallback?.invoke(nextFront)
                }
                else -> {
                    cameraError = stalledMessage
                }
            }
        }
    }

    Box(modifier = modifier) {
        when {
            !hasCameraPermission -> {
                val permanentlyDenied = hasPromptedOnce && !canShowRationale
                CameraPlaceholderState(
                    icon = Icons.Rounded.VideocamOff,
                    title = stringResource(R.string.camera_permission_title),
                    message = if (permanentlyDenied || hasPromptedOnce) {
                        stringResource(R.string.camera_permission_denied_rationale)
                    } else {
                        stringResource(R.string.camera_permission_rationale)
                    },
                    actionLabel = if (permanentlyDenied) {
                        stringResource(R.string.camera_permission_settings)
                    } else {
                        stringResource(R.string.camera_permission_grant)
                    },
                    isSettingsAction = permanentlyDenied,
                    actionTestTag = if (permanentlyDenied) {
                        "open_camera_settings_button"
                    } else {
                        "grant_camera_permission_button"
                    },
                    onAction = {
                        if (permanentlyDenied) {
                            val settingsIntent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(settingsIntent)
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    secondaryActionLabel = if (permanentlyDenied) {
                        stringResource(R.string.camera_permission_grant)
                    } else {
                        null
                    },
                    onSecondaryAction = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                )
            }

            !cameraEnabled -> {
                CameraPlaceholderState(
                    icon = Icons.Rounded.VideocamOff,
                    title = stringResource(R.string.camera_paused_title),
                    message = stringResource(R.string.camera_paused_body),
                )
            }

            cameraError != null -> {
                CameraPlaceholderState(
                    icon = Icons.Rounded.VideocamOff,
                    title = stringResource(R.string.camera_unavailable_title),
                    message = cameraError.orEmpty(),
                    actionLabel = stringResource(R.string.camera_retry),
                    actionTestTag = "retry_camera_button",
                    onAction = {
                        cameraError = null
                        triedAlternateMode = false
                        triedAlternateLens = false
                        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                        bindAttempt++
                    },
                    secondaryActionLabel = stringResource(R.string.camera_switch_lens),
                    secondaryIcon = Icons.Rounded.Cameraswitch,
                    onSecondaryAction = {
                        val nextFront = !effectiveUseFront
                        lastRequestedLens = nextFront
                        effectiveUseFront = nextFront
                        onLensFallback?.invoke(nextFront)
                        cameraError = null
                        triedAlternateMode = false
                        triedAlternateLens = false
                        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                        bindAttempt++
                    },
                )
            }

            else -> {
                androidx.compose.runtime.key(implementationMode) {
                    CameraXLiveFeed(
                        useFrontCamera = effectiveUseFront,
                        scaleType = scaleType,
                        implementationMode = implementationMode,
                        bindAttempt = bindAttempt,
                        onStreamStateChanged = { state -> streamState = state },
                        onCameraError = { err ->
                            if (!triedAlternateLens) {
                                val nextFront = !effectiveUseFront
                                Log.w(
                                    CAMERA_PREVIEW_TAG,
                                    "Camera error ($err) on front=$effectiveUseFront; trying front=$nextFront",
                                )
                                triedAlternateLens = true
                                triedAlternateMode = false
                                lastRequestedLens = nextFront
                                effectiveUseFront = nextFront
                                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                                onLensFallback?.invoke(nextFront)
                            } else {
                                cameraError = err
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("camera_preview_feed"),
                    )
                }

                if (streamState != PreviewView.StreamState.STREAMING) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.58f),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.align(Alignment.Center),
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        ) {
                            androidx.compose.foundation.layout.Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White,
                                )
                                Text(
                                    text = stringResource(R.string.camera_starting),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraXLiveFeed(
    useFrontCamera: Boolean,
    scaleType: PreviewView.ScaleType,
    implementationMode: PreviewView.ImplementationMode,
    bindAttempt: Int,
    onStreamStateChanged: (PreviewView.StreamState) -> Unit,
    onCameraError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Key remember on implementationMode so SurfaceView vs TextureView is cleanly recreated on fallback
    val previewView = remember(implementationMode) {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            this.implementationMode = implementationMode
            this.scaleType = scaleType
        }
    }

    LaunchedEffect(previewView, scaleType) {
        previewView.scaleType = scaleType
    }

    DisposableEffect(lifecycleOwner, previewView, useFrontCamera, bindAttempt) {
        val streamObserver = Observer<PreviewView.StreamState> { state ->
            if (state != null) {
                onStreamStateChanged(state)
            }
        }
        previewView.previewStreamState.observeForever(streamObserver)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        var providerRef: ProcessCameraProvider? = null
        var observedCameraState: androidx.lifecycle.LiveData<CameraState>? = null
        var cameraStateObserver: Observer<CameraState>? = null
        var isDisposed = false

        fun bindWhenLaidOut() {
            if (isDisposed) return
            try {
                val provider = providerRef ?: return

                val preferred = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                val fallback = if (useFrontCamera) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }

                val selector = when {
                    provider.hasCamera(preferred) -> preferred
                    provider.hasCamera(fallback) -> fallback
                    else -> {
                        onCameraError("No camera sensor found on this device.")
                        return
                    }
                }

                val preview = Preview.Builder().build().apply {
                    surfaceProvider = previewView.surfaceProvider
                }

                provider.unbindAll()

                val viewPort = previewView.viewPort
                val camera = if (viewPort != null) {
                    val useCaseGroup = UseCaseGroup.Builder()
                        .setViewPort(viewPort)
                        .addUseCase(preview)
                        .build()
                    provider.bindToLifecycle(lifecycleOwner, selector, useCaseGroup)
                } else {
                    provider.bindToLifecycle(lifecycleOwner, selector, preview)
                }

                val stateLiveData = camera.cameraInfo.cameraState
                val stateObserver = Observer<CameraState> { state ->
                    if (isDisposed || state == null) return@Observer
                    val err = state.error
                    if (err != null) {
                        val message = when (err.code) {
                            CameraState.ERROR_CAMERA_IN_USE,
                            CameraState.ERROR_MAX_CAMERAS_IN_USE ->
                                "Camera is currently in use by another app."
                            CameraState.ERROR_CAMERA_DISABLED ->
                                "Camera is disabled by device policy."
                            CameraState.ERROR_STREAM_CONFIG ->
                                "Camera stream configuration failed for this lens."
                            CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED ->
                                "Turn off Do Not Disturb mode to open the camera."
                            else ->
                                "Camera device could not start (code ${err.code})."
                        }
                        Log.e(CAMERA_PREVIEW_TAG, "CameraState error ${err.code}", err.cause)
                        onCameraError(message)
                    }
                }
                observedCameraState = stateLiveData
                cameraStateObserver = stateObserver
                stateLiveData.observeForever(stateObserver)
            } catch (e: Exception) {
                Log.e(CAMERA_PREVIEW_TAG, "Failed to bind CameraX preview", e)
                onCameraError(e.localizedMessage ?: "Unable to start camera preview.")
            }
        }

        cameraProviderFuture.addListener(
            {
                if (isDisposed) return@addListener
                try {
                    providerRef = cameraProviderFuture.get()
                    // Wait until PreviewView is attached and laid out with valid Display & non-zero dimensions
                    // so SurfaceProvider & ViewPort initialize reliably inside Compose AndroidView.
                    previewView.doOnAttach { attachedView ->
                        attachedView.doOnLayout {
                            bindWhenLaidOut()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(CAMERA_PREVIEW_TAG, "Failed to get ProcessCameraProvider", e)
                    onCameraError(e.localizedMessage ?: "Unable to initialize camera.")
                }
            },
            executor,
        )

        onDispose {
            isDisposed = true
            previewView.previewStreamState.removeObserver(streamObserver)
            val stateLive = observedCameraState
            val stateObs = cameraStateObserver
            if (stateLive != null && stateObs != null) {
                stateLive.removeObserver(stateObs)
            }
            try {
                providerRef?.unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}

@Composable
private fun CameraPlaceholderState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    isSettingsAction: Boolean = false,
    actionTestTag: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    secondaryIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.background,
                    )
                )
            )
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                shape = CircleShape,
                modifier = Modifier.size(80.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .height(50.dp)
                        .let { mod ->
                            if (actionTestTag != null) mod.testTag(actionTestTag) else mod
                        },
                ) {
                    Icon(
                        imageVector = if (isSettingsAction) Icons.Rounded.Settings else Icons.Rounded.Videocam,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(actionLabel)
                }
            }
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                if (secondaryIcon != null) {
                    OutlinedButton(
                        onClick = onSecondaryAction,
                        modifier = Modifier.height(48.dp),
                    ) {
                        Icon(
                            imageVector = secondaryIcon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(secondaryActionLabel)
                    }
                } else {
                    TextButton(
                        onClick = onSecondaryAction,
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text(secondaryActionLabel)
                    }
                }
            }
        }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
