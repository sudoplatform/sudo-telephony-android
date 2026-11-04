/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony

import com.sudoplatform.sudotelephony.SudoTelephonyClient.CryptographicKeysException
import com.sudoplatform.sudotelephony.internal.domain.entities.country.CountryService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageUnsealer
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.PhoneNumberSearchService
import com.sudoplatform.sudotelephony.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudotelephony.keys.DeviceKeyManager
import com.sudoplatform.sudotelephony.keys.ServiceKeyManager
import com.sudoplatform.sudotelephony.subscription.SubscriptionService
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoTelephonyClient.importKeys] and
 * [SudoTelephonyClient.exportKeys] using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoTelephonyKeyArchiveTest : BaseTests() {
    private val mockCountryService by before { mock<CountryService>() }
    private val mockPhoneNumberSearchService by before { mock<PhoneNumberSearchService>() }
    private val mockPhoneNumberService by before { mock<PhoneNumberService>() }
    private val mockPhoneMessageService by before { mock<PhoneMessageService>() }
    private val mockPhoneMessageUnsealer by before { mock<PhoneMessageUnsealer>() }
    private val mockServiceKeyManager by before { mock<ServiceKeyManager>() }
    private val mockSubscriptionService by before { mock<SubscriptionService>() }
    private val mockUseCaseFactory by before { mock<UseCaseFactory>() }

    private val archive = byteArrayOf(1, 2, 3, 4)

    private val client by before {
        DefaultSudoTelephonyClient(
            context = mockContext,
            apiClient = mockApiClient,
            sudoUserClient = mockUserClient,
            logger = mockLogger,
            countryService = mockCountryService,
            phoneNumberSearchService = mockPhoneNumberSearchService,
            phoneNumberService = mockPhoneNumberService,
            phoneMessageService = mockPhoneMessageService,
            phoneMessageUnsealer = mockPhoneMessageUnsealer,
            serviceKeyManager = mockServiceKeyManager,
            subscriptionService = mockSubscriptionService,
            useCaseFactory = mockUseCaseFactory,
        )
    }

    @Test
    fun `exportKeys() should return the archive from the key manager`() =
        runTest {
            mockServiceKeyManager.stub { on { exportKeys() } doReturn archive }

            client.exportKeys() shouldBe archive

            verify(mockServiceKeyManager).exportKeys()
        }

    @Test
    fun `exportKeys() should surface an archive failure`() =
        runTest {
            mockServiceKeyManager.stub {
                on {
                    exportKeys()
                } doThrow DeviceKeyManager.DeviceKeyManagerException.SecureKeyArchiveException("no")
            }

            shouldThrow<CryptographicKeysException.SecureKeyArchiveException> {
                client.exportKeys()
            }
        }

    @Test
    fun `exportKeys() should wrap an unexpected failure`() =
        runTest {
            mockServiceKeyManager.stub { on { exportKeys() } doThrow RuntimeException("boom") }

            shouldThrow<CryptographicKeysException.UnknownException> {
                client.exportKeys()
            }
        }

    @Test
    fun `importKeys() should pass the archive to the key manager`() =
        runTest {
            client.importKeys(archive)

            verify(mockServiceKeyManager).importKeys(eq(archive))
        }

    @Test
    fun `importKeys() should reject an empty archive without touching the key store`() =
        runTest {
            shouldThrow<CryptographicKeysException.InvalidArgumentException> {
                client.importKeys(byteArrayOf())
            }

            // An empty archive would otherwise clear every key for nothing, since importing
            // removes the existing keys first.
            verify(mockServiceKeyManager, never()).importKeys(any())
        }

    @Test
    fun `importKeys() should surface an archive failure`() =
        runTest {
            mockServiceKeyManager.stub {
                on {
                    importKeys(any())
                } doThrow DeviceKeyManager.DeviceKeyManagerException.SecureKeyArchiveException("bad")
            }

            shouldThrow<CryptographicKeysException.SecureKeyArchiveException> {
                client.importKeys(archive)
            }
        }

    @Test
    fun `importKeys() should wrap an unexpected failure`() =
        runTest {
            mockServiceKeyManager.stub { on { importKeys(any()) } doThrow RuntimeException("boom") }

            shouldThrow<CryptographicKeysException.UnknownException> {
                client.importKeys(archive)
            }
        }
}
