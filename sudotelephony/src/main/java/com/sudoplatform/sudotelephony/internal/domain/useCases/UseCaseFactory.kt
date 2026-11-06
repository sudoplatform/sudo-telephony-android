/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.useCases

import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.internal.domain.entities.configuration.ConfigurationDataService
import com.sudoplatform.sudotelephony.internal.domain.entities.country.CountryService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageUnsealer
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.PhoneNumberSearchService
import com.sudoplatform.sudotelephony.internal.domain.useCases.configuration.GetConfigurationDataUseCase
import com.sudoplatform.sudotelephony.internal.domain.useCases.country.GetSupportedCountriesUseCase
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneMessage.DeletePhoneMessagesUseCase
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneMessage.GetPhoneMessageUseCase
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneMessage.ListPhoneMessagesUseCase
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneMessage.UpdatePhoneMessagesUseCase
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumber.DeprovisionPhoneNumberUseCase
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumber.GetPhoneNumberUseCase
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumber.ListPhoneNumbersUseCase
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumber.ProvisionPhoneNumberUseCase
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumberSearch.SearchAvailablePhoneNumbersUseCase
import com.sudoplatform.sudotelephony.keys.ServiceKeyManager

/**
 * Factory responsible for constructing the client's use cases, wiring each one with the
 * domain services it depends on.
 *
 * Declared as an interface so it can be faked in client-level tests, keeping the public
 * client a thin adapter over the factory.
 */
internal interface UseCaseFactory {
    /** Create a [GetSupportedCountriesUseCase]. */
    fun createGetSupportedCountriesUseCase(): GetSupportedCountriesUseCase

    /** Create a [GetConfigurationDataUseCase]. */
    fun createGetConfigurationDataUseCase(): GetConfigurationDataUseCase

    /** Create a [SearchAvailablePhoneNumbersUseCase]. */
    fun createSearchAvailablePhoneNumbersUseCase(): SearchAvailablePhoneNumbersUseCase

    /** Create a [ProvisionPhoneNumberUseCase]. */
    fun createProvisionPhoneNumberUseCase(): ProvisionPhoneNumberUseCase

    /** Create a [DeprovisionPhoneNumberUseCase]. */
    fun createDeprovisionPhoneNumberUseCase(): DeprovisionPhoneNumberUseCase

    /** Create a [GetPhoneNumberUseCase]. */
    fun createGetPhoneNumberUseCase(): GetPhoneNumberUseCase

    /** Create a [ListPhoneNumbersUseCase]. */
    fun createListPhoneNumbersUseCase(): ListPhoneNumbersUseCase

    /** Create a [GetPhoneMessageUseCase]. */
    fun createGetPhoneMessageUseCase(): GetPhoneMessageUseCase

    /** Create a [ListPhoneMessagesUseCase]. */
    fun createListPhoneMessagesUseCase(): ListPhoneMessagesUseCase

    /** Create a [DeletePhoneMessagesUseCase]. */
    fun createDeletePhoneMessagesUseCase(): DeletePhoneMessagesUseCase

    /** Create an [UpdatePhoneMessagesUseCase]. */
    fun createUpdatePhoneMessagesUseCase(): UpdatePhoneMessagesUseCase
}

/**
 * Default [UseCaseFactory] that holds the domain services and the logger and builds each
 * use case.
 *
 * @property countryService [CountryService] The country domain service port.
 * @property configurationDataService [ConfigurationDataService] The service configuration
 *  domain service port.
 * @property phoneNumberSearchService [PhoneNumberSearchService] The phone number search
 *  domain service port.
 * @property phoneNumberService [PhoneNumberService] The provisioned phone number domain
 *  service port.
 * @property phoneMessageService [PhoneMessageService] The phone message domain service port.
 * @property phoneMessageUnsealer [PhoneMessageUnsealer] Makes sealed messages readable.
 * @property serviceKeyManager [ServiceKeyManager] Generates and stores the key pairs that
 *  seal phone number messages.
 * @property logger [Logger] The SDK logger.
 */
internal class DefaultUseCaseFactory(
    private val countryService: CountryService,
    private val configurationDataService: ConfigurationDataService,
    private val phoneNumberSearchService: PhoneNumberSearchService,
    private val phoneNumberService: PhoneNumberService,
    private val phoneMessageService: PhoneMessageService,
    private val phoneMessageUnsealer: PhoneMessageUnsealer,
    private val serviceKeyManager: ServiceKeyManager,
    private val logger: Logger,
) : UseCaseFactory {
    override fun createGetSupportedCountriesUseCase(): GetSupportedCountriesUseCase = GetSupportedCountriesUseCase(countryService, logger)

    override fun createGetConfigurationDataUseCase(): GetConfigurationDataUseCase =
        GetConfigurationDataUseCase(configurationDataService, logger)

    override fun createSearchAvailablePhoneNumbersUseCase(): SearchAvailablePhoneNumbersUseCase =
        SearchAvailablePhoneNumbersUseCase(phoneNumberSearchService, logger)

    override fun createProvisionPhoneNumberUseCase(): ProvisionPhoneNumberUseCase =
        ProvisionPhoneNumberUseCase(phoneNumberService, serviceKeyManager, logger)

    override fun createDeprovisionPhoneNumberUseCase(): DeprovisionPhoneNumberUseCase =
        DeprovisionPhoneNumberUseCase(phoneNumberService, logger)

    override fun createGetPhoneNumberUseCase(): GetPhoneNumberUseCase = GetPhoneNumberUseCase(phoneNumberService, logger)

    override fun createListPhoneNumbersUseCase(): ListPhoneNumbersUseCase = ListPhoneNumbersUseCase(phoneNumberService, logger)

    override fun createGetPhoneMessageUseCase(): GetPhoneMessageUseCase =
        GetPhoneMessageUseCase(phoneMessageService, phoneMessageUnsealer, logger)

    override fun createListPhoneMessagesUseCase(): ListPhoneMessagesUseCase =
        ListPhoneMessagesUseCase(phoneMessageService, phoneMessageUnsealer, logger)

    override fun createDeletePhoneMessagesUseCase(): DeletePhoneMessagesUseCase = DeletePhoneMessagesUseCase(phoneMessageService, logger)

    override fun createUpdatePhoneMessagesUseCase(): UpdatePhoneMessagesUseCase = UpdatePhoneMessagesUseCase(phoneMessageService, logger)
}
