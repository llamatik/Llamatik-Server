package com.llamatik

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

private const val MDNS_SERVICE_TYPE = "_llamatik._tcp.local."

/**
 * Advertises the server on the local network as an mDNS `_llamatik._tcp` service so that
 * Llamatik apps can find it from Settings → Server → Scan.
 *
 * | Variable            | Default            | Notes                                  |
 * |---------------------|--------------------|----------------------------------------|
 * | MDNS_ENABLED        | `true`             | Set to `false` to disable advertising  |
 * | MDNS_SERVICE_NAME   | machine hostname   | Name displayed in the app              |
 *
 * Failures are logged but never abort startup.
 */
fun Application.configureDiscovery(port: Int) {
    if (System.getenv("MDNS_ENABLED")?.equals("false", ignoreCase = true) == true) {
        log.info("mDNS advertising disabled (MDNS_ENABLED=false)")
        return
    }

    var mdns: JmDNS? = null
    var info: ServiceInfo? = null

    monitor.subscribe(ApplicationStarted) { app ->
        @Suppress("TooGenericExceptionCaught")
        try {
            val name = System.getenv("MDNS_SERVICE_NAME")?.takeIf { it.isNotBlank() }
                ?: runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("Llamatik Server")
            val instance = JmDNS.create()
            val service = ServiceInfo.create(MDNS_SERVICE_TYPE, name, port, "Llamatik server")
            instance.registerService(service)
            mdns = instance
            info = service
            app.log.info("Advertising '$name' on the local network as $MDNS_SERVICE_TYPE (port $port)")
        } catch (e: Exception) {
            app.log.warn("Could not start mDNS advertising: ${e.message}")
        }
    }

    monitor.subscribe(ApplicationStopped) {
        @Suppress("TooGenericExceptionCaught")
        try {
            info?.let { mdns?.unregisterService(it) }
            mdns?.close()
        } catch (e: Exception) {
            log.warn("Error stopping mDNS advertising: ${e.message}")
        }
        mdns = null
        info = null
    }
}
