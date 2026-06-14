package at.aau.pulverfass.server

import at.aau.pulverfass.server.ids.IdFactory
import at.aau.pulverfass.server.lobby.mapping.DefaultNetworkToLobbyEventMapper
import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.server.logging.LobbyDomainEventLogger
import at.aau.pulverfass.server.logging.ServerLoggers
import at.aau.pulverfass.server.map.ClasspathMapDefinitionRepository
import at.aau.pulverfass.server.persistence.DatabaseBackedLobbyPersistenceGateway
import at.aau.pulverfass.server.persistence.JdbcLobbyPersistenceStore
import at.aau.pulverfass.server.persistence.JdbcLobbyReconnectSessionStore
import at.aau.pulverfass.server.persistence.LobbyPersistenceCallbacks
import at.aau.pulverfass.server.persistence.LobbyRecoveryLoader
import at.aau.pulverfass.server.routing.MainServerLobbyRoutingService
import at.aau.pulverfass.server.routing.MainServerRouter
import at.aau.pulverfass.server.session.SessionContextPersistenceHooks
import at.aau.pulverfass.server.session.SessionContextRegistry
import at.aau.pulverfass.server.transport.ServerWebSocketTransport
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.SessionToken
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.GameStatus
import at.aau.pulverfass.shared.network.Network
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicLong

internal const val DEFAULT_HOST = "0.0.0.0"
internal const val DEFAULT_PORT = 8080
private val runtimeLogger = ServerLoggers.technical("Runtime")
private const val SERVER_MODE_ENV = "SERVER_MODE"
private const val SERVER_MODE_MIGRATE = "migrate"
private val applicationLogger = ServerLoggers.technical("Application")
private val webSocketLogger = ServerLoggers.technical("WebSocketEndpoint")

/**
 * Startet den eingebetteten Ktor-Server mit der Standardkonfiguration.
 */
fun main() {
    val runtimeConfig = ServerRuntimeConfig.fromEnvironment()
    applicationLogger.info(
        "Starting Pulverfass server host={} port={}",
        runtimeConfig.host,
        runtimeConfig.port,
    )
    prepareServerEngine(runtimeConfig)?.start(wait = true)
}

internal fun prepareServerEngine(
    runtimeConfig: ServerRuntimeConfig,
    serverMode: String = System.getenv(SERVER_MODE_ENV)?.trim()?.lowercase().orEmpty(),
    migrationRunner: (DatabaseRuntimeConfig) -> Unit = ::migrateDatabaseSchema,
    persistenceCallbacksFactory: (
        DatabaseRuntimeConfig,
    ) -> LobbyPersistenceCallbacks = ::createLobbyPersistenceCallbacks,
    databaseReadinessProbeFactory: (
        DatabaseRuntimeConfig,
        LobbyPersistenceCallbacks,
    ) -> DatabaseReadinessProbe =
        { databaseConfig, persistenceCallbacks ->
            CompositeDatabaseReadinessProbe(
                createDatabaseReadinessProbe(databaseConfig),
                persistenceCallbacks,
            )
        },
    serverFactory: (
        String,
        Int,
        ServerNetwork,
        ServerRuntimeConfig,
        DatabaseReadinessProbe,
        LobbyPersistenceCallbacks,
    ) -> ApplicationEngine = ::createServerWithLobbyRuntime,
): ApplicationEngine? {
    logDeploymentInfo(runtimeConfig)
    migrationRunner(runtimeConfig.database)
    if (serverMode == SERVER_MODE_MIGRATE) {
        runtimeLogger.info(
            "Completed migration-only startup for version {}.",
            runtimeConfig.appVersion,
        )
        return null
    }
    val persistenceCallbacks = persistenceCallbacksFactory(runtimeConfig.database)
    val databaseReadinessProbe =
        databaseReadinessProbeFactory(runtimeConfig.database, persistenceCallbacks)
    logDatabaseReadiness(databaseReadinessProbe.readiness())
    runtimeLogger.info(
        "Starting websocket server on {}:{} (version: {}, database env configured: {})",
        runtimeConfig.host,
        runtimeConfig.port,
        runtimeConfig.appVersion,
        runtimeConfig.database.isConfigured,
    )
    return serverFactory(
        runtimeConfig.host,
        runtimeConfig.port,
        ServerNetwork(),
        runtimeConfig,
        databaseReadinessProbe,
        persistenceCallbacks,
    )
}

/**
 * Erzeugt eine startbare Serverinstanz für das Servermodul.
 *
 * Für die Integration soll ausschließlich [ServerNetwork] injiziert werden.
 * Dadurch bleibt die High-Level-Netzwerk-API der einzige öffentliche Einstieg.
 *
 * @param host Hostadresse des eingebetteten Servers
 * @param port Zielport des eingebetteten Servers
 * @param network serverseitige Netzwerkkomposition für `/ws`
 */
fun createServer(
    host: String = DEFAULT_HOST,
    port: Int = DEFAULT_PORT,
    network: ServerNetwork = ServerNetwork(),
    runtimeConfig: ServerRuntimeConfig = ServerRuntimeConfig.fromEnvironment(),
    databaseReadinessProbe: DatabaseReadinessProbe = DatabaseReadinessProbe.disabled(),
): ApplicationEngine =
    embeddedServer(
        factory = Netty,
        host = host,
        port = port,
    ) {
        module(network, runtimeConfig, databaseReadinessProbe)
    }

fun createServer(
    host: String = DEFAULT_HOST,
    port: Int = DEFAULT_PORT,
    transport: ServerWebSocketTransport,
    runtimeConfig: ServerRuntimeConfig = ServerRuntimeConfig.fromEnvironment(),
    databaseReadinessProbe: DatabaseReadinessProbe = DatabaseReadinessProbe.disabled(),
): ApplicationEngine =
    createServer(
        host = host,
        port = port,
        network = ServerNetwork(transport = transport),
        runtimeConfig = runtimeConfig,
        databaseReadinessProbe = databaseReadinessProbe,
    )

/**
 * Erzeugt eine startbare Serverinstanz mit aktivem Lobby-Routing zur
 * Verarbeitung von Create/Join/Leave/Kick/Start-Requests.
 */
fun createServerWithLobbyRuntime(
    host: String = DEFAULT_HOST,
    port: Int = DEFAULT_PORT,
    network: ServerNetwork = ServerNetwork(),
    runtimeConfig: ServerRuntimeConfig = ServerRuntimeConfig.fromEnvironment(),
    databaseReadinessProbe: DatabaseReadinessProbe = DatabaseReadinessProbe.disabled(),
    persistenceCallbacks: LobbyPersistenceCallbacks = LobbyPersistenceCallbacks.disabled(),
): ApplicationEngine =
    embeddedServer(
        factory = Netty,
        host = host,
        port = port,
    ) {
        moduleWithLobbyRuntime(
            network = network,
            runtimeConfig = runtimeConfig,
            databaseReadinessProbe = databaseReadinessProbe,
            persistenceCallbacks = persistenceCallbacks,
        )
    }

/**
 * Konfiguriert die Ktor-Anwendung mit WebSocket-Unterstützung auf `/ws`.
 *
 * Der Endpunkt delegiert den kompletten Verbindungslebenszyklus an
 * [ServerNetwork]. Text Frames werden gemäß [WebSocketPolicy] aktiv abgelehnt.
 *
 * @param network serverseitige Netzwerkkomposition für die WebSocket-Route
 */
fun Application.module(
    network: ServerNetwork = ServerNetwork(),
    runtimeConfig: ServerRuntimeConfig = ServerRuntimeConfig.fromEnvironment(),
    databaseReadinessProbe: DatabaseReadinessProbe = DatabaseReadinessProbe.disabled(),
) {
    val moduleStartedAtEpochMillis = System.currentTimeMillis()

    install(WebSockets) {
        pingPeriodMillis = WebSocketPolicy.PING_PERIOD_MILLIS
        timeoutMillis = WebSocketPolicy.TIMEOUT_MILLIS
        maxFrameSize = runtimeConfig.webSocketMaxFrameSizeBytes
        masking = false
    }

    routing {
        get("/health") {
            call.respondText("OK", status = HttpStatusCode.OK)
        }
        get("/version") {
            call.respondText(runtimeConfig.appVersion, status = HttpStatusCode.OK)
        }
        get("/ready") {
            val readiness = databaseReadinessProbe.readiness()
            val status =
                if (readiness.isReady) {
                    HttpStatusCode.OK
                } else {
                    HttpStatusCode.ServiceUnavailable
                }
            call.respondText(
                formatReadinessResponse(runtimeConfig.appVersion, readiness),
                status = status,
            )
        }
        get("/metrics") {
            val remoteHost = call.request.local.remoteHost
            if (!isInternalMetricsRequest(remoteHost)) {
                call.respondText("Forbidden", status = HttpStatusCode.Forbidden)
                return@get
            }

            call.respondText(
                formatMetricsResponse(
                    runtimeConfig = runtimeConfig,
                    readiness = databaseReadinessProbe.readiness(),
                    startedAtEpochMillis = moduleStartedAtEpochMillis,
                    nowEpochMillis = System.currentTimeMillis(),
                ),
                contentType = ContentType.Text.Plain,
                status = HttpStatusCode.OK,
            )
        }
        webSocket("/ws") {
            handleWebSocketConnection(network)
        }
    }

    environment.monitor.subscribe(ApplicationStopped) {
        databaseReadinessProbe.close()
    }
}

/**
 * Produktionsverdrahtung mit aktiver Lobby-Routing-Pipeline.
 *
 * Im Gegensatz zu [module] wird hier zusätzlich die Routing-Service-Schicht
 * gestartet, damit Create/Join/Leave-Requests tatsächlich in den Lobby-Layer
 * gelangen und Antworten an Clients zurückfließen.
 */
fun Application.moduleWithLobbyRuntime(
    network: ServerNetwork = ServerNetwork(),
    runtimeConfig: ServerRuntimeConfig = ServerRuntimeConfig.fromEnvironment(),
    databaseReadinessProbe: DatabaseReadinessProbe = DatabaseReadinessProbe.disabled(),
    persistenceCallbacks: LobbyPersistenceCallbacks = LobbyPersistenceCallbacks.disabled(),
) {
    module(network, runtimeConfig, databaseReadinessProbe)
    installLobbyRuntime(network, runtimeConfig, persistenceCallbacks)
}

/**
 * Test-Hilfsmethode für direkte Transporttests ohne High-Level-Eventpfad.
 */
internal fun Application.module(transport: ServerWebSocketTransport) {
    module(ServerNetwork(transport = transport))
}

internal fun createDatabaseReadinessProbe(config: DatabaseRuntimeConfig): DatabaseReadinessProbe =
    if (config.isConfigured) {
        PostgresDatabaseReadinessProbe(config)
    } else {
        DatabaseReadinessProbe.disabled()
    }

internal fun createLobbyPersistenceCallbacks(
    config: DatabaseRuntimeConfig,
): LobbyPersistenceCallbacks {
    if (!config.isConfigured) {
        return LobbyPersistenceCallbacks.disabled()
    }

    val dataSource =
        createPostgresDataSource(
            config = config,
            poolName = "pulverfass-lobby-persistence-pool",
            applicationName = "pulverfass-lobby-persistence",
        )
    return DatabaseBackedLobbyPersistenceGateway(
        store = JdbcLobbyPersistenceStore(dataSource),
        closeAction = dataSource::close,
    )
}

internal fun formatReadinessResponse(
    appVersion: String,
    readiness: DatabaseReadiness,
): String {
    val databaseStatus =
        when (readiness.state) {
            DatabaseReadinessState.UP -> "up"
            DatabaseReadinessState.DOWN -> "down"
            DatabaseReadinessState.DISABLED -> "disabled"
        }
    val detail = readiness.detail?.replace(Regex("\\s+"), " ")

    return buildString {
        append(if (readiness.isReady) "READY" else "NOT_READY")
        append(" version=")
        append(appVersion)
        append(" database=")
        append(databaseStatus)
        if (!detail.isNullOrBlank()) {
            append(" detail=")
            append(detail)
        }
    }
}

internal fun formatMetricsResponse(
    runtimeConfig: ServerRuntimeConfig,
    readiness: DatabaseReadiness,
    startedAtEpochMillis: Long,
    nowEpochMillis: Long,
): String {
    val uptimeSeconds = ((nowEpochMillis - startedAtEpochMillis).coerceAtLeast(0L)) / 1_000L
    val databaseConfigured = runtimeConfig.database.isConfigured
    val databaseReady = if (readiness.isReady) 1 else 0

    return buildString {
        appendLine("# HELP pulverfass_server_info Build and deployment information.")
        appendLine("# TYPE pulverfass_server_info gauge")
        append("pulverfass_server_info{version=\"")
        append(metricLabel(runtimeConfig.appVersion))
        append("\",commit=\"")
        append(metricLabel(runtimeConfig.commitSha ?: "unknown"))
        append("\",database_configured=\"")
        append(databaseConfigured)
        appendLine("\"} 1")
        appendLine("# HELP pulverfass_database_readiness Database readiness state.")
        appendLine("# TYPE pulverfass_database_readiness gauge")
        append("pulverfass_database_readiness{state=\"")
        append(readiness.state.name.lowercase())
        append("\"} ")
        appendLine(databaseReady)
        appendLine("# HELP pulverfass_server_uptime_seconds Server uptime in seconds.")
        appendLine("# TYPE pulverfass_server_uptime_seconds gauge")
        append("pulverfass_server_uptime_seconds ")
        appendLine(uptimeSeconds)
    }
}

internal fun isInternalMetricsRequest(remoteHost: String): Boolean {
    val host = remoteHost.trim().removeSurrounding("[", "]").lowercase()
    if (
        host == "localhost" ||
        host == "127.0.0.1" ||
        host == "::1" ||
        host == "0:0:0:0:0:0:0:1"
    ) {
        return true
    }

    if (host.startsWith("10.") || host.startsWith("192.168.")) {
        return true
    }

    val private172SecondOctet =
        host
            .takeIf { it.startsWith("172.") }
            ?.split(".")
            ?.getOrNull(1)
            ?.toIntOrNull()
    return private172SecondOctet != null && private172SecondOctet in 16..31
}

private fun metricLabel(value: String): String =
    value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace(Regex("\\s+"), " ")

private fun logDeploymentInfo(runtimeConfig: ServerRuntimeConfig) {
    runtimeLogger.info(
        "Deployment info version={} commit={} databaseConfigured={}",
        runtimeConfig.appVersion,
        runtimeConfig.commitSha ?: "unknown",
        runtimeConfig.database.isConfigured,
    )
}

private fun logDatabaseReadiness(readiness: DatabaseReadiness) {
    runtimeLogger.info(
        "Database readiness state={} detail={}",
        readiness.state.name,
        readiness.detail,
    )
}

private fun GameState.isRecoverableOnStartup(): Boolean =
    status == GameStatus.WAITING_FOR_PLAYERS ||
        status == GameStatus.RUNNING ||
        status == GameStatus.FINISHED

private fun GameState.requiresTerminalCleanup(): Boolean = status == GameStatus.CLOSED

private fun List<GameState>.maxPlayerId(): Long =
    asSequence()
        .flatMap { state -> state.players.asSequence() }
        .map(PlayerId::value)
        .maxOrNull()
        ?: 0L

private fun logRecoverySummary(
    recoveryEnabled: Boolean,
    restoredStates: List<GameState>,
    recoverableStates: List<GameState>,
    discardedStates: List<GameState>,
) {
    runtimeLogger.info(
        "Lobby recovery completed enabled={} restoredLobbies={} recoverableLobbies={} " +
            "discardedLobbies={}",
        recoveryEnabled,
        restoredStates.size,
        recoverableStates.size,
        discardedStates.size,
    )
    restoredStates.forEach { state ->
        runtimeLogger.info(
            "Lobby restored lobbyId={} stateVersion={} processedEventCount={} " +
                "status={} playerCount={}",
            state.lobbyCode.value,
            state.stateVersion,
            state.processedEventCount,
            state.status.name,
            state.players.size,
        )
    }
}

internal fun persistReconnectSessionIfPossible(
    network: ServerNetwork,
    sessionStore: JdbcLobbyReconnectSessionStore?,
    sessionContextRegistry: SessionContextRegistry,
    sessionToken: SessionToken,
) {
    val session = network.sessionManager.getByToken(sessionToken) ?: return
    val context = sessionContextRegistry.contextFor(sessionToken) ?: return
    if (context.playerId == null) {
        return
    }
    sessionStore?.upsertSession(session, context)
}

internal suspend fun cleanupTerminalLobbyState(
    lobbyCode: at.aau.pulverfass.shared.ids.LobbyCode,
    lobbyManager: LobbyManager,
    sessionContextRegistry: SessionContextRegistry,
    recoveryStore: JdbcLobbyPersistenceStore?,
) {
    var cleanupFailure: Throwable? = null

    try {
        sessionContextRegistry.clearLobbyContextForLobby(lobbyCode)
    } catch (cause: Throwable) {
        cleanupFailure = cause
        runtimeLogger.error(
            "Failed to clear reconnect context for terminal lobby {}",
            lobbyCode.value,
            cause,
        )
    }

    try {
        recoveryStore?.deleteLobbyState(lobbyCode)
    } catch (cause: Throwable) {
        if (cleanupFailure == null) {
            cleanupFailure = cause
        }
        runtimeLogger.error(
            "Failed to delete persisted state for terminal lobby {}",
            lobbyCode.value,
            cause,
        )
    } finally {
        lobbyManager.removeLobby(lobbyCode)
    }

    if (cleanupFailure == null) {
        runtimeLogger.info("Removed terminal lobby {}", lobbyCode.value)
    }
}

private fun Application.installLobbyRuntime(
    network: ServerNetwork,
    runtimeConfig: ServerRuntimeConfig,
    persistenceCallbacks: LobbyPersistenceCallbacks,
) {
    val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val mapDefinitionRepository = ClasspathMapDefinitionRepository.loadDefault()
    val defaultMapDefinition = mapDefinitionRepository.defaultMapDefinition()
    runtimeLogger.info(
        "Runtime assets loaded mapSchemaVersion={} mapHash={} rulesVersion={}",
        defaultMapDefinition.schemaVersion,
        defaultMapDefinition.mapHash,
        null,
    )
    val recoveryDataSource =
        runtimeConfig.database.takeIf(DatabaseRuntimeConfig::isConfigured)?.let { config ->
            createPostgresDataSource(
                config = config,
                poolName = "pulverfass-lobby-recovery-pool",
                applicationName = "pulverfass-lobby-recovery",
            )
        }
    val recoveryStore = recoveryDataSource?.let(::JdbcLobbyPersistenceStore)
    val sessionStore = recoveryDataSource?.let(::JdbcLobbyReconnectSessionStore)
    lateinit var sessionContextRegistry: SessionContextRegistry
    sessionContextRegistry =
        SessionContextRegistry(
            persistenceHooks =
                SessionContextPersistenceHooks(
                    loadContext = { sessionToken -> sessionStore?.loadContext(sessionToken) },
                    persistContext = { sessionToken, _ ->
                        persistReconnectSessionIfPossible(
                            network = network,
                            sessionStore = sessionStore,
                            sessionContextRegistry = sessionContextRegistry,
                            sessionToken = sessionToken,
                        )
                    },
                    removeSession = { sessionToken ->
                        sessionStore?.deleteSession(sessionToken)
                    },
                    clearLobbyContextForLobby = { lobbyCode ->
                        sessionStore?.clearLobbyContextForLobby(lobbyCode)
                    },
                ),
        )
    val recoveryLoader =
        recoveryStore?.let { store ->
            LobbyRecoveryLoader(
                store = store,
                mapDefinitionRepository = mapDefinitionRepository,
            )
        }
    val restoredStates = recoveryLoader?.restoreAll().orEmpty()
    val recoverableStates = restoredStates.filter(GameState::isRecoverableOnStartup)
    val discardedStates = restoredStates.filterNot(GameState::isRecoverableOnStartup)
    logRecoverySummary(
        recoveryEnabled = recoveryLoader != null,
        restoredStates = restoredStates,
        recoverableStates = recoverableStates,
        discardedStates = discardedStates,
    )
    val lobbyManager =
        LobbyManager(
            scope = serverScope,
            initialStateFactory = { lobbyCode ->
                GameState.initial(
                    lobbyCode = lobbyCode,
                    mapDefinition = defaultMapDefinition,
                )
            },
            hooksFactory = { LobbyDomainEventLogger.hooks() },
        )
    recoverableStates.forEach { state ->
        lobbyManager.createLobby(lobbyCode = state.lobbyCode, initialState = state)
    }
    discardedStates.forEach { state ->
        try {
            recoveryStore?.deleteLobbyState(state.lobbyCode)
        } catch (cause: Throwable) {
            runtimeLogger.error(
                "Failed to delete persisted state for discarded startup lobby {}",
                state.lobbyCode.value,
                cause,
            )
        }
        try {
            sessionContextRegistry.clearLobbyContextForLobby(state.lobbyCode)
        } catch (cause: Throwable) {
            runtimeLogger.error(
                "Failed to clear reconnect context for discarded startup lobby {}",
                state.lobbyCode.value,
                cause,
            )
        }
    }
    val router =
        MainServerRouter(
            lobbyManager = lobbyManager,
            mapper = DefaultNetworkToLobbyEventMapper(),
        )

    val nextPlayerId =
        AtomicLong(
            maxOf(
                recoverableStates.maxPlayerId(),
                sessionStore?.maxPersistedPlayerId() ?: 0L,
            ) + 1,
        )
    val playerIdAssignmentLock = Any()

    fun ensurePlayerId(sessionToken: SessionToken): PlayerId {
        sessionContextRegistry.playerIdForSession(sessionToken)?.let { return it }
        synchronized(playerIdAssignmentLock) {
            sessionContextRegistry.playerIdForSession(sessionToken)?.let { return it }
            val assignedPlayerId = PlayerId(nextPlayerId.getAndIncrement())
            sessionContextRegistry.assignPlayer(
                sessionToken = sessionToken,
                playerId = assignedPlayerId,
            )
            return assignedPlayerId
        }
    }
    network.sessionManager.installLifecycleHooks(
        onSessionUpsert = { session ->
            persistReconnectSessionIfPossible(
                network = network,
                sessionStore = sessionStore,
                sessionContextRegistry = sessionContextRegistry,
                sessionToken = session.sessionToken,
            )
        },
        onSessionRemoved = { sessionToken ->
            sessionStore?.deleteSession(sessionToken)
        },
    )
    network.installReconnectHooks(
        reconnectSessionProvider = { sessionToken ->
            sessionStore?.loadSession(sessionToken)
                ?: sessionContextRegistry.contextFor(sessionToken)?.let { context ->
                    network.sessionManager.getByToken(sessionToken)?.let { session ->
                        at.aau.pulverfass.server.session.PersistedReconnectSession(
                            context = context,
                            expiresAtEpochMillis = session.expiresAtEpochMillis,
                            revokedAtEpochMillis = session.revokedAtEpochMillis,
                        )
                    }
                }
        },
        onReconnectSucceeded = { sessionToken ->
            persistReconnectSessionIfPossible(
                network = network,
                sessionStore = sessionStore,
                sessionContextRegistry = sessionContextRegistry,
                sessionToken = sessionToken,
            )
        },
        onSessionRemoved = sessionContextRegistry::removeSession,
    )

    val routingService =
        MainServerLobbyRoutingService(
            network = network,
            router = router,
            lobbyManager = lobbyManager,
            sessionContextRegistry = sessionContextRegistry,
            playerIdResolver = { connectionId ->
                network.sessionManager
                    .getByConnectionId(connectionId)
                    ?.sessionToken
                    ?.let(::ensurePlayerId)
            },
            connectionIdResolver = { playerId ->
                sessionContextRegistry
                    .sessionTokenForPlayer(playerId)
                    ?.let(network.sessionManager::getByToken)
                    ?.connectionId
            },
            persistenceCallbacks = persistenceCallbacks,
            publicStatePayloadMaxBytes = runtimeConfig.webSocketMaxFrameSizeBytes.toInt(),
            privateStatePayloadMaxBytes = runtimeConfig.webSocketMaxFrameSizeBytes.toInt(),
        )
    lobbyManager.registerAcceptedEventListener { lobbyCode, _, _, currentState ->
        if (!currentState.requiresTerminalCleanup()) {
            return@registerAcceptedEventListener
        }
        cleanupTerminalLobbyState(
            lobbyCode = lobbyCode,
            lobbyManager = lobbyManager,
            sessionContextRegistry = sessionContextRegistry,
            recoveryStore = recoveryStore,
        )
    }

    serverScope.launch {
        network.events.collect { event ->
            when (event) {
                is Network.Event.Connected<ConnectionId> -> {
                    val session = network.sessionManager.requireByConnectionId(event.connectionId)
                    ensurePlayerId(session.sessionToken)
                    routingService.onConnectionOpened()
                }

                is Network.Event.Disconnected<ConnectionId> -> {
                    event.sessionToken
                        ?.let(sessionContextRegistry::playerIdForSession)
                        ?.let { playerId ->
                            routingService.onPlayerDisconnected(
                                connectionId = event.connectionId,
                                playerId = playerId,
                                reason = event.reason,
                            )
                        }
                }

                else -> Unit
            }
        }
    }

    routingService.start(serverScope)

    environment.monitor.subscribe(ApplicationStopped) {
        applicationLogger.info("Stopping lobby runtime")
        runBlocking {
            routingService.stop()
            lobbyManager.shutdownAll()
        }
        persistenceCallbacks.close()
        recoveryDataSource?.close()
        serverScope.cancel()
        applicationLogger.info("Lobby runtime stopped")
    }
}

/**
 * Behandelt den Lebenszyklus einer einzelnen WebSocket-Verbindung.
 *
 * Für jede Verbindung wird serverseitig eine neue `ConnectionId` vergeben.
 * Binary Frames werden an [ServerNetwork] weitergereicht, Text Frames dagegen
 * aktiv mit dokumentiertem Close-Reason abgelehnt.
 */
private suspend fun DefaultWebSocketServerSession.handleWebSocketConnection(
    network: ServerNetwork,
) {
    val connectionId = IdFactory.nextConnectionId()

    try {
        webSocketLogger.info(
            "WebSocket connection opened connectionId={}",
            connectionId.value,
        )
        network.onConnected(connectionId, this)
        for (frame in incoming) {
            when (frame) {
                is Frame.Binary -> network.onBinaryMessage(connectionId, frame.data.copyOf())
                is Frame.Text -> {
                    webSocketLogger.warn(
                        "Rejecting text websocket frame on connection {} " +
                            "because only binary frames are supported",
                        connectionId.value,
                    )
                    close(
                        CloseReason(
                            CloseReason.Codes.CANNOT_ACCEPT,
                            WebSocketPolicy.TEXT_FRAMES_NOT_SUPPORTED,
                        ),
                    )
                    break
                }

                else -> Unit
            }
        }
    } catch (cause: Throwable) {
        webSocketLogger.warn(
            "WebSocket connection failed connectionId={}",
            connectionId.value,
            cause,
        )
        network.onError(connectionId, cause)
        throw cause
    } finally {
        val reason = runCatching { closeReason.await()?.message }.getOrNull()
        network.onDisconnected(connectionId, reason)
        webSocketLogger.info(
            "WebSocket connection closed connectionId={} reason={}",
            connectionId.value,
            reason,
        )
    }
}
