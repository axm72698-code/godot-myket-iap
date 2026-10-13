package com.zane.godot.myket

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import ir.myket.billinginterface.IInAppBillingService
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot
import org.json.JSONObject

class MyketBillingPlugin(godot: Godot) : GodotPlugin(godot) {

    private val TAG = "MyketBillingPlugin"
    private val RC_BUY = 1001

    private var billingService: IInAppBillingService? = null
    private var isConnected = false

    override fun getPluginName(): String = "MyketBillingPlugin"

    override fun getPluginSignals(): Set<SignalInfo> {
        return setOf(
            SignalInfo("billing_connected"),
            SignalInfo("purchase_success", String::class.java),
            SignalInfo("purchase_failed", String::class.java)
        )
    }

    private val serviceConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            billingService = IInAppBillingService.Stub.asInterface(service)
            isConnected = true
            Log.d(TAG, "Myket billing connected.")
            emitSignal("billing_connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            billingService = null
            isConnected = false
        }
    }

    @UsedByGodot
    fun connect() {
        val act = activity ?: return
        val serviceIntent = Intent("ir.myket.billing.InAppBillingService.BIND").apply {
            setPackage("ir.mservices.market")
        }
        act.bindService(serviceIntent, serviceConn, Context.BIND_AUTO_CREATE)
    }

    @UsedByGodot
    fun purchase(sku: String) {
        val act = activity ?: return
        if (!isConnected || billingService == null) {
            emitSignal("purchase_failed", "سرویس مایکت متصل نیست.")
            return
        }

        try {
            val buyBundle: Bundle = billingService!!.getBuyIntent(
                3,
                act.packageName,
                sku,
                "inapp",
                "payload_${System.currentTimeMillis()}"
            )
            val responseCode = buyBundle.getInt("RESPONSE_CODE")
            if (responseCode == 0) {
                val pendingIntent = buyBundle.getParcelable<android.app.PendingIntent>("BUY_INTENT")
                if (pendingIntent != null) {
                    act.startIntentSenderForResult(
                        pendingIntent.intentSender,
                        RC_BUY,
                        Intent(),
                        0, 0, 0
                    )
                }
            } else {
                emitSignal("purchase_failed", "کد خطا از مایکت: $responseCode")
            }
        } catch (e: Exception) {
            emitSignal("purchase_failed", e.message ?: "خطای ناشناخته")
        }
    }

    override fun onMainActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == RC_BUY) {
            val responseCode = data?.getIntExtra("RESPONSE_CODE", 0) ?: -1
            val purchaseData = data?.getStringExtra("INAPP_PURCHASE_DATA")

            if (resultCode == Activity.RESULT_OK && responseCode == 0 && purchaseData != null) {
                try {
                    val jo = JSONObject(purchaseData)
                    val sku = jo.getString("productId")
                    emitSignal("purchase_success", sku)
                } catch (e: Exception) {
                    emitSignal("purchase_failed", "خطا در پردازش خرید")
                }
            } else {
                emitSignal("purchase_failed", "پرداخت لغو شد یا با شکست مواجه شد.")
            }
        }
    }
}
