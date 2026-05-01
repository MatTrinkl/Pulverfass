package at.aau.pulverfass.shared.message.lobby.event

/**
 * Marker-Interface für öffentlich an Clients übertragbare GameState-Events.
 *
 * Diese Events dürfen in einem `GameStateDeltaEvent` enthalten sein und können
 * clientseitig deterministisch in derselben Reihenfolge angewendet werden.
 */
interface PublicGameEvent : PublicGameStatePayload
