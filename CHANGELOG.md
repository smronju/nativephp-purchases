# Changelog

All notable changes to this project will be documented in this file.

## [1.0.0] - 2026-10-04

### Added

- `Purchases::getProducts($productIds)` looks up localized title and price; result via `ProductsLoaded`.
- `Purchases::purchase($productId)` presents the store's purchase sheet; result via `PurchaseCompleted` or `PurchaseFailed`.
- `Purchases::restore($silent)` lists owned one-time products; result via `PurchasesRestored`.
- Android: Google Play Billing Library 9. Purchases are acknowledged on purchase and on every restore.
- iOS: StoreKit 2, with a `Transaction.updates` listener for purchases that complete outside the app's own purchase call.
