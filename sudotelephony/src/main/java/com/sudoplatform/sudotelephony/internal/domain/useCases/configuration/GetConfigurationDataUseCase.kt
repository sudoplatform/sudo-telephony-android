/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.useCases.configuration

import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.internal.domain.entities.configuration.ConfigurationDataEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.configuration.ConfigurationDataService

/**
 * Use case that fetches the telephony service configuration.
 *
 * @property configurationDataService [ConfigurationDataService] The domain port used to
 *  retrieve the configuration.
 * @property logger [Logger] The SDK logger.
 */
internal class GetConfigurationDataUseCase(
    private val configurationDataService: ConfigurationDataService,
    private val logger: Logger,
) {
    /**
     * Retrieve the configuration.
     *
     * @return The [ConfigurationDataEntity].
     */
    suspend fun execute(): ConfigurationDataEntity {
        logger.debug(this::class.java.simpleName)
        return configurationDataService.getConfigurationData()
    }
}
