/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.entities.configuration

/**
 * Domain service port for the telephony service configuration.
 */
internal interface ConfigurationDataService {
    /**
     * Fetch the telephony service configuration.
     *
     * @return The [ConfigurationDataEntity].
     */
    suspend fun getConfigurationData(): ConfigurationDataEntity
}
