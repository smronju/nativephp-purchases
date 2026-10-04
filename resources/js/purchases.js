/**
 * Purchases Plugin for NativePHP Mobile
 *
 * Kept so the plugin works from a web-view context too, per NativePHP plugin
 * conventions. Results arrive as native events, not as these calls' return values.
 *
 * @example
 * import { purchases } from '@smronju/nativephp-purchases';
 *
 * await purchases.purchase('remove_ads');
 * // listen for "Smronju\NativephpPurchases\Events\PurchaseCompleted"
 */

const baseUrl = '/_native/api/call';

async function bridgeCall(method, params = {}) {
    const response = await fetch(baseUrl, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-CSRF-TOKEN': document.querySelector('meta[name="csrf-token"]')?.content || ''
        },
        body: JSON.stringify({ method, params })
    });

    const result = await response.json();

    if (result.status === 'error') {
        throw new Error(result.message || 'Native call failed');
    }

    return result.data?.data ?? result.data;
}

export async function getProducts(productIds) {
    return bridgeCall('Purchases.GetProducts', { productIds });
}

export async function purchase(productId) {
    return bridgeCall('Purchases.Purchase', { productId });
}

export async function restore(silent = false) {
    return bridgeCall('Purchases.Restore', { silent });
}

export const purchases = { getProducts, purchase, restore };

export default purchases;
