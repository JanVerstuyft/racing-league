package be.jabapage.racingleague.collector

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

val Context.dataStore by preferencesDataStore(name = "settings")

class TelemetryService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    private var socket: DatagramSocket? = null
    private var running = false
    
    private val client = HttpClient(OkHttp)
    private val baseUrl = "https://racingleague.jabapage.be/api/telemetry/"

    override fun onCreate() {
        super.onCreate()
        android.util.Log.e("TelemetryService", "Service onCreate called")
    }

    companion object {
        const val CHANNEL_ID = "TelemetryServiceChannel"
        const val NOTIFICATION_ID = 1
        
        // Settings Keys
        val UDP_PORT = intPreferencesKey("udp_port")
        val LOCAL_FORWARD_ENABLED = booleanPreferencesKey("local_forward_enabled")
        val LOCAL_FORWARD_HOST = stringPreferencesKey("local_forward_host")
        val LOCAL_FORWARD_PORT = intPreferencesKey("local_forward_port")
        val CLOUD_FORWARD_ENABLED = booleanPreferencesKey("cloud_forward_enabled")
        val CLOUD_UUID = stringPreferencesKey("cloud_uuid")
        val SERVICE_RUNNING = booleanPreferencesKey("service_running")
        val LAST_ERROR = stringPreferencesKey("last_error")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.e("TelemetryService", "onStartCommand called")
        createNotificationChannel()
        val notification = createNotification("Listening for telemetry...")
        startForeground(NOTIFICATION_ID, notification)
        
        if (!running) {
            running = true
            serviceScope.launch {
                dataStore.edit { 
                    it[SERVICE_RUNNING] = true 
                    it[LAST_ERROR] = ""
                }
                listen()
            }
        }
        
        return START_STICKY
    }

    private suspend fun listen() {
        val settings = dataStore.data.first()
        val port = settings[UDP_PORT] ?: 20777
        android.util.Log.e("TelemetryService", "Starting listener on port $port")
        val localEnabled = settings[LOCAL_FORWARD_ENABLED] ?: false
        val localHost = settings[LOCAL_FORWARD_HOST] ?: "127.0.0.1"
        val localPort = settings[LOCAL_FORWARD_PORT] ?: 20778
        val cloudEnabled = settings[CLOUD_FORWARD_ENABLED] ?: false
        val cloudUuid = settings[CLOUD_UUID] ?: ""

        try {
            socket = DatagramSocket(port)
            val buffer = ByteArray(2048)
            val forwardSocket = if (localEnabled) DatagramSocket() else null
            val forwardAddress = if (localEnabled) InetAddress.getByName(localHost) else null

            while (running) {
                val packet = DatagramPacket(buffer, buffer.size)
                socket?.receive(packet)
                
                android.util.Log.e("TelemetryService", "Received packet of size ${packet.length}")
                val data = packet.data.copyOfRange(0, packet.length)
                
                // Local Forwarding
                if (localEnabled && forwardAddress != null && forwardSocket != null) {
                    val forwardPacket = DatagramPacket(data, data.size, forwardAddress, localPort)
                    forwardSocket.send(forwardPacket)
                }
                
                // Cloud Forwarding (Filtered)
                if (cloudEnabled && cloudUuid.isNotEmpty() && data.size > 6) {
                    val packetId = data[6].toInt() and 0xFF
                    val shouldForward = when (packetId) {
                        1, 2, 3, 4, 7, 8, 10 -> true
                        else -> false
                    }
                    
                    if (shouldForward) {
                        val url = baseUrl + cloudUuid
                        serviceScope.launch {
                            try {
                                val response = client.post(url) {
                                    setBody(data)
                                }
                                if (response.status.value !in 200..299) {
                                    val err = "Server returned ${response.status}"
                                    android.util.Log.e("TelemetryService", err)
                                    dataStore.edit { it[LAST_ERROR] = err }
                                }
                            } catch (e: Exception) {
                                val err = "${e.javaClass.simpleName}: ${e.message ?: "No message"}"
                                android.util.Log.e("TelemetryService", "Cloud error: $err", e)
                                dataStore.edit { it[LAST_ERROR] = err }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            val err = "Listener error: ${e.message}"
            android.util.Log.e("TelemetryService", err, e)
            dataStore.edit { it[LAST_ERROR] = err }
            running = false
        } finally {
            android.util.Log.i("TelemetryService", "Listener stopped")
            socket?.close()
            dataStore.edit { it[SERVICE_RUNNING] = false }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Telemetry Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(content: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Telemetry Collector")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        android.util.Log.d("TelemetryService", "onDestroy called")
        running = false
        socket?.close()
        serviceScope.launch {
            dataStore.edit { it[SERVICE_RUNNING] = false }
            serviceJob.cancel()
        }
        super.onDestroy()
    }
}
