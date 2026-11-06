/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.types

import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.internal.data.phoneMessage.transformers.PhoneMessageTransformer
import com.sudoplatform.sudotelephony.internal.data.phoneNumber.transformers.PhoneNumberTransformer
import io.kotlintest.shouldBe
import org.junit.Test

/**
 * Test the `sudoId` property of [PhoneNumber], [PhoneMessage], [PartialPhoneMessage] and
 * [PhoneMessageEvent], which picks the Sudo out of the resource's owners.
 */
class SudoIdTest {
    private fun phoneNumberWith(vararg owners: Pair<String, String>) =
        PhoneNumberTransformer.fromEntityToApi(
            EntityDataFactory.phoneNumberEntity(
                owners = owners.map { EntityDataFactory.ownerEntity(id = it.first, issuer = it.second) },
            ),
        )

    @Test
    fun `sudoId should be the ID of the Sudo owner`() {
        phoneNumberWith("mockSudoId" to "sudoplatform.sudoservice").sudoId shouldBe "mockSudoId"
    }

    @Test
    fun `sudoId should be null for a resource owned only by the user`() {
        phoneNumberWith().sudoId shouldBe null
    }

    @Test
    fun `sudoId should ignore owners from other issuers`() {
        phoneNumberWith("otherId" to "some.other.issuer").sudoId shouldBe null
        phoneNumberWith(
            "otherId" to "some.other.issuer",
            "mockSudoId" to "sudoplatform.sudoservice",
        ).sudoId shouldBe "mockSudoId"
    }

    @Test
    fun `sudoId should be set on messages, partial messages and message events`() {
        PhoneMessageTransformer.fromEntityToApi(EntityDataFactory.phoneMessageEntity()).sudoId shouldBe
            "mockSudoId"
        PhoneMessageTransformer
            .fromPartialEntityToApi(EntityDataFactory.partialPhoneMessageEntity())
            .sudoId shouldBe "mockSudoId"
        EntityDataFactory.phoneMessageEvent().sudoId shouldBe "mockSudoId"
    }
}
