package com.testfun

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.testfun.plugins.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configureSerialization()
        configureMonitoring()
        configureSecurity()
        configureRouting()
    }.start(wait = true)
}
