/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.notification

import com.amazonaws.util.Base64
import com.sudoplatform.sudologging.LogDetails
import com.sudoplatform.sudologging.LogDriverInterface
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudotelephony.keys.DeviceKeyManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import java.security.KeyPair
import java.security.KeyPairGenerator
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Log driver that records every message logged through it. */
internal class CapturingLogDriver : LogDriverInterface {
    val messages = mutableListOf<String>()

    override var logLevel: LogLevel = LogLevel.VERBOSE

    override fun processDetails(details: LogDetails): String = processDetailsDefault(details)

    override fun outputMessage(message: String) {
        messages.add(message)
    }

    override fun outputError(
        error: Error,
        userInfo: Map<String, Any>?,
    ) {
        messages.add(error.toString())
    }
}

/** Seals and unseals notification content the way the telephony service and device do. */
internal object NotificationCrypto {
    private const val TAG_SIZE_BITS = 128
    private val nonce = ByteArray(12) { (it + 1).toByte() }

    fun generateKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    /** Seals [plaintext] as `base64(RSA-OAEP(aesKey) || nonce || AES-256-GCM(plaintext) || tag)`. */
    fun seal(
        keyPair: KeyPair,
        plaintext: String,
    ): String {
        val aesKey = ByteArray(32) { (it * 7).toByte() }
        val encryptedData =
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(TAG_SIZE_BITS, nonce))
                doFinal(plaintext.toByteArray(Charsets.UTF_8))
            }
        val encryptedKey =
            Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding").run {
                init(Cipher.ENCRYPT_MODE, keyPair.public)
                doFinal(aesKey)
            }
        return String(Base64.encode(encryptedKey + nonce + encryptedData), Charsets.UTF_8)
    }

    /** A [DeviceKeyManager] that holds every key ID and decrypts with [keyPair]'s private key. */
    fun deviceKeyManager(keyPair: KeyPair): DeviceKeyManager =
        mock<DeviceKeyManager>().stub {
            on { privateKeyExists(any()) } doReturn true
            on { decryptWithKeyPairId(any(), any(), any()) } doAnswer { invocation ->
                Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding").run {
                    init(Cipher.DECRYPT_MODE, keyPair.private)
                    doFinal(invocation.getArgument<ByteArray>(0))
                }
            }
            on { decryptWithSymmetricKey(any(), any(), any(), any()) } doAnswer { invocation ->
                Cipher.getInstance("AES/GCM/NoPadding").run {
                    init(
                        Cipher.DECRYPT_MODE,
                        SecretKeySpec(invocation.getArgument<ByteArray>(0), "AES"),
                        GCMParameterSpec(TAG_SIZE_BITS, invocation.getArgument<ByteArray>(2)),
                    )
                    doFinal(invocation.getArgument<ByteArray>(1))
                }
            }
        }
}

/** The `push/v1/message-received.json` fixture, and builders for the push data it describes. */
internal object MessageReceivedFixture {
    private val fixture: JsonObject by lazy {
        val text =
            checkNotNull(javaClass.classLoader?.getResource("push/v1/message-received.json")) {
                "push/v1/message-received.json not on the test classpath"
            }.readText()
        Json.parseToJsonElement(text).jsonObject
    }

    /** The payload fields other than `sealed`. */
    val payload: JsonObject get() = fixture.getValue("payload").jsonObject

    /** The sealed content, as an object. */
    val plaintext: JsonObject get() = fixture.getValue("plaintext").jsonObject

    /** [plaintext] as the compact JSON string the service seals. */
    val plaintextJson: String get() = plaintext.toString()

    fun JsonObject.with(vararg entries: Pair<String, JsonElement>): JsonObject = JsonObject(this + entries)

    fun JsonObject.without(vararg keys: String): JsonObject = JsonObject(this - keys.toSet())

    /** Push data whose envelope carries [payload] as its `data` string. */
    fun pushData(
        payload: JsonObject,
        servicename: String = "telephonyService",
        envelopeExtras: Map<String, JsonElement> = emptyMap(),
    ): Map<String, String> {
        val envelope =
            JsonObject(
                mapOf(
                    "servicename" to JsonPrimitive(servicename),
                    "data" to JsonPrimitive(payload.toString()),
                ) + envelopeExtras,
            )
        return mapOf(TelephonyNotificationDecoder.SUDO_PLATFORM_KEY to envelope.toString())
    }

    /** Push data for the fixture's payload, with [sealed] as its sealed content. */
    fun pushData(sealed: String): Map<String, String> = pushData(payload.with("sealed" to JsonPrimitive(sealed)))
}
