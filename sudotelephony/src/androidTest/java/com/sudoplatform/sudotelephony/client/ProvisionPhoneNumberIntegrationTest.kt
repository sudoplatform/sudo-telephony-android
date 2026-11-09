/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudotelephony.BaseIntegrationTest
import com.sudoplatform.sudotelephony.SudoTelephonyClient
import com.sudoplatform.sudotelephony.types.PhoneNumberState
import com.sudoplatform.sudotelephony.types.PhoneNumberType
import com.sudoplatform.sudotelephony.types.inputs.ProvisionPhoneNumberInput
import com.sudoplatform.sudotelephony.types.inputs.SearchAvailablePhoneNumbersInput
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Test the operation of
 * [com.sudoplatform.sudotelephony.SudoTelephonyClient.provisionPhoneNumber] against a
 * deployed telephony service.
 *
 * How many numbers the provider offers is not guaranteed, so a test that needs one is
 * skipped rather than failed when a search comes back empty. A search result is also not a
 * reservation, so provisioning walks the results until one is still free, which is what a
 * real caller has to do.
 */
@RunWith(AndroidJUnit4::class)
class ProvisionPhoneNumberIntegrationTest : BaseIntegrationTest() {
    /** A number in the requested area code that no search will offer. */
    private val numberNotInAnySearch = "+1${areaCode}5550199"

    @Test
    fun provisionPhoneNumberShouldReturnANumberBeingProvisioned() =
        runBlocking {
            val provisioned = provisionANumber().number

            provisioned shouldNotBe null
            provisioned.id.isNotBlank() shouldBe true
            provisioned.phoneNumber.startsWith("+1$areaCode") shouldBe true
            provisioned.countryCode shouldBe countryCode
            provisioned.numberType shouldBe PhoneNumberType.Local
            provisioned.owner.isNotBlank() shouldBe true
            provisioned.identityId.isNotBlank() shouldBe true
            provisioned.version shouldBe 1
            // Provisioning is asynchronous, so the number starts out being ordered.
            provisioned.state shouldBe PhoneNumberState.Provisioning
            provisioned.failureReason shouldBe null
        }

    @Test
    fun provisionPhoneNumberShouldRegisterTheKeyUsedToSealMessages() =
        runBlocking {
            val provisioned = provisionANumber().number

            // The service registered the public key the SDK generated into a new key ring
            // and reports the ring and the key it holds.
            provisioned.keyRingId.isNotBlank() shouldBe true
            provisioned.keyIds.isNotEmpty() shouldBe true
        }

    @Test
    fun provisionPhoneNumberShouldReturnTheClientRefIdUnchanged() =
        runBlocking {
            val clientRefId = UUID.randomUUID().toString()

            val provisioned = provisionANumber(clientRefId).number

            provisioned.clientRefId shouldBe clientRefId
        }

    @Test
    fun provisionPhoneNumberShouldBeIdempotentForTheSameClientRefId() =
        runBlocking {
            val clientRefId = UUID.randomUUID().toString()
            val first = provisionANumber(clientRefId)

            // Repeating the request returns the number the first one provisioned rather than
            // provisioning another.
            val second =
                telephonyClient.provisionPhoneNumber(
                    ProvisionPhoneNumberInput(
                        searchId = first.searchId,
                        phoneNumber = first.number.phoneNumber,
                        clientRefId = clientRefId,
                        ownershipProofToken = first.ownershipProofToken,
                    ),
                )

            second.id shouldBe first.number.id
            second.phoneNumber shouldBe first.number.phoneNumber
        }

    @Test
    fun provisionPhoneNumberShouldRejectANumberThatIsNotInTheSearch() =
        runBlocking {
            val search =
                telephonyClient.searchAvailablePhoneNumbers(
                    SearchAvailablePhoneNumbersInput(
                        countryCode = countryCode,
                        areaCode = areaCode,
                    ),
                )
            assumeTrue(
                "The probe number must not be one the search offered",
                search.results.none { it.phoneNumber == numberNotInAnySearch },
            )

            shouldThrow<SudoTelephonyClient.PhoneNumberException.PhoneNumberNotInSearchException> {
                telephonyClient.provisionPhoneNumber(
                    ProvisionPhoneNumberInput(
                        searchId = search.id,
                        phoneNumber = numberNotInAnySearch,
                        ownershipProofToken = newOwnershipProof(),
                    ),
                )
            }
            Unit
        }

    @Test
    fun provisionPhoneNumberShouldRejectAnUnknownSearch() =
        runBlocking {
            shouldThrow<SudoTelephonyClient.PhoneNumberException.PhoneNumberSearchExpiredException> {
                telephonyClient.provisionPhoneNumber(
                    ProvisionPhoneNumberInput(
                        searchId = "tel-pns-${UUID.randomUUID()}",
                        phoneNumber = numberNotInAnySearch,
                        ownershipProofToken = newOwnershipProof(),
                    ),
                )
            }
            Unit
        }
}
