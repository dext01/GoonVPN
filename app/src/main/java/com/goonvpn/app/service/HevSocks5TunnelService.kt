package com.goonvpn.app.service

object HevSocks5TunnelService {
    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    @JvmStatic external fun TProxyStartService(config: String, fd: Int)
    @JvmStatic external fun TProxyStopService()
    @JvmStatic external fun TProxyGetStats(): LongArray
}
