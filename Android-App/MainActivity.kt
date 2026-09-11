package com.sih.dharascope

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationCompat
import com.google.ai.client.generativeai.GenerativeModel
import com.sih.dharascope.ui.theme.DHARASCOPETheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// Enter your actual Gemini API Key here
private const val GEMINI_API_KEY = "API_KEY"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel(this)
        setContent {
            DHARASCOPETheme {
                DharascopeMasterApp()
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "DHARASCOPE Alerts"
            val descriptionText = "Notifications for important mine deformation events"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("DHARASCOPE_ALERTS", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

// Data models
data class SensorReadingNode(
    val packetNo: Int,
    val distanceCm: Float,
    val deltaCm: Float,
    val speedCmSec: Float,
    val direction: String,
    val timestamp: String,
    val epochTimeMs: Long = System.currentTimeMillis()
)

data class MineEvent(
    val id: Int,
    val title: String,
    val detail: String,
    val timestamp: String,
    val severity: String
)

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
)

// M3 Expressive Colors
val M3ExpressiveBackground = Color(0xFFF6F2F7)
val M3SurfaceHigh = Color(0xFFECE6F0)

val M3PastelPurple = Color(0xFFEADDFF)
val M3OnPastelPurple = Color(0xFF21005D)
val M3PastelBlue = Color(0xFFC2E7FF)
val M3OnPastelBlue = Color(0xFF001D35)
val M3PastelPink = Color(0xFFFFD8E4)
val M3OnPastelPink = Color(0xFF31111D)
val M3PastelGreen = Color(0xFFC4EED0)
val M3OnPastelGreen = Color(0xFF07210C)
val M3PastelYellow = Color(0xFFFFE082)
val M3OnPastelYellow = Color(0xFF3E2723)
val M3AccentPurple = Color(0xFF65558F)

val M3LiveGreen = Color(0xFF146C2E)
val M3OfflineRed = Color(0xFFB3261E)

// Graph Styling Colors
val M3DistanceLineColor = Color(0xFF004F8B)
val M3DistanceFillColor = Color(0xFF82B1FF)
val M3DistanceBgColor = Color(0xFFFAFAFA)

val M3SpeedLineColor = Color(0xFF8D5B00)
val M3SpeedFillColor = Color(0xFFFFD54F)
val M3SpeedBgColor = Color(0xFFFFFDF5)

val M3DeltaLineColor = Color(0xFF1B5E20)
val M3DeltaFillColor = Color(0xFFA5D6A7)
val M3DeltaBgColor = Color(0xFFF1F8E9)

fun triggerLocalNotification(context: Context, title: String, message: String) {
    val builder = NotificationCompat.Builder(context, "DHARASCOPE_ALERTS")
        .setSmallIcon(android.R.drawable.stat_notify_error)
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)

    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
}

@Composable
fun DharascopeMasterApp() {
    val context = LocalContext.current
    var isAppLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(1800)
        isAppLoading = false
    }

    if (isAppLoading) {
        ExpressiveTextOnlySplash()
    } else {
        var selectedTab by remember { mutableIntStateOf(0) }
        var espIpAddress by remember { mutableStateOf("") }
        var isConnected by remember { mutableStateOf(false) }
        var showIpModalDialog by remember { mutableStateOf(false) }

        var currentDistance by remember { mutableFloatStateOf(25.0f) }
        val baselineDistance = 25.0f
        var changeFromBaseline by remember { mutableFloatStateOf(0.0f) }
        var changeSpeed by remember { mutableFloatStateOf(0.0f) }
        var directionOfChange by remember { mutableStateOf("STABLE") }
        var lastChangeTime by remember { mutableStateOf("N/A") }
        var sensorSource by remember { mutableStateOf("HC-SR04 / Ultrasonic") }
        var irStatus by remember { mutableStateOf("CLEAR") }
        var packetNumber by remember { mutableIntStateOf(0) }
        var monitoringStatus by remember { mutableStateOf("ACTIVE") }

        // AI State Variables
        var aiInsightText by remember { mutableStateOf("Tap button to analyze current mine telemetry with Gemini AI.") }
        var isAiAnalyzing by remember { mutableStateOf(false) }

        // AI Chat Bot State Variables
        var chatMessages by remember {
            mutableStateOf(
                listOf(
                    ChatMessage(
                        text = "Hello! I am your DHARASCOPE Assistant. Ask me anything about the live telemetry, movement speed, past activities, or safety status in any language!",
                        isUser = false
                    )
                )
            )
        }
        var isChatBotLoading by remember { mutableStateOf(false) }

        val currentTime = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()) }
        val currentMs = remember { System.currentTimeMillis() }

        var distanceHistory by remember { mutableStateOf(listOf(SensorReadingNode(0, 25f, 0f, 0f, "STABLE", currentTime, currentMs))) }
        var speedHistory by remember { mutableStateOf(listOf(SensorReadingNode(0, 25f, 0f, 0f, "STABLE", currentTime, currentMs))) }
        var deltaHistory by remember { mutableStateOf(listOf(SensorReadingNode(0, 25f, 0f, 0f, "STABLE", currentTime, currentMs))) }

        var eventsList by remember { mutableStateOf(listOf<MineEvent>()) }
        var expandedCardIndex by remember { mutableIntStateOf(-1) }
        val coroutineScope = rememberCoroutineScope()

        val runAiAnalysis: () -> Unit = {
            if (!isAiAnalyzing) {
                isAiAnalyzing = true
                aiInsightText = "Analyzing real-time sensor waveforms with Gemini AI..."
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val generativeModel = GenerativeModel(
                            modelName = "gemini-3.6-flash",
                            apiKey = GEMINI_API_KEY
                        )
                        val prompt = """
                            You are the AI Safety Engine for DHARASCOPE Underground Mine Subsidence Monitoring System.
                            Analyze the following live sensor reading and give a brief 2-3 sentence structural stability and risk analysis:
                            - Distance: $currentDistance cm (Baseline: $baselineDistance cm)
                            - Shift Delta: $changeFromBaseline cm
                            - Speed: $changeSpeed cm/s
                            - Movement Direction: $directionOfChange
                            - IR Sensor Status: $irStatus
                            - Active Data Packets: ${distanceHistory.size}
                        """.trimIndent()

                        val response = generativeModel.generateContent(prompt)
                        withContext(Dispatchers.Main) {
                            aiInsightText = response.text ?: "No insight received from AI."
                            isAiAnalyzing = false
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            aiInsightText = "AI Analysis Error: ${e.localizedMessage ?: "Failed to connect to AI server."}"
                            isAiAnalyzing = false
                        }
                    }
                }
            }
        }

        val sendChatMessage: (String) -> Unit = { userQuery ->
            if (userQuery.isNotBlank() && !isChatBotLoading) {
                val newMsg = ChatMessage(text = userQuery, isUser = true)
                chatMessages = chatMessages + newMsg
                isChatBotLoading = true

                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val generativeModel = GenerativeModel(
                            modelName = "gemini-3.6-flash",
                            apiKey = GEMINI_API_KEY
                        )

                        val firstActivity = distanceHistory.firstOrNull()
                        val lastActivity = distanceHistory.lastOrNull()
                        val maxSpeedRecorded = speedHistory.maxOfOrNull { it.speedCmSec } ?: 0f

                        val systemContext = """
                            You are DHARASCOPE In-App Intelligent Assistant for Underground Mine Subsidence Monitoring.
                            Respond directly to the user's question in the SAME language they used (Hindi, English, Hinglish, Marathi, etc.).
                            Be concise, helpful, and clear.
                            
                            CURRENT LIVE DATA METRICS:
                            - Current Ground Distance: $currentDistance cm
                            - Baseline Setting: $baselineDistance cm
                            - Shift Delta Displacement: $changeFromBaseline cm
                            - Current Movement Speed: $changeSpeed cm/s
                            - Maximum Speed Recorded: $maxSpeedRecorded cm/s
                            - Current Direction: $directionOfChange
                            - IR Obstacle Sensor: $irStatus
                            - Total Recorded Data Packets: ${distanceHistory.size}
                            - Connection Status: ${if (isConnected) "CONNECTED" else "OFFLINE"}
                            - First Activity Recorded At: ${firstActivity?.timestamp ?: "N/A"} (Distance: ${firstActivity?.distanceCm} cm)
                            - Latest Activity Recorded At: ${lastActivity?.timestamp ?: "N/A"} (Distance: ${lastActivity?.distanceCm} cm)
                            - Recent Mine Critical Events Count: ${eventsList.size}
                            
                            User Question: $userQuery
                        """.trimIndent()

                        val response = generativeModel.generateContent(systemContext)
                        withContext(Dispatchers.Main) {
                            val aiReply = response.text ?: "Sorry, I couldn't generate a response."
                            chatMessages = chatMessages + ChatMessage(text = aiReply, isUser = false)
                            isChatBotLoading = false
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            chatMessages = chatMessages + ChatMessage(
                                text = "Error answering query: ${e.localizedMessage ?: "Please try again."}",
                                isUser = false
                            )
                            isChatBotLoading = false
                        }
                    }
                }
            }
        }

        val resetAllMonitoring = {
            currentDistance = 25.0f
            changeFromBaseline = 0.0f
            changeSpeed = 0.0f
            directionOfChange = "STABLE"
            packetNumber = 0
            irStatus = "CLEAR"
            monitoringStatus = "RESETTED"
            val now = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val nowMs = System.currentTimeMillis()
            val defaultNode = SensorReadingNode(0, 25f, 0f, 0f, "STABLE", now, nowMs)
            distanceHistory = listOf(defaultNode)
            speedHistory = listOf(defaultNode)
            deltaHistory = listOf(defaultNode)

            val currentIp = espIpAddress.trim()
            if (currentIp.isNotEmpty()) {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val cleanIp = if (!currentIp.startsWith("http://")) "http://$currentIp" else currentIp
                        val resetUrl = URL("$cleanIp/api/reset")
                        val conn = (resetUrl.openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = 3000
                            readTimeout = 3000
                        }
                        conn.responseCode
                        conn.disconnect()
                    } catch (_: Exception) {}
                }
            }
        }

        // Live Network Worker Loop
        LaunchedEffect(espIpAddress) {
            while (isActive) {
                val currentIp = espIpAddress.trim()
                if (currentIp.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        try {
                            val cleanIp = if (!currentIp.startsWith("http://")) "http://$currentIp" else currentIp
                            val url = URL("$cleanIp/api/data")
                            val conn = (url.openConnection() as HttpURLConnection).apply {
                                requestMethod = "GET"
                                connectTimeout = 2000
                                readTimeout = 2000
                            }

                            if (conn.responseCode == 200) {
                                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                                val sb = StringBuilder()
                                var line: String?
                                while (reader.readLine().also { line = it } != null) sb.append(line)
                                reader.close()

                                val json = JSONObject(sb.toString())
                                withContext(Dispatchers.Main) {
                                    val newDist = json.optDouble("distance", currentDistance.toDouble()).toFloat()
                                    val newPacket = json.optInt("packet", packetNumber)
                                    val newSensor = json.optString("sensor", sensorSource)

                                    val rawIr = when {
                                        json.has("ir_status") -> json.optString("ir_status")
                                        json.has("ir") -> json.optString("ir")
                                        json.has("obstacle") -> json.optString("obstacle")
                                        else -> "1"
                                    }

                                    val newIr = if (rawIr.equals("0", true) || rawIr.equals("false", true) || rawIr.contains("DETECTED", true) || rawIr.contains("LOW", true)) {
                                        "OBSTACLE DETECTED"
                                    } else {
                                        "CLEAR"
                                    }

                                    val newStatus = json.optString("status", "ACTIVE")
                                    val nowTimeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                                    val nowEpoch = System.currentTimeMillis()

                                    val prevDist = currentDistance
                                    val delta = newDist - baselineDistance
                                    val speed = json.optDouble("speed", abs(newDist - prevDist).toDouble()).toFloat()
                                    val dir = when {
                                        newDist > prevDist -> "SUBSIDENCE / SINKING"
                                        newDist < prevDist -> "HEAVE / RISING"
                                        else -> "STABLE"
                                    }

                                    val hasMovement = abs(newDist - prevDist) > 0.05f || speed > 0.05f
                                    val hasPacketChanged = newPacket != packetNumber && newPacket != 0

                                    if (hasMovement || hasPacketChanged) {
                                        val node = SensorReadingNode(newPacket, newDist, delta, speed, dir, nowTimeStr, nowEpoch)
                                        distanceHistory = (distanceHistory + node).takeLast(50)
                                        speedHistory = (speedHistory + node).takeLast(50)
                                        deltaHistory = (deltaHistory + node).takeLast(50)

                                        if (abs(delta) > 3.0f || speed > 2.0f) {
                                            lastChangeTime = nowTimeStr
                                            val eventTitle = if (delta > 3f) "CRITICAL SUBSIDENCE ALERT" else "FAST MOVEMENT DETECTED"
                                            val eventDetail = "Shift: ${"%.2f".format(delta)} cm | Speed: ${"%.2f".format(speed)} cm/s"
                                            eventsList = listOf(MineEvent(eventsList.size + 1, eventTitle, eventDetail, nowTimeStr, "HIGH")) + eventsList
                                            triggerLocalNotification(context, "DHARASCOPE: $eventTitle", eventDetail)
                                        }
                                    }

                                    currentDistance = newDist
                                    changeFromBaseline = delta
                                    changeSpeed = speed
                                    directionOfChange = dir
                                    sensorSource = newSensor
                                    irStatus = newIr
                                    packetNumber = newPacket
                                    monitoringStatus = newStatus
                                    isConnected = true
                                }
                            } else {
                                withContext(Dispatchers.Main) { isConnected = false }
                            }
                            conn.disconnect()
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { isConnected = false }
                        }
                    }
                } else {
                    isConnected = false
                }
                delay(1000)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(M3ExpressiveBackground)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(115.dp))

                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        slideInHorizontally(
                            animationSpec = spring(
                                dampingRatio = 0.85f,
                                stiffness = Spring.StiffnessVeryLow
                            ),
                            initialOffsetX = { if (targetState > initialState) it else -it }
                        ) + fadeIn(animationSpec = tween(500, easing = LinearOutSlowInEasing)) togetherWith
                                slideOutHorizontally(
                                    animationSpec = spring(
                                        dampingRatio = 0.85f,
                                        stiffness = Spring.StiffnessVeryLow
                                    ),
                                    targetOffsetX = { if (targetState > initialState) -it else it }
                                ) + fadeOut(animationSpec = tween(400, easing = FastOutLinearInEasing))
                    },
                    label = "smoothPageTransition"
                ) { tab ->
                    when (tab) {
                        0 -> LiveMonitorContent(
                            currentDist = currentDistance,
                            baselineDist = baselineDistance,
                            delta = changeFromBaseline,
                            speed = changeSpeed,
                            direction = directionOfChange,
                            sensor = sensorSource,
                            ir = irStatus,
                            packet = packetNumber,
                            status = monitoringStatus,
                            distanceGraph = distanceHistory,
                            expandedIndex = expandedCardIndex,
                            onCardClick = { idx -> expandedCardIndex = if (expandedCardIndex == idx) -1 else idx },
                            onResetData = resetAllMonitoring
                        )
                        1 -> AnalyticsContent(
                            distanceGraph = distanceHistory,
                            speedGraph = speedHistory,
                            deltaGraph = deltaHistory,
                            onAskAI = runAiAnalysis
                        )
                        2 -> EventsContent(events = eventsList)
                        3 -> AIInsightsContent(
                            aiText = aiInsightText,
                            isLoading = isAiAnalyzing,
                            chatMessages = chatMessages,
                            isChatLoading = isChatBotLoading,
                            onAskAI = runAiAnalysis,
                            onSendChatMessage = sendChatMessage
                        )
                    }
                }

                Spacer(modifier = Modifier.height(110.dp))
            }

            CompactTopRightHeader(
                isConnected = isConnected,
                onOpenIpModal = { showIpModalDialog = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 44.dp, end = 20.dp)
            )

            SmoothScrollingBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
            )

            if (showIpModalDialog) {
                SmoothIpInputDialog(
                    ipAddress = espIpAddress,
                    onIpChange = { espIpAddress = it },
                    onDismiss = { showIpModalDialog = false }
                )
            }
        }
    }
}

@Composable
fun ExpressiveTextOnlySplash() {
    val scale = remember { Animatable(0.6f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.55f,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            alpha.animateTo(1f, animationSpec = tween(700))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(M3ExpressiveBackground),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "DHARASCOPE",
            fontSize = 42.sp,
            fontWeight = FontWeight.Black,
            color = Color.Black,
            letterSpacing = 3.sp,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                }
        )
    }
}

@Composable
fun CompactTopRightHeader(
    isConnected: Boolean,
    onOpenIpModal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .graphicsLayer {
                shadowElevation = 8f
                shape = RoundedCornerShape(26.dp)
                clip = true
            },
        shape = RoundedCornerShape(26.dp),
        color = Color.White.copy(alpha = 0.85f),
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (isConnected) M3PastelGreen else M3PastelPink
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) M3LiveGreen else M3OfflineRed)
                    )
                    Text(
                        text = if (isConnected) "Connected" else "Offline",
                        fontSize = 11.sp,
                        color = if (isConnected) M3OnPastelGreen else M3OnPastelPink,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = M3AccentPurple,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable { onOpenIpModal() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "IP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun SmoothScrollingBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50.dp),
        color = Color.White.copy(alpha = 0.95f),
        tonalElevation = 10.dp,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                Icons.Default.Info,
                Icons.Default.List,
                Icons.Default.Notifications,
                Icons.Default.Star
            )

            tabs.forEachIndexed { index, icon ->
                val isSelected = selectedTab == index

                val animatedScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.2f else 1.0f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
                    label = "tabIconScale"
                )

                Surface(
                    shape = CircleShape,
                    color = if (isSelected) M3AccentPurple else Color.Transparent,
                    modifier = Modifier
                        .scale(animatedScale)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(index) }
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) Color.White else M3OnPastelPurple.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LiveMonitorContent(
    currentDist: Float,
    baselineDist: Float,
    delta: Float,
    speed: Float,
    direction: String,
    sensor: String,
    ir: String,
    packet: Int,
    status: String,
    distanceGraph: List<SensorReadingNode>,
    expandedIndex: Int,
    onCardClick: (Int) -> Unit,
    onResetData: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val buttonScale = remember { Animatable(1f) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BouncyTextMetricCard(
            title = "Current Distance",
            value = "${"%.2f".format(currentDist)} cm",
            containerColor = M3PastelPurple,
            textColor = M3OnPastelPurple,
            isExpanded = expandedIndex == 0,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 32.dp)
        ) { onCardClick(0) }

        BouncyTextMetricCard(
            title = "Baseline / Shift Delta",
            value = "${baselineDist}cm | ${"%.2f".format(delta)}cm",
            containerColor = M3PastelPink,
            textColor = M3OnPastelPink,
            isExpanded = expandedIndex == 1,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 32.dp, bottomStart = 32.dp, bottomEnd = 16.dp)
        ) { onCardClick(1) }

        BouncyTextMetricCard(
            title = "Speed & Direction",
            value = "${"%.2f".format(speed)} cm/s ($direction)",
            containerColor = M3PastelBlue,
            textColor = M3OnPastelBlue,
            isExpanded = expandedIndex == 2,
            shape = RoundedCornerShape(28.dp)
        ) { onCardClick(2) }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "SENSOR READINGS & NODE STATUS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = M3AccentPurple,
                modifier = Modifier.padding(start = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                M3SquareReadingCard(
                    title = "Sensor Hardware",
                    value = sensor,
                    containerColor = M3PastelGreen,
                    textColor = M3OnPastelGreen,
                    modifier = Modifier.weight(1f)
                )

                val isObstacleDetected = ir.contains("OBSTACLE", true) || ir.contains("DETECTED", true)
                M3SquareReadingCard(
                    title = "IR Obstacle",
                    value = ir,
                    containerColor = if (isObstacleDetected) M3PastelPink else M3PastelGreen,
                    textColor = if (isObstacleDetected) M3OnPastelPink else M3OnPastelGreen,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                M3SquareReadingCard(
                    title = "Packet Number",
                    value = "#$packet",
                    containerColor = M3PastelYellow,
                    textColor = M3OnPastelYellow,
                    modifier = Modifier.weight(1f)
                )

                M3SquareReadingCard(
                    title = "Node Status",
                    value = status,
                    containerColor = M3PastelPurple,
                    textColor = M3OnPastelPurple,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        M3ImageStyleGraphCard(
            title = "Realtime Distance Graph",
            nodes = distanceGraph,
            lineColor = M3DistanceLineColor,
            fillColor = M3DistanceFillColor,
            cardBgColor = M3DistanceBgColor,
            unit = "cm",
            valueSelector = { it.distanceCm }
        )

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = M3PastelPink,
            tonalElevation = 4.dp,
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .scale(buttonScale.value)
                .clip(RoundedCornerShape(28.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    coroutineScope.launch {
                        buttonScale.animateTo(0.90f, spring(0.7f, Spring.StiffnessLow))
                        buttonScale.animateTo(1f, spring(0.5f, Spring.StiffnessLow))
                    }
                    onResetData()
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("RESET MONITORING", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = M3OnPastelPink)
            }
        }
    }
}

@Composable
fun M3SquareReadingCard(
    title: String,
    value: String,
    containerColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow),
        label = "squareCardBounce"
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        tonalElevation = 3.dp,
        modifier = modifier
            .aspectRatio(1f)
            .scale(cardScale)
            .clip(RoundedCornerShape(28.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isPressed = !isPressed
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = textColor.copy(alpha = 0.65f),
                letterSpacing = 0.5.sp
            )

            Text(
                text = value,
                fontSize = if (value.length > 10) 14.sp else 19.sp,
                fontWeight = FontWeight.Black,
                color = textColor,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
fun AnalyticsContent(
    distanceGraph: List<SensorReadingNode>,
    speedGraph: List<SensorReadingNode>,
    deltaGraph: List<SensorReadingNode>,
    onAskAI: () -> Unit
) {
    val maxDist = distanceGraph.maxOfOrNull { it.distanceCm } ?: 0f
    val minDist = distanceGraph.minOfOrNull { it.distanceCm } ?: 0f

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = M3PastelPurple,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("HISTORICAL STATS SUMMARY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = M3AccentPurple)
                Text("Max Depth Recorded: ${"%.2f".format(maxDist)} cm", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = M3OnPastelPurple)
                Text("Min Depth Recorded: ${"%.2f".format(minDist)} cm", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = M3OnPastelPurple)
                Text("Total Data Packets: ${distanceGraph.size}", fontSize = 14.sp, fontWeight = FontWeight.Normal, color = M3OnPastelPurple)
            }
        }

        M3ImageStyleGraphCard(
            title = "Distance Over Time",
            nodes = distanceGraph,
            lineColor = M3DistanceLineColor,
            fillColor = M3DistanceFillColor,
            cardBgColor = M3DistanceBgColor,
            unit = "cm",
            valueSelector = { it.distanceCm }
        )

        M3ImageStyleGraphCard(
            title = "Change Speed Analysis",
            nodes = speedGraph,
            lineColor = M3SpeedLineColor,
            fillColor = M3SpeedFillColor,
            cardBgColor = M3SpeedBgColor,
            unit = "cm/s",
            valueSelector = { it.speedCmSec }
        )

        M3ImageStyleGraphCard(
            title = "Baseline Deviation Shift",
            nodes = deltaGraph,
            lineColor = M3DeltaLineColor,
            fillColor = M3DeltaFillColor,
            cardBgColor = M3DeltaBgColor,
            unit = "cm",
            valueSelector = { it.deltaCm }
        )

        M3AIAnalysisButton(onAskAI)
    }
}

@Composable
fun EventsContent(events: List<MineEvent>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("CRITICAL DEFORMATION LOGS", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = M3OnPastelPurple)

        if (events.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = M3SurfaceHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(30.dp), contentAlignment = Alignment.Center) {
                    Text("No Subsidence or Critical Events Detected Yet.", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center)
                }
            }
        } else {
            events.forEach { event ->
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = M3PastelPink,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(event.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = M3OnPastelPink)
                            Text(event.timestamp, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = M3OfflineRed)
                        }
                        Text(event.detail, fontSize = 13.sp, color = M3OnPastelPink)
                    }
                }
            }
        }
    }
}

@Composable
fun AIInsightsContent(
    aiText: String,
    isLoading: Boolean,
    chatMessages: List<ChatMessage>,
    isChatLoading: Boolean,
    onAskAI: () -> Unit,
    onSendChatMessage: (String) -> Unit
) {
    var queryText by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("AI PREDICTIVE INSIGHTS", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = M3OnPastelPurple)

        // Slide/Card Top Section (Original Functionality Intact)
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = M3PastelPurple,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = M3AccentPurple,
                    modifier = Modifier.size(36.dp)
                )
                Text("Smart Mine Safety Engine", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = M3OnPastelPurple)

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        if (isLoading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = M3AccentPurple, strokeWidth = 2.dp)
                                Text("Analyzing Telemetry...", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = M3OnPastelPurple)
                            }
                        } else {
                            Text(
                                text = aiText,
                                fontSize = 14.sp,
                                color = M3OnPastelPurple,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onAskAI,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = M3AccentPurple)
                ) {
                    Text(if (isLoading) "Analyzing..." else "Launch AI Deep Analysis", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // --- NEW IN-APP INTERACTIVE CHATBOT SECTION ---
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(M3LiveGreen)
                    )
                    Text(
                        text = "Interactive Mine AI Chatbot",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = M3OnPastelPurple
                    )
                }

                Text(
                    text = "Puchein kuch bhi data ke baare me (Speed, Activity timing, Packets, etc.). AI har bhasha me javab dega!",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                // Conversation Box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 320.dp)
                        .background(M3ExpressiveBackground, RoundedCornerShape(18.dp))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    chatMessages.forEach { msg ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (msg.isUser) 16.dp else 4.dp,
                                    bottomEnd = if (msg.isUser) 4.dp else 16.dp
                                ),
                                color = if (msg.isUser) M3AccentPurple else M3PastelPurple
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    Text(
                                        text = msg.text,
                                        fontSize = 13.sp,
                                        color = if (msg.isUser) Color.White else M3OnPastelPurple,
                                        lineHeight = 18.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = msg.timestamp,
                                        fontSize = 10.sp,
                                        color = if (msg.isUser) Color.White.copy(alpha = 0.7f) else M3OnPastelPurple.copy(alpha = 0.6f),
                                        modifier = Modifier.align(Alignment.End)
                                    )
                                }
                            }
                        }
                    }

                    if (isChatLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(6.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = M3AccentPurple, strokeWidth = 2.dp)
                            Text("AI is typing answer...", fontSize = 12.sp, color = M3AccentPurple, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Input Box & Send Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        placeholder = { Text("Puchein: Speed kitni thi? Pehla activity kab hua?", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = M3AccentPurple,
                            unfocusedBorderColor = Color.LightGray,
                            focusedContainerColor = M3ExpressiveBackground,
                            unfocusedContainerColor = M3ExpressiveBackground
                        )
                    )

                    IconButton(
                        onClick = {
                            if (queryText.isNotBlank()) {
                                onSendChatMessage(queryText)
                                queryText = ""
                            }
                        },
                        enabled = !isChatLoading && queryText.isNotBlank(),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (!isChatLoading && queryText.isNotBlank()) M3AccentPurple else Color.LightGray)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BouncyTextMetricCard(
    title: String,
    value: String,
    containerColor: Color,
    textColor: Color,
    isExpanded: Boolean,
    shape: RoundedCornerShape,
    valueFontSize: Int = 26,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isExpanded) 1.04f else 1.0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow),
        label = "expressiveCardBounce"
    )

    Surface(
        shape = shape,
        color = containerColor,
        tonalElevation = if (isExpanded) 8.dp else 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = textColor.copy(alpha = 0.75f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                fontSize = valueFontSize.sp,
                fontWeight = FontWeight.Black,
                color = textColor,
                lineHeight = (valueFontSize + 6).sp
            )
        }
    }
}

@Composable
fun M3ImageStyleGraphCard(
    title: String,
    nodes: List<SensorReadingNode>,
    lineColor: Color,
    fillColor: Color,
    cardBgColor: Color,
    unit: String = "cm",
    valueSelector: (SensorReadingNode) -> Float
) {
    val animateProgress = remember { Animatable(1f) }
    var selectedNodeIndex by remember { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()

    LaunchedEffect(nodes.size) {
        animateProgress.snapTo(0f)
        animateProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow)
        )
    }

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = cardBgColor,
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Black, color = lineColor)

                selectedNodeIndex?.let { index ->
                    val node = nodes.getOrNull(index)
                    if (node != null) {
                        val valToShow = valueSelector(node)
                        Surface(shape = RoundedCornerShape(12.dp), color = lineColor) {
                            Text(
                                text = "${"%.2f".format(valToShow)}$unit @ ${node.timestamp}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            val horizontalScrollState = rememberScrollState()
            val nodeCount = max(8, nodes.size)
            val dynamicCanvasWidth = max(360, nodeCount * 70).dp

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .horizontalScroll(horizontalScrollState)
            ) {
                Canvas(
                    modifier = Modifier
                        .width(dynamicCanvasWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                        .pointerInput(nodes) {
                            detectTapGestures { offset ->
                                if (nodes.isEmpty()) return@detectTapGestures
                                var minDistance = Float.MAX_VALUE
                                var closestIndex = 0

                                val extractedValues = nodes.map(valueSelector)
                                val maxVal = max(1f, extractedValues.maxOrNull() ?: 1f)
                                val minVal = min(0f, extractedValues.minOrNull() ?: 0f)
                                val valRange = max(0.1f, maxVal - minVal)
                                val stepX = size.width / max(1, nodes.size - 1)

                                nodes.forEachIndexed { idx, node ->
                                    val valNum = valueSelector(node)
                                    val x = idx.toFloat() * stepX
                                    val normY = (valNum - minVal) / valRange
                                    val y = size.height - (normY * size.height * 0.75f) - 30f

                                    val dist = (Offset(x, y) - offset).getDistance()
                                    if (dist < minDistance) {
                                        minDistance = dist
                                        closestIndex = idx
                                    }
                                }
                                if (minDistance < 70f) selectedNodeIndex = closestIndex
                            }
                        }
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    val extractedValues = nodes.map(valueSelector)
                    val rawMax = extractedValues.maxOrNull() ?: 10f
                    val rawMin = extractedValues.minOrNull() ?: 0f

                    val maxVal = if (rawMax != 0f) rawMax + (abs(rawMax) * 0.2f) else 5f
                    val minVal = if (rawMin < 0f) rawMin - (abs(rawMin) * 0.2f) else 0f
                    val valRange = max(0.1f, maxVal - minVal)

                    val stepX = canvasWidth / max(1, nodes.size - 1)

                    val gridCount = max(8, nodes.size)
                    val gridSpacing = canvasWidth / max(1, gridCount - 1)
                    for (i in 0 until gridCount) {
                        val gx = i.toFloat() * gridSpacing
                        drawLine(
                            color = Color.LightGray.copy(alpha = 0.35f),
                            start = Offset(gx, 0f),
                            end = Offset(gx, canvasHeight),
                            strokeWidth = 1.5f
                        )
                    }

                    if (nodes.isEmpty()) return@Canvas

                    val points = nodes.mapIndexed { idx, node ->
                        val valNum = valueSelector(node)
                        val x = idx.toFloat() * stepX
                        val normY = (valNum - minVal) / valRange
                        val y = canvasHeight - ((normY * canvasHeight * 0.70f + 25f) * animateProgress.value)
                        Offset(x, y)
                    }

                    val linePath = Path()
                    val fillPath = Path()

                    linePath.moveTo(points[0].x, points[0].y)
                    fillPath.moveTo(points[0].x, canvasHeight)
                    fillPath.lineTo(points[0].x, points[0].y)

                    for (i in 0 until points.size - 1) {
                        val p1 = points[i]
                        val p2 = points[i + 1]
                        val controlPoint1 = Offset(p1.x + (p2.x - p1.x) / 2f, p1.y)
                        val controlPoint2 = Offset(p1.x + (p2.x - p1.x) / 2f, p2.y)

                        linePath.cubicTo(controlPoint1.x, controlPoint1.y, controlPoint2.x, controlPoint2.y, p2.x, p2.y)
                        fillPath.cubicTo(controlPoint1.x, controlPoint1.y, controlPoint2.x, controlPoint2.y, p2.x, p2.y)
                    }

                    fillPath.lineTo(points.last().x, canvasHeight)
                    fillPath.close()

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(fillColor.copy(alpha = 0.40f), Color.Transparent)
                        )
                    )

                    drawPath(
                        path = linePath,
                        color = lineColor,
                        style = Stroke(width = 6f)
                    )

                    points.forEachIndexed { idx, pt ->
                        val isSelected = selectedNodeIndex == idx
                        val node = nodes[idx]
                        val valNum = valueSelector(node)

                        drawCircle(
                            color = if (isSelected) Color.Red else lineColor,
                            radius = if (isSelected) 9f else 5f,
                            center = pt
                        )

                        if (isSelected || idx % max(1, nodes.size / 5) == 0) {
                            drawText(
                                textMeasurer = textMeasurer,
                                text = "%.1f".format(valNum),
                                topLeft = Offset(pt.x - 12f, pt.y - 26f),
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = lineColor
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SmoothIpInputDialog(
    ipAddress: String,
    onIpChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        val animScale = remember { Animatable(0.7f) }

        LaunchedEffect(Unit) {
            animScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.65f,
                    stiffness = Spring.StiffnessLow
                )
            )
        }

        Surface(
            shape = RoundedCornerShape(32.dp),
            color = M3SurfaceHigh,
            tonalElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = animScale.value
                    scaleY = animScale.value
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    text = "Enter ESP32 Node IP",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = M3OnPastelPurple
                )

                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = onIpChange,
                    label = { Text("IP Address (e.g. 192.168.1.15)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = M3AccentPurple,
                        unfocusedBorderColor = Color.LightGray,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = M3AccentPurple)
                ) {
                    Text("Save & Connect Node", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun M3AIAnalysisButton(onAskAI: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = M3PastelPurple,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable { onAskAI() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("AI Mine Health Insights", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = M3OnPastelPurple)
        }
    }
}
