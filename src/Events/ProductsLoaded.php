<?php

namespace Smronju\NativephpPurchases\Events;

use Illuminate\Foundation\Events\Dispatchable;
use Illuminate\Queue\SerializesModels;

/**
 * Dispatched once the store answers a {@see \Smronju\NativephpPurchases\Purchases::getProducts()} lookup.
 *
 * Each product is `['productId' => ..., 'title' => ..., 'description' => ..., 'price' => ...]`,
 * where `price` is already formatted for the store account's country ("$1.99", "৳250.00").
 * `$error` is only set when the store could not be reached at all.
 */
class ProductsLoaded
{
    use Dispatchable, SerializesModels;

    /**
     * @param  list<array{productId: string, title: string, description: string, price: string}>  $products
     */
    public function __construct(
        public array $products = [],
        public ?string $error = null,
    ) {}
}
