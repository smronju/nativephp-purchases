package com.smronju.plugins.nativephp_purchases

import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.nativephp.mobile.bridge.BridgeFunction
import com.nativephp.mobile.utils.NativeActionCoordinator
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference

/**
 * Namespace: "Purchases.*"
 *
 * Every function returns immediately; the store's answer arrives later as an
 * event, since each one is a network round trip to Google Play (and a purchase
 * waits on the user).
 */
object PurchasesFunctions {

    /**
     * Look up localized title/price for one-time products.
     * Parameters: productIds (array of strings)
     * Event: ProductsLoaded
     */
    class GetProducts(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            PlayBilling.getProducts(activity, stringList(parameters["productIds"]))

            return mapOf("success" to true)
        }
    }

    /**
     * Launch Google Play's purchase sheet for a one-time product.
     * Parameters: productId (string)
     * Events: PurchaseCompleted, PurchaseFailed
     */
    class Purchase(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val productId = parameters["productId"] as? String ?: ""

            PlayBilling.purchase(activity, productId)

            return mapOf("success" to true)
        }
    }

    /**
     * List the one-time products this Google account owns.
     * Parameters: silent (bool) — echoed back; Play needs no extra sign-in either way
     * Event: PurchasesRestored
     */
    class Restore(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            PlayBilling.restore(activity, parameters["silent"] as? Boolean ?: false)

            return mapOf("success" to true)
        }
    }

    /**
     * The bridge hands JSON arrays over as `JSONArray`, not a Kotlin list.
     */
    private fun stringList(value: Any?): List<String> = when (value) {
        is JSONArray -> (0 until value.length()).map { value.getString(it) }
        is List<*> -> value.filterIsInstance<String>()
        is String -> listOf(value)
        else -> emptyList()
    }
}

/**
 * One `BillingClient` for the life of the process.
 *
 * Play delivers a purchase's outcome to the client's `PurchasesUpdatedListener`,
 * not to the call that launched it — so the client (and that listener) must
 * outlive any single bridge call.
 */
internal object PlayBilling : PurchasesUpdatedListener {

    private const val TAG = "NativephpPurchases"
    private const val EVENTS = "Smronju\\NativephpPurchases\\Events\\"

    private var client: BillingClient? = null

    /** The latest activity a bridge call came from — events are dispatched through it. */
    private var activityRef: WeakReference<FragmentActivity>? = null

    /** Details from earlier lookups; launching a purchase needs the ProductDetails object itself. */
    private val productDetails = mutableMapOf<String, ProductDetails>()

    /** The product whose purchase sheet is open — a cancel carries no purchase to read it from. */
    private var purchasingProductId: String? = null

    /** Work waiting for the connection to come up. */
    private val pendingWork = mutableListOf<(BillingResult) -> Unit>()

    fun getProducts(activity: FragmentActivity, productIds: List<String>) {
        remember(activity)

        if (productIds.isEmpty()) {
            dispatch("ProductsLoaded", JSONObject().put("products", JSONArray()))
            return
        }

        whenConnected { connection ->
            if (connection.responseCode != BillingClient.BillingResponseCode.OK) {
                dispatch("ProductsLoaded", JSONObject()
                    .put("products", JSONArray())
                    .put("error", describe(connection)))
                return@whenConnected
            }

            queryDetails(productIds) { result, details ->
                val products = JSONArray()

                details.forEach { product ->
                    products.put(JSONObject()
                        .put("productId", product.productId)
                        .put("title", product.name)
                        .put("description", product.description)
                        .put("price", buyOffer(product)?.formattedPrice ?: ""))
                }

                val payload = JSONObject().put("products", products)
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    payload.put("error", describe(result))
                }

                dispatch("ProductsLoaded", payload)
            }
        }
    }

    fun purchase(activity: FragmentActivity, productId: String) {
        remember(activity)

        whenConnected { connection ->
            if (connection.responseCode != BillingClient.BillingResponseCode.OK) {
                failed(productId, "unavailable", describe(connection))
                return@whenConnected
            }

            val cached = productDetails[productId]
            if (cached != null) {
                launch(productId, cached)
                return@whenConnected
            }

            queryDetails(listOf(productId)) { result, details ->
                val product = details.firstOrNull { it.productId == productId }

                if (product == null) {
                    failed(productId, "unavailable", describe(result))
                } else {
                    launch(productId, product)
                }
            }
        }
    }

    fun restore(activity: FragmentActivity, silent: Boolean) {
        remember(activity)

        whenConnected { connection ->
            if (connection.responseCode != BillingClient.BillingResponseCode.OK) {
                dispatch("PurchasesRestored", JSONObject()
                    .put("productIds", JSONArray())
                    .put("silent", silent)
                    .put("error", describe(connection)))
                return@whenConnected
            }

            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()

            billingClient().queryPurchasesAsync(params) { result, purchases ->
                val payload = JSONObject().put("silent", silent)

                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    dispatch("PurchasesRestored", payload
                        .put("productIds", JSONArray())
                        .put("error", describe(result)))
                    return@queryPurchasesAsync
                }

                val owned = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }

                // A purchase made while the app wasn't listening (or whose
                // acknowledgement failed) is still unacknowledged — and Play
                // refunds those automatically after three days.
                owned.forEach { acknowledge(it) }

                dispatch("PurchasesRestored", payload
                    .put("productIds", JSONArray(owned.flatMap { it.products }.distinct())))
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        val productId = purchasingProductId ?: ""
        purchasingProductId = null

        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases.orEmpty().forEach { handle(it) }
        } else {
            reportUnsuccessful(productId, result)
        }
    }

    private fun reportUnsuccessful(productId: String, result: BillingResult) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.USER_CANCELED -> failed(productId, "cancelled", null)
            // Buying what the account already owns is a success for the caller.
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> completed(productId)
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED ->
                failed(productId, "unavailable", describe(result))
            else -> failed(productId, "error", describe(result))
        }
    }

    private fun handle(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                // Grant first, then acknowledge: the user has paid either way, and
                // an acknowledgement that fails is retried by the next restore.
                purchase.products.forEach { completed(it) }
                acknowledge(purchase)
            }
            Purchase.PurchaseState.PENDING ->
                purchase.products.forEach { failed(it, "pending", null) }
            else -> Unit
        }
    }

    private fun acknowledge(purchase: Purchase) {
        if (purchase.isAcknowledged) {
            return
        }

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient().acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "Acknowledging ${purchase.products} failed: ${describe(result)}")
            }
        }
    }

    private fun launch(productId: String, product: ProductDetails) {
        val activity = activityRef?.get() ?: run {
            failed(productId, "error", "No activity to present the purchase sheet from")
            return
        }

        val offer = buyOffer(product) ?: run {
            failed(productId, "unavailable", "$productId has no active buy option")
            return
        }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)

        // Null only for a legacy single-price product, where Play needs no token.
        offer.offerToken?.let { productParams.setOfferToken(it) }

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams.build()))
            .build()

        activity.runOnUiThread {
            purchasingProductId = productId

            val result = billingClient().launchBillingFlow(activity, params)

            // A non-OK launch means the sheet never opened, so no
            // onPurchasesUpdated will follow to report it.
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                purchasingProductId = null
                reportUnsuccessful(productId, result)
            }
        }
    }

    /**
     * The offer to sell — and to show the price of — for a Play Console
     * one-time product.
     *
     * A one-time product can carry several purchase options (buy, rent,
     * pre-order) each with its own offers (the base price plus any discounts).
     * This sells permanent ownership, so rentals and pre-orders are skipped;
     * of what is left, the cheapest offer the user qualifies for wins — the
     * same one Play itself would highlight. Its token pins the purchase sheet
     * to exactly the price shown in the app.
     */
    private fun buyOffer(product: ProductDetails): ProductDetails.OneTimePurchaseOfferDetails? {
        val offers = product.oneTimePurchaseOfferDetailsList
            ?: listOfNotNull(product.oneTimePurchaseOfferDetails)

        return offers
            .filter { it.rentalDetails == null && it.preorderDetails == null }
            .minByOrNull { it.priceAmountMicros }
    }

    private fun queryDetails(productIds: List<String>, done: (BillingResult, List<ProductDetails>) -> Unit) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productIds.distinct().map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            })
            .build()

        billingClient().queryProductDetailsAsync(params) { result, detailsResult ->
            val details = detailsResult.productDetailsList

            details.forEach { productDetails[it.productId] = it }

            done(result, details)
        }
    }

    /**
     * Runs `work` once the client is connected, connecting first if needed.
     * `work` receives the connection's own result, so a failed connect is reported
     * through the same event as the request it was made for.
     */
    @Synchronized
    private fun whenConnected(work: (BillingResult) -> Unit) {
        val billing = billingClient()

        if (billing.isReady) {
            work(BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build())
            return
        }

        pendingWork.add(work)
        if (pendingWork.size > 1) {
            return // A connection attempt is already in flight.
        }

        billing.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                val waiting = synchronized(this@PlayBilling) {
                    pendingWork.toList().also { pendingWork.clear() }
                }

                waiting.forEach { it(result) }
            }

            override fun onBillingServiceDisconnected() {
                // Nothing to do here: the next request reconnects on demand.
            }
        })
    }

    private fun billingClient(): BillingClient {
        client?.let { return it }

        val context = activityRef?.get()?.applicationContext
            ?: throw IllegalStateException("Purchases used before any activity was available")

        return BillingClient.newBuilder(context)
            .setListener(this)
            .enableAutoServiceReconnection()
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()
            .also { client = it }
    }

    private fun remember(activity: FragmentActivity) {
        activityRef = WeakReference(activity)
    }

    private fun completed(productId: String) {
        dispatch("PurchaseCompleted", JSONObject().put("productId", productId))
    }

    private fun failed(productId: String, reason: String, message: String?) {
        // Omit `message` rather than sending JSON null: the PHP #[On] dispatcher
        // casts a present null to '' for a ?string parameter.
        val payload = JSONObject().put("productId", productId).put("reason", reason)
        if (!message.isNullOrEmpty()) {
            payload.put("message", message)
        }

        dispatch("PurchaseFailed", payload)
    }

    private fun describe(result: BillingResult): String =
        result.debugMessage.ifEmpty { "Billing response code ${result.responseCode}" }

    private fun dispatch(event: String, payload: JSONObject) {
        val activity = activityRef?.get() ?: run {
            Log.w(TAG, "Dropping $event: no activity to dispatch through")
            return
        }

        // Billing callbacks can arrive on a background thread, and the
        // coordinator's fragment transaction must run on the main one.
        activity.runOnUiThread {
            NativeActionCoordinator.dispatchEvent(activity, EVENTS + event, payload.toString())
        }
    }
}
