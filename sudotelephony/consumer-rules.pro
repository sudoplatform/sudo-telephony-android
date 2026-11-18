# Rules applied to apps that use this library.

# Keep GraphQL generated code
-keep class com.sudoplatform.sudotelephony.graphql.** { *; }
-keep class com.amplifyframework.api.graphql.** { *; }

# Push notification payloads are decoded with kotlinx.serialization, always through an explicit
# `X.serializer()` call, so R8 keeps the generated serializers by reachability and no rule is
# needed here. Keep it that way: decoding through a serializer looked up at runtime, for example
# `serializer(KType)` or polymorphic decoding, would need keep rules. A minified (R8 full mode)
# app decoding a push was checked to work without any serialization rules.

# Public types are Parcelable so apps can pass them in Intents and Bundles. Android reads
# CREATOR by reflection when unparcelling. The default proguard-android-optimize.txt keeps it
# too; this covers apps that don't use the default file.
-keepclassmembers class com.sudoplatform.sudotelephony.** implements android.os.Parcelable {
    public static final ** CREATOR;
}
