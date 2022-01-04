package com.sudoplatform.sudotelephony.messages

import android.os.Parcelable
import androidx.annotation.Keep
import com.sudoplatform.sudotelephony.graphql.type.Direction
import com.sudoplatform.sudotelephony.graphql.type.MessageState
import com.sudoplatform.sudotelephony.media.MediaObject
import kotlinx.android.parcel.Parcelize
import java.time.Instant

/**
 * A message that has been either received or sent by the user.
 */
@Keep
@Parcelize
data class PhoneMessage(
    val id: String,
    val owner: String,
    val conversation: String,
    val updated: Instant,
    val created: Instant,
    val local: String,
    val remote: String,
    val body: String,
    val direction: Direction,
    val state: MessageState,
    val media: List<MediaObject> = emptyList()
) : Parcelable
