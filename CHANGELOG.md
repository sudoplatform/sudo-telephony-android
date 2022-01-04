## 5.0.3&bull; [diff](https://gitlab.tools.anonyome.com/platform/telephony/sudo-telephony-android/-/compare/5.0.2...5.0.3)
 Released January 4, 2022

No changes - republishing to resolve maven central publication issue.

## 5.0.2&bull; [diff](https://gitlab.tools.anonyome.com/platform/telephony/sudo-telephony-android/-/compare/5.0.1...5.0.2)
 Released January 4, 2022

No changes - republishing to resolve maven central publication issue.

## 5.0.1&bull; [diff](https://gitlab.tools.anonyome.com/platform/telephony/sudo-telephony-android/-/compare/5.0.0...5.0.1)
 Released January 3, 2022

No changes - republishing to resolve maven central publication issue.

## 5.0.0&bull; [diff](https://gitlab.tools.anonyome.com/platform/telephony/sudo-telephony-android/-/compare/4.1.1...5.0.0)
 Released December 22, 2021

### New

- Public methods in the TelephonyClient and CallingClient are now suspend functions and use coroutines

## 4.1.1&bull; [diff](https://gitlab.tools.anonyome.com/platform/telephony/sudo-telephony-android/-/compare/4.0.1...4.1.1)
 Released December 14, 2021

### New

- Bug fixes and improvements.

## 4.0.1&bull; [diff](https://gitlab.tools.anonyome.com/platform/telephony/telephony-sdk-android/compare/4.0.0-beta-3...4.0.1)
 Released August 18, 2020

## 4.0.0-beta-3&bull; [diff](https://gitlab.tools.anonyome.com/platform/telephony/telephony-sdk-android/compare/4.0.0-beta-2...4.0.0-beta-3)
Released July 27, 2020

### New

- Added support for managing voice call history
- Added support for managing voicemail records
- Updated a few dependencies and fixed minor issues
## 4.0.0-beta-1&bull; [diff](https://gitlab.tools.anonyome.com/platform/telephony/telephony-sdk-android/compare/3.0.0...4.0.0-beta-1)
Released July 6, 2020

### New

- Added support for receiving incoming calls
- Added SDK Doc generation
- Updated a few dependencies and fixed minor issues

## 3.0.0&bull; [diff](https://gitlab.tools.anonyome.com/platform/telephony/telephony-sdk-android/compare/2.2.0...3.0.0)
Released June 12, 2020

### New

- Message subscription name change for consistency with iOS.
- Changed the Minimum API Level to 23.

## 2.3.0&bull [diff](https://gitlab.tools.anonyome.com/platform/telephony/telephony-sdk-android/compare/0.0.6...2.3.0)
Released June 9, 2020

### New

- A few changes to the call subscriber interface to make it a bit easier to work with.

### Fixed

- Resolved a user registration issue.

## 2.2.0&bull [diff](https://gitlab.tools.anonyome.com/platform/telephony/telephony-sdk-android/compare/0.0.6...2.2.0)
Released June 4, 2020

### New

- Added outgoing voice calling functionality.

## 2.1.5&bull [diff](https://gitlab.tools.anonyome.com/platform/telephony/telephony-sdk-android/compare/0.0.6...2.1.5)
Released June 1, 2020

### Fixed

 - Removed some unused constant values.

## 2.1.4&bull [diff](https://gitlab.tools.anonyome.com/platform/telephony/telephony-sdk-android/compare/0.0.6...2.1.4)
Released June 1, 2020

### Fixed

- Major model objects parcelable to make them easier to work with.

## 2.1.3&bull [diff](https://gitlab.tools.anonyome.com/platform/telephony/telephony-sdk-android/compare/0.0.6...2.1.3)
Released June 1, 2020

### Fixed

- Major stability improvements

## 2.1.2
Released May 22, 2020

### Fixed

- Fixed an issue related to downloading attachments.

## 2.1.1
Released May 22, 2020

### Fixed

- Fixed various stability issues.

## 2.1.0&bull [diff](https://gitlab.tools.anonyome.com/platform/telephony/telephony-sdk-android/compare/0.0.6...2.1.0)
Released May 18, 2020

### New

- Added the ability to subscribe to new message events.

## 2.0.1
Released May 12, 2020

### Fixed

- Fixed an issue related to retrieving the Key Manager dependency
- Removed some files from the public repository

## 2.0.0
Released May 11, 2020&bull [diff](https://gitlab.tools.anonyome.com/platform/telephony/telephony-sdk-android/compare/0.0.6...2.0.0)

### New

- Changed package names to align with other Sudo Platform SDKs.
- Added the ability to get multiple conversations.

### Fixed

- Fixed an issue with downloading message media attachments.

## 1.1.3
Released May 4, 2020 &bull [diff](https://gitlab.tools.anonyome.com/platform/telephony/telephony-sdk-android/compare/0.0.6...1.1.3)

### Fixed
- Fixed an issue with decrypting sealed message fields
- Now handling paging in various requests.

## 1.1.2
 Released April 29, 2020 &bull [diff](https://gitlab.tools.anonyome.com/platform/telephony/telephony-sdk-android/compare/0.0.6...1.1.2)

 ### Fixed
 - A small update to support a service-side schema change.

## 1.1.1

Released April 14, 2020.

### New

- Using new API Client and config manager
- Interface should match the iOS client now

### Fixed

- None

## 0.0.9

Released August 20, 2019.

### New

- Updated pull_schema.sh to use an env variable for the aws profile

### Fixed

- Updated graphql queries file to reflect changes to the schema on aws

## 0.0.6

Released May 29, 2019.

### New

- Adds `searchAvailablePhoneNumbersByAreaCode` API, to search for available phone numbers given a country code and area code.

### Fixed

- None.
