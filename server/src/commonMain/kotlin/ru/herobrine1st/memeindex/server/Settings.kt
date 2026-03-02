package ru.herobrine1st.memeindex.server

import io.ktor.server.config.ApplicationConfig
import kotlinx.io.files.Path

data class Settings(
    val dbUrl: String,
    val dbUsername: String,
    val dbPassword: String,
    val mediaPath: Path,
    val publicBaseUrl: String,
    val ocr: Ocr,
    val embeddings: Embeddings,
) {
    data class Ocr(
        val baseUrl: String,
        val model: String,
        val apiKey: String?,
        val prompt: String,
        val temperature: Double = 0.0,
        val temperatureMax: Double = 0.7,
        val attempts: Int = 1,
    )

    data class Embeddings(
        val baseUrl: String,
        val model: String,
        val apiKey: String?,
        val dimensions: Int?, // TODO remove
        val batchSize: Int,
        val queryFormat: String,
        val documentFormat: String,
    )
}

fun ApplicationConfig.requireProperty(key: String) =
    propertyOrNull(key)?.getString() ?: error("Configuration key $key is not provided")

fun ApplicationConfig.requirePropertyAsInt(key: String) =
    requireProperty(key).toIntOrNull() ?: error("Configuration key $key is not integer")

fun ApplicationConfig.requirePropertyAsDouble(key: String) =
    requireProperty(key).toDoubleOrNull() ?: error("Configuration key $key is not double")

fun loadSettings(config: ApplicationConfig): Settings {

    val dbUrl = config.requireProperty("database.url")
    val dbUsername = config.requireProperty("database.username")
    val dbPassword = config.requireProperty("database.password")
    val mediaPath = normalizeAbsolutePath(config.requireProperty("media.path"))

    val publicBaseUrl = config.requireProperty("public.baseUrl")
        .trimEnd('/')

    val ocr = Settings.Ocr(
        baseUrl = config.requireProperty("ocr.baseUrl"),
        model = config.requireProperty("ocr.model"),
        apiKey = config.propertyOrNull("ocr.apiKey")?.getString(),
        prompt = config.requireProperty("ocr.prompt"),
        temperature = config.requirePropertyAsDouble("ocr.temperature"),
        temperatureMax = config.propertyOrNull("ocr.temperatureMax")?.getString()
            ?.let { it.toDoubleOrNull() ?: error("Configuration key ocr.temperatureMax is not a double") }
            ?: 0.7,
        attempts = config.propertyOrNull("ocr.attempts")?.getString()
            ?.let { it.toIntOrNull() ?: error("Configuration key ocr.attempts is not an integer") }
            ?: 1,
    )
    if (ocr.attempts < 1) error("Configuration key ocr.attempts must be at least 1")

    val embeddings = Settings.Embeddings(
        baseUrl = config.requireProperty("embeddings.baseUrl"),
        model = config.requireProperty("embeddings.model"),
        apiKey = config.propertyOrNull("embeddings.apiKey")?.getString(),
        dimensions = config.propertyOrNull("embeddings.dimensions")?.getString()?.toIntOrNull(),
        batchSize = config.requirePropertyAsInt("embeddings.batchSize"),
        queryFormat = config.requireProperty("embeddings.queryFormat"),
        documentFormat = config.requireProperty("embeddings.documentFormat")
    )

    return Settings(
        dbUrl = dbUrl,
        dbUsername = dbUsername,
        dbPassword = dbPassword,
        mediaPath = Path(mediaPath),
        publicBaseUrl = publicBaseUrl.trimEnd('/'),
        ocr = ocr,
        embeddings = embeddings,
    )
}
