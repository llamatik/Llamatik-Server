package com.llamatik

import com.llamatik.plugins.configureAdministration
import com.llamatik.plugins.configureHTTP
import com.llamatik.plugins.configureMonitoring
import com.llamatik.plugins.configureSerialization
import com.llamatik.plugins.configureTemplating
import io.ktor.server.engine.embeddedServer
import io.ktor.server.tomcat.jakarta.Tomcat

fun main() {
    embeddedServer(Tomcat, port = SERVER_PORT, host = "0.0.0.0") {
        // Must run before configureGeneralRouting(): the server library only installs
        // ContentNegotiation if the host hasn't already.
        configureSerialization()
        configureSessions()
        configureGeneralRouting()
        configureAuthentication()
        configureHTTP()
        configureMonitoring()
        configureTemplating()
        configureAdministration()
        configureStartupLoading()
        configureDiscovery(port = SERVER_PORT)
    }.start(wait = true)
}

const val SERVER_PORT = 8080
