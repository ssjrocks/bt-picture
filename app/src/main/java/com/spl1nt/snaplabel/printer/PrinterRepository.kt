package com.spl1nt.snaplabel.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

private val Context.dataStore by preferencesDataStore(name = "snaplabel_settings")
private val KEY_PRINTER_ADDRESS = stringPreferencesKey("printer_address")
private val KEY_PRINTER_NAME = stringPreferencesKey("printer_name")

data class FoundDevice(val name: String, val address: String, val device: BluetoothDevice)

/** Scanning, bonding, connecting and remembering the printer across app launches. */
class PrinterRepository(private val context: Context) {
    val ble = BlePrinterManager(context)

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter get() = bluetoothManager.adapter

    private val _foundDevices = MutableStateFlow<List<FoundDevice>>(emptyList())
    val foundDevices: StateFlow<List<FoundDevice>> = _foundDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val rememberedAddress: Flow<String?> = context.dataStore.data.map { it[KEY_PRINTER_ADDRESS] }
    val rememberedName: Flow<String?> = context.dataStore.data.map { it[KEY_PRINTER_NAME] }

    private var scanCallback: ScanCallback? = null

    @SuppressLint("MissingPermission")
    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        _foundDevices.value = emptyList()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: result.scanRecord?.deviceName ?: return
                val found = FoundDevice(name, result.device.address, result.device)
                _foundDevices.update { list -> if (list.any { it.address == found.address }) list else list + found }
            }
        }
        scanCallback = cb
        _isScanning.value = true
        scanner.startScan(cb)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanCallback?.let { adapter?.bluetoothLeScanner?.stopScan(it) }
        scanCallback = null
        _isScanning.value = false
    }

    /** Kick off bonding; Android shows its own system PIN/passkey dialog. */
    @SuppressLint("MissingPermission")
    fun bond(found: FoundDevice) {
        if (found.device.bondState != BluetoothDevice.BOND_BONDED) found.device.createBond()
    }

    @SuppressLint("MissingPermission")
    fun connect(found: FoundDevice) = ble.connect(found.device)

    @SuppressLint("MissingPermission")
    fun connectToRemembered(address: String) {
        adapter?.getRemoteDevice(address)?.let { ble.connect(it) }
    }

    suspend fun remember(found: FoundDevice) {
        context.dataStore.edit {
            it[KEY_PRINTER_ADDRESS] = found.address
            it[KEY_PRINTER_NAME] = found.name
        }
    }

    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<BluetoothDevice> = adapter?.bondedDevices?.toList() ?: emptyList()
}
