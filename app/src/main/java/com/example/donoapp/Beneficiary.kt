package com.example.donoapp

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun BeneficiaryScreen(bluetoothManager: Scanner.BluetoothManager, onSubmit: () -> Unit) {
    var donorName by remember { mutableStateOf("") }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFFE8F5E9)) // Use a lighter green to make it less intense
        .padding(32.dp)) {  // Increased padding for better framing
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),  // Local padding for the column
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Enter Donor Details",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF388E3C) // Deep green for the text
            )
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedTextField(
                value = donorName,
                onValueChange = { donorName = it },
                label = { Text("Donor Name (Optional)") },
                singleLine = true,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color(0xFF4CAF50),
                    unfocusedBorderColor = Color(0xFFC8E6C9)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 50.dp)  // Centering the text field better
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    val nameToSend = if (donorName.isBlank()) "Anonymous" else donorName
                    if (bluetoothManager.isConnected.value) {
                        bluetoothManager.sendText("Donor: $nameToSend")
                    }
                    onSubmit()
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF81C784)),
                modifier = Modifier
                    .fillMaxWidth(0.5f)  // 50% width to not make it too wide
                    .padding(horizontal = 50.dp)  // Add padding to center the button
            ) {
                Text("Submit Donation", fontSize = 18.sp, color = Color.White)
            }
        }
    }
}
