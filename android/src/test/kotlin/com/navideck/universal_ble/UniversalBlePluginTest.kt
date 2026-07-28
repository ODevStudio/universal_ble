package com.navideck.universal_ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.os.Handler
import kotlin.test.Test
import kotlin.test.assertFalse
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

internal class UniversalBlePluginTest {
    @Test
    fun connectedCallbackCancelsPendingReconnect() {
        val plugin = UniversalBlePlugin()
        val handler = handler(runPostedTasks = true)
        val gatt = mock(BluetoothGatt::class.java)
        val device = mock(BluetoothDevice::class.java)
        val pendingConnect = mock(Runnable::class.java)
        val deviceId = "AA:BB:CC:DD:EE:FF"
        val pendingConnects = plugin.field<MutableMap<String, Runnable>>("pendingConnects")
        val disconnectTimestamps = plugin.field<MutableMap<String, Long>>("disconnectTimestamps")

        plugin.setField("mainThreadHandler", handler)
        pendingConnects[deviceId.connectionKey()] = pendingConnect
        disconnectTimestamps[deviceId.connectionKey()] = 1L
        `when`(gatt.device).thenReturn(device)
        `when`(device.address).thenReturn(deviceId)

        plugin.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_CONNECTED)

        verify(handler).removeCallbacks(pendingConnect)
        assertFalse(pendingConnects.containsKey(deviceId.connectionKey()))
        assertFalse(disconnectTimestamps.containsKey(deviceId.connectionKey()))
    }

    @Test
    fun explicitDisconnectCancelsPendingReconnectCaseInsensitively() {
        val plugin = UniversalBlePlugin()
        val handler = handler()
        val pendingConnect = mock(Runnable::class.java)
        val deviceId = "AA:BB:CC:DD:EE:FF"
        val pendingConnects = plugin.field<MutableMap<String, Runnable>>("pendingConnects")

        plugin.setField("mainThreadHandler", handler)
        pendingConnects[deviceId.connectionKey()] = pendingConnect

        plugin.disconnect(deviceId.lowercase())

        verify(handler).removeCallbacks(pendingConnect)
        assertFalse(pendingConnects.containsKey(deviceId.connectionKey()))
    }

    @Test
    fun adapterOffReportsPendingKnownDeviceOnce() {
        val plugin = UniversalBlePlugin()
        val handler = handler()
        val gatt = mock(BluetoothGatt::class.java)
        val device = mock(BluetoothDevice::class.java)
        val pendingConnect = mock(Runnable::class.java)
        val deviceId = "AA:BB:CC:DD:EE:FF"
        val pendingConnects = plugin.field<MutableMap<String, Runnable>>("pendingConnects")

        plugin.setField("mainThreadHandler", handler)
        pendingConnects[deviceId.connectionKey()] = pendingConnect
        `when`(gatt.device).thenReturn(device)
        `when`(device.address).thenReturn(deviceId)
        gatt.saveCacheIfNeeded()

        try {
            plugin.invoke("cleanUpOnAdapterOff")
        } finally {
            gatt.removeCache()
        }

        verify(handler).removeCallbacks(pendingConnect)
        verify(handler, times(1)).post(any(Runnable::class.java))
    }

    private fun handler(runPostedTasks: Boolean = false): Handler {
        val handler = mock(Handler::class.java)
        `when`(handler.post(any(Runnable::class.java))).thenAnswer {
            if (runPostedTasks) (it.arguments[0] as Runnable).run()
            true
        }
        return handler
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> UniversalBlePlugin.field(name: String): T {
        return javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this) as T
    }

    private fun UniversalBlePlugin.setField(name: String, value: Any?) {
        javaClass.getDeclaredField(name).apply { isAccessible = true }.set(this, value)
    }

    private fun UniversalBlePlugin.invoke(name: String) {
        javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(this)
    }
}
