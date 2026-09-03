package com.tetragon.app.subscriptionModel

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.tetragon.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class BillingHelper(
    private val context: Context,
    private val currentUserId: String,
    private val onBillingReady: () -> Unit,
    private val onPurchaseSuccess: (purchase: Purchase, isNewPurchase: Boolean) -> Unit,
    private val onPurchaseCancelled: () -> Unit,
    private val onError: (String) -> Unit
) {

    private val billingJob = Job()
    private val mainScope = CoroutineScope(Dispatchers.Main + billingJob)

    private lateinit var billingClient: BillingClient
    private var productDetailsList: List<ProductDetails> = emptyList()

    private var pendingInitTasks = 2

    // Guards against delivering callbacks after release() has been called
    // (e.g. the hosting Activity was destroyed while a Play call was in flight).
    private var isReleased = false

    companion object {
        const val PREMIUM_MONTHLY = "monthly_premium"
        const val PREMIUM_ANNUAL = "annual_premium"
    }

    init {
        setupBillingClient()
    }

    private fun setupBillingClient() {
        val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()

        billingClient = BillingClient.newBuilder(context)
            .setListener { billingResult, purchases ->
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        purchases?.forEach { purchase ->
                            handlePurchase(purchase, isNewPurchase = true)
                        }
                    }
                    BillingClient.BillingResponseCode.USER_CANCELED,
                    BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> {
                        // User backed out of the Play purchase sheet without buying,
                        // or Play Store's connection dropped mid-flow — either way,
                        // treat it as "no purchase happened", not a hard error.
                        safelyDeliverCancel()
                    }
                    else -> {
                        val rawError = context.getString(
                            R.string.billing_error_purchase,
                            billingResult.debugMessage.orEmpty()
                        )
                        safelyDeliverError(rawError)
                    }
                }
            }
            .enablePendingPurchases(pendingPurchasesParams)
            .build()

        startConnection()
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (isReleased) return
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryUnconsumedPurchases()
                    queryAvailableProducts()
                } else {
                    safelyDeliverError(context.getString(R.string.billing_error_connection))
                }
            }

            override fun onBillingServiceDisconnected() {
                if (isReleased) return
                startConnection()
            }
        })
    }

    private fun checkInitCompletion() {
        pendingInitTasks--
        if (pendingInitTasks <= 0) {
            mainScope.launch { if (!isReleased) onBillingReady() }
        }
    }

    // For consumables, Play only surfaces purchases that were bought but never consumed
    // (e.g. app crashed / closed right after payment, before consumeAsync ran).
    // This is NOT a "restore purchases" flow — consumables leave no lasting
    // ownership record on Play once consumed, so there's nothing to "restore".
    private fun queryUnconsumedPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
            if (isReleased) return@queryPurchasesAsync
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                for (purchase in purchasesList) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        handleUnconsumedLeftover(purchase)
                    }
                }
            }
            mainScope.launch { checkInitCompletion() }
        }
    }

    private fun queryAvailableProducts() {
        val products = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PREMIUM_MONTHLY)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PREMIUM_ANNUAL)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
            if (isReleased) return@queryProductDetailsAsync
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetailsList = productDetailsResult.productDetailsList
                mainScope.launch { checkInitCompletion() }
            } else {
                val rawError = context.getString(
                    R.string.billing_error_fetch_products,
                    billingResult.debugMessage.orEmpty()
                )
                safelyDeliverError(rawError)
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity, productId: String) {
        val productDetails = productDetailsList.find { it.productId == productId }
        if (productDetails == null) {
            safelyDeliverError(context.getString(R.string.billing_error_product_not_found, productId))
            return
        }

        // INAPP one-time products don't use offer tokens — that's a SUBS-only concept.
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .setObfuscatedAccountId(currentUserId)
            .build()

        val result = billingClient.launchBillingFlow(activity, billingFlowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            // Flow never actually opened (e.g. Play Store busy/unavailable) —
            // make sure the caller's "loading" state gets released either way.
            safelyDeliverError(
                context.getString(R.string.billing_error_purchase, result.debugMessage.orEmpty())
            )
        }
    }

    // Fresh purchase just completed in this session -> always grant to currentUserId,
    // then consume so the same Play account can buy this SKU again for a different app account.
    private fun handlePurchase(purchase: Purchase, isNewPurchase: Boolean) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            return
        }

        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.consumeAsync(consumeParams) { billingResult, _ ->
            if (isReleased) return@consumeAsync
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                mainScope.launch { onPurchaseSuccess(purchase, isNewPurchase) }
            } else {
                safelyDeliverError(context.getString(R.string.billing_error_acknowledge))
            }
        }
    }

    // Leftover purchase found at startup (payment succeeded but app closed before consuming).
    // Only grant it if it was tagged for the CURRENT user; otherwise just consume it silently
    // so it doesn't sit around blocking anything, without granting entitlement to the wrong user.
    private fun handleUnconsumedLeftover(purchase: Purchase) {
        val ownerUid = purchase.accountIdentifiers?.obfuscatedAccountId
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        if (ownerUid == currentUserId) {
            billingClient.consumeAsync(consumeParams) { billingResult, _ ->
                if (isReleased) return@consumeAsync
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    mainScope.launch { onPurchaseSuccess(purchase, true) }
                }
            }
        } else {
            // Belongs to a different app-account purchase that never finished consuming.
            // Clear it so it doesn't linger, but don't grant premium here.
            billingClient.consumeAsync(consumeParams) { _, _ -> }
        }
    }

    private fun safelyDeliverError(message: String) {
        mainScope.launch { if (!isReleased) onError(message) }
    }

    private fun safelyDeliverCancel() {
        mainScope.launch { if (!isReleased) onPurchaseCancelled() }
    }

    fun release() {
        isReleased = true
        billingJob.cancel()
        if (::billingClient.isInitialized) {
            billingClient.endConnection()
        }
    }
}