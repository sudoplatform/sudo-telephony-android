/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.useCases.keys

import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.SudoTelephonyClient.CryptographicKeysException
import com.sudoplatform.sudotelephony.internal.data.common.StringConstants
import com.sudoplatform.sudotelephony.keys.ServiceKeyManager
import com.sudoplatform.sudotelephony.keys.SingletonKeyPairResult

/**
 * Application business logic for creating the singleton key pair if this device does not have
 * it.
 *
 * @property serviceKeyManager [ServiceKeyManager] Reads and creates the singleton key pair.
 * @property enforceSingletonKeyPair [Boolean] Whether every phone number shares one key pair.
 * @property logger [Logger] The SDK logger.
 */
internal class CreateSingletonKeysIfAbsentUseCase(
    private val serviceKeyManager: ServiceKeyManager,
    private val enforceSingletonKeyPair: Boolean,
    private val logger: Logger,
) {
    /**
     * Create the singleton key pair if this device does not have it.
     *
     * @return The singleton key pair, and whether it had to be created.
     * @throws CryptographicKeysException.InvalidConfigurationException if the client does not
     *  enforce a singleton key pair, since each number then gets its own key pair when it is
     *  provisioned.
     */
    fun execute(): SingletonKeyPairResult {
        logger.debug(this::class.java.simpleName)
        if (!enforceSingletonKeyPair) {
            throw CryptographicKeysException.InvalidConfigurationException(
                StringConstants.SINGLETON_KEY_PAIR_NOT_ENFORCED_MSG,
            )
        }
        return serviceKeyManager.createSingletonKeyPairIfAbsent()
    }
}
