/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony

import android.content.Context
import com.sudoplatform.sudokeymanager.AndroidSQLiteStore
import com.sudoplatform.sudokeymanager.KeyManagerFactory
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudonotification.types.NotifiableClient
import com.sudoplatform.sudotelephony.keys.DefaultDeviceKeyManager
import com.sudoplatform.sudotelephony.logging.LogConstants
import java.util.Objects

/**
 * Decodes telephony push notifications for the Sudo Platform notification client.
 *
 * Pass an instance to `SudoNotificationClient.Builder.setNotifiableClients`. The notification
 * client registers this client's filter schema with the notification service and hands it the
 * telephony pushes it receives. This client decrypts each push with the phone number's key on
 * this device and passes the result to the app's [SudoTelephonyNotificationHandler].
 *
 * A push that can't be decoded, whose type this SDK version doesn't know, or that is sealed with
 * a key this device doesn't hold is logged and dropped; [processPayload] doesn't throw.
 *
 * Use the `NotificationConfiguration` extensions in this package, starting with
 * `initTelephonyNotifications`, to enable telephony notifications and choose which phone
 * numbers, Sudos and conversations they're sent for.
 *
 * @sample com.sudoplatform.sudotelephony.samples.Samples.sudoTelephonyNotifiableClient
 */
interface SudoTelephonyNotifiableClient : NotifiableClient {
    companion object {
        /** Create a [Builder] for [SudoTelephonyNotifiableClient]. */
        @JvmStatic
        fun builder() = Builder()
    }

    /**
     * Builder that creates a [SudoTelephonyNotifiableClient].
     *
     * The key manager settings must match the [SudoTelephonyClient] that provisioned the phone
     * numbers, so that this client finds their keys.
     */
    class Builder internal constructor() {
        private var context: Context? = null
        private var notificationHandler: SudoTelephonyNotificationHandler? = null
        private var keyManager: KeyManagerInterface? = null
        private var namespace: String = SudoTelephonyClient.DEFAULT_KEY_NAMESPACE
        private var databaseName: String = AndroidSQLiteStore.DEFAULT_DATABASE_NAME
        private var logger: Logger =
            Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO))

        /** Provide the application [Context] (required). */
        fun setContext(context: Context) =
            also {
                this.context = context
            }

        /** Provide the handler that receives decoded notifications (required). */
        fun setNotificationHandler(notificationHandler: SudoTelephonyNotificationHandler) =
            also {
                this.notificationHandler = notificationHandler
            }

        /**
         * Provide the key manager that holds the phone number keys (optional). When not set, a
         * key manager is created from the namespace and database name.
         */
        fun setKeyManager(keyManager: KeyManagerInterface) =
            also {
                this.keyManager = keyManager
            }

        /**
         * Provide the namespace keys are stored under (optional). Defaults to
         * [SudoTelephonyClient.DEFAULT_KEY_NAMESPACE].
         */
        fun setNamespace(namespace: String) =
            also {
                this.namespace = namespace
            }

        /** Provide the name of the key store database (optional). */
        fun setDatabaseName(databaseName: String) =
            also {
                this.databaseName = databaseName
            }

        /** Provide the [Logger] used for errors and diagnostic information (optional). */
        fun setLogger(logger: Logger) =
            also {
                this.logger = logger
            }

        /**
         * Construct the [SudoTelephonyNotifiableClient].
         *
         * @throws NullPointerException if the context or notification handler isn't set.
         */
        @Throws(NullPointerException::class)
        fun build(): SudoTelephonyNotifiableClient {
            Objects.requireNonNull(context, "Context must be provided.")
            Objects.requireNonNull(notificationHandler, "Notification handler must be provided.")

            val deviceKeyManager =
                DefaultDeviceKeyManager(
                    keyManager =
                        keyManager ?: KeyManagerFactory(context!!).createAndroidKeyManager(
                            namespace,
                            databaseName,
                        ),
                    logger = logger,
                )

            return DefaultSudoTelephonyNotifiableClient(
                deviceKeyManager = deviceKeyManager,
                notificationHandler = notificationHandler!!,
                logger = logger,
            )
        }
    }
}
