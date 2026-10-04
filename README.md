# NativePHP Purchases

One-time in-app purchases (non-consumables such as "Remove Ads") with restore, for
[NativePHP Mobile](https://nativephp.com/docs/mobile). Google Play Billing Library 9 on Android,
StoreKit 2 on iOS. No server and no receipt-validation backend: the store confirms ownership,
and your app remembers it.

## Installation

```bash
composer require smronju/nativephp-purchases
php artisan vendor:publish --tag=nativephp-plugins-provider
php artisan native:plugin:register smronju/nativephp-purchases
```

Rebuild the app afterwards (`php artisan native:run`). Native code is only compiled in at build time.

## Usage

```php
use Smronju\NativephpPurchases\Facades\Purchases;

Purchases::getProducts(['remove_ads']); // -> ProductsLoaded
Purchases::purchase('remove_ads');      // -> PurchaseCompleted | PurchaseFailed
Purchases::restore();                   // -> PurchasesRestored
Purchases::restore(silent: true);       // -> PurchasesRestored, without an Apple ID prompt
```

Every call is fire-and-forget. Handle the results in a NativeComponent with `#[On(...)]`:

| Event | Properties |
| --- | --- |
| `ProductsLoaded` | `array $products` (`productId`, `title`, `description`, `price`), `?string $error` |
| `PurchaseCompleted` | `string $productId` |
| `PurchaseFailed` | `string $productId`, `string $reason` (`cancelled`, `pending`, `unavailable`, `error`), `?string $message` |
| `PurchasesRestored` | `array $productIds`, `bool $silent`, `?string $error` |

`PurchaseCompleted` also fires when buying something the account already owns, and when a pending
payment (Ask to Buy, cash payment) clears later.

The plugin does not store entitlements. Persist what `PurchaseCompleted` and `PurchasesRestored`
report in your own app state.

## Store setup

- Create the product as a one-time product (Play Console) / non-consumable (App Store Connect),
  with the same product id on both stores.
- Apple requires a visible "Restore Purchases" button for non-consumables (Guideline 3.1.1).
- Google refunds purchases that aren't acknowledged within three days. The plugin acknowledges on
  purchase and on every `restore()`.
- Test with a Play license-test account / App Store sandbox tester. Real accounts are charged.

## License

MIT
