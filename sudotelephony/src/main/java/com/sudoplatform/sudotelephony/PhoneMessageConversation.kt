package com.sudoplatform.sudotelephony

import android.os.Parcelable
import com.sudoplatform.sudotelephony.type.ConversationType
import kotlinx.android.parcel.Parcelize
import java.util.*

/**
 * Enum that represents the type of the message.
 */
enum class MessageConversationType {
    INDIVIDUAL,
    GROUP,
    UNKNOWN;

    companion object {
        fun fromInternalType(internalType: ConversationType): MessageConversationType {
            return when (internalType) {
                ConversationType.INDIVIDUAL -> INDIVIDUAL
                ConversationType.GROUP -> GROUP
                else -> UNKNOWN
            }
        }
    }
}

/**
 * An object representing a conversation between a sudo phone number and one or more participants. The id of this object will be set as the conversation id on message objects
 */
@Parcelize
data class PhoneMessageConversation (
    /// Unique ID of the conversation. Calculated by calculating the v5 UUID of `localPhoneNumber + remotePhoneNumber` with `owner` as the namespace
    val id: String,
    /// The ID of the owner of the message (also referred to as the subject)
    val owner: String,
    /// The `MessageConversationType` of the conversation
    val type: MessageConversationType,
    /// The id of the latest message in the conversation
    val latestMessageId: String,
    /// The latest phone message
    var latestPhoneMessage: PhoneMessage?,
    /// The creation date of the conversation
    val created: Date,
    /// The date of the last modification to the conversation
    val updated: Date
) : Parcelable
