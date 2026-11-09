/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.configuration.transformers

import com.sudoplatform.sudotelephony.internal.domain.entities.configuration.ConfigurationDataEntity
import com.sudoplatform.sudotelephony.types.ConfigurationData
import java.time.Duration
import com.sudoplatform.sudotelephony.graphql.fragment.TelephonyConfigurationData as TelephonyConfigurationDataFragment

/**
 * Transformer responsible for transforming the telephony service configuration between the
 * GraphQL fragment, the entity type used in business logic, and the public type.
 */
internal object ConfigurationDataTransformer {
    /**
     * Transform the GraphQL [data] to its entity type.
     */
    fun fromGraphQLToEntity(data: TelephonyConfigurationDataFragment): ConfigurationDataEntity =
        ConfigurationDataEntity(
            sudoOwnershipProofRequired = data.sudoOwnershipProofRequired,
            phoneNumberSearchDefaultLimit = data.phoneNumberSearchDefaultLimit,
            phoneNumberSearchMaxLimit = data.phoneNumberSearchMaxLimit,
            phoneNumberSearchLifetimeMs = data.phoneNumberSearchLifetimeMs,
            listDefaultLimit = data.listDefaultLimit,
            listMaxLimit = data.listMaxLimit,
            deletePhoneMessagesLimit = data.deletePhoneMessagesLimit,
            updatePhoneMessagesLimit = data.updatePhoneMessagesLimit,
        )

    /**
     * Transform the [entity] to its public [ConfigurationData] type.
     */
    fun fromEntityToApi(entity: ConfigurationDataEntity): ConfigurationData =
        ConfigurationData(
            sudoOwnershipProofRequired = entity.sudoOwnershipProofRequired,
            phoneNumberSearchDefaultLimit = entity.phoneNumberSearchDefaultLimit,
            phoneNumberSearchMaxLimit = entity.phoneNumberSearchMaxLimit,
            phoneNumberSearchLifetime = Duration.ofMillis(entity.phoneNumberSearchLifetimeMs.toLong()),
            listDefaultLimit = entity.listDefaultLimit,
            listMaxLimit = entity.listMaxLimit,
            deletePhoneMessagesLimit = entity.deletePhoneMessagesLimit,
            updatePhoneMessagesLimit = entity.updatePhoneMessagesLimit,
        )
}
