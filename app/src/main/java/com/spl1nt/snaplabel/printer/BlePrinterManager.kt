package com.spl1nt.snaplabel.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Connects to and prints on a Rongta RPP30 (and compatible) BLE label printer.
 *
 * The protocol here was reverse-engineered by hand against the real printer
 * (see the project's printer notes): it speaks CPCL over what turns out to be
 * an ISSC "transparent UART" characteristic, accepting write-without-response
 * in chunks. It never sends anything back over BLE — there's no ack or status
 * to read — so a successful [print] call only means the Bluetooth stack
 * accepted the bytes, not that a label actually came out.
 */
class BlePrinterManager(private val context: Context) {

    companion object {
        val SERVICE_ISSC: UUID = UUID.fromString("49535343-fe7d-4ae5-8fa9-9fafd205e455")
        val CHAR_ISSC_WRITE: UUID = UUID.fromString("49535343-8841-43f4-a8d4-ecbe34729bb3")
        val CHAR_FF02_WRITE: UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")

        private const val REQUESTED_MTU = 517
        private const val CHUNK_DELAY_MS = 40L
        private const val WRITE_TIMEOUT_MS = 1500L
        private const val MAX_CHUNK_BYTES = 100 // matches the chunk size proven reliable from the desktop proof-of-concept
    }

    /** Diagnostics from the last [print] call, surfaced to the UI so transfer problems are visible without a debugger. */
    data class TransferStats(val bytes: Int, val chunks: Int, val mtu: Int, val elapsedMs: Long)

    sealed class State {
        data object Disconnected : State()
        data object Connecting : State()
        data object Connected : State()
        data class Failed(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Disconnected)
    val state: StateFlow<State> = _state.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var negotiatedMtu = 23
    private var pendingWrite: CancellableContinuation<Boolean>? = null

    /** The MTU actually negotiated with the printer (23 = default/never negotiated). */
    val currentMtu: Int get() = negotiatedMtu

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        disconnect()
        _state.value = State.Connecting
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        writeChar = null
        negotiatedMtu = 23
        _state.value = State.Disconnected
    }

    fun isReady(): Boolean = _state.value is State.Connected && writeChar != null

    @SuppressLint("MissingPermission")
    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.requestMtu(REQUESTED_MTU)
                BluetoothProfile.STATE_DISCONNECTED -> {
                    writeChar = null
                    _state.value = State.Disconnected
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            negotiatedMtu = if (status == BluetoothGatt.GATT_SUCCESS) mtu else 23
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = State.Failed("Service discovery failed ($status)")
                return
            }
            val char = g.getService(SERVICE_ISSC)?.getCharacteristic(CHAR_ISSC_WRITE)
                ?: g.services.firstNotNullOfOrNull { it.getCharacteristic(CHAR_FF02_WRITE) }
            if (char == null) {
                _state.value = State.Failed("No known write characteristic on this device")
                return
            }
            writeChar = char
            _state.value = State.Connected
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            pendingWrite?.let {
                pendingWrite = null
                it.resume(status == BluetoothGatt.GATT_SUCCESS)
            }
        }
    }

    /**
     * Send raw CPCL bytes to the printer in small chunks, write-without-
     * response, the same cadence the working proof-of-concept on the desktop
     * used. Suspends until every chunk is *confirmed* handed to the Bluetooth
     * controller — a chunk that never gets confirmed now fails the whole
     * print instead of being silently skipped (an earlier version of this
     * code assumed success on timeout, which let partial transfers through
     * and corrupted labels past whatever point the link hiccuped).
     */
    @SuppressLint("MissingPermission")
    suspend fun print(data: ByteArray): Result<TransferStats> {
        val g = gatt ?: return Result.failure(IllegalStateException("Not connected"))
        val char = writeChar ?: return Result.failure(IllegalStateException("No write characteristic"))
        val chunkSize = (negotiatedMtu - 3).coerceIn(20, MAX_CHUNK_BYTES)
        val start = System.currentTimeMillis()
        var offset = 0
        var chunkCount = 0
        while (offset < data.size) {
            val end = minOf(offset + chunkSize, data.size)
            val ok = writeChunk(g, char, data.copyOfRange(offset, end))
            if (!ok) {
                return Result.failure(
                    IllegalStateException("Write not confirmed at byte $offset of ${data.size} (chunk $chunkCount) — aborted rather than risk a corrupted label"),
                )
            }
            offset = end
            chunkCount++
            delay(CHUNK_DELAY_MS)
        }
        return Result.success(TransferStats(data.size, chunkCount, negotiatedMtu, System.currentTimeMillis() - start))
    }

    /** Writes one chunk and waits for it to actually be confirmed by the stack; never guesses. */
    @SuppressLint("MissingPermission")
    private suspend fun writeChunk(g: BluetoothGatt, char: BluetoothGattCharacteristic, chunk: ByteArray): Boolean {
        val result = withTimeoutOrNull(WRITE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                pendingWrite = cont
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                char.value = chunk
                @Suppress("DEPRECATION")
                val started = g.writeCharacteristic(char)
                if (!started) {
                    pendingWrite = null
                    cont.resume(false)
                }
            }
        }
        if (result == null) pendingWrite = null // timed out: stop waiting on this continuation, don't let a late callback resolve the *next* chunk's write
        return result ?: false
    }
}
