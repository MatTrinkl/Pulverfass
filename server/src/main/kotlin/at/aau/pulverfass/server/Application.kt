package at.aau.pulverfass.server

import at.aau.pulverfass.server.ids.IdFactory
import at.aau.pulverfass.server.lobby.mapping.DefaultNetworkToLobbyEventMapper
import at.aau.pulverfass.server.lobby.runtime.LobbyManager
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
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong

internal const val DEFAULT_HOST = "0.0.0.0"
internal const val DEFAULT_PORT = 8080
private val logger = LoggerFactory.getLogger("at.aau.pulverfass.server.WebSocketEndpoint")
private val runtimeLogger = LoggerFactory.getLogger("at.aau.pulverfass.server.Runtime")
private const val SERVER_MODE_ENV = "SERVER_MODE"
private const val SERVER_MODE_MIGRATE = "migrate"

/**
 * Startet den eingebetteten Ktor-Server mit der Standardkonfiguration.
 */
fun main() {
    val runtimeConfig = ServerRuntimeConfig.fromEnvironment()
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
 * Test-Hilfsmethode für Low-Level-Transporttests.
 *
 * Die Transportvariante bleibt bewusst intern, damit Produktionscode nicht am
 * High-Level-Network vorbei integriert wird.
 */
internal fun createServer(
    host: String,
    port: Int,
    transport: ServerWebSocketTransport,
): ApplicationEngine = createServer(host, port, ServerNetwork(transport = transport))

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
    install(WebSockets) {
        pingPeriodMillis = 15_000
        timeoutMillis = 15_000
        maxFrameSize = Long.MAX_VALUE
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
    installLobbyRuntime(network, runtimeConfig.database, persistenceCallbacks)
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

private fun GameState.isRecoverableOnStartup(): Boolean =
    status == GameStatus.WAITING_FOR_PLAYERS || status == GameStatus.RUNNING

private fun GameState.isTerminal(): Boolean =
    status == GameStatus.CLOSED || status == GameStatus.FINISHED

private fun List<GameState>.maxPlayerId(): Long =
    asSequence()
        .flatMap { state -> state.players.asSequence() }
        .map(PlayerId::value)
        .maxOrNull()
        ?: 0L

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
    databaseConfig: DatabaseRuntimeConfig,
    persistenceCallbacks: LobbyPersistenceCallbacks,
) {
    val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val mapDefinitionRepository = ClasspathMapDefinitionRepository.loadDefault()
    val defaultMapDefinition = mapDefinitionRepository.defaultMapDefinition()
    val recoveryDataSource =
        databaseConfig.takeIf(DatabaseRuntimeConfig::isConfigured)?.let { config ->
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
    val lobbyManager =
        LobbyManager(
            scope = serverScope,
            initialStateFactory = { lobbyCode ->
                GameState.initial(
                    lobbyCode = lobbyCode,
                    mapDefinition = defaultMapDefinition,
                )
            },
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
                    ?.let(sessionContextRegistry::playerIdForSession)
            },
            connectionIdResolver = { playerId ->
                sessionContextRegistry
                    .sessionTokenForPlayer(playerId)
                    ?.let(network.sessionManager::getByToken)
                    ?.connectionId
            },
            persistenceCallbacks = persistenceCallbacks,
        )
    lobbyManager.registerAcceptedEventListener { lobbyCode, _, _, currentState ->
        if (!currentState.isTerminal()) {
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
                    val playerId =
                        sessionContextRegistry.playerIdForSession(session.sessionToken)
                            ?: PlayerId(nextPlayerId.getAndIncrement()).also { assignedPlayerId ->
                                sessionContextRegistry.assignPlayer(
                                    sessionToken = session.sessionToken,
                                    playerId = assignedPlayerId,
                                )
                            }
                    routingService.onPlayerConnected(playerId)
                }

                is Network.Event.Disconnected<ConnectionId> -> {
                    network.sessionManager
                        .getByConnectionId(event.connectionId)
                        ?.sessionToken
                        ?.let(sessionContextRegistry::playerIdForSession)
                        ?.let { playerId -> routingService.onPlayerDisconnected(playerId) }
                }

                else -> Unit
            }
        }
    }

    routingService.start(serverScope)

    environment.monitor.subscribe(ApplicationStopped) {
        runBlocking {
            routingService.stop()
            lobbyManager.shutdownAll()
        }
        persistenceCallbacks.close()
        recoveryDataSource?.close()
        serverScope.cancel()
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
        network.onConnected(connectionId, this)
        for (frame in incoming) {
            when (frame) {
                is Frame.Binary -> network.onBinaryMessage(connectionId, frame.data.copyOf())
                is Frame.Text -> {
                    logger.warn(
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
        network.onError(connectionId, cause)
        throw cause
    } finally {
        val reason = runCatching { closeReason.await()?.message }.getOrNull()
        network.onDisconnected(connectionId, reason)
    }
}
