package app.p2scam

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.SurfaceHolder
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.alexvas.rtsp.widget.RtspStatusListener
import com.alexvas.rtsp.widget.RtspSurfaceView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            P2sCamTheme {
                P2sCamApp()
            }
        }
    }
}

private data class PrinterConfig(
    val host: String,
    val accessCode: String,
)

private sealed interface CameraStatus {
    data object Connecting : CameraStatus
    data object Connected : CameraStatus
    data object Playing : CameraStatus
    data object Disconnected : CameraStatus
    data object Unauthorized : CameraStatus
    data class Failed(val message: String?) : CameraStatus
}

@Composable
private fun P2sCamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content,
    )
}

@Composable
private fun P2sCamApp() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("p2s_cam", Context.MODE_PRIVATE)
    }

    var config by remember {
        mutableStateOf(
            prefs.getString("host", null)?.let { host ->
                prefs.getString("access_code", null)?.let { code ->
                    PrinterConfig(host, code)
                }
            },
        )
    }
    var editing by remember { mutableStateOf(config == null) }
    var reconnectKey by remember { mutableIntStateOf(0) }

    BackHandler(enabled = editing && config != null) {
        editing = false
    }

    if (editing || config == null) {
        SetupScreen(
            currentConfig = config,
            onConnect = { host, accessCode ->
                prefs.edit()
                    .putString("host", host)
                    .putString("access_code", accessCode)
                    .apply()
                config = PrinterConfig(host, accessCode)
                editing = false
                reconnectKey++
            },
            onCancel = if (config != null) ({ editing = false }) else null,
            onForget = if (config != null) ({
                prefs.edit().clear().apply()
                config = null
                editing = true
            }) else null,
        )
    } else {
        ViewerScreen(
            config = config!!,
            reconnectKey = reconnectKey,
            onEdit = { editing = true },
            onRetry = { reconnectKey++ },
        )
    }
}

@Composable
private fun SetupScreen(
    currentConfig: PrinterConfig?,
    onConnect: (host: String, accessCode: String) -> Unit,
    onCancel: (() -> Unit)?,
    onForget: (() -> Unit)?,
) {
    var hostInput by rememberSaveable(currentConfig?.host) {
        mutableStateOf(currentConfig?.host.orEmpty())
    }
    var codeInput by rememberSaveable(currentConfig?.accessCode) {
        mutableStateOf(currentConfig?.accessCode.orEmpty())
    }
    var showCode by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun connect() {
        val host = P2sEndpoint.normalizeHost(hostInput)
        when {
            host == null -> error = "Enter the printer IP or hostname (no username/password needed)."
            codeInput.trim().isEmpty() -> error = "Enter the printer's LAN access code."
            else -> {
                error = null
                onConnect(host, codeInput.trim())
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("P2S Cam", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Direct LAN camera viewer. No Bambu account, cloud service, ads, or analytics.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                OutlinedTextField(
                    value = hostInput,
                    onValueChange = {
                        hostInput = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Printer IP or hostname") },
                    placeholder = { Text("192.168.1.50") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                )

                OutlinedTextField(
                    value = codeInput,
                    onValueChange = {
                        codeInput = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("LAN access code") },
                    visualTransformation = if (showCode) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(onClick = { showCode = !showCode }) {
                            Text(if (showCode) "Hide" else "Show")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { connect() }),
                )

                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Text(
                    "Connects to rtsps://<printer>:322/streaming/live/1 as bblp. " +
                        "The IP and access code are stored only in this app's private local storage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Button(
                    onClick = { connect() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Connect")
                }

                if (onCancel != null || onForget != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        if (onForget != null) {
                            TextButton(onClick = onForget) { Text("Forget printer") }
                        } else {
                            Spacer(Modifier)
                        }
                        if (onCancel != null) {
                            TextButton(onClick = onCancel) { Text("Cancel") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerScreen(
    config: PrinterConfig,
    reconnectKey: Int,
    onEdit: () -> Unit,
    onRetry: () -> Unit,
) {
    var status by remember(config, reconnectKey) {
        mutableStateOf<CameraStatus>(CameraStatus.Connecting)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        P2sCameraSurface(
            config = config,
            reconnectKey = reconnectKey,
            onStatus = { status = it },
        )

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            color = Color.Black.copy(alpha = 0.72f),
        ) {
            Row(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("P2S Cam", style = MaterialTheme.typography.titleMedium)
                    Text(
                        cameraStatusText(status),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (status is CameraStatus.Failed || status is CameraStatus.Unauthorized) {
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
                TextButton(onClick = onEdit) { Text("Edit") }
            }
        }

        when (val currentStatus = status) {
            CameraStatus.Unauthorized -> ErrorCard(
                title = "Access denied",
                message = "Check the LAN access code on the printer.",
                onRetry = onRetry,
                onEdit = onEdit,
            )

            is CameraStatus.Failed -> ErrorCard(
                title = "Couldn't connect",
                message = currentStatus.message?.takeIf { it.isNotBlank() }
                    ?: "Check that the phone is on the same LAN and local liveview is enabled.",
                onRetry = onRetry,
                onEdit = onEdit,
            )

            else -> Unit
        }
    }
}

@Composable
private fun P2sCameraSurface(
    config: PrinterConfig,
    reconnectKey: Int,
    onStatus: (CameraStatus) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnStatus by rememberUpdatedState(onStatus)

    val rtspView = remember(config.host, config.accessCode, reconnectKey) {
        RtspSurfaceView(context).apply {
            keepScreenOn = true
            videoFrameRateStabilization = true
        }
    }

    AndroidView(
        factory = { rtspView },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
    )

    DisposableEffect(rtspView, lifecycleOwner) {
        var surfaceReady = rtspView.holder.surface?.isValid == true

        fun publish(value: CameraStatus) {
            rtspView.post { latestOnStatus(value) }
        }

        fun startIfReady() {
            if (
                surfaceReady &&
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                !rtspView.isStarted()
            ) {
                publish(CameraStatus.Connecting)
                rtspView.start(
                    requestVideo = true,
                    requestAudio = false,
                    requestApplication = false,
                )
            }
        }

        fun stop() {
            if (rtspView.isStarted()) {
                rtspView.stop()
            }
        }

        val statusListener = object : RtspStatusListener {
            override fun onRtspStatusConnecting() = publish(CameraStatus.Connecting)
            override fun onRtspStatusConnected() = publish(CameraStatus.Connected)
            override fun onRtspFirstFrameRendered() = publish(CameraStatus.Playing)
            override fun onRtspStatusDisconnected() = publish(CameraStatus.Disconnected)
            override fun onRtspStatusFailedUnauthorized() = publish(CameraStatus.Unauthorized)
            override fun onRtspStatusFailed(message: String?) = publish(CameraStatus.Failed(message))
        }

        val surfaceCallback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                surfaceReady = true
                startIfReady()
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int,
            ) = Unit

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                surfaceReady = false
            }
        }

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> startIfReady()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }

        rtspView.setStatusListener(statusListener)
        rtspView.init(
            uri = Uri.parse(P2sEndpoint.streamUrl(config.host)),
            username = P2sEndpoint.USERNAME,
            password = config.accessCode,
            userAgent = "P2S Cam/1.0",
            socketTimeout = 8_000,
        )
        rtspView.holder.addCallback(surfaceCallback)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        // If the Surface already existed before the callback was registered.
        rtspView.post {
            surfaceReady = rtspView.holder.surface?.isValid == true
            startIfReady()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            rtspView.holder.removeCallback(surfaceCallback)
            rtspView.setStatusListener(null)
            stop()
            rtspView.keepScreenOn = false
        }
    }
}

@Composable
private fun ErrorCard(
    title: String,
    message: String,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRetry) { Text("Retry") }
                TextButton(onClick = onEdit) { Text("Edit connection") }
            }
        }
    }
}

private fun cameraStatusText(status: CameraStatus): String = when (status) {
    CameraStatus.Connecting -> "Connecting…"
    CameraStatus.Connected -> "Connected · waiting for video…"
    CameraStatus.Playing -> "Live · LAN only"
    CameraStatus.Disconnected -> "Disconnected"
    CameraStatus.Unauthorized -> "Wrong access code"
    is CameraStatus.Failed -> "Connection failed"
}
