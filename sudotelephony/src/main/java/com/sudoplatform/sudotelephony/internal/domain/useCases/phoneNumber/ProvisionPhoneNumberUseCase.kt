/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumber

import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneNumberException
import com.sudoplatform.sudotelephony.internal.data.common.StringConstants
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.ProvisionPhoneNumberRequest
import com.sudoplatform.sudotelephony.keys.DeviceKeyManager.DeviceKeyManagerException
import com.sudoplatform.sudotelephony.keys.KeyPair
import com.sudoplatform.sudotelephony.keys.ServiceKeyManager

/**
 * Input for [ProvisionPhoneNumberUseCase].
 */
internal data class ProvisionPhoneNumberUseCaseInput(
    val searchId: String,
    val phoneNumber: String,
    val ownershipProofToken: String? = null,
    val clientRefId: String? = null,
)

/**
 * Application business logic for provisioning a phone number.
 *
 * Gets the key pair that seals the number's messages before handing the request to the
 * service, so the private key is stored on the device and only the public key is registered.
 * The key pair is the shared singleton key pair when [enforceSingletonKeyPair] is set, which
 * must already exist on the device, and otherwise a new key pair for this number, which is
 * removed again if the service definitely rejects the request.
 *
 * @property phoneNumberService [PhoneNumberService] The domain port used to provision.
 * @property serviceKeyManager [ServiceKeyManager] Reads or generates the key pair.
 * @property logger [Logger] The SDK logger.
 * @property enforceSingletonKeyPair [Boolean] Whether every phone number shares one key pair.
 */
internal class ProvisionPhoneNumberUseCase(
    private val phoneNumberService: PhoneNumberService,
    private val serviceKeyManager: ServiceKeyManager,
    private val logger: Logger,
    private val enforceSingletonKeyPair: Boolean = false,
) {
    /**
     * Provision the number.
     *
     * @param input [ProvisionPhoneNumberUseCaseInput] The number to provision.
     * @return The provisioned [PhoneNumberEntity].
     */
    suspend fun execute(input: ProvisionPhoneNumberUseCaseInput): PhoneNumberEntity {
        logger.debug(this::class.java.simpleName)

        val keyPair: KeyPair =
            try {
                if (enforceSingletonKeyPair) {
                    serviceKeyManager.getSingletonKeyPair()
                } else {
                    serviceKeyManager.generateKeyPair()
                }
            } catch (e: DeviceKeyManagerException) {
                logger.error("unexpected error $e")
                throw PhoneNumberException.UnknownException(e)
            } ?: throw PhoneNumberException.SingletonKeyPairNotFoundException(
                StringConstants.SINGLETON_KEY_PAIR_NOT_FOUND_MSG,
            )

        try {
            return phoneNumberService.provision(
                ProvisionPhoneNumberRequest(
                    searchId = input.searchId,
                    phoneNumber = input.phoneNumber,
                    keyPair = keyPair,
                    ownershipProofToken = input.ownershipProofToken,
                    clientRefId = input.clientRefId,
                ),
            )
        } catch (e: Throwable) {
            if (!enforceSingletonKeyPair && e.isDefinitiveRejection) {
                removeKeyPair(keyPair.keyId)
            }
            throw e
        }
    }

    /** Removes a key pair the service rejected, logging rather than throwing on failure. */
    private fun removeKeyPair(keyId: String) {
        try {
            serviceKeyManager.removeKeyPair(keyId)
        } catch (e: Exception) {
            logger.warning("Couldn't remove key pair $keyId after a rejected provision; it stays on this device: $e")
        }
    }
}
