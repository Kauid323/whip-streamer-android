package com.example.whiper

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.example.whiper.ui.theme.FfmpegToolTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FfmpegToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MainScreen() {
    val context = LocalContext.current
    
    var streamUrl by remember { mutableStateOf("https://live-push.jwzhd.com/whip/") }
    var streamKey by remember { mutableStateOf("") }
    var audioSource by remember { mutableStateOf("mic") } // "mic" or "system" or "mix" or "none"

    val videoCodecOptions = listOf("H264", "VP8", "VP9")
    var selectedVideoCodec by remember { mutableStateOf("H264") }
    val encoderModeOptions = listOf("Auto", "Hardware", "Software")
    var selectedEncoderMode by remember { mutableStateOf("Auto") }

    var widthText by remember { mutableStateOf("1280") }
    var heightText by remember { mutableStateOf("720") }
    var fpsText by remember { mutableStateOf("30") }
    var videoBitrateKbpsText by remember { mutableStateOf("2500") }
    var audioBitrateKbpsText by remember { mutableStateOf("64") }
    // var isStreaming by remember { mutableStateOf(false) } // Could add state listening later

    val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val width = widthText.toIntOrNull() ?: 1280
                val height = heightText.toIntOrNull() ?: 720
                val fps = fpsText.toIntOrNull() ?: 30
                val videoBitrateKbps = videoBitrateKbpsText.toIntOrNull() ?: 2500
                val audioBitrateKbps = audioBitrateKbpsText.toIntOrNull() ?: 64

                // Start Service
                val intent = Intent(context, StreamService::class.java).apply {
                    putExtra("code", result.resultCode)
                    putExtra("data", result.data)
                    putExtra("url", streamUrl.trim())
                    putExtra("token", streamKey.trim())
                    putExtra("audioSource", audioSource)
                    putExtra("videoCodec", selectedVideoCodec)
                    putExtra("videoEncoderMode", selectedEncoderMode)
                    putExtra("videoWidth", width)
                    putExtra("videoHeight", height)
                    putExtra("videoFps", fps)
                    putExtra("videoBitrateKbps", videoBitrateKbps)
                    putExtra("audioBitrateKbps", audioBitrateKbps)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    )

    val launchScreenCaptureIntent = remember {
        {
            // Trigger MediaProjection permission dialog
            screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                launchScreenCaptureIntent()
            } else {
                Toast.makeText(
                    context,
                    "RECORD_AUDIO permission denied; choose No Audio or grant permission.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("WHIP Streamer", style = MaterialTheme.typography.headlineMedium)

        Text(
            text = "Video Settings",
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = widthText,
                onValueChange = { widthText = it },
                label = { Text("Width") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = heightText,
                onValueChange = { heightText = it },
                label = { Text("Height") },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = fpsText,
                onValueChange = { fpsText = it },
                label = { Text("FPS") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = videoBitrateKbpsText,
                onValueChange = { videoBitrateKbpsText = it },
                label = { Text("Video Bitrate (kbps)") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        OutlinedTextField(
            value = audioBitrateKbpsText,
            onValueChange = { audioBitrateKbpsText = it },
            label = { Text("Audio Bitrate (kbps)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Video Codec", style = MaterialTheme.typography.titleMedium)

                var codecExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = codecExpanded, onExpandedChange = { codecExpanded = !codecExpanded }) {
                    OutlinedTextField(
                        value = selectedVideoCodec,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Codec") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = codecExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = codecExpanded, onDismissRequest = { codecExpanded = false }) {
                        videoCodecOptions.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt) },
                                onClick = {
                                    selectedVideoCodec = opt
                                    codecExpanded = false
                                }
                            )
                        }
                    }
                }

                Text("Encoder Mode", style = MaterialTheme.typography.titleMedium)

                var modeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = modeExpanded, onExpandedChange = { modeExpanded = !modeExpanded }) {
                    OutlinedTextField(
                        value = selectedEncoderMode,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Mode") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = modeExpanded, onDismissRequest = { modeExpanded = false }) {
                        encoderModeOptions.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt) },
                                onClick = {
                                    selectedEncoderMode = opt
                                    modeExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = streamUrl,
            onValueChange = { streamUrl = it },
            label = { Text("WHIP Endpoint URL") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = streamKey,
            onValueChange = { streamKey = it },
            label = { Text("Bearer Token / Stream Key") },
            modifier = Modifier.fillMaxWidth()
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Audio Source", style = MaterialTheme.typography.titleMedium)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    Text(
                        text = "System audio requires Android 10+",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = audioSource == "mic", onClick = { audioSource = "mic" })
                    Text("Microphone")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = audioSource == "none", onClick = { audioSource = "none" })
                    Text("No Audio")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = audioSource == "system",
                        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
                        onClick = { audioSource = "system" }
                    )
                    Text("System Audio(Andorid 10+)")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = audioSource == "mix",
                        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
                        onClick = { audioSource = "mix" }
                    )
                    Text("Mic + System")
                }
            }
        }

        Button(
            onClick = {
                if (audioSource != "none") {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        return@Button
                    }
                }
                launchScreenCaptureIntent()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Streaming")
        }

        Button(
            onClick = {
                val intent = Intent(context, StreamService::class.java)
                intent.action = "STOP"
                context.startService(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Stop Streaming")
        }
    }
}