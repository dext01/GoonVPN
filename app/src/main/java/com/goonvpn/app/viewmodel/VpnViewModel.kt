package com.goonvpn.app.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goonvpn.app.model.DEFAULT_SERVERS
import com.goonvpn.app.model.Server
import com.goonvpn.app.vpn.ServiceNet
import com.goonvpn.app.vpn.XrayConfig
import com.v2raytun.android.service.HevSocks5TunnelService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetSocketAddress
import java.net.Socket

enum class VpnState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

class VpnViewModel(app: Application) : AndroidViewModel(app) {

    private val _vpnState = MutableStateFlow(VpnState.DISCONNECTED)
    val vpnState = _vpnState.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar = _snackbar.asStateFlow()

    fun snackbarShown() { _snackbar.value = null }

    private val _servers = MutableStateFlow(loadServers())
    val servers = _servers.asStateFlow()

    private val _selectedServer = MutableStateFlow(_servers.value.firstOrNull())
    val selectedServer = _selectedServer.asStateFlow()

    private val _connectionTime = MutableStateFlow("00:00:00")
    val connectionTime = _connectionTime.asStateFlow()

    private val _vlessUrl = MutableStateFlow(loadSavedUrl())
    val vlessUrl = _vlessUrl.asStateFlow()

    private val settings = com.goonvpn.app.data.SettingsRepository(app)
    private val _themeMode = MutableStateFlow(settings.themeMode)
    val themeMode = _themeMode.asStateFlow()

    // Трафик-статистика от HEV-socks5-tunnel
    private val _downloadBytes = MutableStateFlow(0L)
    val downloadBytes = _downloadBytes.asStateFlow()

    private val _uploadBytes = MutableStateFlow(0L)
    val uploadBytes = _uploadBytes.asStateFlow()

    fun setThemeMode(mode: Int) {
        _themeMode.value = mode
        settings.themeMode = mode
    }

    private var timerJob: Job? = null
    private var statsJob: Job? = null
    private var timerSeconds = 0

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getStringExtra(ServiceNet.EXTRA_STATE)) {
                ServiceNet.STATE_CONNECTED -> {
                    _vpnState.value = VpnState.CONNECTED
                    startTimer()
                    startStats()
                    pingSelectedServer()
                }
                ServiceNet.STATE_DISCONNECTED -> {
                    _vpnState.value = VpnState.DISCONNECTED
                    stopTimer()
                    stopStats()
                }
                ServiceNet.STATE_ERROR -> {
                    _vpnState.value = VpnState.ERROR
                    stopTimer()
                    stopStats()
                    viewModelScope.launch {
                        delay(2000)
                        _vpnState.value = VpnState.DISCONNECTED
                    }
                }
            }
        }
    }

    init {
        getApplication<Application>().registerReceiver(
            receiver,
            IntentFilter(ServiceNet.BROADCAST_STATE),
            Context.RECEIVER_NOT_EXPORTED
        )
        // Если VPN был активен до того как приложение убили из недавних — восстанавливаем состояние
        restoreVpnStateIfRunning()
    }

    private fun restoreVpnStateIfRunning() {
        val ctx = getApplication<Application>()
        val prefs = ctx.getSharedPreferences("goonvpn", Context.MODE_PRIVATE)
        val savedState = prefs.getString("vpn_state", "") ?: ""
        if (savedState == ServiceNet.STATE_CONNECTED && isServiceRunning()) {
            _vpnState.value = VpnState.CONNECTED
            // Вычисляем сколько секунд уже прошло с момента подключения
            val connectTimeMs = prefs.getLong("vpn_connect_time_ms", 0L)
            val elapsedSeconds = if (connectTimeMs > 0L)
                ((System.currentTimeMillis() - connectTimeMs) / 1000).toInt()
            else 0
            startTimer(elapsedSeconds)
            startStats()
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean {
        val am = getApplication<Application>()
            .getSystemService(android.app.ActivityManager::class.java)
        return am.getRunningServices(100).any {
            it.service.className == ServiceNet::class.java.name
        }
    }

    fun connect(permissionLauncher: ActivityResultLauncher<Intent>) {
        if (_vpnState.value == VpnState.ERROR) _vpnState.value = VpnState.DISCONNECTED
        val ctx = getApplication<Application>()
        val vpnIntent = VpnService.prepare(ctx)
        if (vpnIntent != null) {
            permissionLauncher.launch(vpnIntent)
        } else {
            startService()
        }
    }

    fun onPermissionGranted() = startService()

    fun onPermissionDenied() {
        _snackbar.value = "Разрешение на VPN не дано. Зайди в Настройки → Приложения → GoonVPN → Разрешения"
    }

    fun disconnect() {
        val ctx = getApplication<Application>()
        ctx.startService(Intent(ctx, ServiceNet::class.java).apply {
            action = ServiceNet.ACTION_DISCONNECT
        })
        stopTimer()
        stopStats()
        _vpnState.value = VpnState.DISCONNECTED
    }

    fun selectServer(server: Server) {
        _selectedServer.value = server
    }

    fun deleteServer(server: Server) {
        val updated = _servers.value.filterNot { it.id == server.id }
        _servers.value = updated
        if (_selectedServer.value?.id == server.id) {
            _selectedServer.value = updated.firstOrNull()
        }
        saveServers(updated)
    }

    fun addServerFromUrl(url: String) {
        val params = XrayConfig.parseVlessUrl(url) ?: return
        val newServer = Server(
            id = params.host,
            name = params.serverName,
            country = params.host,
            flag = "🌐",
            address = params.host,
            port = params.port,
            protocol = "VLESS/TCP/Reality",
            vlessUrl = url
        )
        val updated = _servers.value.filterNot { it.id == newServer.id } + newServer
        _servers.value = updated
        _selectedServer.value = newServer
        saveServers(updated)
        viewModelScope.launch { pingServer(newServer) }
    }

    fun addMultipleServers(urls: List<String>) {
        var updated = _servers.value.toMutableList()
        var lastAdded: Server? = null
        for (url in urls) {
            val params = XrayConfig.parseVlessUrl(url) ?: continue
            val server = Server(
                id = params.host,
                name = params.serverName,
                country = params.host,
                flag = "🌐",
                address = params.host,
                port = params.port,
                protocol = "VLESS/TCP/Reality",
                vlessUrl = url
            )
            updated = updated.filterNot { it.id == server.id }.toMutableList()
            updated.add(server)
            lastAdded = server
        }
        _servers.value = updated
        if (lastAdded != null && _selectedServer.value == null) {
            _selectedServer.value = lastAdded
        }
        saveServers(updated)
        viewModelScope.launch {
            updated.takeLast(urls.size).forEach { pingServer(it) }
        }
    }

    fun getVlessUrl(): String {
        val server = _selectedServer.value ?: return ""
        if (server.vlessUrl.isNotEmpty()) return server.vlessUrl
        return loadSavedUrl()
    }

    fun saveVlessUrl(url: String) {
        _vlessUrl.value = url
        val prefs = getApplication<Application>()
            .getSharedPreferences("goonvpn", Context.MODE_PRIVATE)
        prefs.edit().putString("vless_url", url).apply()
    }

    private fun startService() {
        val ctx = getApplication<Application>()
        _vpnState.value = VpnState.CONNECTING
        val url = getVlessUrl()
        if (url.isBlank()) {
            _vpnState.value = VpnState.ERROR
            return
        }
        ctx.startService(Intent(ctx, ServiceNet::class.java).apply {
            action = ServiceNet.ACTION_CONNECT
            putExtra(ServiceNet.EXTRA_VLESS_URL, url)
        })
    }

    private fun startTimer(initialSeconds: Int = 0) {
        timerJob?.cancel()
        timerSeconds = initialSeconds
        timerJob = viewModelScope.launch {
            while (isActive) {
                val h = timerSeconds / 3600
                val m = (timerSeconds % 3600) / 60
                val s = timerSeconds % 60
                _connectionTime.value = "%02d:%02d:%02d".format(h, m, s)
                delay(1000)
                timerSeconds++
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        _connectionTime.value = "00:00:00"
        timerSeconds = 0
    }

    // Опрашиваем HEV-socks5-tunnel каждую секунду — получаем байты ↑↓
    private fun startStats() {
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val stats = HevSocks5TunnelService.TProxyGetStats()
                    if (stats.size >= 2) {
                        _uploadBytes.value = stats[0]
                        _downloadBytes.value = stats[1]
                    }
                } catch (_: Throwable) { /* native lib отсутствует или VPN ещё не стартовал */ }
                delay(1000)
            }
        }
    }

    private fun stopStats() {
        statsJob?.cancel()
        _downloadBytes.value = 0L
        _uploadBytes.value = 0L
    }

    private fun pingSelectedServer() {
        val server = _selectedServer.value ?: return
        viewModelScope.launch { pingServer(server) }
    }

    // Исправлен resource leak: socket закрывается через .use {}
    private suspend fun pingServer(server: Server) {
        val ms = withContext(Dispatchers.IO) {
            try {
                val start = System.currentTimeMillis()
                Socket().use { it.connect(InetSocketAddress(server.address, server.port), 3000) }
                (System.currentTimeMillis() - start).toInt()
            } catch (e: Exception) { -1 }
        }
        val updated = _servers.value.map {
            if (it.id == server.id) it.copy(pingMs = ms) else it
        }
        _servers.value = updated
        if (_selectedServer.value?.id == server.id) {
            _selectedServer.value = _selectedServer.value?.copy(pingMs = ms)
        }
    }

    fun pingAllServers() {
        viewModelScope.launch {
            _servers.value.forEach { pingServer(it) }
        }
    }

    private fun loadSavedUrl(): String {
        return getApplication<Application>()
            .getSharedPreferences("goonvpn", Context.MODE_PRIVATE)
            .getString("vless_url", "") ?: ""
    }

    private fun loadServers(): List<Server> {
        val prefs = getApplication<Application>()
            .getSharedPreferences("goonvpn", Context.MODE_PRIVATE)
        val saved = prefs.getString("saved_servers", "") ?: ""
        if (saved.isBlank()) return DEFAULT_SERVERS
        return saved.split("|").mapNotNull { url ->
            val params = com.goonvpn.app.vpn.XrayConfig.parseVlessUrl(url) ?: return@mapNotNull null
            Server(
                id = params.host,
                name = params.serverName,
                country = params.host,
                flag = "🌐",
                address = params.host,
                port = params.port,
                protocol = "VLESS/TCP/Reality",
                vlessUrl = url
            )
        }
    }

    private fun saveServers(servers: List<Server>) {
        val prefs = getApplication<Application>()
            .getSharedPreferences("goonvpn", Context.MODE_PRIVATE)
        val urls = servers.filter { it.vlessUrl.isNotEmpty() }
            .joinToString("|") { it.vlessUrl }
        prefs.edit().putString("saved_servers", urls).apply()
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unregisterReceiver(receiver)
    }
}

// fix: android 14 broadcast compatibility
