/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.phoneNumber.transformers

import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberStateEntity
import com.sudoplatform.sudotelephony.types.PhoneNumberState
import com.sudoplatform.sudotelephony.graphql.type.PhoneNumberState as PhoneNumberStateGraphQL

/**
 * Transformer responsible for transforming between the [PhoneNumberStateGraphQL] GraphQL
 * enum, the [PhoneNumberStateEntity] entity enum, and the public [PhoneNumberState] type.
 */
internal object PhoneNumberStateTransformer {
    /**
     * Transform the GraphQL [data] to its entity type.
     */
    fun fromGraphQLToEntity(data: PhoneNumberStateGraphQL): PhoneNumberStateEntity =
        when (data) {
            PhoneNumberStateGraphQL.PROVISIONING -> PhoneNumberStateEntity.PROVISIONING
            PhoneNumberStateGraphQL.ACTIVE -> PhoneNumberStateEntity.ACTIVE
            PhoneNumberStateGraphQL.SUSPENDED -> PhoneNumberStateEntity.SUSPENDED
            PhoneNumberStateGraphQL.RELEASED -> PhoneNumberStateEntity.RELEASED
            PhoneNumberStateGraphQL.FAILED -> PhoneNumberStateEntity.FAILED
            else -> PhoneNumberStateEntity.UNKNOWN
        }

    /**
     * Transform the [entity] to its public [PhoneNumberState] type.
     */
    fun fromEntityToApi(entity: PhoneNumberStateEntity): PhoneNumberState =
        when (entity) {
            PhoneNumberStateEntity.PROVISIONING -> PhoneNumberState.PROVISIONING
            PhoneNumberStateEntity.ACTIVE -> PhoneNumberState.ACTIVE
            PhoneNumberStateEntity.SUSPENDED -> PhoneNumberState.SUSPENDED
            PhoneNumberStateEntity.RELEASED -> PhoneNumberState.RELEASED
            PhoneNumberStateEntity.FAILED -> PhoneNumberState.FAILED
            PhoneNumberStateEntity.UNKNOWN -> PhoneNumberState.UNKNOWN
        }
}
