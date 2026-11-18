# Module sudotelephony

Android client SDK for the Sudo Telephony service. Exposes a suspend-based,
coroutine-friendly API over the telephony AppSync GraphQL endpoint.

# Package com.sudoplatform.sudotelephony

The public surface of the SDK: the [SudoTelephonyClient] interface, its `Builder`,
and the per-domain `SupportedCountriesException` and `PhoneNumberSearchException`
hierarchies. Push notification support: [SudoTelephonyNotifiableClient], the
[SudoTelephonyNotificationHandler] it calls, and the `NotificationConfiguration` extensions
that enable telephony notifications and turn them on or off per phone number, Sudo or
conversation.

# Package com.sudoplatform.sudotelephony.types

Public, consumer-facing domain types returned by the client.

# Package com.sudoplatform.sudotelephony.types.inputs

Public input types, one per client operation that takes parameters.

# Package com.sudoplatform.sudotelephony.api

The GraphQL transport wrapper over the AppSync endpoint.
