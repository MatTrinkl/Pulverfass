package at.aau.pulverfass.shared.message.lobby.event

/**
 * Marker-Interface für private GameState-Events.
 *
 * Diese Events repräsentieren autoritative Zustandsänderungen, die ausschließlich
 * an einen einzelnen Spieler ausgeliefert werden dürfen.
 */
interface PrivateGameEvent : PrivateGameStatePayload
