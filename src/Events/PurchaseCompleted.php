<?php

namespace Smronju\NativephpPurchases\Events;

use Illuminate\Foundation\Events\Dispatchable;
use Illuminate\Queue\SerializesModels;

/**
 * Dispatched when the store confirms the account owns `$productId` — a fresh
 * purchase, a pending payment that has now cleared, or a purchase attempt for
 * something already owned. Unlock the product's feature on receipt.
 */
class PurchaseCompleted
{
    use Dispatchable, SerializesModels;

    public function __construct(
        public string $productId,
    ) {}
}
