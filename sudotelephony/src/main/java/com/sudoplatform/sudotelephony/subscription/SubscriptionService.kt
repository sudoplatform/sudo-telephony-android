/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.subscription

import com.amplifyframework.api.ApiException
import com.amplifyframework.api.graphql.GraphQLResponse
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.SudoTelephonyClient
import com.sudoplatform.sudotelephony.api.ApiClient
import com.sudoplatform.sudotelephony.graphql.OnPhoneMessageEventSubscription
import com.sudoplatform.sudotelephony.graphql.OnPhoneNumberUpdateSubscription
import com.sudoplatform.sudotelephony.internal.data.common.StringConstants
import com.sudoplatform.sudotelephony.internal.data.phoneMessage.transformers.PhoneMessageEventTransformer
import com.sudoplatform.sudotelephony.internal.data.phoneNumber.transformers.PhoneNumberTransformer
import com.sudoplatform.sudouser.SudoUserClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Owns the phone number update and phone message event subscriptions.
 *
 * Each subscription is backed by its own [SubscriptionManager], which multiplexes one AppSync
 * watcher to every subscriber of that kind. The watcher is established on the first
 * subscriber and torn down when the last one is removed.
 *
 * @property apiClient [ApiClient] The GraphQL API client used to establish subscriptions.
 * @property userClient [SudoUserClient] Resolves the owner (subject) to subscribe for.
 * @property logger [Logger] Errors and diagnostic information are logged here.
 */
internal class SubscriptionService(
    private val apiClient: ApiClient,
    private val userClient: SudoUserClient,
    private val logger: Logger,
) : AutoCloseable {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val phoneNumberSubscriptionManager =
        PhoneNumberSubscriptionManager<OnPhoneNumberUpdateSubscription.Data>()

    private val phoneMessageSubscriptionManager =
        PhoneMessageSubscriptionManager<OnPhoneMessageEventSubscription.Data>()

    /**
     * Subscribe [subscriber] to phone number updates under [id].
     *
     * @param id [String] A caller-chosen identifier for this subscription.
     * @param subscriber [PhoneNumberSubscriber] Notified when a number changes.
     * @throws SudoTelephonyClient.PhoneNumberException.AuthenticationException if the owner
     *  cannot be resolved.
     */
    suspend fun subscribeToPhoneNumbers(
        id: String,
        subscriber: PhoneNumberSubscriber,
    ) {
        val owner =
            userClient.getSubject()
                ?: throw SudoTelephonyClient.PhoneNumberException.AuthenticationException(
                    StringConstants.SUBSCRIPTION_UNAUTHENTICATED_MSG,
                )

        phoneNumberSubscriptionManager.replaceSubscriber(id, subscriber)

        scope
            .launch {
                if (phoneNumberSubscriptionManager.watcher == null) {
                    phoneNumberSubscriptionManager.watcher =
                        apiClient.onPhoneNumberUpdateSubscription(
                            owner,
                            phoneNumberCallback.onSubscriptionEstablished,
                            phoneNumberCallback.onSubscription,
                            phoneNumberCallback.onSubscriptionCompleted,
                            phoneNumberCallback.onFailure,
                        )
                }
            }.join()
    }

    /** Unsubscribe the subscriber registered under [id] from phone number updates. */
    fun unsubscribeFromPhoneNumbers(id: String) {
        phoneNumberSubscriptionManager.removeSubscriber(id)
    }

    /** Unsubscribe every subscriber from phone number updates. */
    fun unsubscribeAllFromPhoneNumbers() {
        phoneNumberSubscriptionManager.removeAllSubscribers()
    }

    /**
     * Subscribe [subscriber] to phone message events under [id].
     *
     * @param id [String] A caller-chosen identifier for this subscription.
     * @param subscriber [PhoneMessageSubscriber] Notified when a message changes.
     * @throws SudoTelephonyClient.PhoneMessageException.AuthenticationException if the owner
     *  cannot be resolved.
     */
    suspend fun subscribeToPhoneMessages(
        id: String,
        subscriber: PhoneMessageSubscriber,
    ) {
        val owner =
            userClient.getSubject()
                ?: throw SudoTelephonyClient.PhoneMessageException.AuthenticationException(
                    StringConstants.SUBSCRIPTION_UNAUTHENTICATED_MSG,
                )

        phoneMessageSubscriptionManager.replaceSubscriber(id, subscriber)

        scope
            .launch {
                if (phoneMessageSubscriptionManager.watcher == null) {
                    phoneMessageSubscriptionManager.watcher =
                        apiClient.onPhoneMessageEventSubscription(
                            owner,
                            phoneMessageCallback.onSubscriptionEstablished,
                            phoneMessageCallback.onSubscription,
                            phoneMessageCallback.onSubscriptionCompleted,
                            phoneMessageCallback.onFailure,
                        )
                }
            }.join()
    }

    /** Unsubscribe the subscriber registered under [id] from phone message events. */
    fun unsubscribeFromPhoneMessages(id: String) {
        phoneMessageSubscriptionManager.removeSubscriber(id)
    }

    /** Unsubscribe every subscriber from phone message events. */
    fun unsubscribeAllFromPhoneMessages() {
        phoneMessageSubscriptionManager.removeAllSubscribers()
    }

    override fun close() {
        unsubscribeAllFromPhoneNumbers()
        unsubscribeAllFromPhoneMessages()
        scope.cancel()
    }

    private val phoneNumberCallback =
        object {
            val onSubscriptionEstablished: (GraphQLResponse<OnPhoneNumberUpdateSubscription.Data>) -> Unit = {
                phoneNumberSubscriptionManager.connectionStatusChanged(
                    Subscriber.ConnectionState.CONNECTED,
                )
            }
            val onSubscription: (GraphQLResponse<OnPhoneNumberUpdateSubscription.Data>) -> Unit = {
                scope.launch {
                    val update = it.data?.onPhoneNumberUpdate ?: return@launch
                    val entity = PhoneNumberTransformer.fromGraphQLToEntity(update.phoneNumber)
                    phoneNumberSubscriptionManager.phoneNumberUpdated(
                        PhoneNumberTransformer.fromEntityToApi(entity),
                    )
                }
            }
            val onSubscriptionCompleted = {
                phoneNumberSubscriptionManager.connectionStatusChanged(
                    Subscriber.ConnectionState.DISCONNECTED,
                )
            }
            val onFailure: (ApiException) -> Unit = {
                logger.error("Phone number subscription error $it")
                // A failure to establish or maintain the subscription must be reported, or a
                // caller waiting to be told the subscription opened would wait forever.
                phoneNumberSubscriptionManager.connectionStatusChanged(
                    Subscriber.ConnectionState.DISCONNECTED,
                )
            }
        }

    private val phoneMessageCallback =
        object {
            val onSubscriptionEstablished: (GraphQLResponse<OnPhoneMessageEventSubscription.Data>) -> Unit = {
                phoneMessageSubscriptionManager.connectionStatusChanged(
                    Subscriber.ConnectionState.CONNECTED,
                )
            }
            val onSubscription: (GraphQLResponse<OnPhoneMessageEventSubscription.Data>) -> Unit = {
                scope.launch {
                    val event = it.data?.onPhoneMessageEvent ?: return@launch
                    phoneMessageSubscriptionManager.phoneMessageEvent(
                        PhoneMessageEventTransformer.fromGraphQLToApi(event),
                    )
                }
            }
            val onSubscriptionCompleted = {
                phoneMessageSubscriptionManager.connectionStatusChanged(
                    Subscriber.ConnectionState.DISCONNECTED,
                )
            }
            val onFailure: (ApiException) -> Unit = {
                logger.error("Phone message subscription error $it")
                // A failure to establish or maintain the subscription must be reported, or a
                // caller waiting to be told the subscription opened would wait forever.
                phoneMessageSubscriptionManager.connectionStatusChanged(
                    Subscriber.ConnectionState.DISCONNECTED,
                )
            }
        }
}
