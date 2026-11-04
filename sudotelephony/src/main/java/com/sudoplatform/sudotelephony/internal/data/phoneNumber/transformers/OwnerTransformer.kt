/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.phoneNumber.transformers

import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.OwnerEntity
import com.sudoplatform.sudotelephony.types.Owner
import com.sudoplatform.sudotelephony.graphql.fragment.PhoneNumber as PhoneNumberFragment
import com.sudoplatform.sudotelephony.graphql.fragment.SealedPhoneMessage as SealedPhoneMessageFragment

/**
 * Transformer responsible for transforming a resource owner between the GraphQL fragment, the
 * entity type used in business logic, and the public type exposed to consumers.
 */
internal object OwnerTransformer {
    /**
     * Transform the GraphQL [data] to its entity type.
     */
    fun fromGraphQLToEntity(data: PhoneNumberFragment.Owner): OwnerEntity =
        OwnerEntity(
            id = data.id,
            issuer = data.issuer,
        )

    /**
     * Transform the GraphQL [data] of a sealed phone message to its entity type.
     *
     * Apollo generates a separate owner type per fragment, so each needs its own mapping.
     */
    fun fromGraphQLToEntity(data: SealedPhoneMessageFragment.Owner): OwnerEntity =
        OwnerEntity(
            id = data.id,
            issuer = data.issuer,
        )

    /**
     * Transform the [entity] to its public [Owner] type.
     */
    fun fromEntityToApi(entity: OwnerEntity): Owner =
        Owner(
            id = entity.id,
            issuer = entity.issuer,
        )
}
