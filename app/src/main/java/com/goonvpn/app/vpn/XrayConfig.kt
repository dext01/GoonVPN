package com.goonvpn.app.vpn

import org.json.JSONArray
import org.json.JSONObject

object XrayConfig {

    fun build(params: VlessParams, allowLan: Boolean = false, routingMode: String = "GLOBAL"): String {
        val listenAddr = if (allowLan) "0.0.0.0" else "127.0.0.1"
        val user = JSONObject().apply {
            put("id", params.userId)
            put("encryption", params.encryption.ifEmpty { "none" })
            if (params.flow.isNotEmpty()) put("flow", params.flow)
        }

        val vnext = JSONObject().apply {
            put("address", params.host)
            put("port", params.port)
            put("users", JSONArray().put(user))
        }

        val realitySettings = JSONObject().apply {
            put("serverName", params.serverName)
            put("fingerprint", "randomized")
            put("publicKey", params.publicKey)
            put("shortId", params.shortId)
            put("spiderX", params.spiderX.ifEmpty { "/" })
        }

        val streamSettings = JSONObject().apply {
            put("network", params.network.ifEmpty { "tcp" })
            put("security", params.security.ifEmpty { "reality" })
            put("realitySettings", realitySettings)
            // Фрагментирование пакетов — помогает обходить DPI
            if (params.useFragmentation) {
                put("sockopt", JSONObject().apply {
                    put("fragment", JSONObject().apply {
                        put("packets", "tlshello")
                        put("length", "100-200")
                        put("interval", "10-20")
                    })
                })
            }
        }

        val proxyOutbound = JSONObject().apply {
            put("tag", "proxy")
            put("protocol", "vless")
            put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            put("streamSettings", streamSettings)
            // MUX — мультиплексирование: несколько логических потоков в одном TCP
            if (params.useMux) {
                put("mux", JSONObject().apply {
                    put("enabled", true)
                    put("concurrency", 8)
                    put("xudpConcurrency", 8)
                    put("xudpProxyUDP443", "reject")
                })
            }
        }

        val directOutbound = JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
        }

        val blockOutbound = JSONObject().apply {
            put("tag", "block")
            put("protocol", "blackhole")
        }

        val socksInbound = JSONObject().apply {
            put("listen", listenAddr)
            put("port", 10808)
            put("protocol", "socks")
            put("settings", JSONObject().put("auth", "noauth").put("udp", true))
            put("tag", "socks-in")
            // Sniффинг — извлекаем домен из TLS/HTTP трафика для domain-based роутинга
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("destOverride", JSONArray().put("http").put("tls"))
                put("routeOnly", true)
            })
        }

        val httpInbound = JSONObject().apply {
            put("listen", listenAddr)
            put("port", 10809)
            put("protocol", "http")
            put("settings", JSONObject())
            put("tag", "http-in")
        }

        val privateIpRule = JSONObject().apply {
            put("type", "field")
            put("ip", JSONArray()
                .put("127.0.0.0/8")
                .put("10.0.0.0/8")
                .put("172.16.0.0/12")
                .put("192.168.0.0/16")
                .put("169.254.0.0/16")
                .put("fc00::/7")
                .put("fe80::/10"))
            put("outboundTag", "direct")
        }

        val ruDomainsRule = JSONObject().apply {
            put("type", "field")
            put("domain", JSONArray()
                .put("domain:ru")
                .put("domain:xn--p1ai")
                .put("domain:su")
                .put("domain:vk.com")
                .put("domain:ok.ru")
                .put("domain:yandex.ru")
                .put("domain:yandex.net")
                .put("domain:ya.ru")
                .put("domain:mail.ru")
                .put("domain:gosuslugi.ru")
                .put("domain:sberbank.ru")
                .put("domain:tinkoff.ru")
                .put("domain:ozon.ru")
                .put("domain:wildberries.ru")
                .put("domain:avito.ru"))
            put("outboundTag", "direct")
        }

        val routing = when (routingMode) {
            "GLOBAL" -> JSONObject().apply {
                put("domainStrategy", "AsIs")
                put("rules", JSONArray())
            }
            "SPLIT" -> JSONObject().apply {
                put("domainStrategy", "IPIfNonMatch")
                put("rules", JSONArray().put(privateIpRule).put(ruDomainsRule))
            }
            else -> JSONObject().apply { // BYPASS_LAN
                put("domainStrategy", "IPIfNonMatch")
                put("rules", JSONArray().put(privateIpRule))
            }
        }

        val root = JSONObject().apply {
            put("log", JSONObject().put("loglevel", "warning"))
            put("inbounds", JSONArray().put(socksInbound).put(httpInbound))
            put("outbounds", JSONArray().put(proxyOutbound).put(directOutbound).put(blockOutbound))
            put("routing", routing)
        }

        return root.toString(2)
    }

    fun parseVlessUrl(url: String): VlessParams? {
        return try {
            val withoutScheme = url.removePrefix("vless://")
            val atIndex = withoutScheme.indexOf('@')
            val userId = withoutScheme.substring(0, atIndex)
            val rest = withoutScheme.substring(atIndex + 1)
            val questionIndex = rest.indexOf('?')
            val hostPort = rest.substring(0, questionIndex)
            val colonIndex = hostPort.lastIndexOf(':')
            val host = hostPort.substring(0, colonIndex)
            val port = hostPort.substring(colonIndex + 1).toInt()
            val queryString = rest.substring(questionIndex + 1).substringBefore('#')
            val params = queryString.split('&').associate {
                val (k, v) = it.split('=', limit = 2)
                k to java.net.URLDecoder.decode(v, "UTF-8")
            }
            VlessParams(
                userId = userId,
                host = host,
                port = port,
                publicKey = params["pbk"].orEmpty(),
                shortId = params["sid"].orEmpty(),
                serverName = params["sni"] ?: host,
                fingerprint = params["fp"] ?: "chrome",
                flow = params["flow"].orEmpty(),
                spiderX = params["spx"] ?: "/",
                network = params["type"] ?: "tcp",
                security = params["security"] ?: "reality",
                encryption = params["encryption"] ?: "none"
            )
        } catch (e: Exception) {
            null
        }
    }
}

data class VlessParams(
    val userId: String,
    val host: String,
    val port: Int,
    val publicKey: String,
    val shortId: String,
    val serverName: String,
    val fingerprint: String,
    val flow: String,
    val spiderX: String,
    val network: String,
    val security: String,
    val encryption: String,
    val useMux: Boolean = false,
    val useFragmentation: Boolean = false
)
