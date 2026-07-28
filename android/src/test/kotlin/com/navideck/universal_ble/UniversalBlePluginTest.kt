package com.navideck.universal_ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.os.Handler
import kotlin.test.Test
import kotlin.test.assertFalse
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

internal class UniversalBlePluginTest {
    @Test
    fun connectedCallbackCancelsPendingReconnect() {
        val plugin = UniversalBlePlugin()
        val handler = mock(Handler::class.java)
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
        `when`(handler.post(any(Runnable::class.java))).thenAnswer {
            (it.arguments[0] as Runnable).run()
            true
        }

        plugin.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_CONNECTED)

        verify(handler).removeCallbacks(pendingConnect)
        assertFalse(pendingConnects.containsKey(deviceId.connectionKey()))
        assertFalse(disconnectTimestamps.containsKey(deviceId.connectionKey()))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> UniversalBlePlugin.field(name: String): T {
        return javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this) as T
    }

    private fun UniversalBlePlugin.setField(name: String, value: Any?) {
        javaClass.getDeclaredField(name).apply { isAccessible = true }.set(this, value)
    }
}
