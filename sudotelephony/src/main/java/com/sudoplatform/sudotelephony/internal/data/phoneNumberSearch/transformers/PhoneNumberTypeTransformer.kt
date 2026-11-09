/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.phoneNumberSearch.transformers

import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.PhoneNumberTypeEntity
import com.sudoplatform.sudotelephony.types.PhoneNumberType
import com.sudoplatform.sudotelephony.graphql.type.PhoneNumberType as PhoneNumberTypeGraphQL

/**
 * Transformer responsible for transforming between the [PhoneNumberTypeGraphQL] GraphQL enum,
 * the [PhoneNumberTypeEntity] entity type, and the public [PhoneNumberType] type.
 */
internal object PhoneNumberTypeTransformer {
    /**
     * Transform the GraphQL [PhoneNumberTypeGraphQL] to its entity type.
     */
    fun fromGraphQLToEntity(data: PhoneNumberTypeGraphQL): PhoneNumberTypeEntity =
        when (data) {
            PhoneNumberTypeGraphQL.LOCAL -> PhoneNumberTypeEntity.Local
            is PhoneNumberTypeGraphQL.UNKNOWN__ -> PhoneNumberTypeEntity.Unknown(data.rawValue)
        }

    /**
     * Transform the entity [PhoneNumberTypeEntity] to its public [PhoneNumberType] type.
     */
    fun fromEntityToApi(entity: PhoneNumberTypeEntity): PhoneNumberType =
        when (entity) {
            PhoneNumberTypeEntity.Local -> PhoneNumberType.Local
            is PhoneNumberTypeEntity.Unknown -> PhoneNumberType.Unknown(entity.value)
        }
}
