/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.phoneMessage.transformers

import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageDirectionEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageTypeEntity
import com.sudoplatform.sudotelephony.types.PhoneMessageDirection
import com.sudoplatform.sudotelephony.types.PhoneMessageType
import io.kotlintest.shouldBe
import org.junit.Test
import com.sudoplatform.sudotelephony.graphql.type.PhoneMessageDirection as PhoneMessageDirectionGraphQL
import com.sudoplatform.sudotelephony.graphql.type.PhoneMessageType as PhoneMessageTypeGraphQL

/**
 * Pure mapping tests for [PhoneMessageTypeTransformer] and [PhoneMessageDirectionTransformer].
 */
class PhoneMessageEnumTransformersTest {
    @Test
    fun `PhoneMessageTypeTransformer should map SMS in both directions`() {
        PhoneMessageTypeTransformer.fromGraphQLToEntity(PhoneMessageTypeGraphQL.SMS) shouldBe
            PhoneMessageTypeEntity.Sms
        PhoneMessageTypeTransformer.fromEntityToApi(PhoneMessageTypeEntity.Sms) shouldBe
            PhoneMessageType.Sms
    }

    @Test
    fun `PhoneMessageTypeTransformer should map an unrecognised value to Unknown with its value`() {
        PhoneMessageTypeTransformer.fromGraphQLToEntity(PhoneMessageTypeGraphQL.safeValueOf("MMS")) shouldBe
            PhoneMessageTypeEntity.Unknown("MMS")
        PhoneMessageTypeTransformer.fromEntityToApi(PhoneMessageTypeEntity.Unknown("MMS")) shouldBe
            PhoneMessageType.Unknown("MMS")
    }

    @Test
    fun `PhoneMessageDirectionTransformer should map INBOUND in both directions`() {
        PhoneMessageDirectionTransformer.fromGraphQLToEntity(PhoneMessageDirectionGraphQL.INBOUND) shouldBe
            PhoneMessageDirectionEntity.Inbound
        PhoneMessageDirectionTransformer.fromEntityToApi(PhoneMessageDirectionEntity.Inbound) shouldBe
            PhoneMessageDirection.Inbound
    }

    @Test
    fun `PhoneMessageDirectionTransformer should map an unrecognised value to Unknown with its value`() {
        PhoneMessageDirectionTransformer.fromGraphQLToEntity(PhoneMessageDirectionGraphQL.safeValueOf("OUTBOUND")) shouldBe
            PhoneMessageDirectionEntity.Unknown("OUTBOUND")
        PhoneMessageDirectionTransformer.fromEntityToApi(PhoneMessageDirectionEntity.Unknown("OUTBOUND")) shouldBe
            PhoneMessageDirection.Unknown("OUTBOUND")
    }
}
