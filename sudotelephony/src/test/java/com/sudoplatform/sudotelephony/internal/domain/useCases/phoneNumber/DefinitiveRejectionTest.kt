/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumber

import com.sudoplatform.sudotelephony.BaseTests
import com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneNumberException
import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.internal.data.phoneNumber.transformers.PhoneNumberTransformer
import io.kotlintest.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CancellationException

/**
 * Test the correct operation of [isDefinitiveRejection]: only failures that prove the service,
 * or the client before sending, refused the request qualify.
 */
@RunWith(RobolectricTestRunner::class)
class DefinitiveRejectionTest : BaseTests() {
    @Test
    fun `isDefinitiveRejection should be true for a typed rejection`() {
        val rejections =
            listOf(
                PhoneNumberException.AuthenticationException(),
                PhoneNumberException.InvalidArgumentException(),
                PhoneNumberException.IdentityContextMissingException(),
                PhoneNumberException.PhoneNumberSearchExpiredException(),
                PhoneNumberException.PhoneNumberNotInSearchException(),
                PhoneNumberException.PhoneNumberUnavailableException(),
                PhoneNumberException.PhoneNumberNotFoundException(),
                PhoneNumberException.InvalidPhoneNumberStateException(),
                PhoneNumberException.InvalidTokenException(),
                PhoneNumberException.InsufficientEntitlementsException(),
                PhoneNumberException.SingletonKeyPairNotFoundException(),
            )
        for (rejection in rejections) {
            Pair(rejection::class.simpleName, rejection.isDefinitiveRejection) shouldBe
                Pair(rejection::class.simpleName, true)
        }
    }

    @Test
    fun `isDefinitiveRejection should be false for a failure after which the number may exist`() {
        val failures =
            listOf(
                PhoneNumberException.FailedException(),
                PhoneNumberException.ProvisionFailedException(),
                PhoneNumberException.ServiceException(),
                PhoneNumberException.UnknownException(RuntimeException("transport")),
                PhoneNumberException.KeyPairRemovalFailedException(
                    deprovisioned = PhoneNumberTransformer.fromEntityToApi(EntityDataFactory.phoneNumberEntity()),
                ),
            )
        for (failure in failures) {
            Pair(failure::class.simpleName, failure.isDefinitiveRejection) shouldBe
                Pair(failure::class.simpleName, false)
        }
    }

    @Test
    fun `isDefinitiveRejection should be false for a throwable that is not a PhoneNumberException`() {
        RuntimeException("boom").isDefinitiveRejection shouldBe false
        CancellationException("cancelled").isDefinitiveRejection shouldBe false
    }

    @Test
    fun `every PhoneNumberException subtype should be covered by these tests`() {
        PhoneNumberException::class.sealedSubclasses.size shouldBe 16
    }
}
