/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.phoneNumber.transformers

import com.sudoplatform.sudotelephony.data.DataFactory
import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberFailureReasonEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberStateEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.PhoneNumberTypeEntity
import com.sudoplatform.sudotelephony.types.PhoneNumberFailureReason
import com.sudoplatform.sudotelephony.types.PhoneNumberState
import com.sudoplatform.sudotelephony.types.PhoneNumberType
import io.kotlintest.shouldBe
import org.junit.Test
import java.util.Date
import com.sudoplatform.sudotelephony.graphql.type.PhoneNumberFailureReason as PhoneNumberFailureReasonGraphQL
import com.sudoplatform.sudotelephony.graphql.type.PhoneNumberState as PhoneNumberStateGraphQL

/**
 * Pure mapping tests for [PhoneNumberTransformer], [OwnerTransformer],
 * [PhoneNumberStateTransformer] and [PhoneNumberFailureReasonTransformer].
 */
class PhoneNumberTransformerTest {
    @Test
    fun `fromGraphQLToEntity() should map every field onto the entity`() {
        val entity = PhoneNumberTransformer.fromGraphQLToEntity(DataFactory.phoneNumberFragment())

        entity.id shouldBe "mockPhoneNumberId"
        entity.owner shouldBe "mockOwner"
        entity.identityId shouldBe "mockIdentityId"
        entity.keyRingId shouldBe "sudo-telephony.mockOwner"
        entity.keyIds shouldBe listOf("mockKeyId")
        entity.phoneNumber shouldBe "+14155550123"
        entity.countryCode shouldBe "US"
        entity.numberType shouldBe PhoneNumberTypeEntity.Local
        entity.areaCode shouldBe "415"
        entity.state shouldBe PhoneNumberStateEntity.Provisioning
        entity.failureReason shouldBe null
        entity.gracePeriodEndsAt shouldBe null
        entity.clientRefId shouldBe null
        entity.version shouldBe 1
        entity.owners.size shouldBe 1
        with(entity.owners.first()) {
            id shouldBe "mockSudoId"
            issuer shouldBe "sudoplatform.sudoservice"
        }
    }

    @Test
    fun `fromGraphQLToEntity() should convert epoch milliseconds to dates`() {
        val entity =
            PhoneNumberTransformer.fromGraphQLToEntity(
                DataFactory.phoneNumberFragment(
                    gracePeriodEndsAtEpochMs = EntityDataFactory.EXPIRES_AT_EPOCH_MS,
                ),
            )

        entity.createdAt shouldBe Date(EntityDataFactory.CREATED_AT_EPOCH_MS.toLong())
        entity.updatedAt shouldBe Date(EntityDataFactory.UPDATED_AT_EPOCH_MS.toLong())
        entity.gracePeriodEndsAt shouldBe Date(EntityDataFactory.EXPIRES_AT_EPOCH_MS.toLong())
    }

    @Test
    fun `fromGraphQLToEntity() should map a failed number with its reason`() {
        val entity =
            PhoneNumberTransformer.fromGraphQLToEntity(
                DataFactory.phoneNumberFragment(
                    state = PhoneNumberStateGraphQL.FAILED,
                    failureReason = PhoneNumberFailureReasonGraphQL.NUMBER_UNAVAILABLE,
                ),
            )

        entity.state shouldBe PhoneNumberStateEntity.Failed
        entity.failureReason shouldBe PhoneNumberFailureReasonEntity.NumberUnavailable
    }

    @Test
    fun `fromGraphQLToEntity() should map a number with no owners or area code`() {
        val entity =
            PhoneNumberTransformer.fromGraphQLToEntity(
                DataFactory.phoneNumberFragment(owners = emptyList(), areaCode = null),
            )

        entity.owners shouldBe emptyList()
        entity.areaCode shouldBe null
    }

    @Test
    fun `fromEntityToApi() should map every field onto the public type`() {
        val api = PhoneNumberTransformer.fromEntityToApi(EntityDataFactory.phoneNumberEntity())

        api.id shouldBe "mockPhoneNumberId"
        api.owner shouldBe "mockOwner"
        api.identityId shouldBe "mockIdentityId"
        api.keyRingId shouldBe "sudo-telephony.mockOwner"
        api.keyIds shouldBe listOf("mockKeyId")
        api.phoneNumber shouldBe "+14155550123"
        api.countryCode shouldBe "US"
        api.numberType shouldBe PhoneNumberType.Local
        api.areaCode shouldBe "415"
        api.state shouldBe PhoneNumberState.Provisioning
        api.failureReason shouldBe null
        api.version shouldBe 1
        api.createdAt shouldBe Date(EntityDataFactory.CREATED_AT_EPOCH_MS.toLong())
        api.updatedAt shouldBe Date(EntityDataFactory.UPDATED_AT_EPOCH_MS.toLong())
        with(api.owners.first()) {
            id shouldBe "mockSudoId"
            issuer shouldBe "sudoplatform.sudoservice"
        }
    }

    @Test
    fun `PhoneNumberStateTransformer should map every state in both directions`() {
        val pairs =
            listOf(
                Triple(PhoneNumberStateGraphQL.PROVISIONING, PhoneNumberStateEntity.Provisioning, PhoneNumberState.Provisioning),
                Triple(PhoneNumberStateGraphQL.ACTIVE, PhoneNumberStateEntity.Active, PhoneNumberState.Active),
                Triple(PhoneNumberStateGraphQL.SUSPENDED, PhoneNumberStateEntity.Suspended, PhoneNumberState.Suspended),
                Triple(PhoneNumberStateGraphQL.RELEASED, PhoneNumberStateEntity.Released, PhoneNumberState.Released),
                Triple(PhoneNumberStateGraphQL.FAILED, PhoneNumberStateEntity.Failed, PhoneNumberState.Failed),
            )

        pairs.forEach { (graphQL, entity, api) ->
            PhoneNumberStateTransformer.fromGraphQLToEntity(graphQL) shouldBe entity
            PhoneNumberStateTransformer.fromEntityToApi(entity) shouldBe api
        }
    }

    @Test
    fun `PhoneNumberStateTransformer should map an unrecognised state to Unknown with its value`() {
        PhoneNumberStateTransformer.fromGraphQLToEntity(PhoneNumberStateGraphQL.safeValueOf("SOMETHING_NEW")) shouldBe
            PhoneNumberStateEntity.Unknown("SOMETHING_NEW")
        PhoneNumberStateTransformer.fromEntityToApi(PhoneNumberStateEntity.Unknown("SOMETHING_NEW")) shouldBe
            PhoneNumberState.Unknown("SOMETHING_NEW")
    }

    @Test
    fun `PhoneNumberFailureReasonTransformer should map every reason in both directions`() {
        PhoneNumberFailureReasonTransformer.fromGraphQLToEntity(PhoneNumberFailureReasonGraphQL.NUMBER_UNAVAILABLE) shouldBe
            PhoneNumberFailureReasonEntity.NumberUnavailable
        PhoneNumberFailureReasonTransformer.fromGraphQLToEntity(PhoneNumberFailureReasonGraphQL.CARRIER_ERROR) shouldBe
            PhoneNumberFailureReasonEntity.CarrierError
        PhoneNumberFailureReasonTransformer.fromGraphQLToEntity(PhoneNumberFailureReasonGraphQL.OWNER_DELETED) shouldBe
            PhoneNumberFailureReasonEntity.OwnerDeleted
        PhoneNumberFailureReasonTransformer.fromEntityToApi(PhoneNumberFailureReasonEntity.NumberUnavailable) shouldBe
            PhoneNumberFailureReason.NumberUnavailable
        PhoneNumberFailureReasonTransformer.fromEntityToApi(PhoneNumberFailureReasonEntity.CarrierError) shouldBe
            PhoneNumberFailureReason.CarrierError
        PhoneNumberFailureReasonTransformer.fromEntityToApi(PhoneNumberFailureReasonEntity.OwnerDeleted) shouldBe
            PhoneNumberFailureReason.OwnerDeleted
    }

    @Test
    fun `PhoneNumberFailureReasonTransformer should map null and unrecognised values`() {
        PhoneNumberFailureReasonTransformer.fromGraphQLToEntity(null) shouldBe null
        PhoneNumberFailureReasonTransformer.fromEntityToApi(null) shouldBe null
        PhoneNumberFailureReasonTransformer.fromGraphQLToEntity(PhoneNumberFailureReasonGraphQL.safeValueOf("SOMETHING_NEW")) shouldBe
            PhoneNumberFailureReasonEntity.Unknown("SOMETHING_NEW")
        PhoneNumberFailureReasonTransformer.fromEntityToApi(PhoneNumberFailureReasonEntity.Unknown("SOMETHING_NEW")) shouldBe
            PhoneNumberFailureReason.Unknown("SOMETHING_NEW")
    }
}
