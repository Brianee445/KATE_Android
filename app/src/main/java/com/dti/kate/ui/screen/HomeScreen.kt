package com.dti.kate.ui.screen

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dti.kate.R
import com.dti.kate.core.*
import com.dti.kate.service.KateAccessibilityService
import com.dti.kate.ui.components.*
import com.dti.kate.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private enum class KateState { IDLE, LISTENING, PROCESSING, SPEAKING }

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    voskManager: VoskManager,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val micPermission = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)
    val locationPermission = rememberPermissionState(android.Manifest.permission.ACCESS_COARSE_LOCATION)

    val localSettings = remember { LocalSettingsStore(context) }
    val responseGenerator = remember { KateResponseGenerator() }
    val deviceControl = remember { DeviceControlManager(context) }
    val weatherService = remember { WeatherService() }
    val webSearchService = remember { WebSearchService() }
    val locationHelper = remember { LocationHelper(context) }
    val audioCapture = remember { AudioCapture() }
    val appLauncher = remember { AppLauncher(context) }

    var kateState by remember { mutableStateOf(KateState.IDLE) }
    var lastReply by remember { mutableStateOf("") }
    var voskReady by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Warming up Kate...") }
    var listenJobActive by remember { mutableStateOf(false) }

    val liveTranscription by voskManager.transcription.collectAsState()

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val instance = TextToSpeech(context) { }
        tts = instance
        onDispose {
            instance.stop()
            instance.shutdown()
        }
    }

    fun speak(text: String) {
        val tone = toneFromSlider(localSettings.getToneLevel())
        val rate = when (tone) {
            KateTone.PROFESSIONAL -> 0.95f
            KateTone.BALANCED -> 1.0f
            KateTone.SASSY -> 1.05f
        }
        tts?.setSpeechRate(rate)
        tts?.language = Locale.US
        kateState = KateState.SPEAKING
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kate_reply")
    }

    LaunchedEffect(Unit) {
        voskManager.initialize { success ->
            voskReady = success
            statusMessage = if (success) "Tap mic to start" else "Speech engine failed to load"
        }
    }

    suspend fun handleQuery(query: String) {
        if (query.isBlank()) {
            kateState = KateState.IDLE
            return
        }

        kateState = KateState.PROCESSING
        val tone = toneFromSlider(localSettings.getToneLevel())

        val action = responseGenerator.classify(query)
        val reply: String = when (action) {
            is KateAction.OpenApp -> {
                val opened = appLauncher.openAppByName(action.appName)
                if (opened) {
                    responseGenerator.speechForOpenApp(action.appName, tone)
                } else {
                    "I couldn't find an app called ${action.appName} on this device."
                }
            }
            is KateAction.TypeText -> {
                if (!KateAccessibilityService.isEnabled(context)) {
                    KateAccessibilityService.openAccessibilitySettings(context)
                    "I need accessibility access to type for you - please turn it on for Kate."
                } else {
                    val typed = KateAccessibilityService.instance?.typeText(action.text) ?: false
                    if (typed) "Typed it." else "I couldn't find a text field to type into."
                }
            }
            is KateAction.ToggleTorch -> {
                val turningOn = action.turnOn ?: !deviceControl.isTorchOn()
                deviceControl.setTorch(turningOn)
                responseGenerator.speechForTorch(turningOn, tone)
            }
            is KateAction.ToggleBluetooth -> {
                when (action.turnOn) {
                    true -> deviceControl.setBluetooth(true)
                    false -> deviceControl.setBluetooth(false)
                    null -> deviceControl.toggleBluetooth()
                }
                responseGenerator.speechForBluetooth(action.turnOn, tone)
            }
            is KateAction.ToggleWifi -> {
                when (action.turnOn) {
                    true -> deviceControl.setWifi(true)
                    false -> deviceControl.setWifi(false)
                    null -> deviceControl.toggleWifi()
                }
                responseGenerator.speechForWifi(action.turnOn, tone)
            }
            is KateAction.SetVolume -> {
                when {
                    action.level != null -> deviceControl.setVolume(action.level)
                    action.increase == true -> deviceControl.increaseVolume()
                    action.increase == false -> deviceControl.decreaseVolume()
                }
                responseGenerator.speechForVolume(tone)
            }
            is KateAction.MakeCall -> {
                deviceControl.makeCall(action.number)
                responseGenerator.speechForCall(action.number, tone)
            }
            is KateAction.Weather -> {
                if (!locationPermission.status.isGranted) {
                    locationPermission.launchPermissionRequest()
                    "I need location access to check the weather - please grant it and try again."
                } else {
                    val coords = locationHelper.getLastKnownLocation()
                    if (coords == null) {
                        responseGenerator.speechForWeather(null, tone)
                    } else {
                        val result = weatherService.getCurrentWeather(coords.first, coords.second)
                        responseGenerator.speechForWeather(result, tone)
                    }
                }
            }
            is KateAction.WebSearch -> {
                val answer = webSearchService.getInstantAnswer(action.query)
                if (answer != null) {
                    responseGenerator.speechForSearchAnswer(answer, tone)
                } else {
                    responseGenerator.speechForSearchNoAnswer(tone)
                }
            }
            KateAction.Help -> responseGenerator.speechForHelp(tone)
            KateAction.Unknown -> responseGenerator.speechForUnknown(tone)
        }

        lastReply = reply
        speak(reply)
    }

    fun stopListeningAndProcess() {
        if (!listenJobActive) return
        listenJobActive = false
        audioCapture.stop()
        val finalText = voskManager.stopListening()
        coroutineScope.launch {
            handleQuery(finalText ?: "")
        }
    }

    fun startListening() {
        if (!micPermission.status.isGranted) {
            micPermission.launchPermissionRequest()
            return
        }
        if (!voskReady || listenJobActive) return

        voskManager.startListening()
        kateState = KateState.LISTENING
        listenJobActive = true

        val maxDurationSeconds = localSettings.getTimeoutSeconds().coerceAtLeast(5)
        var elapsedSeconds = 0

        audioCapture.start(coroutineScope) { chunk ->
            // feedAudio returns non-null exactly when Vosk's own endpoint
            // detection decides the user finished a phrase - that's the
            // real signal to stop, not a fixed timer.
            val finalResult = voskManager.feedAudio(chunk)
            if (finalResult != null && listenJobActive) {
                stopListeningAndProcess()
            }
        }

        // Safety backstop only - in case Vosk never finalizes (e.g.
        // continuous background noise). Uses the Settings timeout value.
        coroutineScope.launch {
            while (listenJobActive && elapsedSeconds < maxDurationSeconds) {
                delay(1000)
                elapsedSeconds++
            }
            if (listenJobActive) {
                stopListeningAndProcess()
            }
        }
    }

    Scaffold(
        modifier = Modifier.background(Background),
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.kate_avatar_idle),
                            contentDescription = "Kate",
                            modifier = Modifier.size(32.dp).clip(CircleShape),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kate",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                            ),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.kate_avatar_idle),
                contentDescription = "Kate",
                modifier = Modifier.size(120.dp).clip(CircleShape),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = when (kateState) {
                    KateState.IDLE -> if (lastReply.isNotEmpty()) lastReply else "How can I help?"
                    KateState.LISTENING -> if (liveTranscription.isNotEmpty()) liveTranscription else "Listening..."
                    KateState.PROCESSING -> "Thinking..."
                    KateState.SPEAKING -> lastReply
                },
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (kateState == KateState.IDLE && lastReply.isEmpty()) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(if (kateState == KateState.LISTENING) LimeAccent else Purple70)
                    .then(
                        androidx.compose.foundation.clickable {
                            when (kateState) {
                                KateState.IDLE -> startListening()
                                KateState.LISTENING -> stopListeningAndProcess()
                                else -> { /* busy */ }
                            }
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Mic,
                    contentDescription = "Microphone",
                    tint = Background,
                    modifier = Modifier.size(40.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Tap mic to start",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}
