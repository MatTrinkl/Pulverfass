package at.aau.pulverfass.shared.network.transport

/**
 * Minimale gemeinsame Abstraktion zum Weiterreichen von [TransportEvent]s.
 *
 * Die Schnittstelle ist bewusst klein gehalten, damit Server- und Android-Code
 * dieselbe Vertragsfläche für den Transport nutzen können.
 */
fun interface TransportEventDispatcher {
    /**
     * Übergibt ein einzelnes Transportereignis an die nächste Schicht.
     */
    fun dispatch(event: TransportEvent)
}
