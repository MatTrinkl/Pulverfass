package at.aau.pulverfass.app.game

import at.aau.pulverfass.app.ui.map.GameMapRegionState
import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.state.CardType
import at.aau.pulverfass.shared.lobby.state.TurnPhase

/**
 * Android-Projektion des serverautoritativen öffentlichen GameStates.
 *
 * Der Zustand hält shared-IDs als fachliche Quelle und bereitet zusätzlich die
 * vorhandene Karten-UI mit Android-Region-IDs auf.
 */
data class GameUiState(
    val isStarted: Boolean = false,
    val isCatchingUp: Boolean = false,
    val isDesynced: Boolean = false,
    val stateVersion: Long = 0,
    val schemaVersion: Int? = null,
    val mapHash: String? = null,
    val activePlayerId: PlayerId? = null,
    val turnPhase: TurnPhase? = null,
    val turnCount: Int = 0,
    val startPlayerId: PlayerId? = null,
    val isPaused: Boolean = false,
    val pauseReason: String? = null,
    val pausedPlayerId: PlayerId? = null,
    val selectedRegionId: String? = null,
    val selectionFromRegionId: String? = null,
    val selectionToRegionId: String? = null,
    val selectionMessage: String? = null,
    val cardsVisible: Boolean = false,
    val definitionTerritoryIds: List<TerritoryId> = emptyList(),
    val adjacentTerritoryIds: Map<TerritoryId, Set<TerritoryId>> = emptyMap(),
    val territoryStates: Map<TerritoryId, GameTerritoryUiState> = emptyMap(),
    val regionStates: Map<String, GameMapRegionState> = emptyMap(),
    val handCards: List<String> = emptyList(),
    val privateHandCards: List<PrivateHandCardUi> = emptyList(),
    val selectedTradeInCardIds: Set<CardId> = emptySet(),
    val secretObjectives: List<String> = emptyList(),
    val reinforcementState: ReinforcementUiState = ReinforcementUiState(),
    val reinforcementPlacementAmount: Int = 1,
    val attackState: AttackUiState = AttackUiState(),
    val fortifyState: FortifyUiState = FortifyUiState(),
    val lastSyncError: String? = null,
) {
    /**
     * Prüft, ob lokale Spielaktionen aktuell sinnvoll angeboten werden dürfen.
     *
     * @param localPlayerId eigener Spieler aus dem Lobby-Kontext
     * @param isConnected aktueller WebSocket-Zustand
     * @return `true`, wenn UI-Aktionen für den aktiven Spieler erlaubt sind
     */
    fun canUseGameActions(
        localPlayerId: PlayerId?,
        isConnected: Boolean = true,
    ): Boolean =
        localPlayerId != null &&
            activePlayerId == localPlayerId &&
            isConnected &&
            !isPaused &&
            !isCatchingUp &&
            !isDesynced

    /**
     * Prüft speziell den derzeit vorhandenen generischen Phasenwechsel-Button.
     *
     * @param localPlayerId eigener Spieler aus dem Lobby-Kontext
     * @param isConnected aktueller WebSocket-Zustand
     * @return `true`, wenn ein `TurnAdvanceRequest` abgeschickt werden darf
     */
    fun canRequestTurnAdvance(
        localPlayerId: PlayerId?,
        isConnected: Boolean = true,
    ): Boolean =
        canUseGameActions(localPlayerId = localPlayerId, isConnected = isConnected) &&
            turnPhase != null &&
            turnPhase != TurnPhase.REINFORCEMENTS &&
            turnPhase != TurnPhase.ATTACK

    /**
     * Prüft, ob die aktuelle Verstärkungsphase lokal bedient werden kann.
     *
     * Die aktive Spieler-ID allein reicht nicht aus: Der Restpool muss bereits
     * aus einem Snapshot oder Grant bekannt sein, damit keine Platzierung auf
     * Basis eines noch nicht synchronisierten Clientzustands angeboten wird.
     */
    fun canManageReinforcements(
        localPlayerId: PlayerId?,
        isConnected: Boolean = true,
    ): Boolean =
        canUseGameActions(localPlayerId = localPlayerId, isConnected = isConnected) &&
            turnPhase == TurnPhase.REINFORCEMENTS &&
            reinforcementState.playerId == localPlayerId &&
            reinforcementState.pendingAmount != null

    /**
     * Prüft, ob die ausgewählte Anzahl auf das Zielgebiet platziert werden kann.
     *
     * Das Zielgebiet ist zuvor im Reducer auf Eigentum geprüft worden. Der
     * Controller validiert vor dem Senden nochmals dieselbe lokale
     * Handlungsberechtigung; endgültig autoritativ bleibt die Serverprüfung.
     */
    fun canPlaceReinforcements(
        localPlayerId: PlayerId?,
        isConnected: Boolean = true,
    ): Boolean {
        val pendingAmount = reinforcementState.pendingAmount ?: return false
        return canManageReinforcements(localPlayerId, isConnected) &&
            selectedRegionId != null &&
            pendingAmount > 0 &&
            reinforcementPlacementAmount in 1..pendingAmount
    }

    /**
     * Prüft, ob alle Verstärkungen verbraucht sind und die Phase enden darf.
     *
     * Diese Aktion ist fachlich kein normaler `TurnAdvanceRequest`: Der
     * Server erwartet hierfür `ConfirmReinforcementsDoneRequest`. Im Screen
     * wird sie dennoch über den bereits vorhandenen Button "Phase beenden"
     * angeboten, sobald der Restpool exakt null ist.
     */
    fun canConfirmReinforcementsDone(
        localPlayerId: PlayerId?,
        isConnected: Boolean = true,
    ): Boolean =
        canManageReinforcements(localPlayerId, isConnected) &&
            reinforcementState.pendingAmount == 0

    /**
     * Prüft, ob drei private Karten zum Eintausch ausgewählt wurden.
     *
     * Ob die konkrete Kombination gültig oder ein erzwungener Trade nötig ist,
     * entscheidet weiterhin der Server. Die UI beschränkt lediglich die
     * Auswahlmenge, damit kein offensichtlich ungültiger Request entsteht.
     */
    fun canTradeInCards(
        localPlayerId: PlayerId?,
        isConnected: Boolean = true,
    ): Boolean =
        canUseGameActions(localPlayerId = localPlayerId, isConnected = isConnected) &&
            turnPhase == TurnPhase.REINFORCEMENTS &&
            selectedTradeInCardIds.size == 3

    /**
     * Prüft, ob ein Angriff in der aktuellen Phase vorbereitet werden kann.
     */
    fun canManageAttacks(
        localPlayerId: PlayerId?,
        isConnected: Boolean = true,
    ): Boolean =
        canUseGameActions(localPlayerId = localPlayerId, isConnected = isConnected) &&
            turnPhase == TurnPhase.ATTACK

    /**
     * Prüft die vollständig ausgewählte lokale Angriffsabsicht vor dem Request.
     *
     * Eigentum und Nachbarschaft werden beim Auswählen im Reducer geprüft.
     * Würfelresultat und endgültige Capture-Verschiebung bleiben serverautoritativ.
     */
    fun canSubmitAttack(
        localPlayerId: PlayerId?,
        isConnected: Boolean = true,
    ): Boolean =
        canManageAttacks(localPlayerId, isConnected) &&
            selectionFromRegionId != null &&
            selectionToRegionId != null &&
            attackState.attackTroops >= MIN_ATTACK_TROOPS &&
            attackState.moveAfterCapture in
            minimumOccupyingTroopsForAttack(attackState.attackTroops)..attackState.attackTroops

    /**
     * Die Angriffsphase darf ohne Angriff oder nach beliebig vielen Kämpfen enden.
     */
    fun canConfirmAttackDone(
        localPlayerId: PlayerId?,
        isConnected: Boolean = true,
    ): Boolean = canManageAttacks(localPlayerId, isConnected)

    /**
     * Prüft, ob der aktive Spieler aktuell mindestens einen sinnvollen Angriff
     * auswählen kann.
     *
     * Die UI nutzt diese Information, um eine leere Angriffsphase direkt zu
     * bestätigen. Ein Gebiet braucht mehr als zwei Truppen, weil mindestens
     * zwei Angreifer eingesetzt werden und eine Truppe im Ausgangsgebiet bleiben
     * muss. Zielgebiete verlassener Spieler bleiben angreifbar, solange der
     * serverseitige Territory-State noch eine fremde Owner-ID trägt.
     *
     * @param localPlayerId eigener Spieler aus dem Lobby-Kontext
     * @return `true`, wenn mindestens ein eigenes Gebiet ein fremdes Nachbarziel
     * angreifen kann
     */
    fun hasAvailableAttack(localPlayerId: PlayerId?): Boolean {
        if (localPlayerId == null) {
            return false
        }

        return territoryStates.values.any { source ->
            source.ownerId == localPlayerId &&
                source.troopCount > MIN_ATTACK_TROOPS &&
                adjacentTerritoryIds[source.territoryId].orEmpty().any { targetId ->
                    val targetOwnerId = territoryStates[targetId]?.ownerId
                    targetOwnerId != null &&
                        targetOwnerId != localPlayerId
                }
        }
    }

    /**
     * Prüft, ob eine Truppenverschiebung in der Fortify-Phase vorbereitet werden kann.
     *
     * Fortify darf nur einmal pro Zug ausgeführt werden. Der öffentliche
     * Snapshot und die Turn-State-Antwort liefern den serverautoritativen
     * Verbrauchsstatus. `hasMoved` spiegelt diesen Status lokal und blockiert
     * nach einem bestätigten Move weitere Fortify-Requests bis zum nächsten
     * autoritativen Update.
     *
     * @param localPlayerId eigener Spieler aus dem Lobby-Kontext
     * @param isConnected aktueller WebSocket-Zustand
     * @return `true`, wenn Quelle und Ziel für Fortify gewählt werden dürfen
     */
    fun canManageFortify(
        localPlayerId: PlayerId?,
        isConnected: Boolean = true,
    ): Boolean =
        canUseGameActions(localPlayerId = localPlayerId, isConnected = isConnected) &&
            turnPhase == TurnPhase.FORTIFY &&
            !fortifyState.hasMoved

    /**
     * Prüft eine vollständig ausgewählte Fortify-Absicht vor dem Request.
     *
     * Quelle, Ziel, Eigentum und eigener Verbindungspfad werden beim Auswählen
     * im Reducer geprüft. Der Server validiert denselben Move dennoch
     * abschließend, insbesondere nach parallelen Deltas oder Reconnects.
     *
     * @param localPlayerId eigener Spieler aus dem Lobby-Kontext
     * @param isConnected aktueller WebSocket-Zustand
     * @return `true`, wenn der Fortify-Request lokal sendbar ist
     */
    fun canSubmitFortifyMove(
        localPlayerId: PlayerId?,
        isConnected: Boolean = true,
    ): Boolean =
        canManageFortify(localPlayerId, isConnected) &&
            selectionFromRegionId != null &&
            selectionToRegionId != null &&
            fortifyState.troopCount >= MIN_FORTIFY_TROOPS
}

/**
 * Fachlicher Territory-Zustand mit shared-ID, bevor er auf Android-Masken
 * abgebildet wird.
 */
data class GameTerritoryUiState(
    val territoryId: TerritoryId,
    val ownerId: PlayerId?,
    val troopCount: Int,
)

/**
 * Restpool und Bonusaufschlüsselung einer serverseitig gewährten Verstärkungsphase.
 *
 * `pendingAmount == null` bedeutet, dass der Client für die laufende Phase noch
 * keinen autoritativen Restpool empfangen hat. `isBonusBreakdownKnown == false`
 * bedeutet, dass der Restpool aus einem Snapshot stammt, der die Bonusquellen
 * nicht separat enthält.
 */
data class ReinforcementUiState(
    val playerId: PlayerId? = null,
    val pendingAmount: Int? = null,
    val territoryBonus: Int = 0,
    val continentBonus: Int = 0,
    val cardBonus: Int = 0,
    val isBonusBreakdownKnown: Boolean = false,
)

/**
 * Lokale Steuerungswerte und das zuletzt vom Server aufgelöste Kampfergebnis.
 *
 * Die Eingaben beschreiben nur die nächste Angriffsabsicht. Änderungen an
 * Besitz und Truppen werden ausschließlich über Serverevents übernommen.
 */
data class AttackUiState(
    val attackTroops: Int = MIN_ATTACK_TROOPS,
    val moveAfterCapture: Int = minimumOccupyingTroopsForAttack(MIN_ATTACK_TROOPS),
    val latestResult: AttackResultUiState? = null,
)

/**
 * Lokale Eingabe der einmaligen Truppenverschiebung in der Fortify-Phase.
 *
 * Truppen werden nicht optimistisch bewegt; sichtbare Änderungen kommen über
 * die nachgelagerten serverseitigen Territory-Troop-Deltas. `hasMoved` schützt
 * nur die Bedienoberfläche vor einem zweiten Request im gleichen Zug.
 *
 * @property troopCount lokal ausgewählte Anzahl der zu verschiebenden Truppen
 * @property hasMoved Marker, ob der einmalige Fortify-Move laut lokalem oder
 * serverseitigem Status bereits verbraucht ist
 */
data class FortifyUiState(
    val troopCount: Int = MIN_FORTIFY_TROOPS,
    val hasMoved: Boolean = false,
)

/**
 * Präsentationsmodell eines einzelnen serverseitig ausgewürfelten Kampfes.
 */
data class AttackResultUiState(
    val fromTerritoryId: TerritoryId,
    val toTerritoryId: TerritoryId,
    val attackerRolls: List<Int>,
    val defenderRolls: List<Int>,
    val attackerLosses: Int,
    val defenderLosses: Int,
    val attackerRemaining: Int,
    val defenderRemaining: Int,
    val occupyingTroopCount: Int?,
) {
    val captured: Boolean
        get() = defenderRemaining == 0
}

/**
 * Private Handkarte mit stabiler Server-ID für einen möglichen Trade-in.
 */
data class PrivateHandCardUi(
    val cardId: CardId,
    val type: CardType,
)

internal const val MIN_ATTACK_TROOPS = 2
internal const val MAX_ATTACK_DICE = 3
internal const val MIN_FORTIFY_TROOPS = 1

/**
 * Liefert die serverseitig verlangte Mindestbesetzung nach einer Eroberung.
 *
 * Der Server verlangt im Capture-Fall mindestens so viele einziehende Truppen,
 * wie für den Angriff gewürfelt wird. Ein Angriff nutzt höchstens drei Würfel.
 */
internal fun minimumOccupyingTroopsForAttack(attackTroops: Int): Int =
    minOf(MAX_ATTACK_DICE, attackTroops)
