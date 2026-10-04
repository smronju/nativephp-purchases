<?php

namespace Smronju\NativephpPurchases\Events;

use Illuminate\Foundation\Events\Dispatchable;
use Illuminate\Queue\SerializesModels;

/**
 * Dispatched once the store answers a {@see \Smronju\NativephpPurchases\Purchases::restore()}.
 *
 * `$productIds` lists every one-time product this store account owns — empty
 * when it owns none. `$silent` echoes the request, so a background check can
 * stay quiet while a user's own "Restore Purchases" tap reports back.
 * `$error` is only set when the store could not be reached at all; an empty
 * list then means "unknown", not "owns nothing".
 */
class PurchasesRestored
{
    use Dispatchable, SerializesModels;

    /**
     * @param  list<string>  $productIds
     */
    public function __construct(
        public array $productIds = [],
        public bool $silent = false,
        public ?string $error = null,
    ) {}
}
