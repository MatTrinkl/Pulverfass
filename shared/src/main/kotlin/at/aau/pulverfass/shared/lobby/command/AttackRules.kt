package at.aau.pulverfass.shared.lobby.command

/**
 * Kleinste erlaubte Anzahl an Truppen, die ein Angreifer in einen Angriff committen darf.
 *
 * Die Zahl folgt den Risiko-Regeln: mindestens eine Truppe muss nach dem Angriff im
 * Ursprungsterritorium verbleiben.
 */
const val MIN_ATTACK_COMMITTED_TROOPS = 2

/**
 * Kleinste Anzahl an Truppen, die ein Ursprungsterritorium besitzen muss, damit ein Angriff
 * überhaupt zulässig ist.
 */
const val MIN_SOURCE_TROOPS_FOR_ATTACK = MIN_ATTACK_COMMITTED_TROOPS + 1
