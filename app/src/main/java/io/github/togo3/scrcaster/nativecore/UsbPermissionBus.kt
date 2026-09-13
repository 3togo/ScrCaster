package io.github.togo3.scrcaster.nativecore

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import io.github.togo3.scrcaster.util.parcelableExtra

/**
 * USB 权限广播接收器
 *
 * 用于接收 USB 权限请求结果, 避免隐式 Intent + FLAG_MUTABLE 的 Android 14+ 限制
 */
class UsbPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_USB_PERMISSION) return
        
        val device = intent.parcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
        
        if (device != null) {
            Log.i(TAG, "UsbPermissionReceiver: device ${device.deviceName} permission ${if (granted) "granted" else "denied"}")
            // 通知全局事件总线
            UsbPermissionBus.notifyResult(device, granted)
        }
    }
    
    companion object {
        private const val TAG = "UsbPermissionReceiver"
        private const val ACTION_USB_PERMISSION = "io.github.togo3.scrcaster.USB_PERMISSION"
    }
}

/**
 * USB 权限事件总线
 *
 * 用于将权限结果从静态 BroadcastReceiver 传递到 UsbAdbDeviceWatcher 实例
 */
object UsbPermissionBus {
    // Binder 线程的广播回调与主线程的 add/remove 并发, 使用写时复制容器保证线程安全
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(UsbDevice, Boolean) -> Unit>()
    
    fun addListener(listener: (UsbDevice, Boolean) -> Unit) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: (UsbDevice, Boolean) -> Unit) {
        listeners.remove(listener)
    }
    
    fun notifyResult(device: UsbDevice, granted: Boolean) {
        listeners.forEach { it(device, granted) }
    }
}

/**
 * USB 设备事件
 *
 * 用于通知 UI 层设备状态变化
 */
sealed class UsbDeviceEvent {
    /**
     * 设备连接事件
     */
    data class Attached(val device: UsbDevice) : UsbDeviceEvent()
    
    /**
     * 设备断开事件
     */
    data class Detached(val device: UsbDevice) : UsbDeviceEvent()
    
    /**
     * 权限结果事件
     */
    data class PermissionResult(val device: UsbDevice, val granted: Boolean) : UsbDeviceEvent()
}
