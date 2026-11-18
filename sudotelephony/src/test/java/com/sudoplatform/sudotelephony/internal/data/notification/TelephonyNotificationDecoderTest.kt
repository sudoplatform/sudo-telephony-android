/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.notification

import com.amazonaws.util.Base64
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.BaseTests
import com.sudoplatform.sudotelephony.internal.data.notification.MessageReceivedFixture.with
import com.sudoplatform.sudotelephony.internal.data.notification.MessageReceivedFixture.without
import com.sudoplatform.sudotelephony.keys.DeviceKeyManager
import com.sudoplatform.sudotelephony.types.PhoneMessageReceivedNotification
import io.kotlintest.matchers.collections.shouldNotBeEmpty
import io.kotlintest.matchers.string.shouldNotContain
import io.kotlintest.shouldBe
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import java.util.Date

/**
 * Test [TelephonyNotificationDecoder] against push data built from the service's
 * `push/v1/message-received.json` fixture, sealed at runtime with a test key.
 */
@RunWith(RobolectricTestRunner::class)
class TelephonyNotificationDecoderTest : BaseTests() {
    private val keyPair by before { NotificationCrypto.generateKeyPair() }

    private val mockDeviceKeyManager by before { NotificationCrypto.deviceKeyManager(keyPair) }

    private val logDriver by before { CapturingLogDriver() }

    private val instanceUnderTest by before {
        TelephonyNotificationDecoder(mockDeviceKeyManager, Logger("test", logDriver))
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

    private fun seal(plaintext: String) = NotificationCrypto.seal(keyPair, plaintext)

    private fun sealedFixture() = seal(MessageReceivedFixture.plaintextJson)

    private fun payloadWith(
        sealed: String,
        vararg changes: Pair<String, JsonElement>,
    ): JsonObject = MessageReceivedFixture.payload.with("sealed" to JsonPrimitive(sealed), *changes)

    /** Flips a bit in the authentication tag of [sealed]. */
    private fun corrupt(sealed: String): String {
        val bytes = Base64.decode(sealed)
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x01).toByte()
        return String(Base64.encode(bytes), Charsets.UTF_8)
    }

    private fun verifyNoDecryption() {
        verify(mockDeviceKeyManager, never()).decryptWithKeyPairId(any(), any(), any())
        verify(mockDeviceKeyManager, never()).decryptWithSymmetricKey(any(), any(), any(), any())
    }

    @Test
    fun `decode() should map every field of the fixture`() {
        val result = instanceUnderTest.decode(MessageReceivedFixture.pushData(sealedFixture()))

        result shouldBe DecodedNotification.MessageReceived(expectedNotification)
        verify(mockDeviceKeyManager).privateKeyExists("key-1")
        verify(mockDeviceKeyManager).decryptWithKeyPairId(any(), eq("key-1"), any())
        logDriver.messages.forEach { it shouldNotContain "payload version" }
    }

    @Test
    fun `decode() should give a null sudoId when the plaintext omits it`() {
        val plaintext = MessageReceivedFixture.plaintext.without("sudoId")

        val result = instanceUnderTest.decode(MessageReceivedFixture.pushData(seal(plaintext.toString())))

        result shouldBe DecodedNotification.MessageReceived(expectedNotification.copy(sudoId = null))
    }

    @Test
    fun `decode() should ignore unknown fields in the envelope, payload and plaintext`() {
        val plaintext =
            MessageReceivedFixture.plaintext.with(
                "futurePlaintextField" to JsonPrimitive("x"),
                "futureObject" to JsonObject(mapOf("nested" to JsonPrimitive(1))),
            )
        val payload =
            payloadWith(
                seal(plaintext.toString()),
                "futurePayloadField" to JsonArray(listOf(JsonPrimitive(1))),
            )
        val data =
            MessageReceivedFixture.pushData(
                payload,
                envelopeExtras = mapOf("futureEnvelopeField" to JsonPrimitive(true)),
            )

        instanceUnderTest.decode(data) shouldBe DecodedNotification.MessageReceived(expectedNotification)
    }

    @Test
    fun `decode() should drop an unknown type without touching the key manager`() {
        val payload = payloadWith(sealedFixture(), "type" to JsonPrimitive("callReceived"))

        instanceUnderTest.decode(MessageReceivedFixture.pushData(payload)) shouldBe null

        verifyNoInteractions(mockDeviceKeyManager)
    }

    @Test
    fun `decode() should deliver a newer version whose body is v1 compatible`() {
        val payload = payloadWith(sealedFixture(), "version" to JsonPrimitive(2))

        instanceUnderTest.decode(MessageReceivedFixture.pushData(payload)) shouldBe
            DecodedNotification.MessageReceived(expectedNotification)
        logDriver.messages.filter { it.contains("payload version 2, expected 1") }.size shouldBe 1
    }

    @Test
    fun `decode() should warn and still deliver a payload without a version`() {
        val payload = payloadWith(sealedFixture()).without("version")

        instanceUnderTest.decode(MessageReceivedFixture.pushData(payload)) shouldBe
            DecodedNotification.MessageReceived(expectedNotification)
        logDriver.messages.filter { it.contains("payload version null, expected 1") }.size shouldBe 1
    }

    @Test
    fun `decode() should drop a newer version whose payload is incompatible`() {
        val payload =
            payloadWith(
                sealedFixture(),
                "version" to JsonPrimitive(2),
                "keyId" to JsonObject(mapOf("id" to JsonPrimitive("key-1"))),
            )

        instanceUnderTest.decode(MessageReceivedFixture.pushData(payload)) shouldBe null
    }

    @Test
    fun `decode() should drop a newer version whose sealed content is incompatible`() {
        val plaintext =
            MessageReceivedFixture.plaintext.with("receivedAtEpochMs" to JsonPrimitive("2026-05-28T00:00:00Z"))
        val payload = payloadWith(seal(plaintext.toString()), "version" to JsonPrimitive(2))

        instanceUnderTest.decode(MessageReceivedFixture.pushData(payload)) shouldBe null
    }

    @Test
    fun `decode() should drop a payload missing keyId, algorithm or sealed`() {
        val complete = payloadWith(sealedFixture())
        for (field in listOf("keyId", "algorithm", "sealed")) {
            instanceUnderTest.decode(MessageReceivedFixture.pushData(complete.without(field))) shouldBe null
        }

        verify(mockDeviceKeyManager, never()).privateKeyExists(any())
        verifyNoDecryption()
    }

    @Test
    fun `decode() should drop a payload sealed with a key the device doesn't hold`() {
        mockDeviceKeyManager.stub {
            on { privateKeyExists(any()) } doReturn false
        }

        instanceUnderTest.decode(MessageReceivedFixture.pushData(sealedFixture())) shouldBe null

        verify(mockDeviceKeyManager).privateKeyExists("key-1")
        verifyNoDecryption()
    }

    @Test
    fun `decode() should drop an unsupported algorithm`() {
        val payload = payloadWith(sealedFixture(), "algorithm" to JsonPrimitive("SomeFutureAlgorithm"))

        instanceUnderTest.decode(MessageReceivedFixture.pushData(payload)) shouldBe null

        verifyNoDecryption()
    }

    @Test
    fun `decode() should drop corrupted sealed data`() {
        instanceUnderTest.decode(MessageReceivedFixture.pushData(corrupt(sealedFixture()))) shouldBe null
    }

    @Test
    fun `decode() should drop content sealed with a different key`() {
        val otherKeyPair = NotificationCrypto.generateKeyPair()
        val sealed = NotificationCrypto.seal(otherKeyPair, MessageReceivedFixture.plaintextJson)

        instanceUnderTest.decode(MessageReceivedFixture.pushData(sealed)) shouldBe null
    }

    @Test
    fun `decode() should drop plaintext whose type doesn't match the payload's`() {
        val plaintext = MessageReceivedFixture.plaintext.with("type" to JsonPrimitive("messageSent"))

        instanceUnderTest.decode(MessageReceivedFixture.pushData(seal(plaintext.toString()))) shouldBe null
    }

    @Test
    fun `decode() should drop plaintext missing a required field`() {
        val plaintext = MessageReceivedFixture.plaintext.without("messageId")

        instanceUnderTest.decode(MessageReceivedFixture.pushData(seal(plaintext.toString()))) shouldBe null
    }

    @Test
    fun `decode() should drop a push for another service`() {
        val data = MessageReceivedFixture.pushData(payloadWith(sealedFixture()), servicename = "emailService")

        instanceUnderTest.decode(data) shouldBe null

        verifyNoInteractions(mockDeviceKeyManager)
    }

    @Test
    fun `decode() should not log message content, sealed data or input`() {
        val marker = "PLANTED-MARKER-7f3a9c"
        val sealedValues = mutableListOf<String>()

        fun sealTracked(plaintext: String) = seal(plaintext).also { sealedValues.add(it) }

        val inputs =
            listOf(
                // Happy path.
                MessageReceivedFixture.pushData(sealTracked(MessageReceivedFixture.plaintextJson)),
                // Plaintext type mismatch.
                MessageReceivedFixture.pushData(
                    sealTracked(MessageReceivedFixture.plaintext.with("type" to JsonPrimitive("other")).toString()),
                ),
                // Plaintext missing a field.
                MessageReceivedFixture.pushData(
                    sealTracked(MessageReceivedFixture.plaintext.without("messageId").toString()),
                ),
                // Malformed plaintext JSON.
                MessageReceivedFixture.pushData(
                    sealTracked(MessageReceivedFixture.plaintextJson.dropLast(1) + ",\"$marker\":"),
                ),
                // Unsupported algorithm.
                MessageReceivedFixture.pushData(
                    payloadWith(
                        sealTracked(MessageReceivedFixture.plaintextJson),
                        "algorithm" to JsonPrimitive("SomeFutureAlgorithm"),
                    ),
                ),
                // Corrupted sealed data.
                MessageReceivedFixture.pushData(
                    corrupt(sealTracked(MessageReceivedFixture.plaintextJson)).also { sealedValues.add(it) },
                ),
                // Malformed envelope JSON.
                mapOf(TelephonyNotificationDecoder.SUDO_PLATFORM_KEY to "{\"servicename\":\"telephonyService\",\"data\":\"$marker"),
                // Malformed payload JSON.
                mapOf(
                    TelephonyNotificationDecoder.SUDO_PLATFORM_KEY to
                        JsonObject(
                            mapOf(
                                "servicename" to JsonPrimitive("telephonyService"),
                                "data" to JsonPrimitive("{\"type\":\"messageReceived\",\"sealed\":\"$marker"),
                            ),
                        ).toString(),
                ),
            )

        inputs.forEach { instanceUnderTest.decode(it) }

        // The wrong key and the key-not-held cases use the same sealed fixture.
        val wrongKeySealed =
            NotificationCrypto.seal(NotificationCrypto.generateKeyPair(), MessageReceivedFixture.plaintextJson)
        sealedValues.add(wrongKeySealed)
        instanceUnderTest.decode(MessageReceivedFixture.pushData(wrongKeySealed))
        mockDeviceKeyManager.stub { on { privateKeyExists(any()) } doReturn false }
        instanceUnderTest.decode(MessageReceivedFixture.pushData(sealTracked(MessageReceivedFixture.plaintextJson)))

        val logs = logDriver.messages
        logs.shouldNotBeEmpty()
        logs.forEach { message ->
            message shouldNotContain "Hello"
            message shouldNotContain "+12025550111"
            message shouldNotContain MessageReceivedFixture.plaintextJson
            message shouldNotContain marker
            sealedValues.forEach { message shouldNotContain it }
        }
    }
}

/**
 * Test that [TelephonyNotificationDecoder.decode] returns `null` rather than throwing for
 * malformed input and key store failures.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class TelephonyNotificationDecoderGarbageInputTest(
    @Suppress("unused") private val description: String,
    private val case: GarbageCase,
) : BaseTests() {
    /**
     * @property data Builds the push data from a validly sealed fixture.
     * @property setUp Adjusts the key manager before decoding.
     */
    internal class GarbageCase(
        val data: (sealed: String) -> Map<String, String>,
        val setUp: (DeviceKeyManager) -> Unit = {},
    )

    companion object {
        private const val KEY = TelephonyNotificationDecoder.SUDO_PLATFORM_KEY

        private fun envelopeWithData(data: String) =
            mapOf(
                KEY to
                    JsonObject(
                        mapOf("servicename" to JsonPrimitive("telephonyService"), "data" to JsonPrimitive(data)),
                    ).toString(),
            )

        private fun payload(
            sealed: String,
            vararg changes: Pair<String, JsonElement>,
        ) = MessageReceivedFixture.pushData(
            MessageReceivedFixture.payload.with("sealed" to JsonPrimitive(sealed), *changes),
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> =
            listOf(
                "empty map" to GarbageCase({ emptyMap() }),
                "no sudoplatform key" to GarbageCase({ mapOf("other" to "value") }),
                "non-JSON envelope" to GarbageCase({ mapOf(KEY to "not json {") }),
                "empty envelope object" to GarbageCase({ mapOf(KEY to "{}") }),
                "envelope is a JSON array" to GarbageCase({ mapOf(KEY to "[1,2]") }),
                "data is not JSON" to GarbageCase({ envelopeWithData("not json {") }),
                "data is a JSON array" to GarbageCase({ envelopeWithData("[1,2,3]") }),
                "data is an empty object" to GarbageCase({ envelopeWithData("{}") }),
                "type is a number" to GarbageCase({ payload(it, "type" to JsonPrimitive(5)) }),
                "keyId is null" to GarbageCase({ payload(it, "keyId" to JsonNull) }),
                "sealed is not base64" to GarbageCase({ payload("%%% not base64 %%%") }),
                "sealed is too short" to GarbageCase({ payload("AAAA") }),
                "privateKeyExists throws" to
                    GarbageCase({ payload(it) }) { keyManager ->
                        keyManager.stub {
                            on { privateKeyExists(any()) } doThrow
                                DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException("boom")
                        }
                    },
                "decryptWithKeyPairId throws" to
                    GarbageCase({ payload(it) }) { keyManager ->
                        keyManager.stub {
                            on { decryptWithKeyPairId(any(), any(), any()) } doThrow RuntimeException("boom")
                        }
                    },
                "decryptWithSymmetricKey throws" to
                    GarbageCase({ payload(it) }) { keyManager ->
                        keyManager.stub {
                            on { decryptWithSymmetricKey(any(), any(), any(), any()) } doThrow RuntimeException("boom")
                        }
                    },
            ).map { (name, case) -> arrayOf(name, case) }
    }

    private val keyPair by before { NotificationCrypto.generateKeyPair() }

    private val mockDeviceKeyManager by before { NotificationCrypto.deviceKeyManager(keyPair) }

    @Test
    fun `decode() should return null without throwing`() {
        case.setUp(mockDeviceKeyManager)
        val data = case.data(NotificationCrypto.seal(keyPair, MessageReceivedFixture.plaintextJson))

        TelephonyNotificationDecoder(mockDeviceKeyManager, mockLogger).decode(data) shouldBe null
    }
}
