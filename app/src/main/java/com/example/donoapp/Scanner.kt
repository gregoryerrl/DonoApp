package com.example.donoapp

import android.Manifest
import android.bluetooth.*
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Paint
import android.media.Image
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
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
import android.graphics.YuvImage
import android.graphics.Rect
import java.io.ByteArrayOutputStream
import android.view.WindowManager


class Scanner : ComponentActivity() {
    private lateinit var ocrProcessor: OCRProcessor
    private lateinit var cameraExecutor: ExecutorService
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var isConnected by mutableStateOf(false)
    private var connectedDeviceName by mutableStateOf("")
    private var showPairedDevicesDialog by mutableStateOf(false)
    private var pairedDevices = listOf<BluetoothDevice>()
    private var lastToastTime = System.currentTimeMillis()
    private var detectedText by mutableStateOf("")
    private var detectedDate by mutableStateOf("")
    private var lastAnalyzedTimestamp = 0L



    private fun showToast(message: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastToastTime > 500) { // Throttle toast to one every 2 seconds
            runOnUiThread {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
            lastToastTime = currentTime
        }
    }

    private fun Image.yuv420888ToBitmap(): Bitmap? {
        val yBuffer = planes[0].buffer // Y
        val uBuffer = planes[1].buffer // U
        val vBuffer = planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ocrProcessor = OCRProcessor()
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            setupBluetooth()
            setupCamera()
        } else {
            requestPermissions()
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

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
                                        this@Scanner,
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
    fun DetectedTextDisplay() {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (detectedText.isEmpty()) "Detecting..." else detectedText,
                color = Color.Black,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            if (detectedDate.isNotEmpty()) {
                Text(
                    text = "Detected Expiry Date: $detectedDate",
                    color = Color.Red,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
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

            DetectedTextDisplay()
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
                cameraControl.setZoomRatio(5.0f)

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
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAnalyzedTimestamp >= 100) { // Process every 500 milliseconds
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                val bitmap = mediaImage.yuv420888ToBitmap()?.rotate(rotationDegrees)

                bitmap?.let {
                    val inputImage = InputImage.fromBitmap(it, 0)
                    ocrProcessor.processImage(inputImage, { text ->
                        detectedText = text  // Update the state for all text
                    }, { dateText ->
                        if (dateText != null) {
                            detectedDate = dateText
                        }  // Update the state for detected date
                    })
                } ?: run {
                    detectedText = "Failed to decode image data"
                }
            } else {
                detectedText = "No image captured"
            }
            lastAnalyzedTimestamp = currentTime // Update last processed time
        }
        imageProxy.close()
    }

    private fun Image.toBitmap(): Bitmap? {
        val buffer = planes[0].buffer
        buffer.rewind()  // Ensure the buffer is rewound before reading
        val bytes = ByteArray(buffer.capacity())
        buffer.get(bytes)
        Log.d("CameraXApp", "Buffer size: ${buffer.capacity()}, Array size: ${bytes.size}")

        // Try decoding the byte array to a Bitmap
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also {
            Log.d("CameraXApp", "Bitmap decoded successfully")
        } ?: run {
            Log.e("CameraXApp", "Failed to decode Bitmap")
            runOnUiThread {
                Toast.makeText(this@Scanner, "Failed to decode image data", Toast.LENGTH_SHORT).show()
            }
            null
        }
    }
    private fun Bitmap.rotate(degrees: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun Bitmap.toGrayscale(): Bitmap {
        val bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmpGrayscale)
        val paint = Paint()
        val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
        val colorMatrixFilter = ColorMatrixColorFilter(colorMatrix)
        paint.colorFilter = colorMatrixFilter
        canvas.drawBitmap(this, 0f, 0f, paint)
        return bmpGrayscale
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

