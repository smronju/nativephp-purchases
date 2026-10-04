## smronju/nativephp-purchases

One-time (non-consumable) in-app purchases with restore — Google Play Billing on Android,
StoreKit 2 on iOS. Entitlements are not stored by the plugin: persist what `PurchaseCompleted` /
`PurchasesRestored` report in your own app state.

### Installation

```bash
composer require smronju/nativephp-purchases
php artisan vendor:publish --tag=nativephp-plugins-provider
php artisan native:plugin:register smronju/nativephp-purchases
```

Create the product (same id on both stores, e.g. `remove_ads`) in Play Console and App Store
Connect before testing. Play refunds purchases that are not acknowledged within three days — the
plugin acknowledges on purchase and again on every `restore()`.

### PHP Usage

@verbatim
<code-snippet name="Selling and restoring a one-time product" lang="php">
use Smronju\NativephpPurchases\Facades\Purchases;

Purchases::getProducts(['remove_ads']); // -> ProductsLoaded
Purchases::purchase('remove_ads');      // -> PurchaseCompleted | PurchaseFailed
Purchases::restore();                   // -> PurchasesRestored (user's "Restore Purchases" tap)
Purchases::restore(silent: true);       // -> PurchasesRestored (background check, no Apple ID prompt)
</code-snippet>
@endverbatim

Every call is fire-and-forget; results arrive as events.

### Events

Listen with `#[On(Event::class)]` (never `#[OnNative]` in a NativeComponent).

- `ProductsLoaded`: `array $products` (`productId`, `title`, `description`, `price` — already
  formatted for the user's store country), `?string $error`.
- `PurchaseCompleted`: `string $productId` — unlock it. Also fires for an already-owned product
  and for a pending payment that later clears.
- `PurchaseFailed`: `string $productId`, `string $reason` (`cancelled`, `pending`, `unavailable`,
  `error`), `?string $message`.
- `PurchasesRestored`: `array $productIds`, `bool $silent`, `?string $error`.

@verbatim
<code-snippet name="Unlocking on purchase" lang="php">
use Native\Mobile\Attributes\On;
use Smronju\NativephpPurchases\Events\PurchaseCompleted;

#[On(PurchaseCompleted::class)]
public function handlePurchaseCompleted(string $productId): void
{
    if ($productId === 'remove_ads') {
        // persist the entitlement
    }
}
</code-snippet>
@endverbatim
