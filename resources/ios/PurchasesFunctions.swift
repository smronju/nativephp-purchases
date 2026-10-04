import Foundation
import StoreKit

// MARK: - Purchases Function Namespace

/// Namespace: "Purchases.*"
///
/// Every function returns immediately; the App Store's answer arrives later as
/// an event, since each one is a network round trip (and a purchase waits on
/// the user).
enum PurchasesFunctions {

    // MARK: - Purchases.GetProducts

    /// Look up localized title/price for one-time products.
    /// Parameters: productIds (array of strings)
    /// Event: ProductsLoaded
    class GetProducts: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            let productIds = parameters["productIds"] as? [String] ?? []

            Task { await PurchasesStore.shared.getProducts(productIds) }

            return BridgeResponse.success(data: ["success": true])
        }
    }

    // MARK: - Purchases.Purchase

    /// Present the App Store purchase sheet for a one-time product.
    /// Parameters: productId (string)
    /// Events: PurchaseCompleted, PurchaseFailed
    class Purchase: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            let productId = parameters["productId"] as? String ?? ""

            Task { await PurchasesStore.shared.purchase(productId) }

            return BridgeResponse.success(data: ["success": true])
        }
    }

    // MARK: - Purchases.Restore

    /// List the one-time products this Apple ID owns.
    /// Parameters: silent (bool) — when false, syncs with the App Store first,
    /// which may prompt for the Apple ID password
    /// Event: PurchasesRestored
    class Restore: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            let silent = parameters["silent"] as? Bool ?? false

            Task { await PurchasesStore.shared.restore(silent: silent) }

            return BridgeResponse.success(data: ["success": true])
        }
    }
}

// MARK: - Store

/// Owns the long-lived `Transaction.updates` listener.
///
/// Purchases can complete outside any purchase call — Ask to Buy approvals, a
/// pending payment clearing, a purchase made on another device. StoreKit
/// delivers those through `Transaction.updates`, and they must be finished,
/// so the listener lives for the rest of the app's run once anything touches
/// this store.
actor PurchasesStore {
    static let shared = PurchasesStore()

    private static let events = "Smronju\\NativephpPurchases\\Events\\"

    private var updates: Task<Void, Never>?

    private init() {}

    func getProducts(_ productIds: [String]) async {
        listenForTransactions()

        do {
            let products = try await Product.products(for: productIds)

            let payload: [[String: Any]] = products.map { product in
                [
                    "productId": product.id,
                    "title": product.displayName,
                    "description": product.description,
                    "price": product.displayPrice,
                ]
            }

            Self.dispatch("ProductsLoaded", ["products": payload])
        } catch {
            Self.dispatch("ProductsLoaded", [
                "products": [],
                "error": error.localizedDescription,
            ])
        }
    }

    func purchase(_ productId: String) async {
        listenForTransactions()

        do {
            guard let product = try await Product.products(for: [productId]).first else {
                Self.failed(productId, reason: "unavailable", message: "Unknown product \(productId)")
                return
            }

            switch try await product.purchase() {
            case .success(let verification):
                guard case .verified(let transaction) = verification else {
                    Self.failed(productId, reason: "error", message: "The App Store could not verify this purchase")
                    return
                }

                await transaction.finish()
                Self.dispatch("PurchaseCompleted", ["productId": transaction.productID])

            case .pending:
                Self.failed(productId, reason: "pending", message: nil)

            case .userCancelled:
                Self.failed(productId, reason: "cancelled", message: nil)

            @unknown default:
                Self.failed(productId, reason: "error", message: nil)
            }
        } catch {
            Self.failed(productId, reason: "error", message: error.localizedDescription)
        }
    }

    func restore(silent: Bool) async {
        listenForTransactions()

        if !silent {
            do {
                try await AppStore.sync()
            } catch {
                // Cancelling the Apple ID prompt lands here too. Report it as a
                // failure rather than as "owns nothing", which it isn't.
                Self.dispatch("PurchasesRestored", [
                    "productIds": [],
                    "silent": silent,
                    "error": error.localizedDescription,
                ])
                return
            }
        }

        var owned: [String] = []

        for await entitlement in Transaction.currentEntitlements {
            if case .verified(let transaction) = entitlement,
               transaction.productType == .nonConsumable,
               transaction.revocationDate == nil {
                owned.append(transaction.productID)
            }
        }

        Self.dispatch("PurchasesRestored", ["productIds": owned, "silent": silent])
    }

    private func listenForTransactions() {
        guard updates == nil else {
            return
        }

        updates = Task.detached {
            for await update in Transaction.updates {
                guard case .verified(let transaction) = update else {
                    continue
                }

                await transaction.finish()

                if transaction.revocationDate == nil {
                    PurchasesStore.dispatch("PurchaseCompleted", ["productId": transaction.productID])
                }
            }
        }
    }

    private static func failed(_ productId: String, reason: String, message: String?) {
        // Omit `message` rather than sending nil: the PHP #[On] dispatcher casts
        // a present null to '' for a ?string parameter.
        var payload: [String: Any] = ["productId": productId, "reason": reason]
        if let message = message, !message.isEmpty {
            payload["message"] = message
        }

        dispatch("PurchaseFailed", payload)
    }

    private static func dispatch(_ event: String, _ payload: [String: Any]) {
        DispatchQueue.main.async {
            LaravelBridge.shared.send?(events + event, payload)
        }
    }
}
