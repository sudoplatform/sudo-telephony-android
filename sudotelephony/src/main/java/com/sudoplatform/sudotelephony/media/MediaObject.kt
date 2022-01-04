package com.sudoplatform.sudotelephony.media

import android.os.Parcelable
import androidx.annotation.Keep
import com.sudoplatform.sudotelephony.graphql.fragment.S3MediaObject
import kotlinx.android.parcel.Parcelize

/**
 * A remote media object
 */
@Keep
@Parcelize
data class MediaObject(
    val key: String,
    val bucket: String,
    val region: String
) : Parcelable {
    internal companion object {
        fun fromS3Media(media: S3MediaObject): MediaObject {
            return MediaObject(media.key(), media.bucket(), media.region())
        }
    }
}
