/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.phoneMessage

import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneMessageException
import com.sudoplatform.sudotelephony.internal.data.common.StringConstants
import com.sudoplatform.sudotelephony.internal.data.common.mechanisms.Unsealer
import com.sudoplatform.sudotelephony.internal.data.phoneMessage.transformers.PhoneMessageTransformer
import com.sudoplatform.sudotelephony.internal.domain.entities.common.PartialResultEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PartialPhoneMessageEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageUnsealer
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.SealedPhoneMessageEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.UnsealedPhoneMessages
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberService
import com.sudoplatform.sudotelephony.keys.ServiceKeyManager

/**
 * Implementation of [PhoneMessageUnsealer] that resolves the sealing key from the phone
 * number's key ring.
 *
 * The service seals a message with the public keys of the key ring it registered when the
 * number was provisioned, and the GraphQL message carries no key identifier, so the key ring
 * is read and whichever of its keys this device holds the private half of is used.
 *
 * @property phoneNumberService [PhoneNumberService] Reads the number's key ring.
 * @property serviceKeyManager [ServiceKeyManager] Says which private keys this device holds.
 * @property unsealer [Unsealer] Performs the decryption.
 * @property logger [Logger] Errors and diagnostic information are logged here.
 */
internal class DefaultPhoneMessageUnsealer(
    private val phoneNumberService: PhoneNumberService,
    private val serviceKeyManager: ServiceKeyManager,
    private val unsealer: Unsealer,
    private val logger: Logger,
) : PhoneMessageUnsealer {
    override suspend fun unseal(
        phoneNumberId: String,
        sealed: List<SealedPhoneMessageEntity>,
    ): UnsealedPhoneMessages {
        if (sealed.isEmpty()) {
            return UnsealedPhoneMessages(unsealed = emptyList(), failed = emptyList())
        }

        val keyId = resolveKeyId(phoneNumberId)
        val unsealed = mutableListOf<PhoneMessageEntity>()
        val failed = mutableListOf<PartialResultEntity<PartialPhoneMessageEntity>>()
        for (message in sealed) {
            try {
                unsealed.add(unsealOne(message, keyId))
            } catch (e: PhoneMessageException.UnsealingFailedException) {
                failed.add(
                    PartialResultEntity(PhoneMessageTransformer.fromSealedToPartialEntity(message), e),
                )
            }
        }
        return UnsealedPhoneMessages(unsealed = unsealed, failed = failed)
    }

    private suspend fun resolveKeyId(phoneNumberId: String): String {
        val phoneNumber =
            phoneNumberService.get(phoneNumberId)
                ?: throw PhoneMessageException.PhoneNumberNotFoundException(
                    StringConstants.PHONE_NUMBER_NOT_FOUND_MSG,
                )

        return phoneNumber.keyIds.firstOrNull { serviceKeyManager.privateKeyExists(it) }
            ?: throw PhoneMessageException.KeyNotFoundException(
                StringConstants.PHONE_MESSAGE_KEY_NOT_FOUND_MSG,
            )
    }

    private fun unsealOne(
        sealed: SealedPhoneMessageEntity,
        keyId: String,
    ): PhoneMessageEntity {
        try {
            return PhoneMessageTransformer.fromSealedToUnsealedEntity(
                sealed = sealed,
                // A withheld number is absent rather than sealed, so it stays null.
                remotePhoneNumber = sealed.remotePhoneNumber?.let { unsealer.unseal(keyId, it) },
                body = unsealer.unseal(keyId, sealed.body),
            )
        } catch (e: Throwable) {
            logger.error("failed to unseal message ${sealed.id}: $e")
            throw PhoneMessageException.UnsealingFailedException(
                "Could not unseal message ${sealed.id}",
                e,
            )
        }
    }
}
