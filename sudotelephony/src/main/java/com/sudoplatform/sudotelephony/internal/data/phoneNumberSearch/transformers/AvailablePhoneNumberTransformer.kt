/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.phoneNumberSearch.transformers

import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.AvailablePhoneNumberEntity
import com.sudoplatform.sudotelephony.types.AvailablePhoneNumber
import com.sudoplatform.sudotelephony.graphql.fragment.AvailablePhoneNumber as AvailablePhoneNumberFragment

/**
 * Transformer responsible for transforming an available phone number between the GraphQL
 * fragment, the entity type used in business logic, and the public type exposed to consumers.
 */
internal object AvailablePhoneNumberTransformer {
    /**
     * Transform the GraphQL [data] to its entity type.
     */
    fun fromGraphQLToEntity(data: AvailablePhoneNumberFragment): AvailablePhoneNumberEntity =
        AvailablePhoneNumberEntity(
            phoneNumber = data.phoneNumber,
            countryCode = data.countryCode,
            numberType = PhoneNumberTypeTransformer.fromGraphQLToEntity(data.numberType),
            locality = data.locality,
            region = data.region,
        )

    /**
     * Transform the [entity] to its public [AvailablePhoneNumber] type.
     */
    fun fromEntityToApi(entity: AvailablePhoneNumberEntity): AvailablePhoneNumber =
        AvailablePhoneNumber(
            phoneNumber = entity.phoneNumber,
            countryCode = entity.countryCode,
            numberType = PhoneNumberTypeTransformer.fromEntityToApi(entity.numberType),
            locality = entity.locality,
            region = entity.region,
        )
}
