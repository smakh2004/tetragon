package com.tetragon.app.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.android.billingclient.api.*
import com.tetragon.app.R
import com.tetragon.app.subscriptionModel.IntroSubscriptionActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar
import java.util.Date

class ShopFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var userListener: ListenerRegistration? = null

    private lateinit var billingClient: BillingClient
    private var inAppProductDetailsList: List<ProductDetails> = emptyList()

    companion object {
        private const val COINS_100 = "coins_100"
        private const val COINS_200 = "coins_200"
        private const val COINS_500 = "coins_500"
    }

    private lateinit var coinCountText: TextView
    private lateinit var itemStar: ConstraintLayout
    private lateinit var starPriceContainer: LinearLayout
    private lateinit var starStatusText: TextView
    private lateinit var itemInfinity: ConstraintLayout
    private lateinit var infinityPriceContainer: LinearLayout
    private lateinit var infinityStatusText: TextView

    private lateinit var btnBuyCoins100: LinearLayout
    private lateinit var btnBuyCoins200: LinearLayout
    private lateinit var btnBuyCoins500: LinearLayout

    private lateinit var startContainer: FrameLayout
    private lateinit var startLessonLabel: TextView
    private lateinit var confirmBtn: LinearLayout
    private lateinit var confirmCoinAmount: TextView
    private lateinit var confirmBtnContainer: FrameLayout
    private lateinit var processingBtnContainer: FrameLayout
    private lateinit var shopScrollView: NestedScrollView
    private lateinit var shopLineDivider: View
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private lateinit var loadingOverlayContainer: FrameLayout
    private var isInitialDataLoaded = false

    private var currentCoins: Long = 0
    private var currentStars: Long = 0
    private var pendingPurchaseType: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_shop, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupBillingClient()
        setupListeners(view)
    }

    private fun initViews(view: View) {
        coinCountText = view.findViewById(R.id.coinCountText)
        shopScrollView = view.findViewById(R.id.shopScrollView)
        shopLineDivider = view.findViewById(R.id.shopLineDivider)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)

        itemStar = view.findViewById(R.id.item_star)
        starPriceContainer = view.findViewById(R.id.star_price_container)
        starStatusText = view.findViewById(R.id.star_status_text)

        itemInfinity = view.findViewById(R.id.item_infinity)
        infinityPriceContainer = view.findViewById(R.id.infinity_price_container)
        infinityStatusText = view.findViewById(R.id.infinity_status_text)

        btnBuyCoins100 = view.findViewById(R.id.btn_buy_coins_100)
        btnBuyCoins200 = view.findViewById(R.id.btn_buy_coins_200)
        btnBuyCoins500 = view.findViewById(R.id.btn_buy_coins_500)

        startContainer = view.findViewById(R.id.start_container)
        startLessonLabel = view.findViewById(R.id.start_lesson_label)

        confirmBtn = view.findViewById(R.id.continue_enabled_btn)
        confirmCoinAmount = view.findViewById(R.id.confirm_coin_amount)

        confirmBtnContainer = view.findViewById(R.id.start_enabled_btn_container)
        processingBtnContainer = view.findViewById(R.id.start_disabled_btn_container)

        loadingOverlayContainer = view.findViewById(R.id.loadingOverlayContainer)

        context?.let { ctx ->
            swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(ctx, R.color.blue_2))
        }
        swipeRefreshLayout.setSlingshotDistance(0)
        swipeRefreshLayout.setProgressViewEndTarget(false, 140)

        startContainer.visibility = View.GONE
        startContainer.alpha = 0f
    }

    private fun setupBillingClient() {
        val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()

        billingClient = BillingClient.newBuilder(requireContext())
            .setListener { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (purchase in purchases) {
                        handleInAppPurchase(purchase, enforceOwnership = false)
                    }
                } else if (billingResult.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
                    Toast.makeText(context, billingResult.debugMessage, Toast.LENGTH_SHORT).show()
                }
            }
            .enablePendingPurchases(pendingPurchasesParams)
            .build()

        connectToGooglePlay()
    }

    private fun connectToGooglePlay() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryCoinPacks()

                    val params = QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()

                    billingClient.queryPurchasesAsync(params) { result, purchaseList ->
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            for (purchase in purchaseList) {
                                handleInAppPurchase(purchase, enforceOwnership = true)
                            }
                        }
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                connectToGooglePlay()
            }
        })
    }

    private fun queryCoinPacks() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(COINS_100).setProductType(BillingClient.ProductType.INAPP).build(),
            QueryProductDetailsParams.Product.newBuilder().setProductId(COINS_200).setProductType(BillingClient.ProductType.INAPP).build(),
            QueryProductDetailsParams.Product.newBuilder().setProductId(COINS_500).setProductType(BillingClient.ProductType.INAPP).build()
        )

        val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                inAppProductDetailsList = productDetailsResult.productDetailsList
            }
        }
    }

    private fun launchCoinPurchaseFlow(productId: String) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(context, getString(R.string.error_auth_failed), Toast.LENGTH_SHORT).show()
            return
        }

        val productDetails = inAppProductDetailsList.find { it.productId == productId }
        if (productDetails == null) {
            Toast.makeText(context, getString(R.string.coin_pack_not_found), Toast.LENGTH_SHORT).show()
            return
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .setObfuscatedAccountId(uid)
            .build()

        billingClient.launchBillingFlow(requireActivity(), billingFlowParams)
    }

    private fun handleInAppPurchase(purchase: Purchase, enforceOwnership: Boolean) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            return
        }

        if (enforceOwnership) {
            val ownerUid = purchase.accountIdentifiers?.obfuscatedAccountId
            val uid = auth.currentUser?.uid
            if (ownerUid == null || ownerUid != uid) {
                return
            }
        }

        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.consumeAsync(consumeParams) { billingResult, _ ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                activity?.runOnUiThread {
                    val purchasedId = purchase.products.firstOrNull()
                    val coinsToAward = when (purchasedId) {
                        COINS_100 -> 100L
                        COINS_200 -> 200L
                        COINS_500 -> 500L
                        else -> 0L
                    }
                    if (coinsToAward > 0) {
                        awardCoinsToUser(coinsToAward)
                    }
                }
            }
        }
    }

    private fun awardCoinsToUser(amount: Long) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .update("coins", FieldValue.increment(amount))
            .addOnSuccessListener {
                if (isAdded) {
                    Toast.makeText(context, getString(R.string.coins_purchased_success, amount), Toast.LENGTH_LONG).show()
                }
            }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners(view: View) {
        val subscribeBtn = view.findViewById<View>(R.id.subscribe_enabled_btn)

        btnBuyCoins100.setOnClickListener { launchCoinPurchaseFlow(COINS_100) }
        btnBuyCoins200.setOnClickListener { launchCoinPurchaseFlow(COINS_200) }
        btnBuyCoins500.setOnClickListener { launchCoinPurchaseFlow(COINS_500) }

        swipeRefreshLayout.setOnRefreshListener {
            refreshShopData(isManualSwipe = true)
        }

        shopScrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (isAdded) {
                shopLineDivider.visibility = if (scrollY > 0) View.VISIBLE else View.INVISIBLE
            }

            if (startContainer.visibility == View.VISIBLE && Math.abs(scrollY - oldScrollY) > 10) {
                hidePurchaseDrawer()
            }
        })

        shopScrollView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && startContainer.visibility == View.VISIBLE) {
                hidePurchaseDrawer()
            }
            false
        }

        itemStar.setOnClickListener {
            val isSubscribed = starStatusText.visibility == View.VISIBLE && !itemStar.isClickable
            if (isSubscribed) {
                Toast.makeText(context, getString(R.string.stars_full_toast), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (currentStars < 15) {
                pendingPurchaseType = "STAR"
                showPurchaseDrawer(getString(R.string.refill_stars_label), "50")
            } else {
                Toast.makeText(context, getString(R.string.stars_full_toast), Toast.LENGTH_SHORT).show()
            }
        }

        itemInfinity.setOnClickListener {
            pendingPurchaseType = "INFINITY"
            showPurchaseDrawer(getString(R.string.monthly_infinity_label), "1000")
        }

        confirmBtn.setOnClickListener {
            handleConfirmPurchase()
        }

        subscribeBtn.setOnClickListener {
            val intent = Intent(requireContext(), IntroSubscriptionActivity::class.java)
            startActivity(intent)
            requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun refreshShopData(isManualSwipe: Boolean) {
        if (!isManualSwipe) {
            isInitialDataLoaded = false
            loadingOverlayContainer.visibility = View.VISIBLE
        }

        userListener?.remove()
        observeUserStats()
    }

    private fun showPurchaseDrawer(label: String, price: String) {
        startLessonLabel.text = label
        confirmCoinAmount.text = price

        confirmBtnContainer.visibility = View.VISIBLE
        processingBtnContainer.visibility = View.GONE
        startContainer.visibility = View.VISIBLE

        startContainer.translationY = 400f
        startContainer.alpha = 0f
        startContainer.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator(0.7f))
            .start()
    }

    private fun hidePurchaseDrawer() {
        if (startContainer.visibility == View.GONE) return

        startContainer.animate()
            .translationY(400f)
            .alpha(0f)
            .setDuration(300)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                startContainer.visibility = View.GONE
            }
            .start()
        pendingPurchaseType = null
    }

    // 🟢 ИСПРАВЛЕНО: раньше отправлялось несколько отдельных transaction.update(ref, "field", value)
    // вызовов подряд. Теперь каждый кейс отправляет ОДНО объединённое обновление (map),
    // чтобы весь набор полей менялся одним атомарным write — это и требуется для совместимости
    // с правилами безопасности Firestore, и просто правильнее с точки зрения консистентности данных.
    private fun handleConfirmPurchase() {
        val type = pendingPurchaseType ?: return
        val uid = auth.currentUser?.uid ?: return
        val userDocRef = db.collection("users").document(uid)

        confirmBtnContainer.visibility = View.GONE
        processingBtnContainer.visibility = View.VISIBLE

        db.runTransaction { transaction ->
            val snapshot = transaction.get(userDocRef)
            val freshCoins = snapshot.getLong("coins") ?: 0L

            when (type) {
                "STAR" -> {
                    if (freshCoins >= 50) {
                        transaction.update(userDocRef, mapOf(
                            "coins" to freshCoins - 50,
                            "stars" to 15L,
                            "lastStarUsedTime" to FieldValue.delete()
                        ))
                    } else {
                        throw Exception(getString(R.string.not_enough_coins))
                    }
                }
                "INFINITY" -> {
                    if (freshCoins >= 1000) {
                        val calendar = Calendar.getInstance().apply { add(Calendar.MONTH, 1) }
                        transaction.update(userDocRef, mapOf(
                            "coins" to freshCoins - 1000,
                            "subscriptionUntil" to Timestamp(calendar.time),
                            "planType" to "monthly",
                            "subscription" to true
                        ))
                    } else {
                        throw Exception(getString(R.string.not_enough_coins))
                    }
                }
            }
        }.addOnSuccessListener {
            if (isAdded) {
                val successMessage = if (type == "STAR") {
                    getString(R.string.stars_refilled_toast)
                } else {
                    getString(R.string.infinity_activated_toast)
                }
                Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
                hidePurchaseDrawer()
            }
        }.addOnFailureListener { e ->
            if (isAdded) {
                confirmBtnContainer.visibility = View.VISIBLE
                processingBtnContainer.visibility = View.GONE

                val errorMsg = e.message ?: getString(R.string.transaction_failed)
                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()

                if (errorMsg == getString(R.string.not_enough_coins)) {
                    hidePurchaseDrawer()
                }
            }
        }
    }

    private fun observeUserStats() {
        val uid = auth.currentUser?.uid ?: return
        userListener = db.collection("users").document(uid)
            .addSnapshotListener { snapshot, _ ->
                if (!isAdded) return@addSnapshotListener

                if (snapshot != null && snapshot.exists()) {
                    currentCoins = snapshot.getLong("coins") ?: 0L
                    currentStars = snapshot.getLong("stars") ?: 0L

                    val expiry = snapshot.getTimestamp("subscriptionUntil")
                    val isSubscriptionActive = snapshot.getBoolean("subscription") == true &&
                            expiry != null && expiry.toDate().after(Date())

                    coinCountText.text = currentCoins.toString()

                    updateItemUI(isSubscriptionActive, currentStars >= 15)
                }

                swipeRefreshLayout.isRefreshing = false

                if (!isInitialDataLoaded) {
                    isInitialDataLoaded = true
                    loadingOverlayContainer.visibility = View.GONE
                }
            }
    }

    private fun updateItemUI(isSubscribed: Boolean, isStarsFull: Boolean) {
        if (!isAdded) return

        if (isSubscribed) {
            infinityPriceContainer.visibility = View.GONE
            infinityStatusText.visibility = View.VISIBLE
            itemInfinity.isClickable = false
        } else {
            infinityPriceContainer.visibility = View.VISIBLE
            infinityStatusText.visibility = View.GONE
            itemInfinity.isClickable = true
        }

        if (isStarsFull || isSubscribed) {
            starPriceContainer.visibility = View.GONE
            starStatusText.visibility = View.VISIBLE
            itemStar.isClickable = false
        } else {
            starPriceContainer.visibility = View.VISIBLE
            starStatusText.visibility = View.GONE
            itemStar.isClickable = true
        }
    }

    override fun onStart() {
        super.onStart()
        refreshShopData(isManualSwipe = false)
    }

    override fun onStop() {
        super.onStop()
        userListener?.remove()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::billingClient.isInitialized) {
            billingClient.endConnection()
        }
    }

    override fun onResume() {
        super.onResume()
        if (view != null) {
            view?.findViewById<FrameLayout>(R.id.start_container)?.visibility = View.GONE
        }
    }
}