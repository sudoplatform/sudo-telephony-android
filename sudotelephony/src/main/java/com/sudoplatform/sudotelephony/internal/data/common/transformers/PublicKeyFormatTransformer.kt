/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.common.transformers

import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PublicKeyFormatEntity
import com.sudoplatform.sudotelephony.graphql.type.KeyFormat as KeyFormatGraphQL

/**
 * Transformer responsible for transforming a public key encoding between the entity type used
 * in business logic and the [KeyFormatGraphQL] GraphQL enum the service expects.
 */
internal object PublicKeyFormatTransformer {
    /**
     * Transform the [entity] to the GraphQL [KeyFormatGraphQL] enum.
     */
    fun fromEntityToGraphQL(entity: PublicKeyFormatEntity): KeyFormatGraphQL =
        when (entity) {
            PublicKeyFormatEntity.RSA_PUBLIC_KEY -> KeyFormatGraphQL.RSA_PUBLIC_KEY
            PublicKeyFormatEntity.SPKI -> KeyFormatGraphQL.SPKI
        }
}
