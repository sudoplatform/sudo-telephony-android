package com.sudoplatform.sudotelephony.messages

import androidx.annotation.Keep
import com.sudoplatform.sudotelephony.graphql.type.ConversationType

/**
 * Enum that represents the type of the message.
 */
@Keep
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
