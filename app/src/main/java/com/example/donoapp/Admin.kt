package com.example.donoapp

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.example.donoapp.Scanner
import java.time.LocalDate
import java.time.format.DateTimeFormatter


@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AdminScreen(
    bluetoothManager: Scanner.BluetoothManager,
    onSubmit: () -> Unit
) {
    val context = LocalContext.current
    var inputDate by remember { mutableStateOf(LocalDate.now()) }
    val isConnected by remember { bluetoothManager.isConnected }
    var showDeviceListDialog by remember { mutableStateOf(false) }

    var beneficiaryName by remember { mutableStateOf("") }

    val requestBluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            showDeviceListDialog = true
        } else {
            Toast.makeText(context, "Bluetooth permissions are required!", Toast.LENGTH_LONG).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Check if all permissions are granted or do something accordingly
        if (permissions.all { it.value }) {
            Log.d("Permissions", "All permissions granted")
        } else {
            Log.d("Permissions", "Not all permissions granted")
        }
    }

    LaunchedEffect(key1 = true) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                )
            }
            else -> {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                )
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFE8F5E9) // Light green background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Configurations",
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.DarkGray // Darker green
            )

            Spacer(Modifier.height(20.dp))

            if (isConnected) {
                Text(
                    "Connected to: " + bluetoothManager.connectedDeviceName.value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1BB500)
                )
            } else {
                Button(
                    onClick = { showDeviceListDialog = true },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Blue), // Light green
                    modifier = Modifier.widthIn(min = 125.dp, max = 512.dp), // Control width for tablet sizes
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Connect", fontSize = 18.sp, color = Color.White)

                    Spacer(Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Filled.Bluetooth, // Using Material Icons for settings
                        contentDescription = "Admin Settings",
                        tint = Color.White
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            if (!isConnected) {
                Text(
                    "No device connected. Pair and connect your device to proceed.",
                    color = Color.Red,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }

            // Date input field
            DateInputField(date = inputDate, onDateChange = { inputDate = it })

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = beneficiaryName,
                onValueChange = { beneficiaryName = it },
                label = { Text("Beneficiary Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    if (isConnected) {
                        bluetoothManager.sendText("Beneficiary: ${if (beneficiaryName.isBlank()) "Anonymous" else beneficiaryName}  Dispatch Date: ${DateTimeFormatter.ISO_LOCAL_DATE.format(inputDate)}")
                    }
                    onSubmit()  // This should only dismiss if needed, the function may need adjustment
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50)), // Primary green
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .padding(horizontal = 32.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Confirm", fontSize = 18.sp, color = Color.White)
            }
        }
    }

    // Device List Dialog
    if (showDeviceListDialog) {
        DeviceListDialog(bluetoothManager, onDismiss = { showDeviceListDialog = false })
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DateInputField(date: LocalDate, onDateChange: (LocalDate) -> Unit) {
    val context = LocalContext.current
    val dateString = remember(date) { DateTimeFormatter.ISO_LOCAL_DATE.format(date) }

    OutlinedButton(
        onClick = {
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    val selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                    onDateChange(selectedDate)
                },
                date.year, date.monthValue - 1, date.dayOfMonth
            ).show()
        },
        modifier = Modifier
            .widthIn(max = 600.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text("Select Dispatch Date:", color = Color(0xFF388E3C),fontSize = 25.sp)
        Spacer(modifier = Modifier.width(15.dp))
        Text( "$dateString", color = Color.Red,fontSize = 25.sp )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DeviceListDialog(
    bluetoothManager: Scanner.BluetoothManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select a Device", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                bluetoothManager.pairedDevices.forEach { device ->
                    TextButton(
                        onClick = {
                            bluetoothManager.connectToDevice(device)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                    ) {
                        if (ActivityCompat.checkSelfPermission(
                                context,
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
                            return@TextButton
                        }
                        Text(device.name ?: "Unknown Device", modifier = Modifier.padding(4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick =  onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(8.dp),
        backgroundColor = Color(0xFFF0F4C3)
    )
}
