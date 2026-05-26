package at.aau.pulverfass.shared.lobby.state

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
