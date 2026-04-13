package at.aau.pulverfass.server.routing

import at.aau.pulverfass.server.lobby.mapping.DecodedNetworkRequest
import at.aau.pulverfass.server.lobby.mapping.MappedLobbyEvents
import at.aau.pulverfass.server.lobby.mapping.NetworkToLobbyEventMapper
import at.aau.pulverfass.server.lobby.mapping.NetworkToLobbyEventMappingException
import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.lobby.reducer.InvalidLobbyEventException
import at.aau.pulverfass.shared.lobby.reducer.LobbyCodeMismatchException
import org.slf4j.LoggerFactory

/**
 * Orchestriert das Routing von technisch dekodierten Requests in Lobby-Runtimes.
 *
 * Der Router enthält keine Spiellogik. Er validiert Mapping-Ergebnisse, löst
 * die Ziel-Lobby über [LobbyManager] auf und reicht Events weiter.
 */
class MainServerRouter(
    private val lobbyManager: LobbyManager,
    private val mapper: NetworkToLobbyEventMapper,
    private val hooks: MainServerRouterHooks = MainServerRouterHooks(),
) {
    private val logger = LoggerFactory.getLogger(MainServerRouter::class.java)

    /**
     * Kompatible Exception-basierte API für bestehende Aufrufer.
     *
     * Intern wird immer auf das neue Result-Modell [route] delegiert.
     */
    suspend fun handle(request: DecodedNetworkRequest) {
        when (val result = route(request)) {
            is LobbyRoutingResult.Success -> Unit
            is LobbyRoutingResult.Failure -> throw result.error.toException()
        }
    }

    /**
     * Routet einen Request und liefert ein typisiertes Ergebnis statt Exception.
     *
     * @return [LobbyRoutingResult.Success] oder [LobbyRoutingResult.Failure]
     */
    suspend fun route(request: DecodedNetworkRequest): LobbyRoutingResult {
        var mappedLobbyCode: LobbyCode? = null
        val baseContext =
            LobbyRoutingContext(
                connectionId = request.connectionId,
                messageType = request.header.type,
            )

        return try {
            val mapped = mapper.map(request)
            mappedLobbyCode = mapped.lobbyCode
            validateMappedEvents(mapped)
            val context = baseContext.copy(lobbyCode = mapped.lobbyCode)

            val runtime =
                lobbyManager.getLobby(mapped.lobbyCode)
                    ?: throw UnknownLobbyRoutingException(mapped.lobbyCode)

            mapped.events.forEach { event ->
                runtime.submit(event = event, context = mapped.context)
            }

            hooks.onRouted(mapped.lobbyCode, mapped.events.size)
            LobbyRoutingResult.Success(
                context = context,
                eventCount = mapped.events.size,
            )
        } catch (cause: Throwable) {
            val error = mapError(baseContext.copy(lobbyCode = mappedLobbyCode), cause)
            logger.warn(
                "Failed to route request for connection {}",
                request.connectionId.value,
                cause,
            )
            hooks.onRoutingError(request, error)
            LobbyRoutingResult.Failure(error)
        }
    }

    private fun validateMappedEvents(mapped: MappedLobbyEvents) {
        if (mapped.events.isEmpty()) {
            throw EmptyMappedEventsRoutingException(mapped.lobbyCode)
        }

        val mismatch = mapped.events.firstOrNull { it.lobbyCode != mapped.lobbyCode }
        if (mismatch != null) {
            throw RoutedLobbyMismatchException(
                expectedLobbyCode = mapped.lobbyCode,
                actualLobbyCode = mismatch.lobbyCode,
            )
        }
    }

    private fun mapError(
        context: LobbyRoutingContext,
        cause: Throwable,
    ): LobbyRoutingError {
        return when (cause) {
            is UnknownLobbyRoutingException ->
                context.lobbyCode?.let { code ->
                    LobbyRoutingError.LobbyNotFound(
                        lobbyCode = code,
                        context = context,
                    )
                } ?: LobbyRoutingError.InvalidRoutingData(
                    reason = cause.message ?: "Unbekannte Lobby.",
                    context = context,
                    cause = cause,
                )

            is EmptyMappedEventsRoutingException,
            is RoutedLobbyMismatchException,
            is NetworkToLobbyEventMappingException,
            ->
                LobbyRoutingError.InvalidRoutingData(
                    reason = cause.message ?: "Ungültige Routingdaten.",
                    context = context,
                    cause = cause,
                )

            is LobbyCodeMismatchException ->
                LobbyRoutingError.InvalidEvent(
                    reason = cause.message ?: "Ungültiges Event für Lobby.",
                    context = context,
                    cause = cause,
                )

            is InvalidLobbyEventException ->
                LobbyRoutingError.InvalidStateTransition(
                    reason = cause.message ?: "Ungültige Zustandsänderung.",
                    context = context,
                    cause = cause,
                )

            else ->
                LobbyRoutingError.InvalidRoutingData(
                    reason = cause.message ?: "Unbekannter Routingfehler.",
                    context = context,
                    cause = cause,
                )
        }
    }

    private fun LobbyRoutingError.toException(): MainServerRoutingException =
        when (this) {
            is LobbyRoutingError.LobbyNotFound -> UnknownLobbyRoutingException(lobbyCode)
            is LobbyRoutingError.InvalidRoutingData -> InvalidRoutingDataRoutingException(reason)
            is LobbyRoutingError.InvalidEvent -> InvalidRoutedEventException(reason)
            is LobbyRoutingError.InvalidStateTransition ->
                InvalidStateTransitionRoutingException(reason)
        }
}

/**
 * Hooks für observability-Integrationen ohne feste Outbound-Abhängigkeit.
 */
data class MainServerRouterHooks(
    /** Wird nach erfolgreichem Routing mit Lobby und Eventanzahl ausgelöst. */
    val onRouted: (LobbyCode, Int) -> Unit = { _, _ -> },
    /** Wird bei jedem Routingfehler mit typisiertem Fehler ausgelöst. */
    val onRoutingError: (DecodedNetworkRequest, LobbyRoutingError) -> Unit = { _, _ -> },
)
