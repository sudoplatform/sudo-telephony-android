/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumber

import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.ProvisionPhoneNumberRequest
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
 * Generates the key pair that seals the number's messages before handing the request to the
 * service, so the private key is stored on the device and only the public key is registered.
 *
 * @property phoneNumberService [PhoneNumberService] The domain port used to provision.
 * @property serviceKeyManager [ServiceKeyManager] Generates and stores the key pair.
 * @property logger [Logger] The SDK logger.
 */
internal class ProvisionPhoneNumberUseCase(
    private val phoneNumberService: PhoneNumberService,
    private val serviceKeyManager: ServiceKeyManager,
    private val logger: Logger,
) {
    /**
     * Provision the number.
     *
     * @param input [ProvisionPhoneNumberUseCaseInput] The number to provision.
     * @return The provisioned [PhoneNumberEntity].
     */
    suspend fun execute(input: ProvisionPhoneNumberUseCaseInput): PhoneNumberEntity {
        logger.debug(this::class.java.simpleName)

        val keyPair: KeyPair = serviceKeyManager.generateKeyPair()

        return phoneNumberService.provision(
            ProvisionPhoneNumberRequest(
                searchId = input.searchId,
                phoneNumber = input.phoneNumber,
                keyPair = keyPair,
                ownershipProofToken = input.ownershipProofToken,
                clientRefId = input.clientRefId,
            ),
        )
    }
}
