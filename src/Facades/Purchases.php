<?php

namespace Smronju\NativephpPurchases\Facades;

use Illuminate\Support\Facades\Facade;

/**
 * @method static void getProducts(array $productIds)
 * @method static void purchase(string $productId)
 * @method static void restore(bool $silent = false)
 *
 * @see \Smronju\NativephpPurchases\Purchases
 */
class Purchases extends Facade
{
    protected static function getFacadeAccessor(): string
    {
        return \Smronju\NativephpPurchases\Purchases::class;
    }
}
