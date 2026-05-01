package com.goonvpn.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class GoonVpnApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            "goonvpn_channel",
            "GoonVPN",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
