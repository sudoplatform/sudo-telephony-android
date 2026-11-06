/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudotelephony.BaseIntegrationTest
import com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneNumberException
import com.sudoplatform.sudotelephony.types.inputs.ListPhoneNumbersForSudoIdInput
import com.sudoplatform.sudotelephony.types.inputs.ProvisionPhoneNumberInput
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test provisioning phone numbers that belong to a Sudo, using ownership proofs from the Sudo
 * service, against a deployed telephony service.
 */
@RunWith(AndroidJUnit4::class)
class SudoOwnershipIntegrationTest : BaseIntegrationTest() {
    @Test
    fun provisionPhoneNumberWithASudoProofShouldMakeTheSudoTheOwner() =
        runBlocking {
            val sudo = createSudo()

            val provisioned = provisionANumber { getOwnershipProof(sudo) }.number

            provisioned.sudoId shouldBe sudo.id
            telephonyClient.getPhoneNumber(provisioned.id)?.sudoId shouldBe sudo.id
        }

    @Test
    fun listPhoneNumbersForSudoIdShouldListOnlyThatSudosNumbers() =
        runBlocking {
            val owningSudo = createSudo()
            val otherSudo = createSudo()
            val provisioned = provisionANumber { getOwnershipProof(owningSudo) }.number

            val owningPage =
                telephonyClient.listPhoneNumbersForSudoId(ListPhoneNumbersForSudoIdInput(sudoId = owningSudo.id!!))
            val otherPage =
                telephonyClient.listPhoneNumbersForSudoId(ListPhoneNumbersForSudoIdInput(sudoId = otherSudo.id!!))

            owningPage.items.map { it.id } shouldBe listOf(provisioned.id)
            otherPage.items shouldBe emptyList()
        }

    @Test
    fun provisionPhoneNumberWithoutAProofShouldFollowTheDeploymentRule() =
        runBlocking {
            val config = telephonyClient.getConfigurationData()

            if (config.sudoOwnershipProofRequired) {
                val search = searchAvailableNumbersOrSkip()
                shouldThrow<PhoneNumberException.InvalidTokenException> {
                    telephonyClient.provisionPhoneNumber(
                        ProvisionPhoneNumberInput(
                            searchId = search.id,
                            phoneNumber = search.results.first().phoneNumber,
                        ),
                    )
                }
            } else {
                provisionANumber { null }.number.sudoId shouldBe null
            }
            Unit
        }

    @Test
    fun provisionPhoneNumberShouldRejectAProofForAnotherAudience() =
        runBlocking {
            val sudo = createSudo()
            val search = searchAvailableNumbersOrSkip()

            shouldThrow<PhoneNumberException.InvalidTokenException> {
                telephonyClient.provisionPhoneNumber(
                    ProvisionPhoneNumberInput(
                        searchId = search.id,
                        phoneNumber = search.results.first().phoneNumber,
                        ownershipProofToken = getOwnershipProof(sudo, "sudoplatform.email.email-address"),
                    ),
                )
            }
            Unit
        }
}
