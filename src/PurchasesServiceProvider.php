<?php

namespace Smronju\NativephpPurchases;

use Illuminate\Support\ServiceProvider;

class PurchasesServiceProvider extends ServiceProvider
{
    public function register(): void
    {
        $this->app->singleton(Purchases::class, function () {
            return new Purchases;
        });
    }
}
