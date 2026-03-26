package com.example.androidapp

import at.aau.pulverfass.shared.network.MessageType
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageTypeTest {
    @Test
    fun loginRequestMessageTypeHasCorrectId() {
        assertEquals(1, MessageType.LOGIN_REQUEST.id)
    }

    @Test
    fun messageTypeFromIdReturnsLoginRequest() {
        assertEquals(MessageType.LOGIN_REQUEST, MessageType.fromId(1))
    }
}
