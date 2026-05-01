package com.goonvpn.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.goonvpn.app.ui.screens.*
import com.goonvpn.app.ui.theme.*
import com.goonvpn.app.viewmodel.VpnViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GoonVPNTheme {
                MainNavigation()
            }
        }
    }
}

private data class TabItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val TABS = listOf(
    TabItem("home",     "Главная",   Icons.Filled.Home),
    TabItem("proxy",    "Прокси",    Icons.Filled.Wifi),
    TabItem("profiles", "Профили",   Icons.Filled.Person),
    TabItem("settings", "Настройки", Icons.Filled.Settings)
)

// Экраны, у которых не показываем bottom bar
private val SUB_ROUTES = setOf("apps", "profile", "logs")

@Composable
private fun MainNavigation(vm: VpnViewModel = viewModel()) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    val vpnState       by vm.vpnState.collectAsStateWithLifecycle()
    val servers        by vm.servers.collectAsStateWithLifecycle()
    val selectedServer by vm.selectedServer.collectAsStateWithLifecycle()
    val connectionTime by vm.connectionTime.collectAsStateWithLifecycle()
    val vlessUrl       by vm.vlessUrl.collectAsStateWithLifecycle()
    val themeMode      by vm.themeMode.collectAsStateWithLifecycle()
    val downloadBytes  by vm.downloadBytes.collectAsStateWithLifecycle()
    val uploadBytes    by vm.uploadBytes.collectAsStateWithLifecycle()

    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        1    -> false
        2    -> true
        else -> systemDark
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) vm.onPermissionGranted()
    }

    val showBottomBar = currentRoute !in SUB_ROUTES

    val navContainer = if (isDark) Color(0xFF0D1B2A) else Surface
    val navSelected  = if (isDark) Color(0xFF90CAF9) else AccentBlue
    val navUnsel     = if (isDark) Color(0xFF546E7A)  else TextHint
    val navIndicator = if (isDark) Color(0xFF1E3A5F)  else AccentBlue.copy(0.12f)

    Scaffold(
        containerColor = if (isDark) Color(0xFF06111C) else Background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = navContainer, tonalElevation = 0.dp) {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, null) },
                            label = {
                                Text(tab.label, fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.labelSmall)
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor   = navSelected,
                                selectedTextColor   = navSelected,
                                unselectedIconColor = navUnsel,
                                unselectedTextColor = navUnsel,
                                indicatorColor      = navIndicator
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController    = navController,
            startDestination = "home",
            modifier         = Modifier.padding(padding)
        ) {
            composable("home") {
                HomeScreen(
                    vpnState       = vpnState,
                    servers        = servers,
                    selectedServer = selectedServer,
                    connectionTime = connectionTime,
                    downloadBytes  = downloadBytes,
                    uploadBytes    = uploadBytes,
                    isDark         = isDark,
                    onConnect      = { vm.connect(permissionLauncher) },
                    onDisconnect   = { vm.disconnect() },
                    onServerSelect = { vm.selectServer(it) },
                    onServerAdded  = { vm.addServerFromUrl(it) },
                    onDeleteServer = { vm.deleteServer(it) },
                    onSettingsClick = { navController.navigate("settings") }
                )
            }
            composable("proxy") {
                ProxyScreen(
                    servers              = servers,
                    selectedServer       = selectedServer,
                    isDark               = isDark,
                    onServerSelect       = { vm.selectServer(it) },
                    onServerAdded        = { vm.addServerFromUrl(it) },
                    onMultipleServersAdded = { vm.addMultipleServers(it) },
                    onDeleteServer       = { vm.deleteServer(it) },
                    onPingAll            = { vm.pingAllServers() }
                )
            }
            composable("profiles") {
                ProfilesListScreen(
                    vlessUrl      = vlessUrl,
                    isDark        = isDark,
                    onEditProfile = { navController.navigate("profile") }
                )
            }
            composable("profile") {
                ProfileScreen(
                    vlessUrl = vlessUrl,
                    onSave   = { vm.saveVlessUrl(it) },
                    onBack   = { navController.popBackStack() }
                )
            }
            composable("settings") {
                SettingsScreen(
                    themeMode   = themeMode,
                    onThemeMode = { vm.setThemeMode(it) },
                    onBack      = { navController.popBackStack() },
                    onOpenApps  = { navController.navigate("apps") },
                    onOpenLogs  = { navController.navigate("logs") }
                )
            }
            composable("apps") {
                AppsScreen(onBack = { navController.popBackStack() })
            }
            composable("logs") {
                LogViewerScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
