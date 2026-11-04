/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony

import android.content.Context
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.api.ApiClient
import com.sudoplatform.sudotelephony.internal.data.common.StringConstants
import com.sudoplatform.sudotelephony.internal.data.common.mechanisms.Unsealer
import com.sudoplatform.sudotelephony.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudotelephony.internal.data.country.GraphQLCountryService
import com.sudoplatform.sudotelephony.internal.data.phoneMessage.DefaultPhoneMessageUnsealer
import com.sudoplatform.sudotelephony.internal.data.phoneMessage.GraphQLPhoneMessageService
import com.sudoplatform.sudotelephony.internal.data.phoneMessage.transformers.PhoneMessageBatchResultTransformer
import com.sudoplatform.sudotelephony.internal.data.phoneMessage.transformers.PhoneMessageTransformer
import com.sudoplatform.sudotelephony.internal.data.phoneMessage.transformers.SortOrderTransformer
import com.sudoplatform.sudotelephony.internal.data.phoneNumber.GraphQLPhoneNumberService
import com.sudoplatform.sudotelephony.internal.data.phoneNumber.transformers.PhoneNumberTransformer
import com.sudoplatform.sudotelephony.internal.data.phoneNumberSearch.GraphQLPhoneNumberSearchService
import com.sudoplatform.sudotelephony.internal.data.phoneNumberSearch.transformers.PhoneNumberSearchTransformer
import com.sudoplatform.sudotelephony.internal.domain.entities.country.CountryService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageUnsealer
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.PhoneNumberSearchService
import com.sudoplatform.sudotelephony.internal.domain.useCases.DefaultUseCaseFactory
import com.sudoplatform.sudotelephony.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneMessage.ListPhoneMessagesUseCaseInput
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneMessage.UpdatePhoneMessagesUseCaseInput
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumber.ListPhoneNumbersUseCaseInput
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumber.ProvisionPhoneNumberUseCaseInput
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumberSearch.SearchAvailablePhoneNumbersUseCaseInput
import com.sudoplatform.sudotelephony.keys.ServiceKeyManager
import com.sudoplatform.sudotelephony.logging.LogConstants
import com.sudoplatform.sudotelephony.subscription.PhoneMessageSubscriber
import com.sudoplatform.sudotelephony.subscription.PhoneNumberSubscriber
import com.sudoplatform.sudotelephony.subscription.SubscriptionService
import com.sudoplatform.sudotelephony.types.ListOutput
import com.sudoplatform.sudotelephony.types.PhoneMessage
import com.sudoplatform.sudotelephony.types.PhoneMessageBatchResult
import com.sudoplatform.sudotelephony.types.PhoneNumber
import com.sudoplatform.sudotelephony.types.PhoneNumberSearch
import com.sudoplatform.sudotelephony.types.inputs.DeletePhoneMessagesInput
import com.sudoplatform.sudotelephony.types.inputs.ListPhoneMessagesInput
import com.sudoplatform.sudotelephony.types.inputs.ListPhoneNumbersInput
import com.sudoplatform.sudotelephony.types.inputs.ProvisionPhoneNumberInput
import com.sudoplatform.sudotelephony.types.inputs.SearchAvailablePhoneNumbersInput
import com.sudoplatform.sudotelephony.types.inputs.UpdatePhoneMessagesInput
import com.sudoplatform.sudouser.SudoUserClient

/**
 * Default implementation of the [SudoTelephonyClient] interface and the SDK's composition
 * root. Each public method is a thin adapter: create the use case from the factory, execute
 * it, and map the resulting entity to the public type.
 *
 * Consumers construct instances through [SudoTelephonyClient.Builder].
 *
 * @property context [Context] Application context.
 * @property apiClient [ApiClient] GraphQL client used to make service requests.
 * @property sudoUserClient [SudoUserClient] Used to determine ownership and sign in state.
 * @property serviceKeyManager [ServiceKeyManager] Generates and stores the key pairs that
 *  seal phone number messages.
 * @property logger [Logger] Errors and diagnostic information are logged here.
 * @property countryService [CountryService] The country domain service.
 * @property phoneNumberSearchService [PhoneNumberSearchService] The phone number search
 *  domain service.
 * @property phoneNumberService [PhoneNumberService] The provisioned phone number domain
 *  service.
 * @property phoneMessageService [PhoneMessageService] The phone message domain service.
 * @property phoneMessageUnsealer [PhoneMessageUnsealer] Makes sealed messages readable.
 * @property subscriptionService [SubscriptionService] Owns the subscription lifecycle.
 * @property useCaseFactory [UseCaseFactory] Builds the use cases the client delegates to.
 */
internal class DefaultSudoTelephonyClient(
    private val context: Context,
    private val apiClient: ApiClient,
    private val sudoUserClient: SudoUserClient,
    private val serviceKeyManager: ServiceKeyManager,
    private val logger: Logger =
        Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO)),
    private val countryService: CountryService =
        GraphQLCountryService(apiClient, logger),
    private val phoneNumberSearchService: PhoneNumberSearchService =
        GraphQLPhoneNumberSearchService(apiClient, logger),
    private val phoneNumberService: PhoneNumberService =
        GraphQLPhoneNumberService(apiClient, logger),
    private val phoneMessageService: PhoneMessageService =
        GraphQLPhoneMessageService(apiClient, logger),
    private val phoneMessageUnsealer: PhoneMessageUnsealer =
        DefaultPhoneMessageUnsealer(
            phoneNumberService = phoneNumberService,
            serviceKeyManager = serviceKeyManager,
            unsealer = Unsealer(serviceKeyManager),
            logger = logger,
        ),
    private val subscriptionService: SubscriptionService =
        SubscriptionService(apiClient, sudoUserClient, logger),
    private val useCaseFactory: UseCaseFactory =
        DefaultUseCaseFactory(
            countryService = countryService,
            phoneNumberSearchService = phoneNumberSearchService,
            phoneNumberService = phoneNumberService,
            phoneMessageService = phoneMessageService,
            phoneMessageUnsealer = phoneMessageUnsealer,
            serviceKeyManager = serviceKeyManager,
            logger = logger,
        ),
) : SudoTelephonyClient {
    /**
     * Checksum's for each file are generated and are used to create a checksum that is used when
     * publishing to maven central. In order to retry a failed publish without needing to change any
     * functionality, we need a way to generate a different checksum for the source code. We can
     * change the value of this property which will generate a different checksum for publishing
     * and allow us to retry. The value of `version` doesn't need to be kept up-to-date with the
     * version of the code.
     */
    private val version: String = "1.0.0"

    override suspend fun getSupportedCountries(): List<String> {
        logger.debug("getSupportedCountries")
        val useCase = useCaseFactory.createGetSupportedCountriesUseCase()
        return useCase.execute()
    }

    override suspend fun searchAvailablePhoneNumbers(input: SearchAvailablePhoneNumbersInput): PhoneNumberSearch {
        logger.debug("searchAvailablePhoneNumbers")
        val useCase = useCaseFactory.createSearchAvailablePhoneNumbersUseCase()
        val result =
            useCase.execute(
                SearchAvailablePhoneNumbersUseCaseInput(
                    countryCode = input.countryCode,
                    areaCode = input.areaCode,
                    limit = input.limit,
                ),
            )
        return PhoneNumberSearchTransformer.fromEntityToApi(result)
    }

    override suspend fun provisionPhoneNumber(input: ProvisionPhoneNumberInput): PhoneNumber {
        logger.debug("provisionPhoneNumber")
        val useCase = useCaseFactory.createProvisionPhoneNumberUseCase()
        val result =
            useCase.execute(
                ProvisionPhoneNumberUseCaseInput(
                    searchId = input.searchId,
                    phoneNumber = input.phoneNumber,
                    ownershipProofToken = input.ownershipProofToken,
                    clientRefId = input.clientRefId,
                ),
            )
        return PhoneNumberTransformer.fromEntityToApi(result)
    }

    override suspend fun deprovisionPhoneNumber(id: String): PhoneNumber {
        logger.debug("deprovisionPhoneNumber")
        val useCase = useCaseFactory.createDeprovisionPhoneNumberUseCase()
        return PhoneNumberTransformer.fromEntityToApi(useCase.execute(id))
    }

    override suspend fun getPhoneNumber(id: String): PhoneNumber? {
        logger.debug("getPhoneNumber")
        val useCase = useCaseFactory.createGetPhoneNumberUseCase()
        return useCase.execute(id)?.let { PhoneNumberTransformer.fromEntityToApi(it) }
    }

    override suspend fun listPhoneNumbers(input: ListPhoneNumbersInput): ListOutput<PhoneNumber> {
        logger.debug("listPhoneNumbers")
        val useCase = useCaseFactory.createListPhoneNumbersUseCase()
        val result =
            useCase.execute(
                ListPhoneNumbersUseCaseInput(
                    limit = input.limit,
                    nextToken = input.nextToken,
                ),
            )
        return ListOutput(
            items = result.items.map { PhoneNumberTransformer.fromEntityToApi(it) },
            nextToken = result.nextToken,
        )
    }

    override suspend fun getPhoneMessage(id: String): PhoneMessage? {
        logger.debug("getPhoneMessage")
        val useCase = useCaseFactory.createGetPhoneMessageUseCase()
        return useCase.execute(id)?.let { PhoneMessageTransformer.fromEntityToApi(it) }
    }

    override suspend fun listPhoneMessages(input: ListPhoneMessagesInput): ListOutput<PhoneMessage> {
        logger.debug("listPhoneMessages")
        val useCase = useCaseFactory.createListPhoneMessagesUseCase()
        val result =
            useCase.execute(
                ListPhoneMessagesUseCaseInput(
                    phoneNumberId = input.phoneNumberId,
                    conversationId = input.conversationId,
                    sortOrder = input.sortOrder?.let { SortOrderTransformer.fromApiToEntity(it) },
                    limit = input.limit,
                    nextToken = input.nextToken,
                ),
            )
        return ListOutput(
            items = result.items.map { PhoneMessageTransformer.fromEntityToApi(it) },
            nextToken = result.nextToken,
        )
    }

    override suspend fun deletePhoneMessages(input: DeletePhoneMessagesInput): PhoneMessageBatchResult {
        logger.debug("deletePhoneMessages")
        val useCase = useCaseFactory.createDeletePhoneMessagesUseCase()
        return PhoneMessageBatchResultTransformer.fromEntityToApi(useCase.execute(input.ids))
    }

    override suspend fun updatePhoneMessages(input: UpdatePhoneMessagesInput): PhoneMessageBatchResult {
        logger.debug("updatePhoneMessages")
        val useCase = useCaseFactory.createUpdatePhoneMessagesUseCase()
        val result =
            useCase.execute(
                UpdatePhoneMessagesUseCaseInput(ids = input.ids, seen = input.seen),
            )
        return PhoneMessageBatchResultTransformer.fromEntityToApi(result)
    }

    override suspend fun subscribeToPhoneNumbers(
        subscriptionId: String,
        subscriber: PhoneNumberSubscriber,
    ) {
        logger.debug("subscribeToPhoneNumbers")
        subscriptionService.subscribeToPhoneNumbers(subscriptionId, subscriber)
    }

    override fun unsubscribeFromPhoneNumbers(subscriptionId: String) {
        logger.debug("unsubscribeFromPhoneNumbers")
        subscriptionService.unsubscribeFromPhoneNumbers(subscriptionId)
    }

    override fun unsubscribeAllFromPhoneNumbers() {
        logger.debug("unsubscribeAllFromPhoneNumbers")
        subscriptionService.unsubscribeAllFromPhoneNumbers()
    }

    override suspend fun subscribeToPhoneMessages(
        subscriptionId: String,
        subscriber: PhoneMessageSubscriber,
    ) {
        logger.debug("subscribeToPhoneMessages")
        subscriptionService.subscribeToPhoneMessages(subscriptionId, subscriber)
    }

    override fun unsubscribeFromPhoneMessages(subscriptionId: String) {
        logger.debug("unsubscribeFromPhoneMessages")
        subscriptionService.unsubscribeFromPhoneMessages(subscriptionId)
    }

    override fun unsubscribeAllFromPhoneMessages() {
        logger.debug("unsubscribeAllFromPhoneMessages")
        subscriptionService.unsubscribeAllFromPhoneMessages()
    }

    override suspend fun importKeys(archiveData: ByteArray) {
        logger.debug("importKeys")
        if (archiveData.isEmpty()) {
            throw SudoTelephonyClient.CryptographicKeysException.InvalidArgumentException(
                StringConstants.EMPTY_KEY_ARCHIVE_MSG,
            )
        }
        try {
            serviceKeyManager.importKeys(archiveData)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw ErrorTransformer.interpretCryptographicKeysException(e)
        }
    }

    override suspend fun exportKeys(): ByteArray {
        logger.debug("exportKeys")
        try {
            return serviceKeyManager.exportKeys()
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw ErrorTransformer.interpretCryptographicKeysException(e)
        }
    }

    override fun reset() {
        logger.debug("reset")
        close()
        this.serviceKeyManager.removeAllKeys()
    }

    override fun close() {
        logger.debug("close")
        subscriptionService.close()
    }
}
