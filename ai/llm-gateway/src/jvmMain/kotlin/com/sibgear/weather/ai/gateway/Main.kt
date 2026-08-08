package com.sibgear.weather.ai.gateway

import java.nio.file.Path
import kotlin.io.path.Path

public fun main(args: Array<String>) {
    if ("--live-smoke" in args) {
        runLiveSmoke()
        return
    }
    val config = GatewayConfig.fromEnvironment()
    val apiKey = DeepSeekApiKeyLoader.load(config.keysFile)
        ?: error("DEEPSEEK_API_KEY or deepseek_api_key in ${config.keysFile} is required")
    val repository = GatewayRepository.open(config.databasePath)
    repository.recoverInterrupted(System.currentTimeMillis())
    Runtime.getRuntime().addShutdownHook(Thread(repository::close))
    val service = GatewayService(repository, DeepSeekProvider(apiKey = apiKey, model = config.model))
    println("llm_gateway host=${config.host} port=${config.port} database=${config.databasePath} model=${config.model}")
    startGatewayServer(service, config.host, config.port, wait = true)
}

internal data class GatewayConfig(
    val host: String,
    val port: Int,
    val databasePath: Path,
    val keysFile: Path,
    val model: String,
) {

    companion object {

        fun fromEnvironment(): GatewayConfig = GatewayConfig(
            host = System.getenv("LLM_GATEWAY_HOST") ?: "127.0.0.1",
            port = System.getenv("LLM_GATEWAY_PORT")?.toIntOrNull() ?: 18090,
            databasePath = Path(System.getenv("LLM_GATEWAY_DB") ?: "ai_training/day13/runtime/llm-gateway.sqlite"),
            keysFile = Path(System.getenv("LLM_GATEWAY_KEYS_FILE") ?: ".keys.txt"),
            model = System.getenv("DEEPSEEK_MODEL") ?: "deepseek-v4-flash",
        )
    }
}
