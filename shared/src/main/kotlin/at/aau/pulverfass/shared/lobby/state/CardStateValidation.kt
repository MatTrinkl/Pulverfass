package at.aau.pulverfass.shared.lobby.state

/**
 * Erzwingt, dass eine Kartenmenge keine doppelt vorkommenden [CardState.cardId]-Werte enthält.
 *
 * Der Helper wird von Hand-, Deck- und Ablagestapelzuständen genutzt, damit eine Karte nie
 * gleichzeitig mehrfach in derselben Struktur auftaucht.
 *
 * @param cards zu prüfende Kartenliste
 * @param containerName Name des validierten Containers für Fehlermeldungen
 * @throws IllegalArgumentException wenn Duplikate gefunden werden
 */
internal fun validateDistinctCardIds(
    cards: List<CardState>,
    containerName: String,
) {
    val duplicateCardIds =
        cards
            .groupingBy { card -> card.cardId }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys

    require(duplicateCardIds.isEmpty()) {
        val duplicateIds = duplicateCardIds.joinToString { cardId -> cardId.value }
        "$containerName darf keine doppelten CardIds enthalten: $duplicateIds."
    }
}
