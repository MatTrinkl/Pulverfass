package at.aau.pulverfass.server.entities

import at.aau.pulverfass.shared.ids.EntityId
import at.aau.pulverfass.shared.ids.PlayerId

/**
 * Repräsentiert ein Gebiet auf der Risiko-Karte.
 *
 * @property entityId technische ID der Entity
 * @property ownerId Spieler, dem das Gebiet aktuell gehört
 * @property troops aktuelle Anzahl an Truppen auf dem Gebiet
 */
data class TerritoryEntity(
    override val entityId: EntityId,
    val ownerId: PlayerId?,
    private var troopCount: Int,
) : BaseEntity(entityId, EntityType.TERRITORY) {
    /**
     * Öffentliche Sicht auf die aktuelle Truppenanzahl.
     */
    val troops: Int
        get() = troopCount

    /**
     * Erhöht die Truppenanzahl auf dem Gebiet.
     *
     * @param amount Anzahl der Truppen, die hinzugefügt werden sollen
     * @param validateInput legt fest, ob die Eingabe geprüft werden soll
     */
    fun addTroops(
        amount: Int,
        validateInput: Boolean = true,
    ) {
        if (validateInput && amount <= 0) {
            throw IllegalArgumentException("Es können nur positive Truppenwerte hinzugefügt werden.")
        }

        troopCount += amount
    }

    /**
     * Verringert die Truppenanzahl auf dem Gebiet.
     *
     * @param amount Anzahl der Truppen, die entfernt werden sollen
     * @param validateInput legt fest, ob die Eingabe geprüft werden soll
     */
    fun subtractTroops(
        amount: Int,
        validateInput: Boolean = true,
    ) {
        if (validateInput && amount <= 0) {
            throw IllegalArgumentException("Es können nur positive Truppenwerte entfernt werden.")
        }

        if (validateInput && amount > troopCount) {
            throw IllegalArgumentException("Es können nicht mehr Truppen entfernt werden als vorhanden sind.")
        }

        troopCount -= amount
    }
}
