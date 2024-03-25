package com.example.donoapp

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import java.io.IOException
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var ocrProcessor: OCRProcessor
    private lateinit var cameraExecutor: ExecutorService
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var isConnected by mutableStateOf(false)
    private var connectedDeviceName by mutableStateOf("")
    private var showPairedDevicesDialog by mutableStateOf(false)
    private var pairedDevices = listOf<BluetoothDevice>()

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            setupBluetooth()
            setupCamera()
        } else {
            showManualPermissionSettingsDialog()
        }
    }
    private fun requestPermissions() {
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ocrProcessor = OCRProcessor()
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Check permissions for Camera and Bluetooth
        if (allPermissionsGranted()) {
            setupBluetooth()
            setupCamera()
        } else {
            // Request necessary permissions
            requestPermissions()
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        // Close Bluetooth connection when the app is destroyed
        bluetoothSocket?.close()
    }

    @Composable
    fun ConnectionIndicator() {
        Text(
            text = if (isConnected) "Connected to $connectedDeviceName" else "Not Connected",
            color = if (isConnected) Color.Green else Color.Red,
            modifier = Modifier.padding(16.dp),
            fontSize = 20.sp
        )
    }

    @Composable
    fun ChooseDeviceDialog() {
        if (showPairedDevicesDialog) {
            AlertDialog(
                onDismissRequest = { showPairedDevicesDialog = false },
                title = { Text("Select Bluetooth Device") },
                text = {
                    Column {
                        pairedDevices.forEach { device ->
                            Button(onClick = { connectToDevice(device) }) {
                                val deviceName = if (ActivityCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    device.name ?: "Unknown Device"
                                } else {
                                    "Unknown Device"
                                }
                                Text(deviceName)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showPairedDevicesDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    @Composable
    fun CameraPreviewWithOverlay(onViewFinderAvailable: (PreviewView) -> Unit) {

        Box(modifier = Modifier
            .fillMaxSize()
            .background(Color.Green)) {
            // Camera feed box
            Box(modifier = Modifier.fillMaxSize()) {
                ConnectionIndicator()
                Button(
                    onClick = { showPairedDevicesDialog = true },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Text("Select Device")
                }
                ChooseDeviceDialog()
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(300.dp)
                    .height(200.dp)
                    .border(2.dp, Color.Green)
            ) {
                AndroidView(
                    factory = { context ->
                        PreviewView(context).apply {
                            scaleType = PreviewView.ScaleType.FIT_CENTER
                            onViewFinderAvailable(this)
                        }
                    },
                    modifier = Modifier.matchParentSize()
                )
            }
            // Text above the camera feed
            Text(
                text = "PLACE THE ITEM IN THE CAMERA",
                fontSize = 36.sp,
                fontWeight = FontWeight.Light,
                color = Color.Black,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 70.dp)
            )
        }
    }

    private fun setupCamera() {
        setContent {
            CameraPreviewWithOverlay { viewFinder ->
                startCamera(viewFinder)
            }
        }
    }

    private fun setupBluetooth() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions()
            return
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, "Bluetooth is not enabled", Toast.LENGTH_SHORT).show()
        } else {
            pairedDevices = bluetoothAdapter!!.bondedDevices.toList()
        }
    }

    private fun startCamera(viewFinder: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis
                )

                // Apply a 2x zoom
                val cameraControl = camera.cameraControl
                cameraControl.setZoomRatio(2.0f)

            } catch (e: Exception) {
                Toast.makeText(this, "Failed to start camera.", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }




    private fun showManualPermissionSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Denied")
            .setMessage("Camera permission is needed for this app to function. Please grant permission.")
            .setPositiveButton("App Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
            ocrProcessor.processImage(image) { text ->
                sendTextToBluetoothDevice(text)
                runOnUiThread {
                    Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(applicationContext, "No image captured", Toast.LENGTH_SHORT).show()
        }
        imageProxy.close()
    }

    private fun sendTextToBluetoothDevice(text: String) {
        try {
            bluetoothSocket?.outputStream?.write(text.toByteArray())
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to send data.", Toast.LENGTH_SHORT).show()
        }
    }


    private fun connectToDevice(device: BluetoothDevice) {
        try {
            val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard SerialPortService ID
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            bluetoothSocket?.connect()
            isConnected = true
            connectedDeviceName = device.name
        } catch (e: IOException) {
            isConnected = false
        }
    }

    private fun showBluetoothDeviceSelectionDialog() {
        showPairedDevicesDialog = true
    }



}

