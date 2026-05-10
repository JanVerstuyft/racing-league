package be.jabapage.racingleague.collector

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

class MainViewModel(private val context: android.content.Context) : ViewModel() {
    val udpPort = context.dataStore.data.map { it[TelemetryService.UDP_PORT] ?: 20777 }
    val localForwardEnabled = context.dataStore.data.map { it[TelemetryService.LOCAL_FORWARD_ENABLED] ?: false }
    val localForwardHost = context.dataStore.data.map { it[TelemetryService.LOCAL_FORWARD_HOST] ?: "127.0.0.1" }
    val localForwardPort = context.dataStore.data.map { it[TelemetryService.LOCAL_FORWARD_PORT] ?: 20778 }
    val cloudForwardEnabled = context.dataStore.data.map { it[TelemetryService.CLOUD_FORWARD_ENABLED] ?: false }
    val cloudUuid = context.dataStore.data.map { it[TelemetryService.CLOUD_UUID] ?: "" }
    val serviceRunning = context.dataStore.data.map { it[TelemetryService.SERVICE_RUNNING] ?: false }
    val lastError = context.dataStore.data.map { it[TelemetryService.LAST_ERROR] ?: "" }

    fun updateUdpPort(value: Int) = viewModelScope.launch { context.dataStore.edit { it[TelemetryService.UDP_PORT] = value } }
    fun updateLocalForwardEnabled(value: Boolean) = viewModelScope.launch { context.dataStore.edit { it[TelemetryService.LOCAL_FORWARD_ENABLED] = value } }
    fun updateLocalForwardHost(value: String) = viewModelScope.launch { context.dataStore.edit { it[TelemetryService.LOCAL_FORWARD_HOST] = value } }
    fun updateLocalForwardPort(value: Int) = viewModelScope.launch { context.dataStore.edit { it[TelemetryService.LOCAL_FORWARD_PORT] = value } }
    fun updateCloudForwardEnabled(value: Boolean) = viewModelScope.launch { context.dataStore.edit { it[TelemetryService.CLOUD_FORWARD_ENABLED] = value } }
    fun updateCloudUuid(value: String) = viewModelScope.launch { context.dataStore.edit { it[TelemetryService.CLOUD_UUID] = value } }

    fun getIpAddresses(): List<String> {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            return interfaces.flatMap { it.inetAddresses.asSequence() }
                .filter { !it.isLoopbackAddress && it is Inet4Address }
                .map { it.hostAddress ?: "Unknown" }
                .toList()
        } catch (e: Exception) {
            return listOf("Error: ${e.message}")
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel = remember { MainViewModel(applicationContext) }
            MainScreen(viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("F1 Telemetry Collector") }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        val serviceRunning by viewModel.serviceRunning.collectAsStateWithLifecycle(false)
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (selectedTab == 0) {
                DashboardScreen(context, viewModel, serviceRunning)
            } else {
                SettingsScreen(viewModel)
            }
        }
    }
}
@Composable
fun DashboardScreen(context: android.content.Context, viewModel: MainViewModel, serviceRunning: Boolean) {
    val ipAddresses = remember { viewModel.getIpAddresses() }
    val udpPort by viewModel.udpPort.collectAsStateWithLifecycle(20777)
    val lastError by viewModel.lastError.collectAsStateWithLifecycle("")

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Detected IP Addresses", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (ipAddresses.isEmpty()) {
            Text("Not connected", color = androidx.compose.ui.graphics.Color.Gray)
        } else {
            ipAddresses.forEach { ip ->
                Text(ip, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text("Listening on UDP Port: $udpPort", fontSize = 14.sp)

        Spacer(modifier = Modifier.height(32.dp))

        Text("Service Status", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = if (serviceRunning) "RUNNING" else "STOPPED",
            color = if (serviceRunning) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        )
        
        if (lastError.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = lastError,
                color = androidx.compose.ui.graphics.Color.Red,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                android.util.Log.e("MainActivity", "Start Button Clicked")
                try {
                    val intent = Intent(context, TelemetryService::class.java)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to start service: ${e.message}", e)
                }
            }
        ) {
            Text("Start Collector")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedButton(
            onClick = {
                android.util.Log.e("MainActivity", "Stop Button Clicked")
                context.stopService(Intent(context, TelemetryService::class.java))
            }
        ) {
            Text("Stop Collector")
        }
    }
}

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val udpPort by viewModel.udpPort.collectAsStateWithLifecycle(20777)
    val localEnabled by viewModel.localForwardEnabled.collectAsStateWithLifecycle(false)
    val localHost by viewModel.localForwardHost.collectAsStateWithLifecycle("127.0.0.1")
    val localPort by viewModel.localForwardPort.collectAsStateWithLifecycle(20778)
    val cloudEnabled by viewModel.cloudForwardEnabled.collectAsStateWithLifecycle(false)
    val cloudUuid by viewModel.cloudUuid.collectAsStateWithLifecycle("")

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Text("UDP Listener Settings", fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = udpPort.toString(),
            onValueChange = { s: String -> viewModel.updateUdpPort(s.toIntOrNull() ?: 20777) },
            label = { Text("UDP Port") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Local Forwarding", fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = localEnabled, onCheckedChange = { b: Boolean -> viewModel.updateLocalForwardEnabled(b) })
            Text("Enabled")
        }
        OutlinedTextField(
            value = localHost,
            onValueChange = { s: String -> viewModel.updateLocalForwardHost(s) },
            label = { Text("Target IP") },
            modifier = Modifier.fillMaxWidth(),
            enabled = localEnabled
        )
        OutlinedTextField(
            value = localPort.toString(),
            onValueChange = { s: String -> viewModel.updateLocalForwardPort(s.toIntOrNull() ?: 20778) },
            label = { Text("Target Port") },
            modifier = Modifier.fillMaxWidth(),
            enabled = localEnabled
        )

        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Cloud Forwarding", fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = cloudEnabled, onCheckedChange = { b: Boolean -> viewModel.updateCloudForwardEnabled(b) })
            Text("Enabled")
        }
        OutlinedTextField(
            value = cloudUuid,
            onValueChange = { s: String -> viewModel.updateCloudUuid(s) },
            label = { Text("Cloud UUID") },
            modifier = Modifier.fillMaxWidth(),
            enabled = cloudEnabled
        )
        Text("Base URL is hidden for security.", style = MaterialTheme.typography.bodySmall)
    }
}
