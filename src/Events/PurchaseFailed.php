<?php

namespace Smronju\NativephpPurchases\Events;

use Illuminate\Foundation\Events\Dispatchable;
use Illuminate\Queue\SerializesModels;

/**
 * Dispatched when a purchase did not (yet) result in ownership.
 *
 * `$reason` is one of:
 * - `cancelled` — the user closed the purchase sheet. Usually nothing to show.
 * - `pending` — payment is awaiting approval (Ask to Buy, cash at a store, ...).
 *   A {@see PurchaseCompleted} follows if and when it clears.
 * - `unavailable` — the store doesn't know the product, or billing isn't available.
 * - `error` — anything else; `$message` carries the store's own description.
 */
class PurchaseFailed
{
    use Dispatchable, SerializesModels;

    public function __construct(
        public string $productId,
        public string $reason,
        public ?string $message = null,
    ) {}
}
