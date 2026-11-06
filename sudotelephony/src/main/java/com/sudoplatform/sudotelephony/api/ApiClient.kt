/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.api

import com.amplifyframework.api.ApiException
import com.amplifyframework.api.graphql.GraphQLOperation
import com.amplifyframework.api.graphql.GraphQLResponse
import com.amplifyframework.core.Action
import com.amplifyframework.core.Consumer
import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.graphql.DeletePhoneMessagesMutation
import com.sudoplatform.sudotelephony.graphql.DeprovisionPhoneNumberMutation
import com.sudoplatform.sudotelephony.graphql.GetPhoneMessageQuery
import com.sudoplatform.sudotelephony.graphql.GetPhoneNumberQuery
import com.sudoplatform.sudotelephony.graphql.GetSupportedCountriesForTelephonyQuery
import com.sudoplatform.sudotelephony.graphql.GetTelephonyConfigQuery
import com.sudoplatform.sudotelephony.graphql.ListPhoneMessagesForConversationIdQuery
import com.sudoplatform.sudotelephony.graphql.ListPhoneMessagesForPhoneNumberIdQuery
import com.sudoplatform.sudotelephony.graphql.ListPhoneNumbersForSudoIdQuery
import com.sudoplatform.sudotelephony.graphql.ListPhoneNumbersQuery
import com.sudoplatform.sudotelephony.graphql.OnPhoneMessageEventSubscription
import com.sudoplatform.sudotelephony.graphql.OnPhoneNumberUpdateSubscription
import com.sudoplatform.sudotelephony.graphql.ProvisionPhoneNumberMutation
import com.sudoplatform.sudotelephony.graphql.SearchAvailablePhoneNumbersQuery
import com.sudoplatform.sudotelephony.graphql.UpdatePhoneMessagesMutation
import com.sudoplatform.sudotelephony.graphql.type.DeletePhoneMessagesInput
import com.sudoplatform.sudotelephony.graphql.type.ListPhoneMessagesForConversationIdInput
import com.sudoplatform.sudotelephony.graphql.type.ListPhoneMessagesForPhoneNumberIdInput
import com.sudoplatform.sudotelephony.graphql.type.ListPhoneNumbersForSudoIdInput
import com.sudoplatform.sudotelephony.graphql.type.ListPhoneNumbersInput
import com.sudoplatform.sudotelephony.graphql.type.ProvisionPhoneNumberInput
import com.sudoplatform.sudotelephony.graphql.type.SearchAvailablePhoneNumbersInput
import com.sudoplatform.sudotelephony.graphql.type.UpdatePhoneMessagesInput
import com.sudoplatform.sudouser.amplify.GraphQLClient

/**
 * Wrapper around the [GraphQLClient] and its generated queries and mutations.
 * Provides a testable class to handle API requests.
 *
 * @property graphQLClient [GraphQLClient] The GraphQLClient that will be used to make the requests.
 * @property logger [Logger] The logger used to log errors.
 */
open class ApiClient(
    private val graphQLClient: GraphQLClient,
    private val logger: Logger,
) {
    // Queries

    /**
     * Execute the `GetSupportedCountriesForTelephony` query.
     *
     * @return [GraphQLResponse] carrying the supported ISO country codes and any GraphQL errors.
     */
    open suspend fun getSupportedCountriesForTelephonyQuery(): GraphQLResponse<GetSupportedCountriesForTelephonyQuery.Data> =
        this.graphQLClient.query<GetSupportedCountriesForTelephonyQuery, GetSupportedCountriesForTelephonyQuery.Data>(
            GetSupportedCountriesForTelephonyQuery.OPERATION_DOCUMENT,
            emptyMap(),
        )

    /**
     * Execute the `GetTelephonyConfig` query.
     *
     * @return [GraphQLResponse] carrying the service configuration and any GraphQL errors.
     */
    open suspend fun getTelephonyConfigQuery(): GraphQLResponse<GetTelephonyConfigQuery.Data> =
        this.graphQLClient.query<GetTelephonyConfigQuery, GetTelephonyConfigQuery.Data>(
            GetTelephonyConfigQuery.OPERATION_DOCUMENT,
            emptyMap(),
        )

    /**
     * Execute the `SearchAvailablePhoneNumbers` query.
     *
     * @param input [SearchAvailablePhoneNumbersInput] The search criteria.
     * @return [GraphQLResponse] carrying the phone number search and any GraphQL errors.
     */
    open suspend fun searchAvailablePhoneNumbersQuery(
        input: SearchAvailablePhoneNumbersInput,
    ): GraphQLResponse<SearchAvailablePhoneNumbersQuery.Data> =
        this.graphQLClient.query<SearchAvailablePhoneNumbersQuery, SearchAvailablePhoneNumbersQuery.Data>(
            SearchAvailablePhoneNumbersQuery.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    /**
     * Execute the `GetPhoneNumber` query.
     *
     * @param id [String] ID of the phone number to retrieve.
     * @return [GraphQLResponse] carrying the phone number and any GraphQL errors.
     */
    open suspend fun getPhoneNumberQuery(id: String): GraphQLResponse<GetPhoneNumberQuery.Data> =
        this.graphQLClient.query<GetPhoneNumberQuery, GetPhoneNumberQuery.Data>(
            GetPhoneNumberQuery.OPERATION_DOCUMENT,
            mapOf("id" to id),
        )

    /**
     * Execute the `ListPhoneNumbers` query.
     *
     * @param input [ListPhoneNumbersInput] Paging parameters.
     * @return [GraphQLResponse] carrying a page of phone numbers and any GraphQL errors.
     */
    open suspend fun listPhoneNumbersQuery(input: ListPhoneNumbersInput): GraphQLResponse<ListPhoneNumbersQuery.Data> =
        this.graphQLClient.query<ListPhoneNumbersQuery, ListPhoneNumbersQuery.Data>(
            ListPhoneNumbersQuery.OPERATION_DOCUMENT,
            mapOf("input" to Optional.presentIfNotNull(input)),
        )

    /**
     * Execute the `ListPhoneNumbersForSudoId` query.
     *
     * @param input [ListPhoneNumbersForSudoIdInput] The Sudo and paging parameters.
     * @return [GraphQLResponse] carrying a page of phone numbers and any GraphQL errors.
     */
    open suspend fun listPhoneNumbersForSudoIdQuery(
        input: ListPhoneNumbersForSudoIdInput,
    ): GraphQLResponse<ListPhoneNumbersForSudoIdQuery.Data> =
        this.graphQLClient.query<ListPhoneNumbersForSudoIdQuery, ListPhoneNumbersForSudoIdQuery.Data>(
            ListPhoneNumbersForSudoIdQuery.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    /**
     * Execute the `GetPhoneMessage` query.
     *
     * @param id [String] ID of the phone message to retrieve.
     * @return [GraphQLResponse] carrying the sealed message and any GraphQL errors.
     */
    open suspend fun getPhoneMessageQuery(id: String): GraphQLResponse<GetPhoneMessageQuery.Data> =
        this.graphQLClient.query<GetPhoneMessageQuery, GetPhoneMessageQuery.Data>(
            GetPhoneMessageQuery.OPERATION_DOCUMENT,
            mapOf("id" to id),
        )

    /**
     * Execute the `ListPhoneMessagesForPhoneNumberId` query.
     *
     * @param input [ListPhoneMessagesForPhoneNumberIdInput] Which phone number's messages to list and how to page them.
     * @return [GraphQLResponse] carrying a page of sealed messages and any GraphQL errors.
     */
    open suspend fun listPhoneMessagesForPhoneNumberIdQuery(
        input: ListPhoneMessagesForPhoneNumberIdInput,
    ): GraphQLResponse<ListPhoneMessagesForPhoneNumberIdQuery.Data> =
        this.graphQLClient.query<ListPhoneMessagesForPhoneNumberIdQuery, ListPhoneMessagesForPhoneNumberIdQuery.Data>(
            ListPhoneMessagesForPhoneNumberIdQuery.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    /**
     * Execute the `ListPhoneMessagesForConversationId` query.
     *
     * @param input [ListPhoneMessagesForConversationIdInput] Which conversation's messages to list and how to page them.
     * @return [GraphQLResponse] carrying a page of sealed messages and any GraphQL errors.
     */
    open suspend fun listPhoneMessagesForConversationIdQuery(
        input: ListPhoneMessagesForConversationIdInput,
    ): GraphQLResponse<ListPhoneMessagesForConversationIdQuery.Data> =
        this.graphQLClient.query<ListPhoneMessagesForConversationIdQuery, ListPhoneMessagesForConversationIdQuery.Data>(
            ListPhoneMessagesForConversationIdQuery.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    // Mutations

    /**
     * Execute the `ProvisionPhoneNumber` mutation.
     *
     * @param input [ProvisionPhoneNumberInput] The number to provision and the public key to
     *  register for it.
     * @return [GraphQLResponse] carrying the provisioned phone number and any GraphQL errors.
     */
    open suspend fun provisionPhoneNumberMutation(input: ProvisionPhoneNumberInput): GraphQLResponse<ProvisionPhoneNumberMutation.Data> =
        this.graphQLClient.mutate<ProvisionPhoneNumberMutation, ProvisionPhoneNumberMutation.Data>(
            ProvisionPhoneNumberMutation.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    /**
     * Execute the `DeprovisionPhoneNumber` mutation.
     *
     * @param id [String] ID of the phone number to release.
     * @return [GraphQLResponse] carrying the released phone number and any GraphQL errors.
     */
    open suspend fun deprovisionPhoneNumberMutation(id: String): GraphQLResponse<DeprovisionPhoneNumberMutation.Data> =
        this.graphQLClient.mutate<DeprovisionPhoneNumberMutation, DeprovisionPhoneNumberMutation.Data>(
            DeprovisionPhoneNumberMutation.OPERATION_DOCUMENT,
            mapOf("id" to id),
        )

    /**
     * Execute the `DeletePhoneMessages` mutation.
     *
     * @param input [DeletePhoneMessagesInput] The messages to delete.
     * @return [GraphQLResponse] carrying the batch outcome and any GraphQL errors.
     */
    open suspend fun deletePhoneMessagesMutation(input: DeletePhoneMessagesInput): GraphQLResponse<DeletePhoneMessagesMutation.Data> =
        this.graphQLClient.mutate<DeletePhoneMessagesMutation, DeletePhoneMessagesMutation.Data>(
            DeletePhoneMessagesMutation.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    /**
     * Execute the `UpdatePhoneMessages` mutation.
     *
     * @param input [UpdatePhoneMessagesInput] The messages to update and the state to set.
     * @return [GraphQLResponse] carrying the batch outcome and any GraphQL errors.
     */
    open suspend fun updatePhoneMessagesMutation(input: UpdatePhoneMessagesInput): GraphQLResponse<UpdatePhoneMessagesMutation.Data> =
        this.graphQLClient.mutate<UpdatePhoneMessagesMutation, UpdatePhoneMessagesMutation.Data>(
            UpdatePhoneMessagesMutation.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    // Subscriptions

    /**
     * Establish the `OnPhoneNumberUpdate` subscription for [owner].
     *
     * @param owner [String] The subject whose phone numbers to watch.
     * @return The [GraphQLOperation] watching the subscription, which cancels it when
     *  cancelled.
     */
    open suspend fun onPhoneNumberUpdateSubscription(
        owner: String,
        onSubscriptionEstablished: Consumer<GraphQLResponse<OnPhoneNumberUpdateSubscription.Data>>,
        onSubscription: Consumer<GraphQLResponse<OnPhoneNumberUpdateSubscription.Data>>,
        onSubscriptionCompleted: Action,
        onFailure: Consumer<ApiException>,
    ): GraphQLOperation<OnPhoneNumberUpdateSubscription.Data>? =
        this.graphQLClient.subscribe<OnPhoneNumberUpdateSubscription, OnPhoneNumberUpdateSubscription.Data>(
            OnPhoneNumberUpdateSubscription.OPERATION_DOCUMENT,
            mapOf("owner" to owner),
            onSubscriptionEstablished,
            onSubscription,
            onSubscriptionCompleted,
            onFailure,
        )

    /**
     * Establish the `OnPhoneMessageEvent` subscription for [owner].
     *
     * @param owner [String] The subject whose phone messages to watch.
     * @return The [GraphQLOperation] watching the subscription, which cancels it when
     *  cancelled.
     */
    open suspend fun onPhoneMessageEventSubscription(
        owner: String,
        onSubscriptionEstablished: Consumer<GraphQLResponse<OnPhoneMessageEventSubscription.Data>>,
        onSubscription: Consumer<GraphQLResponse<OnPhoneMessageEventSubscription.Data>>,
        onSubscriptionCompleted: Action,
        onFailure: Consumer<ApiException>,
    ): GraphQLOperation<OnPhoneMessageEventSubscription.Data>? =
        this.graphQLClient.subscribe<OnPhoneMessageEventSubscription, OnPhoneMessageEventSubscription.Data>(
            OnPhoneMessageEventSubscription.OPERATION_DOCUMENT,
            mapOf("owner" to owner),
            onSubscriptionEstablished,
            onSubscription,
            onSubscriptionCompleted,
            onFailure,
        )
}
