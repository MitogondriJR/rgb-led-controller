package com.example.rgbledcontroller

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*

class MainActivity : AppCompatActivity() {
    
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private lateinit var bluetoothGatt: BluetoothGatt
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    
    private lateinit var statusText: TextView
    private lateinit var connectButton: Button
    private lateinit var redSeekBar: SeekBar
    private lateinit var greenSeekBar: SeekBar
    private lateinit var blueSeekBar: SeekBar
    private lateinit var colorPreview: View
    private lateinit var deviceSpinner: Spinner
    
    private val foundDevices = mutableListOf<BluetoothDevice>()
    private val deviceNames = mutableListOf<String>()
    private lateinit var spinnerAdapter: ArrayAdapter<String>
    
    private var isScanning = false
    private var isConnected = false
    
    // ELK-BLEDOM için bilinen servis ve karakteristik UUID'leri
    private val SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    private val CHARACTERISTIC_UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
    
    private val PERMISSION_REQUEST_CODE = 1
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupBluetoothAdapter()
        requestPermissions()
        setupSeekBars()
    }
    
    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        connectButton = findViewById(R.id.connectButton)
        redSeekBar = findViewById(R.id.redSeekBar)
        greenSeekBar = findViewById(R.id.greenSeekBar)
        blueSeekBar = findViewById(R.id.blueSeekBar)
        colorPreview = findViewById(R.id.colorPreview)
        deviceSpinner = findViewById(R.id.deviceSpinner)
        
        spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, deviceNames)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        deviceSpinner.adapter = spinnerAdapter
        
        connectButton.setOnClickListener {
            if (isConnected) {
                disconnect()
            } else {
                if (foundDevices.isNotEmpty()) {
                    connectToDevice(foundDevices[deviceSpinner.selectedItemPosition])
                } else {
                    scanForDevices()
                }
            }
        }
    }
    
    private fun setupBluetoothAdapter() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        
        if (bluetoothAdapter == null) {
            statusText.text = "Bluetooth desteklenmiyor"
            return
        }
        
        if (!bluetoothAdapter.isEnabled) {
            statusText.text = "Bluetooth kapalı - lütfen açın"
            return
        }
        
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
    }
    
    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
        
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            scanForDevices()
        }
    }
    
    private fun scanForDevices() {
        if (isScanning) return
        
        foundDevices.clear()
        deviceNames.clear()
        spinnerAdapter.notifyDataSetChanged()
        
        statusText.text = "Cihazlar aranıyor..."
        isScanning = true
        
        bluetoothLeScanner.startScan(scanCallback)
        
        // 10 saniye sonra taramayı durdur
        Handler(Looper.getMainLooper()).postDelayed({
            if (isScanning) {
                bluetoothLeScanner.stopScan(scanCallback)
                isScanning = false
                statusText.text = "${foundDevices.size} cihaz bulundu"
            }
        }, 10000)
    }
    
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: "Bilinmeyen Cihaz"
            
            // ELK-BLEDOM veya benzer isimleri ara
            if (deviceName.contains("ELK-BLEDOM", ignoreCase = true) || 
                deviceName.contains("LED", ignoreCase = true) ||
                deviceName.contains("RGB", ignoreCase = true)) {
                
                if (!foundDevices.contains(device)) {
                    foundDevices.add(device)
                    deviceNames.add("$deviceName (${device.address})")
                    
                    runOnUiThread {
                        spinnerAdapter.notifyDataSetChanged()
                        statusText.text = "${foundDevices.size} LED cihazı bulundu"
                    }
                }
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            runOnUiThread {
                statusText.text = "Tarama başarısız: $errorCode"
                isScanning = false
            }
        }
    }
    
    private fun connectToDevice(device: BluetoothDevice) {
        statusText.text = "Bağlanıyor..."
        connectButton.text = "Bağlanıyor..."
        
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            runOnUiThread {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        statusText.text = "Bağlandı - Servisler keşfediliyor..."
                        bluetoothGatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        statusText.text = "Bağlantı kesildi"
                        connectButton.text = "Bağlan"
                        isConnected = false
                        enableControls(false)
                    }
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    writeCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
                    if (writeCharacteristic != null) {
                        runOnUiThread {
                            statusText.text = "Bağlandı ve hazır!"
                            connectButton.text = "Bağlantıyı Kes"
                            isConnected = true
                            enableControls(true)
                        }
                    } else {
                        runOnUiThread {
                            statusText.text = "Yazma karakteristiği bulunamadı"
                        }
                    }
                } else {
                    runOnUiThread {
                        statusText.text = "Servis bulunamadı"
                    }
                }
            }
        }
        
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread {
                    statusText.text = "Renk gönderildi"
                }
            }
        }
    }
    
    private fun setupSeekBars() {
        val colorChangeListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    updateColor()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        }
        
        redSeekBar.max = 255
        greenSeekBar.max = 255
        blueSeekBar.max = 255
        
        redSeekBar.setOnSeekBarChangeListener(colorChangeListener)
        greenSeekBar.setOnSeekBarChangeListener(colorChangeListener)
        blueSeekBar.setOnSeekBarChangeListener(colorChangeListener)
        
        enableControls(false)
    }
    
    private fun updateColor() {
        val red = redSeekBar.progress
        val green = greenSeekBar.progress
        val blue = blueSeekBar.progress
        
        // Renk önizlemesini güncelle
        colorPreview.setBackgroundColor(android.graphics.Color.rgb(red, green, blue))
        
        // LED'e renk gönder
        if (isConnected) {
            sendColorToDevice(red, green, blue)
        }
    }
    
    private fun sendColorToDevice(red: Int, green: Int, blue: Int) {
        if (writeCharacteristic == null || !isConnected) return
        
        // ELK-BLEDOM protokolü: 7E 00 04 [R] [G] [B] 00 FF 00 EF
        val command = byteArrayOf(
            0x7E.toByte(),
            0x00.toByte(),
            0x04.toByte(),
            red.toByte(),
            green.toByte(),
            blue.toByte(),
            0x00.toByte(),
            0xFF.toByte(),
            0x00.toByte(),
            0xEF.toByte()
        )
        
        writeCharacteristic?.value = command
        bluetoothGatt.writeCharacteristic(writeCharacteristic)
    }
    
    private fun enableControls(enabled: Boolean) {
        redSeekBar.isEnabled = enabled
        greenSeekBar.isEnabled = enabled
        blueSeekBar.isEnabled = enabled
    }
    
    private fun disconnect() {
        if (::bluetoothGatt.isInitialized) {
            bluetoothGatt.disconnect()
            bluetoothGatt.close()
        }
        isConnected = false
        enableControls(false)
        statusText.text = "Bağlantı kesildi"
        connectButton.text = "Bağlan"
    }
    
    override fun onDestroy() {
        super.onDestroy()
        disconnect()
        if (isScanning) {
            bluetoothLeScanner.stopScan(scanCallback)
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                scanForDevices()
            } else {
                statusText.text = "İzinler gerekli"
            }
        }
    }
    
    // Hızlı renk butonları için fonksiyonlar
    fun setRed(view: android.view.View) {
        redSeekBar.progress = 255
        greenSeekBar.progress = 0
        blueSeekBar.progress = 0
        updateColor()
    }
    
    fun setGreen(view: android.view.View) {
        redSeekBar.progress = 0
        greenSeekBar.progress = 255
        blueSeekBar.progress = 0
        updateColor()
    }
    
    fun setBlue(view: android.view.View) {
        redSeekBar.progress = 0
        greenSeekBar.progress = 0
        blueSeekBar.progress = 255
        updateColor()
    }
    
    fun turnOff(view: android.view.View) {
        redSeekBar.progress = 0
        greenSeekBar.progress = 0
        blueSeekBar.progress = 0
        updateColor()
    }
}