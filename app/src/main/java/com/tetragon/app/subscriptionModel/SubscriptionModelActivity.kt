package com.tetragon.app.subscriptionModel

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import app.rive.runtime.kotlin.core.Rive
import com.android.billingclient.api.Purchase
import com.tetragon.app.R
import com.tetragon.app.databinding.ActivitySubscriptionModelBinding
import com.tetragon.app.utils.languageChangeUtils.BaseActivity

class SubscriptionModelActivity : BaseActivity() {
    private lateinit var binding: ActivitySubscriptionModelBinding
    private lateinit var billingHelper: BillingHelper

    // По умолчанию выбран годовой тариф, как у тебя в XML (bg_subscription_selected)
    private var selectedProductId = BillingHelper.PREMIUM_ANNUAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init(this)
        enableEdgeToEdge()
        binding = ActivitySubscriptionModelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Показываем состояние загрузки подписок по умолчанию
        showLoading(true)

        // Инициализируем менеджер биллинга
        initBilling()

        // Первоначальный запуск анимаций Rive
        binding.correctMrSquare2.visibility = View.VISIBLE
        binding.correctMrSquare2.fireState("State Machine 1", "play")

        setupClickListeners()
    }

    private fun initBilling() {
        billingHelper = BillingHelper(
            context = this,
            onBillingReady = {
                // Данные о тарифах загружены из Google Play, активируем кнопку покупки
                showLoading(false)
            },
            onPurchaseSuccess = { purchase ->
                // Покупка прошла успешно!
                handleSuccessfulPremium(purchase)
            },
            onError = { errorMessage ->
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                // Оставляем кнопку в состоянии "Loading" или даем нажать повторно в зависимости от логики
            }
        )
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        binding.containerAnnual.setOnClickListener {
            selectedProductId = BillingHelper.PREMIUM_ANNUAL

            // UI Logic
            binding.containerAnnual.setBackgroundResource(R.drawable.bg_subscription_selected)
            binding.containerMonthly.setBackgroundResource(R.drawable.bg_subscription_unselected)

            binding.correctMrSquare2.visibility = View.VISIBLE
            binding.correctMrSquare2.fireState("State Machine 1", "play")

            // Rive Logic
            binding.correctMrSquare.visibility = View.GONE
            binding.correctMrSquare.fireState("State Machine 1", "finish")
        }

        binding.containerMonthly.setOnClickListener {
            selectedProductId = BillingHelper.PREMIUM_MONTHLY

            // UI Logic
            binding.containerAnnual.setBackgroundResource(R.drawable.bg_subscription_unselected)
            binding.containerMonthly.setBackgroundResource(R.drawable.bg_subscription_selected)

            binding.correctMrSquare2.visibility = View.GONE
            binding.correctMrSquare2.fireState("State Machine 1", "finish")

            // Rive Logic
            binding.correctMrSquare.visibility = View.VISIBLE
            binding.correctMrSquare.fireState("State Machine 1", "play")
        }

        // Обработка клика по кнопке "Subscribe"
        binding.subscribeEnabledBtn.setOnClickListener {
            billingHelper.launchPurchaseFlow(this, selectedProductId)
        }
    }

    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            binding.subscribeEnabledBtnContainer.visibility = View.INVISIBLE
            binding.subscribeDisabledBtnContainer.visibility = View.VISIBLE
        } else {
            binding.subscribeEnabledBtnContainer.visibility = View.VISIBLE
            binding.subscribeDisabledBtnContainer.visibility = View.INVISIBLE
        }
    }

    private fun handleSuccessfulPremium(purchase: Purchase) {
        Toast.makeText(this, "Премиум успешно активирован!", Toast.LENGTH_SHORT).show()

        // TODO: Синхронизация с сервером / Firebase FirebaseFirestore или FirebaseAuth
        // Например:
        // val userId = FirebaseAuth.getInstance().currentUser?.uid
        // FirebaseFirestore.getInstance().collection("users").document(userId).update("isPremium", true)

        // Закрываем экран оплаты после успешной транзакции
        finish()
    }
}