<?php

namespace Smronju\NativephpPurchases;

class Purchases
{
    /**
     * Look up the store's own details for one-time products — the localized
     * title and formatted price, which the app must show as-is rather than
     * hardcode (stores price per country and currency).
     *
     * Fire-and-forget: the details arrive via the {@see Events\ProductsLoaded}
     * event. Products the store doesn't know about are simply left out.
     *
     * @param  list<string>  $productIds
     */
    public function getProducts(array $productIds): void
    {
        if (function_exists('nativephp_call')) {
            nativephp_call('Purchases.GetProducts', json_encode([
                'productIds' => array_values($productIds),
            ]));
        }
    }

    /**
     * Present the store's purchase sheet for a one-time (non-consumable)
     * product.
     *
     * Fire-and-forget: the outcome arrives via {@see Events\PurchaseCompleted}
     * or {@see Events\PurchaseFailed}. Buying something the account already
     * owns reports {@see Events\PurchaseCompleted}, not a failure — owning it is
     * what the caller wanted.
     */
    public function purchase(string $productId): void
    {
        if (function_exists('nativephp_call')) {
            nativephp_call('Purchases.Purchase', json_encode([
                'productId' => $productId,
            ]));
        }
    }

    /**
     * Ask the store which one-time products this account owns.
     *
     * Fire-and-forget: the owned product ids arrive via
     * {@see Events\PurchasesRestored}. A `$silent` check reads only what the
     * device already knows — on iOS it skips `AppStore.sync()`, which may
     * prompt for an Apple ID password. Use it for a background check on
     * launch; leave it off for a user's own "Restore Purchases" tap.
     */
    public function restore(bool $silent = false): void
    {
        if (function_exists('nativephp_call')) {
            nativephp_call('Purchases.Restore', json_encode([
                'silent' => $silent,
            ]));
        }
    }
}
