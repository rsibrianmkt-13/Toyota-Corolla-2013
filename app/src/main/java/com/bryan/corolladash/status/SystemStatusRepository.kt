package com.bryan.corolladash.status

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado REAL de Bluetooth y Wi-Fi (solo lectura).
 * Se escucha durante toda la vida de la app (es el launcher) y se re-lee al volver a primer plano.
 */
class SystemStatusRepository(private val context: Context) {

    private val _bluetoothOn = MutableStateFlow(false)
    val bluetoothOn: StateFlow<Boolean> = _bluetoothOn.asStateFlow()

    /** Hay un teléfono conectado (perfiles de audio/llamadas o enlace ACL). Necesita BLUETOOTH_CONNECT. */
    private val _phoneConnected = MutableStateFlow(false)
    val phoneConnected: StateFlow<Boolean> = _phoneConnected.asStateFlow()

    /** Nombre del teléfono conectado ("iPhone de Bryan"), si Android lo informa. */
    private val _phoneName = MutableStateFlow<String?>(null)
    val phoneName: StateFlow<String?> = _phoneName.asStateFlow()

    private val _wifiConnected = MutableStateFlow(false)
    val wifiConnected: StateFlow<Boolean> = _wifiConnected.asStateFlow()

    private val adapter: BluetoothAdapter? get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> refreshBluetooth()
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    _phoneConnected.value = true
                    deviceName(i)?.let { _phoneName.value = it } ?: lookupPhoneName()
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> refreshBluetooth()
                else -> refreshBluetooth()
            }
        }
    }

    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { _wifiConnected.value = true }
        override fun onLost(network: Network) { refreshWifi() }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        }
        runCatching { ContextCompat.registerReceiver(context, btReceiver, filter, ContextCompat.RECEIVER_EXPORTED) }
        runCatching {
            val req = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
            cm?.registerNetworkCallback(req, wifiCallback)
        }
        refresh()
    }

    fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun refresh() {
        refreshBluetooth()
        refreshWifi()
    }

    @SuppressLint("MissingPermission")
    private fun refreshBluetooth() {
        val a = adapter
        val on = runCatching { a?.isEnabled == true }.getOrDefault(false)
        _bluetoothOn.value = on
        _phoneConnected.value = on && hasConnectPermission() && runCatching {
            // Perfiles públicos + perfiles de "receptor" que usan las radios (A2DP sink 11, HFP client 16, AVRCP controller 12).
            PROFILES.any { a?.getProfileConnectionState(it) == BluetoothProfile.STATE_CONNECTED }
        }.getOrDefault(false)
        if (!_phoneConnected.value) _phoneName.value = null else if (_phoneName.value == null) lookupPhoneName()
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun deviceName(i: Intent): String? = runCatching {
        if (!hasConnectPermission()) return null
        i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)?.name?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /** Pregunta a los perfiles de la radio (A2DP sink, HFP client, A2DP) qué teléfono está conectado. */
    @SuppressLint("MissingPermission")
    private fun lookupPhoneName() {
        val a = adapter ?: return
        if (!hasConnectPermission()) return
        for (profile in listOf(11, 16, BluetoothProfile.A2DP, BluetoothProfile.HEADSET)) {
            runCatching {
                a.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                        runCatching {
                            proxy.connectedDevices.firstNotNullOfOrNull { d -> d.name?.takeIf { it.isNotBlank() } }
                                ?.let { if (_phoneName.value == null) _phoneName.value = it }
                        }
                        runCatching { a.closeProfileProxy(p, proxy) }
                    }
                    override fun onServiceDisconnected(p: Int) {}
                }, profile)
            }
        }
    }

    private fun refreshWifi() {
        _wifiConnected.value = runCatching {
            cm?.let { it.getNetworkCapabilities(it.activeNetwork) }?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }.getOrDefault(false)
    }

    private companion object {
        val PROFILES = listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP, 11, 12, 16)
    }
}
