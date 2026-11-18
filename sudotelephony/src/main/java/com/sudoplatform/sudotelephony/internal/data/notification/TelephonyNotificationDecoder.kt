/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.notification

import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.SudoTelephonyClient
import com.sudoplatform.sudotelephony.internal.data.common.mechanisms.Unsealer
import com.sudoplatform.sudotelephony.internal.data.notification.transformers.PhoneMessageReceivedNotificationTransformer
import com.sudoplatform.sudotelephony.keys.DeviceKeyManager
import com.sudoplatform.sudotelephony.types.PhoneMessageReceivedNotification

/** A telephony push notification decoded to its public type. */
internal sealed interface DecodedNotification {
    data class MessageReceived(
        val notification: PhoneMessageReceivedNotification,
    ) : DecodedNotification
}

/**
 * Decodes the data of a telephony push notification.
 *
 * Decoding never throws. A payload that can't be decoded, whose type this SDK version doesn't
 * know, or that is sealed with a key this device doesn't hold, is logged and dropped. A payload
 * whose version isn't [TelephonyNotificationData.SUPPORTED_VERSION] is logged as a warning and
 * still decoded.
 *
 * Log output names only the service name, the notification type, the payload version, the key
 * ID and resource IDs.
 * Message content, the sealed data, the unsealed JSON and exception messages, which can quote
 * the input, are never logged.
 *
 * @property deviceKeyManager [DeviceKeyManager] Holds the phone number keys payloads are sealed
 *  with.
 * @property logger [Logger] Errors and diagnostic information are logged here.
 */
internal class TelephonyNotificationDecoder(
    private val deviceKeyManager: DeviceKeyManager,
    private val logger: Logger,
) {
    companion object {
        /** Key of the push data entry the platform notification service puts the payload in. */
        const val SUDO_PLATFORM_KEY = "sudoplatform"
    }

    private val unsealer = Unsealer(deviceKeyManager)

    /**
     * Decode the data of a push.
     *
     * @param data [Map] The push's data, as delivered by Firebase Cloud Messaging.
     * @return The decoded notification, or `null` if the push was dropped.
     */
    fun decode(data: Map<String, String>): DecodedNotification? =
        try {
            decodeOrNull(data)
        } catch (e: Exception) {
            logger.error("Dropping notification that could not be decoded: ${e.javaClass.simpleName}")
            null
        }

    private fun decodeOrNull(data: Map<String, String>): DecodedNotification? {
        val envelopeJson = data[SUDO_PLATFORM_KEY]
        if (envelopeJson == null) {
            logger.error("Dropping notification without a $SUDO_PLATFORM_KEY entry")
            return null
        }
        val envelope = NOTIFICATION_JSON.decodeFromString(SudoPlatformEnvelope.serializer(), envelopeJson)
        if (envelope.servicename != SudoTelephonyClient.CONFIG_TELEPHONY_SERVICE) {
            logger.error("Dropping notification for service ${envelope.servicename}")
            return null
        }

        val payload = NOTIFICATION_JSON.decodeFromString(TelephonyNotificationData.serializer(), envelope.data)
        if (payload.type !in KNOWN_NOTIFICATION_TYPES) {
            logger.info("Dropping notification of unknown type ${payload.type}")
            return null
        }
        if (payload.version != TelephonyNotificationData.SUPPORTED_VERSION) {
            logger.warning(
                "Decoding notification of type ${payload.type} with payload version ${payload.version}, " +
                    "expected ${TelephonyNotificationData.SUPPORTED_VERSION}",
            )
        }
        val keyId = payload.keyId
        val algorithm = payload.algorithm
        val sealed = payload.sealed
        if (keyId == null || algorithm == null || sealed == null) {
            logger.error("Dropping unsealed notification of type ${payload.type}")
            return null
        }
        if (!deviceKeyManager.privateKeyExists(keyId)) {
            logger.info("Dropping notification of type ${payload.type} sealed with key $keyId, which this device doesn't hold")
            return null
        }

        val content = unsealer.unseal(keyId, sealed, algorithm)

        return when (payload.type) {
            MessageReceivedPayload.TYPE -> decodeMessageReceived(content)
            else -> null
        }
    }

    private fun decodeMessageReceived(content: String): DecodedNotification? {
        val payload = NOTIFICATION_JSON.decodeFromString(MessageReceivedPayload.serializer(), content)
        if (payload.type != MessageReceivedPayload.TYPE) {
            logger.error("Dropping notification whose sealed type ${payload.type} doesn't match ${MessageReceivedPayload.TYPE}")
            return null
        }
        logger.debug(
            "Decoded ${payload.type} notification: phoneNumberId=${payload.phoneNumberId}, " +
                "messageId=${payload.messageId}, conversationId=${payload.conversationId}",
        )
        return DecodedNotification.MessageReceived(
            PhoneMessageReceivedNotificationTransformer.fromPayloadToApi(payload),
        )
    }
}
