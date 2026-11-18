/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony

import com.sudoplatform.sudonotification.types.NotificationConfiguration
import com.sudoplatform.sudonotification.types.NotificationFilterItem
import com.sudoplatform.sudotelephony.internal.data.notification.KNOWN_NOTIFICATION_TYPES
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

private const val SERVICE_NAME = SudoTelephonyClient.CONFIG_TELEPHONY_SERVICE

/**
 * Rule matching any notification type this SDK doesn't know:
 * `{"!":[{"in":[{"var":"meta.type"},[<known types>]]}]}`. The argument of `!` is wrapped in an
 * array so that the notification service finds the `var` when it validates the rule.
 */
internal val DEFAULT_FIRST_RULE_STRING =
    buildJsonObject {
        putJsonArray("!") {
            addJsonObject {
                putJsonArray("in") {
                    addJsonObject { put("var", "meta.type") }
                    addJsonArray { KNOWN_NOTIFICATION_TYPES.forEach { add(it) } }
                }
            }
        }
    }.toString()

/**
 * Default first rule strings written by earlier versions of this SDK. They are removed along
 * with [DEFAULT_FIRST_RULE_STRING] whenever the telephony rules are rewritten.
 */
internal val PREVIOUS_DEFAULT_FIRST_RULE_STRINGS: List<String> = emptyList()

/** Disables notification types this SDK doesn't know. */
internal val DEFAULT_FIRST_RULE =
    NotificationFilterItem(
        name = SERVICE_NAME,
        status = NotificationConfiguration.DISABLE_STR,
        rules = DEFAULT_FIRST_RULE_STRING,
    )

internal const val DEFAULT_LAST_RULE_STRING = NotificationConfiguration.DEFAULT_RULE_STRING

/** Enables every notification no earlier rule matched. */
internal val DEFAULT_LAST_RULE =
    NotificationFilterItem(
        name = SERVICE_NAME,
        status = NotificationConfiguration.ENABLE_STR,
        rules = DEFAULT_LAST_RULE_STRING,
    )

internal fun isDefaultRule(
    rule: String?,
    previousDefaultFirstRuleStrings: List<String>,
): Boolean =
    rule == DEFAULT_FIRST_RULE_STRING ||
        rule == DEFAULT_LAST_RULE_STRING ||
        rule in previousDefaultFirstRuleStrings

internal fun isRuleMatchingSingleMeta(
    rule: String?,
    metaName: String,
    metaValue: String,
): Boolean {
    if (rule == null) {
        return false
    }

    val jsonRules =
        try {
            Json.decodeFromString<JsonObject>(rule)
        } catch (e: SerializationException) {
            return false
        } catch (e: IllegalArgumentException) {
            return false
        }
    val equality = jsonRules["=="]
    if (equality is JsonArray && equality.size == 2) {
        val lhs = equality[0]
        val rhs = equality[1]

        // {"==":[{"var":"meta.<metaName>"},"<metaValue>"]}
        if (lhs is JsonObject && rhs is JsonPrimitive && rhs.isString) {
            val v = lhs["var"]
            if (v is JsonPrimitive && v.isString && v.content == "meta.$metaName" && rhs.content == metaValue) {
                return true
            }
        } else if (rhs is JsonObject && lhs is JsonPrimitive && lhs.isString) { // {"==":["<metaValue>",{"var":"meta.<metaName>"}]}
            val v = rhs["var"]
            if (v is JsonPrimitive && v.isString && v.content == "meta.$metaName" && lhs.content == metaValue) {
                return true
            }
        }
    }

    return false
}

internal fun NotificationConfiguration.initTelephonyNotifications(
    previousDefaultFirstRuleStrings: List<String>,
): NotificationConfiguration {
    val newConfigs =
        this.configs
            .filter { it.name != SERVICE_NAME }
            .toMutableList()

    val telephonyServiceConfigs =
        this.configs
            .filter { it.name == SERVICE_NAME }
            .filter { !isDefaultRule(it.rules, previousDefaultFirstRuleStrings) }

    newConfigs.add(DEFAULT_FIRST_RULE)
    newConfigs.addAll(telephonyServiceConfigs)
    newConfigs.add(DEFAULT_LAST_RULE)

    return NotificationConfiguration(
        configs = newConfigs.toList(),
    )
}

internal fun NotificationConfiguration.setTelephonyNotificationsForSingleMeta(
    metaName: String,
    metaValue: String,
    enabled: Boolean,
    previousDefaultFirstRuleStrings: List<String> = PREVIOUS_DEFAULT_FIRST_RULE_STRINGS,
): NotificationConfiguration {
    // Rules for other services come first, unchanged.
    val newRules =
        this.configs
            .filter { it.name != SERVICE_NAME }
            .toMutableList()

    // Telephony service rules other than the defaults and any rule for this meta value.
    val newTelephonyServiceRules =
        this.configs
            .filter { it.name == SERVICE_NAME }
            .filter { !isDefaultRule(it.rules, previousDefaultFirstRuleStrings) }
            .filter { !isRuleMatchingSingleMeta(it.rules, metaName, metaValue) }

    newRules.add(DEFAULT_FIRST_RULE)
    newRules.addAll(newTelephonyServiceRules)

    if (!enabled) {
        val newJsonRule =
            buildJsonObject {
                putJsonArray("==") {
                    addJsonObject { put("var", "meta.$metaName") }
                    add(metaValue)
                }
            }

        newRules.add(
            NotificationFilterItem(
                name = SERVICE_NAME,
                status = NotificationConfiguration.DISABLE_STR,
                rules = newJsonRule.toString(),
            ),
        )
    }

    newRules.add(DEFAULT_LAST_RULE)

    return NotificationConfiguration(
        configs = newRules.toList(),
    )
}

internal fun NotificationConfiguration.isTelephonyNotificationForSingleMetaEnabled(
    metaName: String,
    metaValue: String,
): Boolean {
    val rule =
        this.configs
            .filter { it.name == SERVICE_NAME }
            .filter { !isDefaultRule(it.rules, PREVIOUS_DEFAULT_FIRST_RULE_STRINGS) }
            .find { isRuleMatchingSingleMeta(it.rules, metaName, metaValue) }

    // With no matching rule, the default last rule enables the notification.
    return rule == null || rule.status == NotificationConfiguration.ENABLE_STR
}

/**
 * Ensures a [NotificationConfiguration] is set up to receive telephony service notifications.
 * Puts a rule disabling notification types this SDK doesn't know before the existing telephony
 * service rules, and a rule enabling everything else after them. Rules for other services are
 * kept unchanged and in order.
 *
 * Once all notification configurations across all Sudo Platform SDKs have been performed, call
 * [com.sudoplatform.sudonotification.SudoNotificationClient.setNotificationConfiguration]
 * to set the full notification configuration for your application.
 *
 * @return [NotificationConfiguration] A new configuration with the telephony service rules.
 */
fun NotificationConfiguration.initTelephonyNotifications(): NotificationConfiguration =
    initTelephonyNotifications(PREVIOUS_DEFAULT_FIRST_RULE_STRINGS)

/**
 * Enables or disables telephony service notifications for a phone number.
 *
 * Once all notification configurations across all Sudo Platform SDKs have been performed, call
 * [com.sudoplatform.sudonotification.SudoNotificationClient.setNotificationConfiguration]
 * to set the full notification configuration for your application.
 *
 * @param phoneNumberId [String] ID of the phone number to set notifications for.
 * @param enabled [Boolean] Whether notifications for the phone number are enabled.
 * @return [NotificationConfiguration] A new configuration with the updated rules.
 */
fun NotificationConfiguration.setTelephonyNotificationsForPhoneNumberId(
    phoneNumberId: String,
    enabled: Boolean,
): NotificationConfiguration = setTelephonyNotificationsForSingleMeta("phoneNumberId", phoneNumberId, enabled)

/**
 * Checks whether telephony service notifications are enabled for a phone number.
 *
 * @param phoneNumberId [String] ID of the phone number to check.
 * @return [Boolean] True if notifications for the phone number are enabled.
 */
fun NotificationConfiguration.isTelephonyNotificationForPhoneNumberIdEnabled(phoneNumberId: String): Boolean =
    isTelephonyNotificationForSingleMetaEnabled("phoneNumberId", phoneNumberId)

/**
 * Enables or disables telephony service notifications for a Sudo.
 *
 * Once all notification configurations across all Sudo Platform SDKs have been performed, call
 * [com.sudoplatform.sudonotification.SudoNotificationClient.setNotificationConfiguration]
 * to set the full notification configuration for your application.
 *
 * @param sudoId [String] ID of the Sudo to set notifications for.
 * @param enabled [Boolean] Whether notifications for the Sudo are enabled.
 * @return [NotificationConfiguration] A new configuration with the updated rules.
 */
fun NotificationConfiguration.setTelephonyNotificationsForSudoId(
    sudoId: String,
    enabled: Boolean,
): NotificationConfiguration = setTelephonyNotificationsForSingleMeta("sudoId", sudoId, enabled)

/**
 * Checks whether telephony service notifications are enabled for a Sudo.
 *
 * @param sudoId [String] ID of the Sudo to check.
 * @return [Boolean] True if notifications for the Sudo are enabled.
 */
fun NotificationConfiguration.isTelephonyNotificationForSudoIdEnabled(sudoId: String): Boolean =
    isTelephonyNotificationForSingleMetaEnabled("sudoId", sudoId)

/**
 * Enables or disables telephony service notifications for a conversation.
 *
 * Once all notification configurations across all Sudo Platform SDKs have been performed, call
 * [com.sudoplatform.sudonotification.SudoNotificationClient.setNotificationConfiguration]
 * to set the full notification configuration for your application.
 *
 * @param conversationId [String] ID of the conversation to set notifications for.
 * @param enabled [Boolean] Whether notifications for the conversation are enabled.
 * @return [NotificationConfiguration] A new configuration with the updated rules.
 */
fun NotificationConfiguration.setTelephonyNotificationsForConversationId(
    conversationId: String,
    enabled: Boolean,
): NotificationConfiguration = setTelephonyNotificationsForSingleMeta("conversationId", conversationId, enabled)

/**
 * Checks whether telephony service notifications are enabled for a conversation.
 *
 * @param conversationId [String] ID of the conversation to check.
 * @return [Boolean] True if notifications for the conversation are enabled.
 */
fun NotificationConfiguration.isTelephonyNotificationForConversationIdEnabled(conversationId: String): Boolean =
    isTelephonyNotificationForSingleMetaEnabled("conversationId", conversationId)
