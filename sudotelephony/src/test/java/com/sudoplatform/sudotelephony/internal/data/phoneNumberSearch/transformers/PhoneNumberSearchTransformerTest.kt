/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.phoneNumberSearch.transformers

import com.sudoplatform.sudotelephony.data.DataFactory
import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.PhoneNumberTypeEntity
import com.sudoplatform.sudotelephony.types.PhoneNumberType
import io.kotlintest.shouldBe
import org.junit.Test
import java.util.Date
import com.sudoplatform.sudotelephony.graphql.type.PhoneNumberType as PhoneNumberTypeGraphQL

/**
 * Pure mapping tests for [PhoneNumberSearchTransformer], [AvailablePhoneNumberTransformer]
 * and [PhoneNumberTypeTransformer].
 */
class PhoneNumberSearchTransformerTest {
    @Test
    fun `fromGraphQLToEntity() should map every field onto the entity`() {
        val entity =
            PhoneNumberSearchTransformer.fromGraphQLToEntity(
                DataFactory.phoneNumberSearchFragment(),
            )

        entity.id shouldBe "mockSearchId"
        entity.countryCode shouldBe "US"
        entity.areaCode shouldBe "415"
        entity.owner shouldBe "mockOwner"
        entity.version shouldBe 1
        entity.results.size shouldBe 1
        with(entity.results.first()) {
            phoneNumber shouldBe "+14155550123"
            countryCode shouldBe "US"
            numberType shouldBe PhoneNumberTypeEntity.Local
            locality shouldBe "San Francisco"
            region shouldBe "CA"
        }
    }

    @Test
    fun `fromGraphQLToEntity() should convert epoch milliseconds to dates`() {
        val entity =
            PhoneNumberSearchTransformer.fromGraphQLToEntity(
                DataFactory.phoneNumberSearchFragment(),
            )

        entity.expiresAt shouldBe Date(EntityDataFactory.EXPIRES_AT_EPOCH_MS.toLong())
        entity.createdAt shouldBe Date(EntityDataFactory.CREATED_AT_EPOCH_MS.toLong())
        entity.updatedAt shouldBe Date(EntityDataFactory.UPDATED_AT_EPOCH_MS.toLong())
    }

    @Test
    fun `fromGraphQLToEntity() should map a country-wide search with no area code`() {
        val entity =
            PhoneNumberSearchTransformer.fromGraphQLToEntity(
                DataFactory.phoneNumberSearchFragment(areaCode = null),
            )

        entity.areaCode shouldBe null
    }

    @Test
    fun `fromGraphQLToEntity() should map a search with no results`() {
        val entity =
            PhoneNumberSearchTransformer.fromGraphQLToEntity(
                DataFactory.phoneNumberSearchFragment(results = emptyList()),
            )

        entity.results shouldBe emptyList()
    }

    @Test
    fun `fromGraphQLToEntity() should map absent locality and region to null`() {
        val entity =
            PhoneNumberSearchTransformer.fromGraphQLToEntity(
                DataFactory.phoneNumberSearchFragment(
                    results =
                        listOf(
                            DataFactory.availablePhoneNumberFragment(
                                locality = null,
                                region = null,
                            ),
                        ),
                ),
            )

        with(entity.results.first()) {
            locality shouldBe null
            region shouldBe null
        }
    }

    @Test
    fun `fromEntityToApi() should map every field onto the public type`() {
        val api =
            PhoneNumberSearchTransformer.fromEntityToApi(
                EntityDataFactory.phoneNumberSearchEntity(),
            )

        api.id shouldBe "mockSearchId"
        api.countryCode shouldBe "US"
        api.areaCode shouldBe "415"
        api.owner shouldBe "mockOwner"
        api.version shouldBe 1
        api.expiresAt shouldBe Date(EntityDataFactory.EXPIRES_AT_EPOCH_MS.toLong())
        api.createdAt shouldBe Date(EntityDataFactory.CREATED_AT_EPOCH_MS.toLong())
        api.updatedAt shouldBe Date(EntityDataFactory.UPDATED_AT_EPOCH_MS.toLong())
        api.results.size shouldBe 1
        with(api.results.first()) {
            phoneNumber shouldBe "+14155550123"
            countryCode shouldBe "US"
            numberType shouldBe PhoneNumberType.Local
            locality shouldBe "San Francisco"
            region shouldBe "CA"
        }
    }

    @Test
    fun `PhoneNumberTypeTransformer should map LOCAL in both directions`() {
        PhoneNumberTypeTransformer.fromGraphQLToEntity(PhoneNumberTypeGraphQL.LOCAL) shouldBe
            PhoneNumberTypeEntity.Local
        PhoneNumberTypeTransformer.fromEntityToApi(PhoneNumberTypeEntity.Local) shouldBe
            PhoneNumberType.Local
    }

    @Test
    fun `PhoneNumberTypeTransformer should map an unrecognised GraphQL value to Unknown with its value`() {
        PhoneNumberTypeTransformer.fromGraphQLToEntity(PhoneNumberTypeGraphQL.safeValueOf("SOMETHING_NEW")) shouldBe
            PhoneNumberTypeEntity.Unknown("SOMETHING_NEW")
        PhoneNumberTypeTransformer.fromEntityToApi(PhoneNumberTypeEntity.Unknown("SOMETHING_NEW")) shouldBe
            PhoneNumberType.Unknown("SOMETHING_NEW")
    }
}
