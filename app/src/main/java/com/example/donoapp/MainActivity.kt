package com.example.donoapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.compose.foundation.border
import androidx.compose.material.Text
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.camera.core.ImageAnalysis
import androidx.compose.foundation.layout.padding
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import androidx.compose.foundation.background
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.util.*
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.ui.text.font.FontWeight

class MainActivity : ComponentActivity() {
    private lateinit var ocrProcessor: OCRProcessor
    private lateinit var cameraExecutor: ExecutorService

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                setupCamera()
            } else {
                showManualPermissionSettingsDialog()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ocrProcessor = OCRProcessor()
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            setupCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    @Composable
    fun CameraPreviewWithOverlay(onViewFinderAvailable: (PreviewView) -> Unit) {
        Box(modifier = Modifier
            .fillMaxSize()
            .background(Color.Green)) {
            // The text should be placed above the box
            Text(
                text = "PLACE THE ITEM IN THE CAMERA",
                fontSize = 36.sp,
                fontWeight = FontWeight.Light,
                color = Color.Black,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 70.dp) // Adjust this value as needed
            )
            // Create a box that will contain the camera feed
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(300.dp) // Width of the camera feed and green border
                    .height(200.dp) // Height of the camera feed and green border
                    .border(2.dp, Color.Green)
            ) {
                // Camera preview is placed inside this Box
                AndroidView(
                    factory = { context ->
                        PreviewView(context).apply {
                            scaleType = PreviewView.ScaleType.FIT_CENTER
                            onViewFinderAvailable(this)
                        }
                    },
                    modifier = Modifier.matchParentSize() // Match the size of the enclosing box
                )
            }
        }
    }


    private fun setupCamera() {
        setContent {
            CameraPreviewWithOverlay { viewFinder ->
                startCamera(viewFinder)
            }
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
                // Send detected text to Arduino
                // Update your UI here. This is a simplified example using Toast.
                runOnUiThread {
                    Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(applicationContext, "No image captured", Toast.LENGTH_SHORT).show()
        }
        imageProxy.close() // Always close the imageProxy after use.
    }



}

