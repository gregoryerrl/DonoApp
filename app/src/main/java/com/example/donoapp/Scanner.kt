package com.example.donoapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
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
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.unit.dp
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
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
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.graphics.YuvImage
import android.graphics.Rect
import java.io.ByteArrayOutputStream
import android.view.WindowManager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.io.IOException
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID


@RequiresApi(Build.VERSION_CODES.O)
class Scanner() : ComponentActivity() {
    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 101
        private const val REQUEST_BLUETOOTH_PERMISSION = 102// Define a unique request code
    }

    private lateinit var ocrProcessor: OCRProcessor
    private lateinit var cameraExecutor: ExecutorService
    private var detectedText by mutableStateOf("")
    var detectedDate by mutableStateOf("")
    private var lastAnalyzedTimestamp = 0L
    private var lastToastTime = System.currentTimeMillis()
    var inputDate by mutableStateOf(LocalDate.now())
    var isDateAcceptable by mutableStateOf(true)
    private lateinit var bluetoothManager: BluetoothManager

    class BluetoothManager(val context: Context) {
        private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        var isConnected = mutableStateOf(false)
        var connectedDeviceName = mutableStateOf("")

        private var bluetoothSocket: BluetoothSocket? = null

        val pairedDevices: List<BluetoothDevice>
            get() {
                // Before Android 12, check location permission for device discovery
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        Log.e("BluetoothManager", "Location permission not granted for Bluetooth device discovery.")
                        return emptyList()
                    }
                }
                // For Android 12 and above, check BLUETOOTH_CONNECT permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        Log.e("BluetoothManager", "Bluetooth connect permission not granted for accessing paired devices.")
                        return emptyList()
                    }
                }
                return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
            }

        fun connectToDevice(device: BluetoothDevice) {
            try {
                val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        Log.e("BluetoothManager", "BLUETOOTH_CONNECT permission not granted for initiating connection.")
                        return
                    }
                }

                try {
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
                    bluetoothSocket?.connect()
                } catch (e: Exception) {
                    Log.e("BluetoothManager", "Primary connect method failed for ${device.name}, trying fallback.", e)
                    // Fallback for some devices like older Android phones
                    try {
                        bluetoothSocket?.close()
                        bluetoothSocket = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType).invoke(device, 1) as BluetoothSocket
                        bluetoothSocket?.connect()
                    } catch (e2: Exception) {
                        Log.e("BluetoothManager", "Fallback connect method also failed for ${device.name}.", e2)
                        bluetoothSocket = null
                        isConnected.value = false
                        return
                    }
                }

                isConnected.value = true
                connectedDeviceName.value = device.name ?: "Unknown Device"
            } catch (e: IOException) {
                Log.e("BluetoothManager", "Failed to connect to ${device.name}: ${e.message}", e)
                isConnected.value = false
            }
        }

        fun sendText(text: String) {
            try {
                bluetoothSocket?.outputStream?.write(text.toByteArray())
            } catch (e: IOException) {
                Log.e("BluetoothManager", "Failed to send text: $text", e)
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.S)
    @Composable
    fun AppNavigation() {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = "welcome") {
            composable("welcome") {
                WelcomeScreen(onDonateClick = { navController.navigate("camera") }, onAdmin = {navController.navigate("admin")})
            }
            composable("camera") {
                CameraPreview(navController)
            }
            composable("beneficiary") {
                BeneficiaryScreen(bluetoothManager = bluetoothManager) { navController.navigate("confirmation") }
            }
            composable("confirmation") {
                ConfirmationScreen(onSubmit = {navController.navigate("welcome")})
            }
            composable("admin") {
                AdminScreen(bluetoothManager = bluetoothManager, onSubmit = {navController.navigate("welcome")})
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun updateDateAcceptance(inputDateString: String, detectedDateString: String, context: Context): Boolean {
        if (inputDateString.isEmpty() || detectedDateString.isEmpty()) {
            return false
        }
        return try {
            val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val detectedFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

            val inputDate = LocalDate.parse(inputDateString, inputFormatter)
            val detectedDate = LocalDate.parse(detectedDateString, detectedFormatter)

            // Calculate the difference in days between the two dates
            val daysBetween = ChronoUnit.DAYS.between(inputDate, detectedDate)
            val result = daysBetween >= 7


            result  // Properly return the computed result
        } catch (e: DateTimeParseException) {
            false  // Handle parsing errors gracefully
        }
    }

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
        bluetoothManager = BluetoothManager(this)
        if (allPermissionsGranted()) {
            setContent {
                AppNavigation()
            }
        } else {
            requestPermissions()
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            REQUEST_CAMERA_PERMISSION
        )
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    @RequiresApi(Build.VERSION_CODES.O)
    @Composable
    fun CameraPreview(navController: NavHostController) {
        val context = LocalContext.current

        LaunchedEffect(inputDate, detectedDate) {
            isDateAcceptable = updateDateAcceptance(
                DateTimeFormatter.ISO_LOCAL_DATE.format(inputDate),
                detectedDate,
                context
            )
        }

        Column(modifier = Modifier
            .fillMaxSize()
            .background(Color.Green)
            .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.background(Color(0xFF388E3C), shape = RoundedCornerShape(12.dp))
            ){
                Text(
                    text = "SCAN YOUR ITEM",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraLight,
                    color = Color.White,
                    modifier = Modifier.padding(30.dp)
                )
            }

            Spacer(Modifier.height(50.dp))

            // Camera feed
            Box(
                modifier = Modifier
                    .width(300.dp)
                    .height(200.dp)
                    .padding(top = 20.dp)
            ) {
                AndroidView(
                    factory = { context ->
                        PreviewView(context).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            setupCamera(this)
                        }
                    },
                    modifier = Modifier.matchParentSize()
                )
            }

            Spacer(Modifier.height(50.dp))

            DetectedTextDisplay()

            Spacer(modifier = Modifier.weight(1f)) // This pushes the button to the bottom

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        if (!isDateAcceptable) {
                            Toast.makeText(context, "Product is nearly or already expired", Toast.LENGTH_LONG).show()
                            navController.navigate("welcome")
                        } else {
                            Toast.makeText(context, "Product Accepted", Toast.LENGTH_LONG).show()
                            navController.navigate("beneficiary")
                        }
                    },
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("Check and Proceed")
                }
            }
        }
    }

    @Composable
    fun DetectedTextDisplay() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = detectedText.ifEmpty { "Detecting..." },
                color = Color.Black,
                fontSize = 24.sp,
                fontWeight = FontWeight.Light
            )
            if (detectedDate.isNotEmpty()) {
                Text(
                    text = "Expiry Date: $detectedDate",
                    color = Color.Red,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }



    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupCamera(viewFinder: PreviewView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (allPermissionsGranted()) {
                startCamera(viewFinder)
            } else {
                requestPermissions()
            }} else {
            // Assume permissions are granted as they are not dynamically controlled
            startCamera(viewFinder)
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

    @RequiresApi(Build.VERSION_CODES.O)
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
                        }
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

}

