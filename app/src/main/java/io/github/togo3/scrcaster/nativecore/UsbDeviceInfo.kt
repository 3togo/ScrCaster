package io.github.togo3.scrcaster.nativecore

import android.hardware.usb.UsbDevice

/**
 * USB 设备信息
 *
 * 用于 UI 显示和设备管理
 */
data class UsbDeviceInfo(
    val device: UsbDevice,
    val hasPermission: Boolean
) {
    /**
     * 获取设备显示名称
     */
    fun getDisplayName(): String {
        val manufacturer = device.manufacturerName ?: "Unknown"
        val product = device.productName ?: "Device"
        return "$manufacturer $product"
    }

    /**
     * 获取设备 VID
     */
    fun getVendorId(): Int = device.vendorId

    /**
     * 获取设备 PID
     */
    fun getProductId(): Int = device.productId

    /**
     * 获取设备 ID
     */
    fun getDeviceId(): Int = device.deviceId

    /**
     * 获取 USB 地址字符串
     * 格式: usb:0x{VID}/0x{PID}#{deviceId}
     */
    fun getUsbAddress(): String {
        val vid = String.format("0x%04X", device.vendorId)
        val pid = String.format("0x%04X", device.productId)
        return "usb:$vid/$pid#${device.deviceId}"
    }
}
