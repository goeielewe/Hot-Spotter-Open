package com.benchfirewatch.source

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * USB permission for a camera the phone is hosting. A charging hub is fine
 * when the phone stays the USB host; the hub itself is invisible and the
 * camera shows up as a normal device.
 */
object UsbAccess {
    private const val ACTION = "com.hotspotter.open.USB_PERMISSION"

    fun awaitDevice(context: Context, device: UsbDevice, timeoutMs: Long): Boolean {
        val usb = context.getSystemService(UsbManager::class.java) ?: return false
        if (usb.hasPermission(device)) return true
        if (timeoutMs <= 0) return false
        return request(context, usb, device)
    }

    fun hasPermission(context: Context, device: UsbDevice): Boolean {
        val usb = context.getSystemService(UsbManager::class.java) ?: return false
        return usb.hasPermission(device)
    }

    /**
     * Blocks until [device] is permitted. Call off the main thread.
     * One denial ends the wait so the dialog is not stacked.
     */
    fun awaitPermission(context: Context, device: UsbDevice, timeoutMs: Long): Boolean {
        val usb = context.getSystemService(UsbManager::class.java) ?: return false
        if (usb.hasPermission(device)) return true
        if (timeoutMs <= 0) return false
        return request(context, usb, device)
    }

    private fun request(context: Context, usb: UsbManager, device: UsbDevice): Boolean {
        val granted = AtomicBoolean(false)
        val latch = CountDownLatch(1)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                granted.set(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                latch.countDown()
            }
        }
        val filter = IntentFilter(ACTION)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        try {
            val flags = if (Build.VERSION.SDK_INT >= 31) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val intent = Intent(ACTION).setPackage(context.packageName)
            val pending = PendingIntent.getBroadcast(context, 0, intent, flags)
            usb.requestPermission(device, pending)
            val deadline = System.currentTimeMillis() + 25_000
            while (System.currentTimeMillis() < deadline) {
                if (latch.await(400, TimeUnit.MILLISECONDS)) break
                if (Thread.currentThread().isInterrupted) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        } finally {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
            }
        }
        return granted.get()
    }
}
