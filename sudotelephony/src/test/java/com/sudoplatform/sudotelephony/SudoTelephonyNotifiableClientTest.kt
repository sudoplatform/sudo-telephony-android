/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony

import com.google.firebase.messaging.RemoteMessage
import com.sudoplatform.sudotelephony.internal.data.notification.MessageReceivedFixture
import com.sudoplatform.sudotelephony.internal.data.notification.NotificationCrypto
import com.sudoplatform.sudotelephony.types.PhoneMessageReceivedNotification
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import java.util.Date

/**
 * Test [SudoTelephonyNotifiableClient] and [DefaultSudoTelephonyNotifiableClient].
 */
@RunWith(RobolectricTestRunner::class)
class SudoTelephonyNotifiableClientTest : BaseTests() {
    private val keyPair by before { NotificationCrypto.generateKeyPair() }

    private val mockDeviceKeyManager by before { NotificationCrypto.deviceKeyManager(keyPair) }

    private val mockHandler by before { mock<SudoTelephonyNotificationHandler>() }

    private val instanceUnderTest by before {
        DefaultSudoTelephonyNotifiableClient(mockDeviceKeyManager, mockHandler, mockLogger)
    }

    private val expectedNotification =
        PhoneMessageReceivedNotification(
            messageId = "tel-pmg-1",
            owner = "owner-1",
            sudoId = "sudo-1",
            phoneNumberId = "tel-pnm-1",
            conversationId = "tel-cnv-" + "a".repeat(64),
            remotePhoneNumber = "+12025550111",
            bodyPreview = "Hello",
            bodyTruncated = false,
            mediaOmitted = false,
            receivedAt = Date(1780000000000),
        )

    private fun remoteMessage(data: Map<String, String>) =
        mock<RemoteMessage>().stub {
            on { this.data } doReturn data
        }

    private fun fixtureMessage() =
        remoteMessage(
            MessageReceivedFixture.pushData(NotificationCrypto.seal(keyPair, MessageReceivedFixture.plaintextJson)),
        )

    @Test
    fun `serviceName should be telephonyService`() {
        instanceUnderTest.serviceName shouldBe "telephonyService"
    }

    @Test
    fun `getSchema() should describe the telephony filter fields`() {
        val schema = instanceUnderTest.getSchema()

        schema.serviceName shouldBe "telephonyService"
        schema.schema shouldHaveSize 5
        schema.schema.map { it.fieldName } shouldBe
            listOf("meta.type", "meta.phoneNumberId", "meta.sudoId", "meta.conversationId", "meta.keyId")
        schema.schema.forEach { it.type shouldBe "string" }
    }

    @Test
    fun `processPayload() should pass a decoded notification to the handler once`() {
        instanceUnderTest.processPayload(fixtureMessage())

        verify(mockHandler).onPhoneMessageReceived(expectedNotification)
    }

    @Test
    fun `processPayload() should not call the handler for a dropped payload`() {
        mockDeviceKeyManager.stub {
            on { privateKeyExists(any()) } doReturn false
        }

        instanceUnderTest.processPayload(fixtureMessage())
        instanceUnderTest.processPayload(remoteMessage(mapOf("sudoplatform" to "not json")))
        instanceUnderTest.processPayload(remoteMessage(emptyMap()))

        verify(mockHandler, never()).onPhoneMessageReceived(any())
    }

    @Test
    fun `processPayload() should not throw when the message data can't be read`() {
        val message =
            mock<RemoteMessage>().stub {
                on { data } doThrow RuntimeException("unreadable")
            }

        instanceUnderTest.processPayload(message)

        verify(mockHandler, never()).onPhoneMessageReceived(any())
    }

    @Test
    fun `processPayload() should propagate an exception thrown by the handler`() {
        mockHandler.stub {
            on { onPhoneMessageReceived(any()) } doThrow IllegalStateException("app failure")
        }

        shouldThrow<IllegalStateException> {
            instanceUnderTest.processPayload(fixtureMessage())
        }
    }

    @Test
    fun `build() should throw without a context`() {
        shouldThrow<NullPointerException> {
            SudoTelephonyNotifiableClient
                .builder()
                .setNotificationHandler(mockHandler)
                .build()
        }
    }

    @Test
    fun `build() should throw without a notification handler`() {
        shouldThrow<NullPointerException> {
            SudoTelephonyNotifiableClient
                .builder()
                .setContext(mockContext)
                .build()
        }
    }
}
