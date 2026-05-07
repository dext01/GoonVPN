package com.goonvpn.app.data

import android.content.Context
import android.content.SharedPreferences

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var lastConnectedUrl: String
        get() = prefs.getString(KEY_LAST_URL, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_LAST_URL, v).apply()

    var autoReconnect: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RECONNECT, true)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_RECONNECT, v).apply()

    var disallowedApps: Set<String>
        get() = prefs.getStringSet(KEY_DISALLOWED_APPS, emptySet()).orEmpty()
        set(v) = prefs.edit().putStringSet(KEY_DISALLOWED_APPS, v).apply()

    var useMux: Boolean
        get() = prefs.getBoolean(KEY_MUX, false)
        set(v) = prefs.edit().putBoolean(KEY_MUX, v).apply()

    var useFragmentation: Boolean
        get() = prefs.getBoolean(KEY_FRAGMENT, false)
        set(v) = prefs.edit().putBoolean(KEY_FRAGMENT, v).apply()

    var useXrayTun: Boolean
        get() = prefs.getBoolean(KEY_XRAY_TUN, false)
        set(v) = prefs.edit().putBoolean(KEY_XRAY_TUN, v).apply()

    var blockBindToTun: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_BIND, false)
        set(v) = prefs.edit().putBoolean(KEY_BLOCK_BIND, v).apply()

    var allowLan: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_LAN, false)
        set(v) = prefs.edit().putBoolean(KEY_ALLOW_LAN, v).apply()

    // 0 = system, 1 = light, 2 = dark
    var themeMode: Int
        get() = prefs.getInt(KEY_THEME, 0)
        set(v) = prefs.edit().putInt(KEY_THEME, v).apply()

    // GLOBAL / BYPASS_LAN / SPLIT
    var routingMode: String
        get() = prefs.getString(KEY_ROUTING_MODE, "GLOBAL").orEmpty()
        set(v) = prefs.edit().putString(KEY_ROUTING_MODE, v).apply()

    // 0=Google(8.8.8.8), 1=Cloudflare(1.1.1.1), 2=AdGuard(94.140.14.14)
    var dnsChoice: Int
        get() = prefs.getInt(KEY_DNS_CHOICE, 0)
        set(v) = prefs.edit().putInt(KEY_DNS_CHOICE, v).apply()

    val dnsServers: Pair<String, String>
        get() = when (dnsChoice) {
            1    -> "1.1.1.1" to "1.0.0.1"
            2    -> "94.140.14.14" to "94.140.15.15"
            else -> "8.8.8.8" to "8.8.4.4"
        }

    fun resetAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS               = "goonvpn"
        private const val KEY_LAST_URL         = "last_connected_url"
        private const val KEY_AUTO_RECONNECT   = "auto_reconnect"
        private const val KEY_DISALLOWED_APPS  = "disallowed_apps"
        private const val KEY_MUX              = "use_mux"
        private const val KEY_FRAGMENT         = "use_fragment"
        private const val KEY_XRAY_TUN         = "use_xray_tun"
        private const val KEY_BLOCK_BIND       = "block_bind_to_tun"
        private const val KEY_ALLOW_LAN        = "allow_lan"
        private const val KEY_THEME            = "theme_mode"
        private const val KEY_DNS_CHOICE       = "dns_choice"
        private const val KEY_ROUTING_MODE     = "routing_mode"
    }
}

// settings version: 2
