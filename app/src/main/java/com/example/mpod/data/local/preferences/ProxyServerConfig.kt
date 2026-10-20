package com.example.mpod.data.local.preferences

import java.util.UUID

data class ProxyServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 1080,
    val username: String = "",
    val password: String = ""
) {
    val addressPortDisplay: String
        get() = if (host.isNotBlank()) "$host:$port" else ""
}

fun serializeProxyServers(servers: List<ProxyServerConfig>): String {
    return servers.joinToString(prefix = "[", postfix = "]") { server ->
        """{"id":"${escapeJson(server.id)}","name":"${escapeJson(server.name)}","host":"${escapeJson(server.host)}","port":${server.port},"username":"${escapeJson(server.username)}","password":"${escapeJson(server.password)}"}"""
    }
}

fun parseProxyServersJson(json: String): List<ProxyServerConfig> {
    val trimmed = json.trim()
    if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
    val content = trimmed.substring(1, trimmed.length - 1).trim()
    if (content.isEmpty()) return emptyList()

    val result = mutableListOf<ProxyServerConfig>()
    var depth = 0
    var inString = false
    var escape = false
    var start = -1

    for (i in content.indices) {
        val c = content[i]
        if (escape) {
            escape = false
            continue
        }
        if (c == '\\') {
            escape = true
            continue
        }
        if (c == '"') {
            inString = !inString
            continue
        }
        if (!inString) {
            if (c == '{') {
                if (depth == 0) start = i
                depth++
            } else if (c == '}') {
                depth--
                if (depth == 0 && start != -1) {
                    val objStr = content.substring(start, i + 1)
                    parseProxyServerObject(objStr)?.let { result.add(it) }
                    start = -1
                }
            }
        }
    }
    return result
}

private fun parseProxyServerObject(objJson: String): ProxyServerConfig? {
    fun extractString(key: String): String {
        val regex = Regex(""""$key"\s*:\s*"((?:\\.|[^"\\])*)"""")
        val raw = regex.find(objJson)?.groupValues?.get(1) ?: return ""
        return unescapeJson(raw)
    }

    fun extractInt(key: String, default: Int): Int {
        val regex = Regex(""""$key"\s*:\s*(\d+)""")
        return regex.find(objJson)?.groupValues?.get(1)?.toIntOrNull() ?: default
    }

    val id = extractString("id")
    val name = extractString("name")
    val host = extractString("host")
    val port = extractInt("port", 1080)
    val username = extractString("username")
    val password = extractString("password")

    if (id.isBlank() && host.isBlank()) return null
    return ProxyServerConfig(
        id = id.ifBlank { UUID.randomUUID().toString() },
        name = name.ifBlank { host },
        host = host,
        port = if (port in 1..65535) port else 1080,
        username = username,
        password = password
    )
}

internal fun escapeJson(s: String): String {
    val sb = StringBuilder()
    for (c in s) {
        when (c) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '\b' -> sb.append("\\b")
            '\u000c' -> sb.append("\\f")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> sb.append(c)
        }
    }
    return sb.toString()
}

internal fun unescapeJson(s: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '\\' && i + 1 < s.length) {
            when (val next = s[i + 1]) {
                '"' -> sb.append('"')
                '\\' -> sb.append('\\')
                '/' -> sb.append('/')
                'b' -> sb.append('\b')
                'f' -> sb.append('\u000c')
                'n' -> sb.append('\n')
                'r' -> sb.append('\r')
                't' -> sb.append('\t')
                'u' -> {
                    if (i + 5 < s.length) {
                        val hex = s.substring(i + 2, i + 6)
                        hex.toIntOrNull(16)?.let { sb.append(it.toChar()) }
                        i += 4
                    }
                }
                else -> sb.append(next)
            }
            i += 2
        } else {
            sb.append(c)
            i++
        }
    }
    return sb.toString()
}
