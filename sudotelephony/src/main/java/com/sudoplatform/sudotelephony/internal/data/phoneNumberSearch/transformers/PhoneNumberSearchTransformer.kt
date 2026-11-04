/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.phoneNumberSearch.transformers

import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.PhoneNumberSearchEntity
import com.sudoplatform.sudotelephony.internal.util.toDate
import com.sudoplatform.sudotelephony.types.PhoneNumberSearch
import com.sudoplatform.sudotelephony.graphql.fragment.PhoneNumberSearch as PhoneNumberSearchFragment

/**
 * Transformer responsible for transforming a phone number search between the GraphQL
 * fragment, the entity type used in business logic, and the public type exposed to consumers.
 */
internal object PhoneNumberSearchTransformer {
    /**
     * Transform the GraphQL [data] to its entity type.
     */
    fun fromGraphQLToEntity(data: PhoneNumberSearchFragment): PhoneNumberSearchEntity =
        PhoneNumberSearchEntity(
            id = data.id,
            countryCode = data.countryCode,
            areaCode = data.areaCode,
            results =
                data.results.map {
                    AvailablePhoneNumberTransformer.fromGraphQLToEntity(it.availablePhoneNumber)
                },
            expiresAt = data.expiresAtEpochMs.toDate(),
            owner = data.owner,
            version = data.version,
            createdAt = data.createdAtEpochMs.toDate(),
            updatedAt = data.updatedAtEpochMs.toDate(),
        )

    /**
     * Transform the [entity] to its public [PhoneNumberSearch] type.
     */
    fun fromEntityToApi(entity: PhoneNumberSearchEntity): PhoneNumberSearch =
        PhoneNumberSearch(
            id = entity.id,
            countryCode = entity.countryCode,
            areaCode = entity.areaCode,
            results = entity.results.map { AvailablePhoneNumberTransformer.fromEntityToApi(it) },
            expiresAt = entity.expiresAt,
            owner = entity.owner,
            version = entity.version,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
}
