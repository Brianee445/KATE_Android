package com.dti.kate.ui.screen

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
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
import com.dti.kate.utils.DeviceControlManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

private enum class KateState { IDLE, LISTENING, PROCESSING, SPEAKING }

// Mirrors the backend's MIN_AUDIO_BYTES floor (transcribe.py) - skips the
// upload entirely for near-silent/too-short buffers rather than wasting a
// round-trip on something the server would reject anyway. 16kHz mono
// 16-bit = 32000 bytes/sec, so this is a ~0.3s floor.
private const val MIN_CLOUD_AUDIO_BYTES = 9600

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
    val contactsPermission = rememberPermissionState(android.Manifest.permission.READ_CONTACTS)
    val contactsHelper = remember { ContactsHelper(context) }

    val localSettings = remember { LocalSettingsStore(context) }
    val responseGenerator = remember { KateResponseGenerator() }
    val deviceControl = remember { DeviceControlManager(context) }
    val weatherService = remember { WeatherService() }
    val webSearchService = remember { WebSearchService() }
    val locationHelper = remember { LocationHelper(context) }
    val audioCapture = remember { AudioCapture() }
    val appLauncher = remember { AppLauncher(context) }
    val musicLauncher = remember { MusicLauncher(context) }
    val kateApiClient = remember { com.dti.kate.network.KateApiClient(context) }
    val repository = remember { com.dti.kate.repository.Repository(context.applicationContext) }
    val recordedAudioBuffer = remember { java.io.ByteArrayOutputStream() }

    var kateState by remember { mutableStateOf(KateState.IDLE) }
    var lastReply by remember { mutableStateOf("") }
    var voskReady by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Warming up Kate...") }
    var sttSourceLabel by remember { mutableStateOf<String?>(null) }
    var listenJobActive by remember { mutableStateOf(false) }

    val liveTranscription by voskManager.transcription.collectAsState()

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var speechCompletion by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }
    DisposableEffect(Unit) {
        val instance = TextToSpeech(context) { }
        instance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                if (utteranceId == "kate_reply") {
                    coroutineScope.launch(Dispatchers.Main) {
                        kateState = KateState.IDLE
                        speechCompletion?.complete(Unit)
                        speechCompletion = null
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == "kate_reply") {
                    coroutineScope.launch(Dispatchers.Main) {
                        kateState = KateState.IDLE
                        speechCompletion?.complete(Unit)
                        speechCompletion = null
                    }
                }
            }
        })
        tts = instance
        onDispose {
            instance.stop()
            instance.shutdown()
        }
    }

    fun speak(text: String) {
        val tone = toneFromSlider(localSettings.getToneLevel())
        // Slightly slower + slightly lower pitch than TTS defaults, layered
        // under the existing tone slider, for a calmer overall delivery -
        // easier to follow for elderly listeners in particular.
        val rate = when (tone) {
            KateTone.PROFESSIONAL -> 0.88f
            KateTone.BALANCED -> 0.92f
            KateTone.SASSY -> 0.97f
        }
        tts?.setSpeechRate(rate)
        tts?.setPitch(0.95f)
        tts?.language = Locale.US
        kateState = KateState.SPEAKING
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kate_reply")
    }

    /** Speaks and suspends until TTS actually finishes - needed before re-listening for a name. */
    suspend fun speakAndWait(text: String) {
        val deferred = CompletableDeferred<Unit>()
        speechCompletion = deferred
        speak(text)
        deferred.await()
    }

    /**
     * Resolves a spoken name to a device contact.
     *
     * Two layers, in order:
     *  1. Fuzzy match against whatever open-vocabulary text Vosk already
     *     produced. Fast, no extra round-trip, works fine when the name
     *     came through reasonably intact.
     *  2. Grammar-constrained re-listen: if that fails, prompt the user
     *     and listen again with the recognizer restricted to just the
     *     device's contact names (see VoskManager.startListeningWithGrammar).
     *     This steers Vosk's own acoustic search toward the closest real
     *     name using the full model, rather than fuzzy-matching after the
     *     fact on free-form output - meaningfully better for names outside
     *     the model's training data (many African names, in particular).
     */
    suspend fun resolveContact(spokenName: String): Contact? {
        if (!contactsPermission.status.isGranted) {
            contactsPermission.launchPermissionRequest()
            return null
        }

        val contacts = contactsHelper.getAllContacts()
        if (contacts.isEmpty()) return null

        contactsHelper.findBestMatch(spokenName, contacts)?.let { return it }

        val tone = toneFromSlider(localSettings.getToneLevel())
        speakAndWait(responseGenerator.speechForWhoToCall(tone))

        val contactNames = contacts.map { it.name }
        if (!voskManager.startListeningWithGrammar(contactNames)) return null

        val reheard = CompletableDeferred<String?>()
        val timeoutJob = coroutineScope.launch {
            delay(6000)
            if (!reheard.isCompleted) reheard.complete(null)
        }

        com.dti.kate.core.MicArbiter.setCapturing(true)
        audioCapture.start(context, coroutineScope) { chunk ->
            val finalResult = voskManager.feedAudio(chunk)
            if (finalResult != null && !reheard.isCompleted) {
                reheard.complete(finalResult)
            }
        }

        val heardDuringCapture = reheard.await()
        timeoutJob.cancel()
        audioCapture.stop()
        com.dti.kate.core.MicArbiter.setCapturing(false)
        val finalText = heardDuringCapture ?: voskManager.stopListening()
        voskManager.restoreDefaultRecognizer()

        if (finalText.isNullOrBlank()) return null

        return contacts.firstOrNull { it.name.equals(finalText, ignoreCase = true) }
            ?: contactsHelper.findBestMatch(finalText, contacts, minSimilarity = 0.4)
    }

    LaunchedEffect(Unit) {
        voskManager.initialize { success ->
            voskReady = success
            statusMessage = if (success) "Tap for a command, hold to search" else "Speech engine failed to load"
        }
    }

    /**
     * Checks whether enough voice interactions have accumulated locally to
     * be worth a sync round-trip, and if so uploads a batch in the
     * background. Fire-and-forget by design - a failed sync just leaves
     * the entries in place to retry next time, never blocks or affects
     * the current interaction.
     */
    fun maybeSyncVoiceLogs() {
        val syncBatchThreshold = 10
        if (VoiceInteractionLogger.unsyncedCount(context) < syncBatchThreshold) return
        if (!NetworkMonitor.isOnline(context) || !kateApiClient.isAuthenticated()) return

        coroutineScope.launch {
            val batch = VoiceInteractionLogger.peekBatch(context, limit = 100)
            if (batch.isEmpty()) return@launch
            repository.uploadSyncLogs(batch).onSuccess {
                VoiceInteractionLogger.removeOldest(context, batch.size)
            }
        }
    }

    val commandProcessor = remember {
        KateCommandProcessor(
            context = context,
            responseGenerator = responseGenerator,
            deviceControl = deviceControl,
            weatherService = weatherService,
            webSearchService = webSearchService,
            appLauncher = appLauncher,
            musicLauncher = musicLauncher,
            contactsHelper = contactsHelper,
            locationHelper = locationHelper,
            permissionBridge = object : KateCommandProcessor.PermissionBridge {
                override fun hasContacts() = contactsPermission.status.isGranted
                override fun hasLocation() = locationPermission.status.isGranted
                override fun requestContacts() = contactsPermission.launchPermissionRequest()
                override fun requestLocation() = locationPermission.launchPermissionRequest()
            },
        )
    }

    suspend fun handleQuery(query: String, usedCloud: Boolean = false, confidence: Float = 0f) {
        val tone = toneFromSlider(localSettings.getToneLevel())

        if (query.isBlank()) {
            kateState = KateState.IDLE
            speak(responseGenerator.speechForNoSpeech(tone))
            return
        }

        kateState = KateState.PROCESSING

        // MakeCall/SendMessage with no fuzzy-match still benefit from the
        // richer in-app flow (asks "who do you mean" and re-listens with a
        // name grammar) - that interactive sub-flow lives here rather than
        // in the shared KateCommandProcessor, which only does the fast
        // path. Everything else routes through the shared processor so
        // HomeScreen and the overlay stay in lockstep.
        val action = responseGenerator.classify(query)
        val reply: String = when (action) {
            is KateAction.MakeCall -> {
                if (!contactsPermission.status.isGranted) {
                    contactsPermission.launchPermissionRequest()
                    responseGenerator.speechForNoContactsPermission(tone)
                } else {
                    val contact = resolveContact(action.spokenName)
                    if (contact != null) {
                        deviceControl.makeCall(contact.phoneNumber)
                        responseGenerator.speechForCall(contact.name, tone)
                    } else {
                        responseGenerator.speechForContactNotFound(action.spokenName, tone)
                    }
                }
            }
            is KateAction.SendMessage -> {
                if (!contactsPermission.status.isGranted) {
                    contactsPermission.launchPermissionRequest()
                    responseGenerator.speechForNoContactsPermission(tone)
                } else {
                    val contact = resolveContact(action.spokenName)
                    if (contact != null) {
                        val body = action.body ?: "Hi"
                        deviceControl.sendSms(contact.phoneNumber, body)
                        responseGenerator.speechForMessage(contact.name, tone)
                    } else {
                        responseGenerator.speechForContactNotFound(action.spokenName, tone)
                    }
                }
            }
            else -> commandProcessor.process(query, tone).speech
        }

        lastReply = reply
        VoiceInteractionLogger.logInteraction(
            context = context,
            query = query,
            response = reply,
            intent = action::class.simpleName ?: "Unknown",
            confidence = confidence,
            usedCloud = usedCloud,
            modelVersion = "vosk-0.3.47+deepgram",
        )
        maybeSyncVoiceLogs()
        speak(reply)
    }

    fun stopListeningAndProcess() {
        if (!listenJobActive) return
        listenJobActive = false
        audioCapture.stop()
        com.dti.kate.core.MicArbiter.setCapturing(false)
        val localText = voskManager.stopListening()
        voskManager.restoreDefaultRecognizer()
        val bufferedAudio = recordedAudioBuffer.toByteArray()

        coroutineScope.launch {
            var usedCloud = false
            var confidence = 0f

            val finalText = if (NetworkMonitor.isOnline(context) && kateApiClient.isAuthenticated() && bufferedAudio.size >= MIN_CLOUD_AUDIO_BYTES) {
                statusMessage = "Refining transcription..."
                // 6s cap: cloud STT should be well under this for a short
                // command/query, and we always have the local result ready
                // as a fallback if it isn't.
                val cloudResult = withTimeoutOrNull(6000) {
                    kateApiClient.transcribeAudio(bufferedAudio)
                }
                DebugLog.log(
                    context, "CloudSTT",
                    "local=\"${localText ?: ""}\" cloud=${cloudResult?.let { "\"${it.text}\" (conf=${it.confidence})" } ?: "unavailable"}"
                )
                val cloudText = cloudResult?.text?.takeIf { it.isNotBlank() }
                if (cloudText != null) {
                    usedCloud = true
                    confidence = cloudResult.confidence
                    sttSourceLabel = "☁️ Cloud (Deepgram)"
                } else {
                    sttSourceLabel = "📱 On-device (cloud unavailable)"
                }
                cloudText ?: localText
            } else {
                sttSourceLabel = if (!NetworkMonitor.isOnline(context)) "📱 On-device (offline)" else "📱 On-device"
                localText
            }
            handleQuery(finalText ?: "", usedCloud, confidence)
        }
    }

    /**
     * Builds the vocabulary for grammar-constrained command listening:
     * fixed command/control words plus the device's actual installed app
     * names and contacts, so "call chidinma" or "open <whatever's really
     * installed>" can resolve correctly in a single pass, not just the
     * fixed English trigger words.
     *
     * Deliberately excludes open-ended search/question triggers ("search",
     * "what", "who", "how"...) - those need free-vocabulary dictation
     * (long-press), since there's no fixed vocabulary to constrain a
     * search query or typed text to.
     */
    fun buildCommandGrammar(): List<String> {
        val staticWords = listOf(
            "weather", "temperature", "forecast",
            "play", "music", "song", "spotify", "audiomack",
            "torch", "flashlight", "flash", "on", "off", "turn",
            "bluetooth", "wifi", "wi-fi",
            "volume", "up", "down", "increase", "decrease", "mute", "set",
            "call", "phone", "dial",
            "message", "text", "saying",
            "open", "launch", "start", "run",
            "help",
            "zero", "one", "two", "three", "four", "five", "six", "seven",
            "eight", "nine", "ten", "eleven", "twelve", "thirteen",
            "fourteen", "fifteen", "twenty", "thirty", "forty", "fifty", "hundred",
        )
        val appNames = appLauncher.getInstalledAppNames()
        val contactNames = if (contactsPermission.status.isGranted) {
            contactsHelper.getAllContacts().map { it.name }
        } else {
            emptyList()
        }
        return (staticWords + appNames + contactNames).distinct()
    }

    /** useCommandGrammar=true (default, tap) constrains recognition to known command vocabulary for accuracy. false (long-press) uses open dictation for search/free text. */
    fun startListening(useCommandGrammar: Boolean = true) {
        if (!micPermission.status.isGranted) {
            micPermission.launchPermissionRequest()
            return
        }
        if (!voskReady || listenJobActive) return

        val started = if (useCommandGrammar) {
            voskManager.startListeningWithGrammar(buildCommandGrammar())
        } else {
            voskManager.startListening()
        }
        if (!started) return

        com.dti.kate.core.MicArbiter.setCapturing(true)
        kateState = KateState.LISTENING
        listenJobActive = true
        recordedAudioBuffer.reset()
        statusMessage = if (useCommandGrammar) "Listening for a command..." else "Listening..."

        val maxDurationSeconds = localSettings.getTimeoutSeconds().coerceAtLeast(5)
        var elapsedSeconds = 0

        audioCapture.start(context, coroutineScope) { chunk ->
            recordedAudioBuffer.write(chunk)
            val finalResult = voskManager.feedAudio(chunk)
            if (finalResult != null && listenJobActive) {
                stopListeningAndProcess()
            }
        }

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

    // Fires when a background wake gesture (Raise/Shake) is detected -
    // auto-starts listening once Kate is idle and ready.
    //
    // Two race conditions handled here, both specific to cold starts
    // (app wasn't already open when the gesture fired):
    //  1. KateWakeSignal now replays its last event, so a collector that
    //     subscribes *after* the emission (e.g. HomeScreen composing after
    //     KateForegroundService's startActivity() call queues a fresh
    //     launch) still receives it - previously it was emitted into an
    //     empty flow and lost.
    //  2. Even with the replay, voskReady may still be false at the exact
    //     moment this LaunchedEffect first sees the token (native engine
    //     init is still running). A plain `.collect { if (voskReady) ... }`
    //     would see that single delivery, find voskReady false, and drop
    //     the wake request on the floor. Tracking the token as state and
    //     re-evaluating whenever voskReady/kateState change means we act
    //     on it the moment the engine actually becomes ready instead.
    var pendingWakeToken by remember { mutableLongStateOf(0L) }
    var lastHandledWakeToken by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        KateWakeSignal.events.collect { token ->
            pendingWakeToken = token
        }
    }

    LaunchedEffect(pendingWakeToken, voskReady, kateState) {
        if (pendingWakeToken != lastHandledWakeToken && voskReady && kateState == KateState.IDLE) {
            lastHandledWakeToken = pendingWakeToken
            startListening()
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
                    IconButton(onClick = { navController.navigate("chat") }) {
                        Icon(Icons.Outlined.Chat, contentDescription = "Chat with Kate", tint = TextSecondary)
                    }
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

            sttSourceLabel?.let { label ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
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
                    .pointerInput(kateState) {
                        detectTapGestures(
                            onTap = {
                                when (kateState) {
                                    KateState.IDLE -> startListening(useCommandGrammar = true)
                                    KateState.LISTENING -> stopListeningAndProcess()
                                    else -> { /* busy */ }
                                }
                            },
                            onLongPress = {
                                if (kateState == KateState.IDLE) {
                                    startListening(useCommandGrammar = false)
                                }
                            },
                        )
                    },
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
                text = "Tap for a command, hold to search",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}
