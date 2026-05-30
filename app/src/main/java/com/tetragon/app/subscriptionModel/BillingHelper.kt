package com.tetragon.app.subscriptionModel

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BillingHelper(
    private val context: Context,
    private val onBillingReady: () -> Unit,
    private val onPurchaseSuccess: (Purchase) -> Unit,
    private val onError: (String) -> Unit
) {

    private lateinit var billingClient: BillingClient
    private var productDetailsList: List<ProductDetails> = emptyList()

    // Твои Product ID из Google Play Console
    companion object {
        const val PREMIUM_MONTHLY = "premium_monthly"
        const val PREMIUM_ANNUAL = "premium_annual"
    }

    init {
        setupBillingClient()
    }

    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(context)
            .setListener { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (purchase in purchases) {
                        handlePurchase(purchase)
                    }
                } else if (billingResult.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
                    onError("Ошибка покупки: ${billingResult.debugMessage}")
                }
            }
            .enablePendingPurchases()
            .build()

        startConnection()
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryAvailableProducts()
                } else {
                    onError("Не удалось подключить Google Play Billing")
                }
            }

            override fun onBillingServiceDisconnected() {
                // Переподключение при разрыве связи с сервисом Google Play
                startConnection()
            }
        })
    }

    private fun queryAvailableProducts() {
        val products = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PREMIUM_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PREMIUM_ANNUAL)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, detailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetailsList = detailsList
                // Переключаемся на главный поток, чтобы обновить UI
                CoroutineScope(Dispatchers.Main).launch {
                    onBillingReady()
                }
            } else {
                onError("Ошибка получения продуктов: ${billingResult.debugMessage}")
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity, productId: String) {
        val productDetails = productDetailsList.find { it.productId == productId }
        if (productDetails == null) {
            onError("Продукт $productId не найден в Google Play")
            return
        }

        // В Billing Library v5+ подписки содержат Base Plans и Offers.
        // Берем самый первый доступный базовый план (offerToken)
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken

        if (offerToken == null) {
            onError("У продукта нет активных базовых тарифных планов")
            return
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    private fun handlePurchase(purchase: Purchase) {
        // Подтверждение покупки (Acknowledge) обязательно в течение 3 дней, иначе Google вернет деньги
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        CoroutineScope(Dispatchers.Main).launch {
                            onPurchaseSuccess(purchase)
                        }
                    } else {
                        onError("Ошибка подтверждения покупки")
                    }
                }
            } else {
                onPurchaseSuccess(purchase)
            }
        }
    }
}