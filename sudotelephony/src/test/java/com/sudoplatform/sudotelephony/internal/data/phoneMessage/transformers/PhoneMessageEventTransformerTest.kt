/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.phoneMessage.transformers

import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.graphql.OnPhoneMessageEventSubscription
import com.sudoplatform.sudotelephony.types.Owner
import com.sudoplatform.sudotelephony.types.PhoneMessageChangeType
import com.sudoplatform.sudotelephony.types.PhoneMessageDirection
import com.sudoplatform.sudotelephony.types.PhoneMessageType
import io.kotlintest.shouldBe
import org.junit.Test
import java.util.Date
import com.sudoplatform.sudotelephony.graphql.type.PhoneMessageChangeType as PhoneMessageChangeTypeGraphQL
import com.sudoplatform.sudotelephony.graphql.type.PhoneMessageDirection as PhoneMessageDirectionGraphQL
import com.sudoplatform.sudotelephony.graphql.type.PhoneMessageType as PhoneMessageTypeGraphQL

/**
 * Pure mapping tests for [PhoneMessageEventTransformer] and
 * [PhoneMessageChangeTypeTransformer].
 */
class PhoneMessageEventTransformerTest {
    private fun graphQLEvent(
        changeType: PhoneMessageChangeTypeGraphQL = PhoneMessageChangeTypeGraphQL.RECEIVED,
        seen: Boolean = false,
        owners: List<OnPhoneMessageEventSubscription.Owner> =
            listOf(OnPhoneMessageEventSubscription.Owner(id = "mockSudoId", issuer = "sudoplatform.sudoservice")),
    ) = OnPhoneMessageEventSubscription.OnPhoneMessageEvent(
        id = "mockMessageId",
        phoneNumberId = "mockPhoneNumberId",
        conversationId = "mockConversationId",
        type = PhoneMessageTypeGraphQL.SMS,
        direction = PhoneMessageDirectionGraphQL.INBOUND,
        changeType = changeType,
        seen = seen,
        owner = "mockOwner",
        owners = owners,
        version = 1,
        updatedAtEpochMs = EntityDataFactory.UPDATED_AT_EPOCH_MS,
    )

    @Test
    fun `fromGraphQLToApi() should map every field onto the public type`() {
        val event = PhoneMessageEventTransformer.fromGraphQLToApi(graphQLEvent())

        event.id shouldBe "mockMessageId"
        event.phoneNumberId shouldBe "mockPhoneNumberId"
        event.conversationId shouldBe "mockConversationId"
        event.type shouldBe PhoneMessageType.Sms
        event.direction shouldBe PhoneMessageDirection.Inbound
        event.changeType shouldBe PhoneMessageChangeType.Received
        event.seen shouldBe false
        event.owner shouldBe "mockOwner"
        event.owners shouldBe listOf(Owner(id = "mockSudoId", issuer = "sudoplatform.sudoservice"))
        event.sudoId shouldBe "mockSudoId"
        event.version shouldBe 1
        event.updatedAt shouldBe Date(EntityDataFactory.UPDATED_AT_EPOCH_MS.toLong())
    }

    @Test
    fun `fromGraphQLToApi() should map an updated seen state`() {
        val event =
            PhoneMessageEventTransformer.fromGraphQLToApi(
                graphQLEvent(changeType = PhoneMessageChangeTypeGraphQL.UPDATED, seen = true),
            )

        event.changeType shouldBe PhoneMessageChangeType.Updated
        event.seen shouldBe true
    }

    @Test
    fun `fromGraphQLToApi() should map an event without a Sudo owner`() {
        val event = PhoneMessageEventTransformer.fromGraphQLToApi(graphQLEvent(owners = emptyList()))

        event.owners shouldBe emptyList()
        event.sudoId shouldBe null
    }

    @Test
    fun `PhoneMessageChangeTypeTransformer should map every change in the schema`() {
        PhoneMessageChangeTypeTransformer.fromGraphQLToApi(PhoneMessageChangeTypeGraphQL.RECEIVED) shouldBe
            PhoneMessageChangeType.Received
        PhoneMessageChangeTypeTransformer.fromGraphQLToApi(PhoneMessageChangeTypeGraphQL.UPDATED) shouldBe
            PhoneMessageChangeType.Updated
        PhoneMessageChangeTypeTransformer.fromGraphQLToApi(PhoneMessageChangeTypeGraphQL.DELETED) shouldBe
            PhoneMessageChangeType.Deleted
    }

    @Test
    fun `PhoneMessageChangeTypeTransformer should map an unrecognised change to Unknown with its value`() {
        PhoneMessageChangeTypeTransformer.fromGraphQLToApi(PhoneMessageChangeTypeGraphQL.safeValueOf("SOMETHING_NEW")) shouldBe
            PhoneMessageChangeType.Unknown("SOMETHING_NEW")
    }
}
