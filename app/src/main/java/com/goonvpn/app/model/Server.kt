package com.goonvpn.app.model

data class Server(
    val id: String,
    val name: String,
    val country: String,
    val flag: String,
    val address: String,
    val port: Int,
    val protocol: String = "VLESS/TCP/Reality",
    val vlessUrl: String = "",
    val pingMs: Int = -1
)

val DEFAULT_SERVERS = emptyList<Server>()
