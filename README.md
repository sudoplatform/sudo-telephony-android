# Sudo Telephony Android SDK

## Overview

The Sudo Telephony SDK allows you to offer your users' the ability to integrate phone numbers, messaging and calling within your existing or new products.

Please see the [Sudo Platform Developer Docs](https://docs.sudoplatform.com/guides/telephony) for an overview of Telephony in the Sudo Platform.

## Version Support
| Technology          | Supported version |
| ------------------- | ----------------- |
| Min. API Level      | 26                |
| Kotlin version      | 2.1+              |

## Integration Instructions

Visit the [Telephony section](https://docs.sudoplatform.com/guides/telephony) of the Sudo Platform Developer Docs for SDK integration instructions.

## Key Management

Each phone number's messages are sealed to a key pair whose private key stays on the device.
Use `exportKeys()` and `importKeys(archiveData)` to move keys to another device, and `reset()`
to remove every key from the device (for example on sign-out).

### One key for every number (`enforceSingletonKeyPair`)

By default each phone number is provisioned with a key pair of its own. Its key is removed from
the device when the number is deprovisioned, and when the service rejects the provisioning
request. A failure that doesn't prove the service rejected it (a transport failure, an internal
service error) keeps the key, because the number may exist. The service keeps a deprovisioned
number's messages, so once its key is removed, reading them on this device fails with
`PhoneMessageException.KeyNotFoundException`. If the number is released but the device can't remove its key,
deprovisioning throws `PhoneNumberException.KeyPairRemovalFailedException`, which carries the
released number as `deprovisioned`. Treat the number as gone; the exception means the device key
store failed, and the key stays on the device.

Apps that don't need numbers kept cryptographically separate can share one key pair across all
of them. Messages are still sealed, but every number's messages are sealed to the same key, so
all numbers can be read with that one key:

```kotlin
val client = SudoTelephonyClient.builder()
    .setContext(context)
    .setSudoUserClient(sudoUserClient)
    .setEnforceSingletonKeyPair(true)
    .build()
```

The app creates the shared key pair explicitly once the user is signed in, and again after
importing keys, before provisioning. Provisioning never creates it, and fails with
`PhoneNumberException.SingletonKeyPairNotFoundException` without it:

```kotlin
val result = client.createSingletonKeysIfAbsent()
if (result.keyPair.created) {
    // The device's key material changed, so back the keys up again with exportKeys().
}
```

If the key had to be created, a key backup made before then doesn't contain it. If the user
already has phone numbers, a new key also means this device doesn't have the one they're sealed to
(for example a second device), so import the user's key backup with `importKeys(archiveData)`
instead of provisioning. Calling `createSingletonKeysIfAbsent()` on a client without
`setEnforceSingletonKeyPair(true)` fails with `CryptographicKeysException.InvalidConfigurationException`.

The shared key pair is carried by `exportKeys()` / `importKeys(archiveData)`, and removed only by
`reset()`. If its private key is ever missing from the device, `createSingletonKeysIfAbsent()`
replaces it with a new shared key pair, and the SDK logs an error and outputs it to the log driver
(so an app's driver can record it as a non-fatal): numbers sealed to the missing key can't be read
on that device until a key archive containing it is imported.

## Questions and Support

File any issues you find on the project's GitHub repository. Be careful not to share any Personally Identifiable Information (PII) or sensitive account information (API keys, credentials, etc.) when reporting an issue.

For general inquiries related to the Sudo Platform, please contact [partners@sudoplatform.com](mailto:partners@sudoplatform.com)
