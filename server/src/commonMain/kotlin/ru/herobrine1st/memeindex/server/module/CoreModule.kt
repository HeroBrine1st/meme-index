package ru.herobrine1st.memeindex.server.module

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.sqlx4k.ConnectionPool.Options
import io.github.smyrgeorge.sqlx4k.ValueEncoderRegistry
import io.github.smyrgeorge.sqlx4k.impl.migrate.utils.readMigrationFilesFromResources
import io.github.smyrgeorge.sqlx4k.postgres.IPostgresSQL
import io.github.smyrgeorge.sqlx4k.postgres.postgreSQL
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import kotlinx.io.bytestring.ByteString
import io.ktor.server.application.ApplicationStopped
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import ru.herobrine1st.memeindex.server.api.Embedder
import ru.herobrine1st.memeindex.server.Indexer
import ru.herobrine1st.memeindex.server.Settings
import ru.herobrine1st.memeindex.server.database.EmbeddingSchemaSync
import ru.herobrine1st.memeindex.server.loadSettings
import ru.herobrine1st.memeindex.server.repository.ByteStringEnc
import ru.herobrine1st.memeindex.server.repository.VectorEncoder
import ru.herobrine1st.memeindex.shared.client.TypedAPIClient
import ru.herobrine1st.memeindex.shared.model.Vector
import ru.herobrine1st.memeindex.shared.model.llama.endpoint.LlamaCppScope
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class Components(
    val settings: Settings,
    val db: IPostgresSQL,
    val embedder: Embedder,
)

private val ComponentsKey = AttributeKey<Components>("components")

val Application.components: Components
    get() = attributes[ComponentsKey]

private val logger = KotlinLogging.logger {}

suspend fun Application.coreModule() {
    val settings = loadSettings(environment.config)
    val db = postgreSQL(
        url = settings.dbUrl,
        username = settings.dbUsername,
        password = settings.dbPassword,
        options = Options(
            minConnections = 1,
            maxConnections = 16,
            acquireTimeout = 60.seconds,
            idleTimeout = 10.minutes,
            maxLifetime = 30.minutes,
        ),
        encoders = ValueEncoderRegistry().register<Vector>(VectorEncoder).register<ByteString>(ByteStringEnc),
    )
    val llama: TypedAPIClient<LlamaCppScope> = TypedAPIClient.of {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 600_000
            connectTimeoutMillis = 10_000
        }
    }
    val components = Components(settings, db, Embedder(llama, settings))
    attributes.put(ComponentsKey, components)

    val files = readMigrationFilesFromResources("db/migrations")
    require(files.isNotEmpty()) { "No migration files found in db/migrations" }
    db.migrate(
        files = files,
        afterFileMigration = { migration, duration ->
            logger.info { "Applied ${migration.name} in $duration" }
        },
    ).getOrThrow()

    // Align the embedding schema (typed vector columns + HNSW indexes) with the
    // configured model. Zero API calls on a steady-state startup.
    EmbeddingSchemaSync(db, settings, components.embedder).sync()

    // NOTE: launched on the Application scope itself — the module body must return
    // promptly (no `coroutineScope` wrapping) or Ktor's default 10s `startupTimeout`
    // kills the whole server.
    val indexerJob = launch {
        try {
            Indexer(settings, db, components.embedder).run(this)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.error(e) { "Indexer failed" }
        }
    }

    logger.info { "Meme Index is ready (media: ${settings.mediaPath})" }

    monitor.subscribe(ApplicationStopped) {
        indexerJob.cancel()
        runCatching { runBlocking { db.close().getOrThrow() } }
        runCatching { llama.client.close() }
    }
}
