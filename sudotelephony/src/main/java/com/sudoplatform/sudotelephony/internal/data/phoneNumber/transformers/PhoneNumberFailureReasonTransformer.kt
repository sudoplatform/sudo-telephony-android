/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.phoneNumber.transformers

import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberFailureReasonEntity
import com.sudoplatform.sudotelephony.types.PhoneNumberFailureReason
import com.sudoplatform.sudotelephony.graphql.type.PhoneNumberFailureReason as PhoneNumberFailureReasonGraphQL

/**
 * Transformer responsible for transforming between the [PhoneNumberFailureReasonGraphQL]
 * GraphQL enum, the [PhoneNumberFailureReasonEntity] entity enum, and the public
 * [PhoneNumberFailureReason] type.
 */
internal object PhoneNumberFailureReasonTransformer {
    /**
     * Transform the GraphQL [data] to its entity type, or `null` when the number has not
     * failed.
     */
    fun fromGraphQLToEntity(data: PhoneNumberFailureReasonGraphQL?): PhoneNumberFailureReasonEntity? =
        when (data) {
            null -> null
            PhoneNumberFailureReasonGraphQL.NUMBER_UNAVAILABLE -> PhoneNumberFailureReasonEntity.NUMBER_UNAVAILABLE
            PhoneNumberFailureReasonGraphQL.CARRIER_ERROR -> PhoneNumberFailureReasonEntity.CARRIER_ERROR
            else -> PhoneNumberFailureReasonEntity.UNKNOWN
        }

    /**
     * Transform the [entity] to its public [PhoneNumberFailureReason] type, or `null` when
     * the number has not failed.
     */
    fun fromEntityToApi(entity: PhoneNumberFailureReasonEntity?): PhoneNumberFailureReason? =
        when (entity) {
            null -> null
            PhoneNumberFailureReasonEntity.NUMBER_UNAVAILABLE -> PhoneNumberFailureReason.NUMBER_UNAVAILABLE
            PhoneNumberFailureReasonEntity.CARRIER_ERROR -> PhoneNumberFailureReason.CARRIER_ERROR
            PhoneNumberFailureReasonEntity.UNKNOWN -> PhoneNumberFailureReason.UNKNOWN
        }
}
