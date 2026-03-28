package at.aau.pulverfass.shared.ids

import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class IdFactoryTest {
    @Test
    fun `ids should be unique`() {
        val id1 = IdFactory.nextPlayerId()
        val id2 = IdFactory.nextPlayerId()

        assertNotEquals(id1, id2)
    }
}
