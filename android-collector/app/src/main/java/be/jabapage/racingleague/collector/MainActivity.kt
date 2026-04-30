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

class MainViewModel(private val context: android.content.Context) : ViewModel() {
    val udpPort = context.dataStore.data.map { it[TelemetryService.UDP_PORT] ?: 20777 }
    val localForwardEnabled = context.dataStore.data.map { it[TelemetryService.LOCAL_FORWARD_ENABLED] ?: false }
    val localForwardHost = context.dataStore.data.map { it[TelemetryService.LOCAL_FORWARD_HOST] ?: "127.0.0.1" }
    val localForwardPort = context.dataStore.data.map { it[TelemetryService.LOCAL_FORWARD_PORT] ?: 20778 }
    val cloudForwardEnabled = context.dataStore.data.map { it[TelemetryService.CLOUD_FORWARD_ENABLED] ?: false }
    val cloudUuid = context.dataStore.data.map { it[TelemetryService.CLOUD_UUID] ?: "" }

    fun updateUdpPort(value: Int) = viewModelScope.launch { context.dataStore.edit { it[TelemetryService.UDP_PORT] = value } }
    fun updateLocalForwardEnabled(value: Boolean) = viewModelScope.launch { context.dataStore.edit { it[TelemetryService.LOCAL_FORWARD_ENABLED] = value } }
    fun updateLocalForwardHost(value: String) = viewModelScope.launch { context.dataStore.edit { it[TelemetryService.LOCAL_FORWARD_HOST] = value } }
    fun updateLocalForwardPort(value: Int) = viewModelScope.launch { context.dataStore.edit { it[TelemetryService.LOCAL_FORWARD_PORT] = value } }
    fun updateCloudForwardEnabled(value: Boolean) = viewModelScope.launch { context.dataStore.edit { it[TelemetryService.CLOUD_FORWARD_ENABLED] = value } }
    fun updateCloudUuid(value: String) = viewModelScope.launch { context.dataStore.edit { it[TelemetryService.CLOUD_UUID] = value } }
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
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (selectedTab == 0) {
                DashboardScreen(context)
            } else {
                SettingsScreen(viewModel)
            }
        }
    }
}

@Composable
fun DashboardScreen(context: android.content.Context) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Service Status", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(onClick = {
            val intent = Intent(context, TelemetryService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }) {
            Text("Start Collector")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedButton(onClick = {
            context.stopService(Intent(context, TelemetryService::class.java))
        }) {
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
            onValueChange = { viewModel.updateUdpPort(it.toIntOrNull() ?: 20777) },
            label = { Text("UDP Port") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Local Forwarding", fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = localEnabled, onCheckedChange = { viewModel.updateLocalForwardEnabled(it) })
            Text("Enabled")
        }
        OutlinedTextField(
            value = localHost,
            onValueChange = { viewModel.updateLocalForwardHost(it) },
            label = { Text("Target IP") },
            modifier = Modifier.fillMaxWidth(),
            enabled = localEnabled
        )
        OutlinedTextField(
            value = localPort.toString(),
            onValueChange = { viewModel.updateLocalForwardPort(it.toIntOrNull() ?: 20778) },
            label = { Text("Target Port") },
            modifier = Modifier.fillMaxWidth(),
            enabled = localEnabled
        )

        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Cloud Forwarding", fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = cloudEnabled, onCheckedChange = { viewModel.updateCloudForwardEnabled(it) })
            Text("Enabled")
        }
        OutlinedTextField(
            value = cloudUuid,
            onValueChange = { viewModel.updateCloudUuid(it) },
            label = { Text("Cloud UUID") },
            modifier = Modifier.fillMaxWidth(),
            enabled = cloudEnabled
        )
        Text("Base URL is hidden for security.", style = MaterialTheme.typography.bodySmall)
    }
}
