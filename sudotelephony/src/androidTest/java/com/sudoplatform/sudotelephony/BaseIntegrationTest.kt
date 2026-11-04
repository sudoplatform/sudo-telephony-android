/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony

import android.content.Context
import android.os.StrictMode
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.sudoplatform.sudoentitlements.SudoEntitlementsClient
import com.sudoplatform.sudoentitlementsadmin.SudoEntitlementsAdminClient
import com.sudoplatform.sudoentitlementsadmin.types.Entitlement
import com.sudoplatform.sudokeymanager.KeyManagerFactory
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.types.PhoneNumber
import com.sudoplatform.sudotelephony.types.PhoneNumberState
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.TESTAuthenticationProvider
import io.kotlintest.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.AfterClass
import org.junit.BeforeClass
import timber.log.Timber

/**
 * Base class for the [SudoTelephonyClient] integration tests.
 *
 * Handles the register / sign in / entitlement lifecycle once per test class and exposes the
 * client to the concrete test suites. Credentials are supplied by the CI runner as
 * instrumentation arguments, falling back to assets copied into the test APK.
 */
abstract class BaseIntegrationTest {
    /** Numbers provisioned by the running test, released by [releaseProvisionedPhoneNumbers]. */
    private val provisionedPhoneNumberIds = mutableSetOf<String>()

    /**
     * Registers a provisioned phone number for automatic release after the test, so a run
     * does not consume the user's phone number allowance.
     *
     * @param id [String] ID of the provisioned phone number.
     * @return [id], so this can wrap a provisioning call.
     */
    protected fun trackForRelease(id: String): String =
        id.also {
            provisionedPhoneNumberIds.add(it)
        }

    /**
     * Releases every number the test provisioned.
     *
     * A number cannot be released until its provisioning order completes, so this waits for
     * it to leave [PhoneNumberState.PROVISIONING] first. Failures are logged rather than
     * thrown: cleanup must not turn a passing test red, and a FAILED number can never be
     * released.
     */
    @After
    fun releaseProvisionedPhoneNumbers() =
        runBlocking {
            provisionedPhoneNumberIds.forEach { releaseQuietly(it) }
            provisionedPhoneNumberIds.clear()
        }

    /**
     * Reads a phone number until [isSettled] holds for its state.
     *
     * @param id [String] ID of the phone number to read.
     * @param isSettled Predicate on the number's state that ends the wait.
     * @return The number once [isSettled] holds.
     * @throws AssertionError if the number never satisfies [isSettled], or does not exist.
     */
    protected suspend fun awaitState(
        id: String,
        isSettled: (PhoneNumberState) -> Boolean,
    ): PhoneNumber {
        val deadline = System.currentTimeMillis() + STATE_TIMEOUT_MS
        while (true) {
            val phoneNumber =
                telephonyClient.getPhoneNumber(id)
                    ?: throw AssertionError("Phone number $id does not exist")
            if (isSettled(phoneNumber.state)) {
                return phoneNumber
            }
            if (System.currentTimeMillis() >= deadline) {
                throw AssertionError("$id stayed in state ${phoneNumber.state}")
            }
            delay(STATE_POLL_INTERVAL_MS)
        }
    }

    private suspend fun releaseQuietly(id: String) {
        try {
            // Only an ACTIVE or SUSPENDED number can be released, and a released one needs
            // no further work.
            val phoneNumber = awaitState(id) { it != PhoneNumberState.PROVISIONING }
            if (phoneNumber.state == PhoneNumberState.RELEASED) {
                return
            }
            if (phoneNumber.state == PhoneNumberState.FAILED) {
                logger.error("cannot release $id, it failed to provision")
                return
            }
            telephonyClient.deprovisionPhoneNumber(id)
            logger.info("released phone number $id")
        } catch (e: Throwable) {
            logger.error("failed to release $id: $e")
        }
    }

    companion object {
        private const val NAMESPACE = "tels-client-test"

        /** How long to wait for a phone number to reach an expected state. */
        private const val STATE_TIMEOUT_MS = 60_000L

        /** How long to wait between state reads. */
        private const val STATE_POLL_INTERVAL_MS = 2_000L

        val context: Context = ApplicationProvider.getApplicationContext()

        private const val VERBOSE = false
        private val logLevel = if (VERBOSE) LogLevel.VERBOSE else LogLevel.INFO
        val logger = Logger("tels-test", AndroidUtilsLogDriver(logLevel))

        /**
         * The telephony entitlements the test user needs. Searching for available numbers is
         * gated behind the service entitlement. The phone number allowance is deliberately
         * small: [releaseProvisionedPhoneNumbers] returns each number after its test, so a
         * run that leaks numbers fails here rather than passing quietly.
         */
        @JvmStatic
        protected val entitlements =
            listOf(
                Entitlement("sudoplatform.telephony.serviceUserEntitled", "test", 1),
                Entitlement("sudoplatform.telephony.phoneNumberMaxPerUser", "test", 2),
            )

        @JvmStatic
        protected val userClient by lazy {
            SudoUserClient
                .builder(context)
                .setNamespace(NAMESPACE)
                .build()
        }

        @JvmStatic
        protected val entitlementsClient by lazy {
            SudoEntitlementsClient
                .builder()
                .setContext(context)
                .setSudoUserClient(userClient)
                .build()
        }

        @JvmStatic
        protected val entitlementsAdminClient by lazy {
            val adminApiKey = readArgument("ADMIN_API_KEY", "api.key")
            SudoEntitlementsAdminClient.builder(context, adminApiKey).build()
        }

        @JvmStatic
        protected val keyManager by lazy {
            KeyManagerFactory(context).createAndroidKeyManager(NAMESPACE)
        }

        @JvmStatic
        protected lateinit var telephonyClient: SudoTelephonyClient

        private fun readTextFile(fileName: String): String =
            context.assets.open(fileName).bufferedReader().use {
                it.readText().trim()
            }

        /**
         * The value of an optional instrumentation argument, or [default] when the runner did
         * not supply one. Lets a test target an instance whose provider inventory differs.
         *
         * @param argumentName [String] Name of the instrumentation argument.
         * @param default [String] Value to use when the argument is absent.
         * @return The argument value or [default].
         */
        @JvmStatic
        protected fun optionalArgument(
            argumentName: String,
            default: String,
        ): String =
            InstrumentationRegistry
                .getArguments()
                .getString(argumentName)
                ?.trim()
                ?.ifEmpty { null } ?: default

        @JvmStatic
        protected fun readArgument(
            argumentName: String,
            fallbackFileName: String?,
        ): String {
            val argumentValue =
                InstrumentationRegistry.getArguments().getString(argumentName)?.trim()
            if (argumentValue != null) {
                return argumentValue
            }
            if (fallbackFileName != null) {
                return readTextFile(fallbackFileName)
            }
            throw IllegalArgumentException("$argumentName property not found")
        }

        private suspend fun register() {
            userClient.isRegistered() shouldBe false

            val privateKey = readArgument("REGISTER_KEY", "register_key.private")
            val keyId = readArgument("REGISTER_KEY_ID", "register_key.id")

            val authProvider =
                TESTAuthenticationProvider(
                    name = NAMESPACE,
                    privateKey = privateKey,
                    publicKey = null,
                    keyManager = keyManager,
                    keyId = keyId,
                )

            userClient.registerWithAuthenticationProvider(authProvider, NAMESPACE)
        }

        @JvmStatic
        protected suspend fun deregister() {
            userClient.deregister()
        }

        private suspend fun registerAndSignIn() {
            userClient.isRegistered() shouldBe false
            register()
            userClient.isRegistered() shouldBe true
            userClient.signInWithKey()
            userClient.isSignedIn() shouldBe true
        }

        @JvmStatic
        protected suspend fun registerSignInAndEntitle() {
            registerAndSignIn()
            val externalId = entitlementsClient.getExternalId()
            entitlementsAdminClient.applyEntitlementsToUser(externalId, entitlements)
            entitlementsClient.redeemEntitlements()
        }

        @BeforeClass
        @JvmStatic
        fun init() {
            Timber.plant(Timber.DebugTree())
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy
                    .Builder()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build(),
            )

            userClient.reset()

            telephonyClient =
                SudoTelephonyClient
                    .builder()
                    .setContext(context)
                    .setSudoUserClient(userClient)
                    .setLogger(logger)
                    .build()

            runTest {
                registerSignInAndEntitle()
            }
        }

        @AfterClass
        @JvmStatic
        fun fini() =
            runTest {
                if (userClient.isRegistered()) {
                    deregister()
                }
                telephonyClient.close()
                userClient.reset()
                Timber.uprootAll()
            }
    }
}
