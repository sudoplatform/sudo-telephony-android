/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony

import com.sudoplatform.sudonotification.types.NotificationConfiguration
import com.sudoplatform.sudonotification.types.NotificationFilterItem
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct manipulation of the Notification SDK's [NotificationConfiguration] by the
 * telephony SDK's extensions to it.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationConfigurationExtensionsTest {
    private val serviceName = "telephonyService"

    private val expectedFirstRule =
        "{\"!\":[{\"in\":[{\"var\":\"meta.type\"},[\"messageReceived\"]]}]}"

    private val staleFirstRule =
        "{\"!\":[{\"in\":[{\"var\":\"meta.type\"},[\"oldType\"]]}]}"

    private fun disableRule(
        metaName: String,
        metaValue: String,
    ) = NotificationFilterItem(
        name = serviceName,
        status = NotificationConfiguration.DISABLE_STR,
        rules = "{\"==\":[{\"var\":\"meta.$metaName\"},\"$metaValue\"]}",
    )

    private fun initialised() = NotificationConfiguration(configs = listOf()).initTelephonyNotifications()

    @Test
    fun `default first rule should be the exact expected JSON`() {
        DEFAULT_FIRST_RULE_STRING shouldBe expectedFirstRule
        DEFAULT_FIRST_RULE.name shouldBe serviceName
        DEFAULT_FIRST_RULE.status shouldBe NotificationConfiguration.DISABLE_STR
        DEFAULT_LAST_RULE.name shouldBe serviceName
        DEFAULT_LAST_RULE.status shouldBe NotificationConfiguration.ENABLE_STR
        DEFAULT_LAST_RULE.rules shouldBe NotificationConfiguration.DEFAULT_RULE_STRING
    }

    @Test
    fun `initTelephonyNotifications() should accept all message received notifications`() {
        val configs = initialised().configs

        configs shouldHaveSize 2

        configs[0].name shouldBe serviceName
        configs[0].status shouldBe NotificationConfiguration.DISABLE_STR
        configs[0].rules shouldBe expectedFirstRule

        configs[1].name shouldBe serviceName
        configs[1].status shouldBe NotificationConfiguration.ENABLE_STR
        configs[1].rules shouldBe NotificationConfiguration.DEFAULT_RULE_STRING
    }

    @Test
    fun `initTelephonyNotifications() should preserve existing rules`() {
        val existingItem = disableRule("phoneNumberId", "ignored-phone-number-id")

        val configs =
            NotificationConfiguration(configs = listOf(existingItem))
                .initTelephonyNotifications()
                .configs

        configs shouldHaveSize 3
        configs[0] shouldBe DEFAULT_FIRST_RULE
        configs[1] shouldBe existingItem
        configs[2] shouldBe DEFAULT_LAST_RULE
    }

    @Test
    fun `initTelephonyNotifications() should preserve existing rules on reinitialisation`() {
        val existingItem = disableRule("phoneNumberId", "ignored-phone-number-id")

        val configs =
            NotificationConfiguration(configs = listOf(existingItem))
                .initTelephonyNotifications()
                .initTelephonyNotifications()
                .configs

        configs shouldHaveSize 3
        configs[0] shouldBe DEFAULT_FIRST_RULE
        configs[1] shouldBe existingItem
        configs[2] shouldBe DEFAULT_LAST_RULE
    }

    @Test
    fun `initTelephonyNotifications() should be idempotent`() {
        val once =
            initialised()
                .setTelephonyNotificationsForSudoId(sudoId = "sudo-id", enabled = false)
                .initTelephonyNotifications()

        once.initTelephonyNotifications() shouldBe once
    }

    @Test
    fun `initTelephonyNotifications() should preserve other services' rules in order`() {
        val other1 = NotificationFilterItem(name = "emService", status = NotificationConfiguration.DISABLE_STR, rules = "{\"==\":[1,2]}")
        val other2 = NotificationFilterItem(name = "vcService", status = NotificationConfiguration.ENABLE_STR)
        val existingItem = disableRule("sudoId", "sudo-id")

        val configs =
            NotificationConfiguration(configs = listOf(other1, existingItem, other2))
                .initTelephonyNotifications()
                .configs

        configs shouldHaveSize 5
        configs[0] shouldBe other1
        configs[1] shouldBe other2
        configs[2] shouldBe DEFAULT_FIRST_RULE
        configs[3] shouldBe existingItem
        configs[4] shouldBe DEFAULT_LAST_RULE
    }

    @Test
    fun `initTelephonyNotifications() should replace a stale previous default first rule`() {
        val staleItem =
            NotificationFilterItem(
                name = serviceName,
                status = NotificationConfiguration.DISABLE_STR,
                rules = staleFirstRule,
            )
        val existingItem = disableRule("phoneNumberId", "phone-number-id")

        val configs =
            NotificationConfiguration(configs = listOf(staleItem, existingItem, DEFAULT_LAST_RULE))
                .initTelephonyNotifications(previousDefaultFirstRuleStrings = listOf(staleFirstRule))
                .configs

        configs shouldHaveSize 3
        configs[0] shouldBe DEFAULT_FIRST_RULE
        configs[1] shouldBe existingItem
        configs[2] shouldBe DEFAULT_LAST_RULE
    }

    @Test
    fun `setting a rule should replace a stale previous default first rule`() {
        val staleItem =
            NotificationFilterItem(
                name = serviceName,
                status = NotificationConfiguration.DISABLE_STR,
                rules = staleFirstRule,
            )

        val configs =
            NotificationConfiguration(configs = listOf(staleItem, DEFAULT_LAST_RULE))
                .setTelephonyNotificationsForSingleMeta(
                    metaName = "conversationId",
                    metaValue = "conversation-id",
                    enabled = false,
                    previousDefaultFirstRuleStrings = listOf(staleFirstRule),
                ).configs

        configs shouldHaveSize 3
        configs[0] shouldBe DEFAULT_FIRST_RULE
        configs[1] shouldBe disableRule("conversationId", "conversation-id")
        configs[2] shouldBe DEFAULT_LAST_RULE
    }

    @Test
    fun `enabling phone number when not disabled has no effect`() {
        val initialConfig = initialised()

        val config =
            initialConfig.setTelephonyNotificationsForPhoneNumberId(
                phoneNumberId = "phone-number-id",
                enabled = true,
            )

        config shouldBe initialConfig
    }

    @Test
    fun `disabling phone number when not disabled adds rule`() {
        val initialConfig = initialised()

        val phoneNumberId = "phone-number-id"
        val config =
            initialConfig.setTelephonyNotificationsForPhoneNumberId(
                phoneNumberId = phoneNumberId,
                enabled = false,
            )

        config.configs shouldHaveSize 3
        config.configs[0] shouldBe initialConfig.configs[0]
        config.configs[1] shouldBe disableRule("phoneNumberId", phoneNumberId)
        config.configs[2] shouldBe initialConfig.configs[1]
    }

    @Test
    fun `disabling phone number when already disabled has no effect`() {
        val phoneNumberId = "phone-number-id"

        val initialConfig =
            initialised()
                .setTelephonyNotificationsForPhoneNumberId(phoneNumberId = phoneNumberId, enabled = false)

        val config =
            initialConfig.setTelephonyNotificationsForPhoneNumberId(
                phoneNumberId = phoneNumberId,
                enabled = false,
            )

        config shouldBe initialConfig
    }

    @Test
    fun `enabling phone number when disabled removes disable rule`() {
        val phoneNumberId = "phone-number-id"

        val initialConfig =
            initialised()
                .setTelephonyNotificationsForPhoneNumberId(phoneNumberId = phoneNumberId, enabled = false)

        val config =
            initialConfig.setTelephonyNotificationsForPhoneNumberId(
                phoneNumberId = phoneNumberId,
                enabled = true,
            )

        config.configs shouldHaveSize 2
        config.configs[0] shouldBe initialConfig.configs[0]
        config.configs[1] shouldBe initialConfig.configs[2]
    }

    @Test
    fun `disabling phone number preserves existing rules`() {
        val phoneNumberId1 = "phone-number-id-1"
        val phoneNumberId2 = "phone-number-id-2"
        val sudoId1 = "sudo-id-1"

        val initialConfig =
            initialised()
                .setTelephonyNotificationsForPhoneNumberId(phoneNumberId = phoneNumberId1, enabled = false)
                .setTelephonyNotificationsForSudoId(sudoId = sudoId1, enabled = false)

        val config =
            initialConfig.setTelephonyNotificationsForPhoneNumberId(
                phoneNumberId = phoneNumberId2,
                enabled = false,
            )

        config.configs shouldHaveSize 5
        config.configs[0] shouldBe initialConfig.configs[0]
        config.configs[1] shouldBe initialConfig.configs[1]
        config.configs[2] shouldBe initialConfig.configs[2]
        config.configs[3] shouldBe disableRule("phoneNumberId", phoneNumberId2)
        config.configs[4] shouldBe initialConfig.configs[3]
    }

    @Test
    fun `enabling phone number preserves existing rules`() {
        val phoneNumberId1 = "phone-number-id-1"
        val phoneNumberId2 = "phone-number-id-2"
        val sudoId1 = "sudo-id-1"

        val initialConfig =
            initialised()
                .setTelephonyNotificationsForPhoneNumberId(phoneNumberId = phoneNumberId1, enabled = false)
                .setTelephonyNotificationsForPhoneNumberId(phoneNumberId = phoneNumberId2, enabled = false)
                .setTelephonyNotificationsForSudoId(sudoId = sudoId1, enabled = false)

        val config =
            initialConfig.setTelephonyNotificationsForPhoneNumberId(
                phoneNumberId = phoneNumberId2,
                enabled = true,
            )

        config.configs shouldHaveSize 4
        config.configs[0] shouldBe initialConfig.configs[0]
        config.configs[1] shouldBe initialConfig.configs[1]
        config.configs[2] shouldBe initialConfig.configs[3]
        config.configs[3] shouldBe initialConfig.configs[4]
    }

    @Test
    fun `enabling sudo when not disabled has no effect`() {
        val initialConfig = initialised()

        val config =
            initialConfig.setTelephonyNotificationsForSudoId(
                sudoId = "sudo-id",
                enabled = true,
            )

        config shouldBe initialConfig
    }

    @Test
    fun `disabling sudo when not disabled adds rule`() {
        val initialConfig = initialised()

        val sudoId = "sudo-id"
        val config =
            initialConfig.setTelephonyNotificationsForSudoId(
                sudoId = sudoId,
                enabled = false,
            )

        config.configs shouldHaveSize 3
        config.configs[0] shouldBe initialConfig.configs[0]
        config.configs[1] shouldBe disableRule("sudoId", sudoId)
        config.configs[2] shouldBe initialConfig.configs[1]
    }

    @Test
    fun `disabling sudo when already disabled has no effect`() {
        val sudoId = "sudo-id"

        val initialConfig =
            initialised()
                .setTelephonyNotificationsForSudoId(sudoId = sudoId, enabled = false)

        val config =
            initialConfig.setTelephonyNotificationsForSudoId(
                sudoId = sudoId,
                enabled = false,
            )

        config shouldBe initialConfig
    }

    @Test
    fun `enabling sudo when disabled removes disable rule`() {
        val sudoId = "sudo-id"

        val initialConfig =
            initialised()
                .setTelephonyNotificationsForSudoId(sudoId = sudoId, enabled = false)

        val config =
            initialConfig.setTelephonyNotificationsForSudoId(
                sudoId = sudoId,
                enabled = true,
            )

        config.configs shouldHaveSize 2
        config.configs[0] shouldBe initialConfig.configs[0]
        config.configs[1] shouldBe initialConfig.configs[2]
    }

    @Test
    fun `disabling sudo preserves existing rules`() {
        val phoneNumberId1 = "phone-number-id-1"
        val sudoId1 = "sudo-id-1"
        val sudoId2 = "sudo-id-2"

        val initialConfig =
            initialised()
                .setTelephonyNotificationsForPhoneNumberId(phoneNumberId = phoneNumberId1, enabled = false)
                .setTelephonyNotificationsForSudoId(sudoId = sudoId1, enabled = false)

        val config =
            initialConfig.setTelephonyNotificationsForSudoId(
                sudoId = sudoId2,
                enabled = false,
            )

        config.configs shouldHaveSize 5
        config.configs[0] shouldBe initialConfig.configs[0]
        config.configs[1] shouldBe initialConfig.configs[1]
        config.configs[2] shouldBe initialConfig.configs[2]
        config.configs[3] shouldBe disableRule("sudoId", sudoId2)
        config.configs[4] shouldBe initialConfig.configs[3]
    }

    @Test
    fun `enabling sudo preserves existing rules`() {
        val phoneNumberId1 = "phone-number-id-1"
        val sudoId1 = "sudo-id-1"
        val sudoId2 = "sudo-id-2"

        val initialConfig =
            initialised()
                .setTelephonyNotificationsForPhoneNumberId(phoneNumberId = phoneNumberId1, enabled = false)
                .setTelephonyNotificationsForSudoId(sudoId = sudoId1, enabled = false)
                .setTelephonyNotificationsForSudoId(sudoId = sudoId2, enabled = false)

        val config =
            initialConfig.setTelephonyNotificationsForSudoId(
                sudoId = sudoId1,
                enabled = true,
            )

        config.configs shouldHaveSize 4
        config.configs[0] shouldBe initialConfig.configs[0]
        config.configs[1] shouldBe initialConfig.configs[1]
        config.configs[2] shouldBe initialConfig.configs[3]
        config.configs[3] shouldBe initialConfig.configs[4]
    }

    @Test
    fun `enabling conversation when not disabled has no effect`() {
        val initialConfig = initialised()

        val config =
            initialConfig.setTelephonyNotificationsForConversationId(
                conversationId = "conversation-id",
                enabled = true,
            )

        config shouldBe initialConfig
    }

    @Test
    fun `disabling conversation when not disabled adds rule`() {
        val initialConfig = initialised()

        val conversationId = "conversation-id"
        val config =
            initialConfig.setTelephonyNotificationsForConversationId(
                conversationId = conversationId,
                enabled = false,
            )

        config.configs shouldHaveSize 3
        config.configs[0] shouldBe initialConfig.configs[0]
        config.configs[1] shouldBe disableRule("conversationId", conversationId)
        config.configs[2] shouldBe initialConfig.configs[1]
    }

    @Test
    fun `disabling conversation when already disabled has no effect`() {
        val conversationId = "conversation-id"

        val initialConfig =
            initialised()
                .setTelephonyNotificationsForConversationId(conversationId = conversationId, enabled = false)

        val config =
            initialConfig.setTelephonyNotificationsForConversationId(
                conversationId = conversationId,
                enabled = false,
            )

        config shouldBe initialConfig
    }

    @Test
    fun `enabling conversation when disabled removes disable rule`() {
        val conversationId = "conversation-id"

        val initialConfig =
            initialised()
                .setTelephonyNotificationsForConversationId(conversationId = conversationId, enabled = false)

        val config =
            initialConfig.setTelephonyNotificationsForConversationId(
                conversationId = conversationId,
                enabled = true,
            )

        config.configs shouldHaveSize 2
        config.configs[0] shouldBe initialConfig.configs[0]
        config.configs[1] shouldBe initialConfig.configs[2]
    }

    @Test
    fun `conversation rules do not affect phone number or sudo rules with the same value`() {
        val id = "same-id"

        val config =
            initialised()
                .setTelephonyNotificationsForPhoneNumberId(phoneNumberId = id, enabled = false)
                .setTelephonyNotificationsForSudoId(sudoId = id, enabled = false)
                .setTelephonyNotificationsForConversationId(conversationId = id, enabled = false)
                .setTelephonyNotificationsForConversationId(conversationId = id, enabled = true)

        config.configs shouldHaveSize 4
        config.isTelephonyNotificationForPhoneNumberIdEnabled(phoneNumberId = id) shouldBe false
        config.isTelephonyNotificationForSudoIdEnabled(sudoId = id) shouldBe false
        config.isTelephonyNotificationForConversationIdEnabled(conversationId = id) shouldBe true
    }

    @Test
    fun `disabling should escape the meta value in the rule`() {
        val id = """id-with-"quote"-and-\backslash"""

        val config =
            initialised()
                .setTelephonyNotificationsForConversationId(conversationId = id, enabled = false)

        config.configs[1].rules shouldBe """{"==":[{"var":"meta.conversationId"},"id-with-\"quote\"-and-\\backslash"]}"""
        config.isTelephonyNotificationForConversationIdEnabled(conversationId = id) shouldBe false
        config.setTelephonyNotificationsForConversationId(conversationId = id, enabled = true) shouldBe initialised()
    }

    @Test
    fun `isTelephonyNotificationForPhoneNumberIdEnabled() should return true for initial configuration`() {
        initialised().isTelephonyNotificationForPhoneNumberIdEnabled(phoneNumberId = "phone-number-id") shouldBe true
    }

    @Test
    fun `isTelephonyNotificationForPhoneNumberIdEnabled() should return false if disabled`() {
        val phoneNumberId = "phone-number-id"

        val config =
            initialised()
                .setTelephonyNotificationsForPhoneNumberId(phoneNumberId = phoneNumberId, enabled = false)

        config.isTelephonyNotificationForPhoneNumberIdEnabled(phoneNumberId = phoneNumberId) shouldBe false
    }

    @Test
    fun `isTelephonyNotificationForPhoneNumberIdEnabled() should return true if other phone number is disabled`() {
        val config =
            initialised()
                .setTelephonyNotificationsForPhoneNumberId(phoneNumberId = "phone-number-id-1", enabled = false)

        config.isTelephonyNotificationForPhoneNumberIdEnabled(phoneNumberId = "phone-number-id-2") shouldBe true
    }

    @Test
    fun `isTelephonyNotificationForPhoneNumberIdEnabled() should return false for multiple disabled phone numbers`() {
        val phoneNumberId1 = "phone-number-id-1"
        val phoneNumberId2 = "phone-number-id-2"

        val config =
            initialised()
                .setTelephonyNotificationsForPhoneNumberId(phoneNumberId = phoneNumberId1, enabled = false)
                .setTelephonyNotificationsForPhoneNumberId(phoneNumberId = phoneNumberId2, enabled = false)

        config.isTelephonyNotificationForPhoneNumberIdEnabled(phoneNumberId = phoneNumberId1) shouldBe false
        config.isTelephonyNotificationForPhoneNumberIdEnabled(phoneNumberId = phoneNumberId2) shouldBe false
    }

    @Test
    fun `isTelephonyNotificationForSudoIdEnabled() should return true for initial configuration`() {
        initialised().isTelephonyNotificationForSudoIdEnabled(sudoId = "sudo-id") shouldBe true
    }

    @Test
    fun `isTelephonyNotificationForSudoIdEnabled() should return false if disabled`() {
        val sudoId = "sudo-id"

        val config =
            initialised()
                .setTelephonyNotificationsForSudoId(sudoId = sudoId, enabled = false)

        config.isTelephonyNotificationForSudoIdEnabled(sudoId = sudoId) shouldBe false
    }

    @Test
    fun `isTelephonyNotificationForSudoIdEnabled() should return true if other sudo is disabled`() {
        val config =
            initialised()
                .setTelephonyNotificationsForSudoId(sudoId = "sudo-id-1", enabled = false)

        config.isTelephonyNotificationForSudoIdEnabled(sudoId = "sudo-id-2") shouldBe true
    }

    @Test
    fun `isTelephonyNotificationForSudoIdEnabled() should return false for multiple disabled sudos`() {
        val sudoId1 = "sudo-id-1"
        val sudoId2 = "sudo-id-2"

        val config =
            initialised()
                .setTelephonyNotificationsForSudoId(sudoId = sudoId1, enabled = false)
                .setTelephonyNotificationsForSudoId(sudoId = sudoId2, enabled = false)

        config.isTelephonyNotificationForSudoIdEnabled(sudoId = sudoId1) shouldBe false
        config.isTelephonyNotificationForSudoIdEnabled(sudoId = sudoId2) shouldBe false
    }

    @Test
    fun `isTelephonyNotificationForConversationIdEnabled() should return true for initial configuration`() {
        initialised().isTelephonyNotificationForConversationIdEnabled(conversationId = "conversation-id") shouldBe true
    }

    @Test
    fun `isTelephonyNotificationForConversationIdEnabled() should return false if disabled`() {
        val conversationId = "conversation-id"

        val config =
            initialised()
                .setTelephonyNotificationsForConversationId(conversationId = conversationId, enabled = false)

        config.isTelephonyNotificationForConversationIdEnabled(conversationId = conversationId) shouldBe false
    }

    @Test
    fun `isTelephonyNotificationForConversationIdEnabled() should return true if other conversation is disabled`() {
        val config =
            initialised()
                .setTelephonyNotificationsForConversationId(conversationId = "conversation-id-1", enabled = false)

        config.isTelephonyNotificationForConversationIdEnabled(conversationId = "conversation-id-2") shouldBe true
    }

    @Test
    fun `isTelephonyNotificationForConversationIdEnabled() should return false for multiple disabled conversations`() {
        val conversationId1 = "conversation-id-1"
        val conversationId2 = "conversation-id-2"

        val config =
            initialised()
                .setTelephonyNotificationsForConversationId(conversationId = conversationId1, enabled = false)
                .setTelephonyNotificationsForConversationId(conversationId = conversationId2, enabled = false)

        config.isTelephonyNotificationForConversationIdEnabled(conversationId = conversationId1) shouldBe false
        config.isTelephonyNotificationForConversationIdEnabled(conversationId = conversationId2) shouldBe false
    }

    @Test
    fun `isRuleMatchingSingleMeta() should ignore rules that are not JSON objects`() {
        isRuleMatchingSingleMeta("not json", "sudoId", "sudo-id") shouldBe false
        isRuleMatchingSingleMeta("[1,2]", "sudoId", "sudo-id") shouldBe false
        isRuleMatchingSingleMeta(null, "sudoId", "sudo-id") shouldBe false
    }

    @Test
    fun `isRuleMatchingSingleMeta() should match either operand order`() {
        isRuleMatchingSingleMeta("{\"==\":[\"sudo-id\",{\"var\":\"meta.sudoId\"}]}", "sudoId", "sudo-id") shouldBe true
        isRuleMatchingSingleMeta("{\"==\":[{\"var\":\"meta.sudoId\"},\"sudo-id\"]}", "sudoId", "sudo-id") shouldBe true
    }
}
