package com.goonvpn.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.goonvpn.app.MainActivity
import com.goonvpn.app.R
import com.goonvpn.app.data.SettingsRepository
import com.v2raytun.android.service.HevSocks5TunnelService
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

class ServiceNet : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.goonvpn.app.CONNECT"
        const val ACTION_DISCONNECT = "com.goonvpn.app.DISCONNECT"
        const val EXTRA_VLESS_URL = "vless_url"
        const val BROADCAST_STATE = "com.goonvpn.app.VPN_STATE"
        const val EXTRA_STATE = "state"
        const val STATE_CONNECTED = "connected"
        const val STATE_DISCONNECTED = "disconnected"
        const val STATE_ERROR = "error"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "goonvpn_channel"
        private const val TAG = "ServiceNet"
        private const val VPN_MTU = 1500
        private const val SOCKS_PORT = 10808
        private const val TUN_ADDR = "26.26.26.1"
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var xrayProcess: Process? = null
    private lateinit var settings: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val url = intent.getStringExtra(EXTRA_VLESS_URL) ?: return START_NOT_STICKY
                settings.lastConnectedUrl = url
                Thread { startVpn(url) }.start()
            }
            ACTION_DISCONNECT -> {
                settings.lastConnectedUrl = ""
                stopVpn()
            }
            else -> {
                // Service restarted by Android after being killed — auto-reconnect if enabled
                val savedUrl = settings.lastConnectedUrl
                if (settings.autoReconnect && savedUrl.isNotBlank()) {
                    Log.d(TAG, "Auto-reconnect with saved URL")
                    Thread { startVpn(savedUrl) }.start()
                } else {
                    stopSelf(startId)
                }
            }
        }
        return START_STICKY
    }

    private fun startVpn(vlessUrl: String) {
        try {
            startForeground(NOTIFICATION_ID, buildNotification())

            val baseParams = XrayConfig.parseVlessUrl(vlessUrl)
                ?: throw IllegalArgumentException("Invalid vless URL")

            // Применяем настройки пользователя к конфигу
            val params = baseParams.copy(
                useMux = settings.useMux,
                useFragmentation = settings.useFragmentation
            )

            Log.d(TAG, "VLESS params: host=${params.host} port=${params.port} " +
                "sni=${params.serverName} pbk=${params.publicKey} sid=${params.shortId} " +
                "fp=${params.fingerprint} flow='${params.flow}' spx=${params.spiderX} " +
                "type=${params.network} sec=${params.security} enc=${params.encryption} " +
                "mux=${params.useMux} fragment=${params.useFragmentation}")

            val config = XrayConfig.build(params, allowLan = settings.allowLan, routingMode = settings.routingMode)

            if (!startXray(config)) throw RuntimeException("Xray не запустился за 5 секунд")

            // DNS из настроек пользователя
            val (dns1, dns2) = settings.dnsServers

            val builder = Builder()
                .setSession("GoonVPN")
                .setMtu(VPN_MTU)
                .addAddress(TUN_ADDR, 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(dns1)
                .addDnsServer(dns2)
                .addDisallowedApplication(packageName)
                .apply { if (!settings.blockBindToTun) allowBypass() }

            settings.disallowedApps.forEach { pkg ->
                try {
                    builder.addDisallowedApplication(pkg)
                } catch (e: Exception) {
                    Log.w(TAG, "Skip disallowed app $pkg: ${e.message}")
                }
            }

            val iface = builder.establish()
                ?: throw RuntimeException("Не удалось создать TUN-интерфейс")
            vpnInterface = iface

            val tunFd = iface.fd
            val hevConfigPath = writeHevConfig()
            Log.d(TAG, "TUN fd=$tunFd, DNS=$dns1/$dns2, hev config: $hevConfigPath, starting TProxy")
            broadcastState(STATE_CONNECTED)
            Thread {
                Log.d(TAG, "TProxyStartService thread started")
                try {
                    HevSocks5TunnelService.TProxyStartService(hevConfigPath, tunFd)
                    Log.d(TAG, "TProxyStartService returned")
                } catch (e: Exception) {
                    Log.e(TAG, "TProxyStartService error: ${e.message}")
                }
            }.start()

        } catch (e: Exception) {
            Log.e(TAG, "VPN start failed", e)
            broadcastState(STATE_ERROR)
            stopVpnInternal()
        }
    }

    private fun startXray(configJson: String): Boolean {
        xrayProcess?.destroy()
        xrayProcess?.waitFor()
        xrayProcess = null

        val configFile = File(filesDir, "xray_config.json")
        configFile.writeText(configJson)

        val xrayBin = "${applicationInfo.nativeLibraryDir}/libxray_exec.so"
        Log.d(TAG, "Starting Xray: $xrayBin")

        xrayProcess = ProcessBuilder(xrayBin, "run", "-c", configFile.absolutePath)
            .apply { environment()["XRAY_LOCATION_ASSET"] = filesDir.absolutePath }
            .redirectErrorStream(true)
            .start()

        val xrayLogFile = File(filesDir, "xray.log").also { it.delete() }
        val logProcess = xrayProcess
        Thread {
            try {
                logProcess?.inputStream?.bufferedReader()?.use { reader ->
                    xrayLogFile.bufferedWriter().use { writer ->
                        reader.forEachLine { line ->
                            Log.d("Xray", line)
                            writer.write(line)
                            writer.newLine()
                            writer.flush()
                        }
                    }
                }
            } catch (_: Exception) { /* stream closed when process is destroyed */ }
        }.start()

        // Poll SOCKS5 port until ready (max 5 seconds)
        val deadline = System.currentTimeMillis() + 5000L
        while (System.currentTimeMillis() < deadline) {
            try {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", SOCKS_PORT), 300) }
                Log.d(TAG, "Xray SOCKS5 ready")
                return true
            } catch (_: Exception) {
                Thread.sleep(300)
            }
        }
        Log.e(TAG, "Xray SOCKS5 timeout")
        return false
    }

    private fun writeHevConfig(): String {
        val logPath = "${filesDir.absolutePath}/hev.log"
        val config = """
tunnel:
  name: tun0
  ipv4: $TUN_ADDR
  mtu: $VPN_MTU
  multi-queue: false

socks5:
  address: 127.0.0.1
  port: $SOCKS_PORT
  udp: 'udp'

misc:
  log-file: $logPath
  log-level: debug
  limit-nofile: 65535
""".trimIndent()
        val configFile = File(filesDir, "hev_config.yml")
        configFile.writeText(config)
        return configFile.absolutePath
    }

    private fun stopVpn() {
        broadcastState(STATE_DISCONNECTED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        Thread {
            stopVpnInternal()
            stopSelf()
        }.start()
    }

    private fun stopVpnInternal() {
        try { HevSocks5TunnelService.TProxyStopService() } catch (e: Exception) { Log.e(TAG, "TProxyStop: ${e.message}") }
        vpnInterface?.close()
        vpnInterface = null
        xrayProcess?.destroy()
        xrayProcess = null
    }

    private fun broadcastState(state: String) {
        // Сохраняем состояние в prefs — при перезапуске приложения ViewModel его прочитает
        val prefs = getSharedPreferences("goonvpn", Context.MODE_PRIVATE).edit()
        prefs.putString("vpn_state", state)
        if (state == STATE_CONNECTED) {
            prefs.putLong("vpn_connect_time_ms", System.currentTimeMillis())
        } else {
            prefs.remove("vpn_connect_time_ms")
        }
        prefs.apply()
        sendBroadcast(Intent(BROADCAST_STATE).apply {
            putExtra(EXTRA_STATE, state)
            setPackage(packageName)
        })
    }

    override fun onRevoke() = stopVpn()

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, ServiceNet::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GoonVPN активен")
            .setContentText("Соединение защищено")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pi)
            .addAction(0, "Отключить", stopPi)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "GoonVPN", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "Статус VPN-подключения" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
}

// dev: extended logging for debug builds
