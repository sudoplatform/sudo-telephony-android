/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumber

import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberService
import com.sudoplatform.sudotelephony.keys.ServiceKeyManager

/**
 * Application business logic for releasing a phone number.
 *
 * Unless [enforceSingletonKeyPair] is set, the released number's key pairs are then removed
 * from the device.
 *
 * @property phoneNumberService [PhoneNumberService] The domain port used to release.
 * @property serviceKeyManager [ServiceKeyManager] Removes the released number's key pairs.
 * @property logger [Logger] The SDK logger.
 * @property enforceSingletonKeyPair [Boolean] Whether every phone number shares one key pair.
 */
internal class DeprovisionPhoneNumberUseCase(
    private val phoneNumberService: PhoneNumberService,
    private val serviceKeyManager: ServiceKeyManager,
    private val logger: Logger,
    private val enforceSingletonKeyPair: Boolean = false,
) {
    /**
     * Release the number.
     *
     * @param id [String] ID of the phone number to release.
     * @return The released [PhoneNumberEntity].
     * @throws DeprovisionedKeyPairRemovalException if the number was released but one of its key
     *  pairs could not be removed. Every key pair is attempted first.
     */
    suspend fun execute(id: String): PhoneNumberEntity {
        logger.debug(this::class.java.simpleName)
        val phoneNumber = phoneNumberService.deprovision(id)
        if (!enforceSingletonKeyPair) {
            // removeKeyPair skips the singleton key pair in case the app changed modes.
            removeKeyPairs(phoneNumber)
        }
        return phoneNumber
    }

    private fun removeKeyPairs(deprovisioned: PhoneNumberEntity) {
        var firstError: Exception? = null
        deprovisioned.keyIds.forEach { keyId ->
            try {
                serviceKeyManager.removeKeyPair(keyId)
            } catch (e: Exception) {
                logger.error(
                    "Couldn't remove key pair $keyId after deprovisioning phone number ${deprovisioned.id}; " +
                        "it stays on this device: $e",
                )
                firstError = firstError ?: e
            }
        }
        firstError?.let { throw DeprovisionedKeyPairRemovalException(deprovisioned, it) }
    }
}

/**
 * The phone number [deprovisioned] was released, but this device could not remove one of its key
 * pairs.
 *
 * @property deprovisioned [PhoneNumberEntity] The released number.
 */
internal class DeprovisionedKeyPairRemovalException(
    val deprovisioned: PhoneNumberEntity,
    cause: Throwable,
) : RuntimeException(cause)
