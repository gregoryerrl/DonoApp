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
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.res.painterResource

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun WelcomeScreen(
    onDonateClick: () -> Unit,
    onAdmin: () -> Unit,
    onClearDetectedDate: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFDCFFF3))  // Light green background for a fresh look
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Admin button with a gear icon at the top right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onAdmin,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Yellow),
                    modifier = Modifier
                        .widthIn(max = 200.dp)  // Max width for better control on tablets
                        .padding(horizontal = 32.dp)
                ) {Text("Admin", fontSize = 15.sp, color = Color.DarkGray)
                    Icon(
                        imageVector = Icons.Filled.Settings, // Using Material Icons for settings
                        contentDescription = "Admin Settings",
                        tint = Color.DarkGray
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // App Logo and Welcome Texts
            Image(
                painter = painterResource(id = R.drawable.logo), // Your app's logo
                contentDescription = "App Logo",
                modifier = Modifier.size(150.dp)
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "WELCOME TO DONOAPP",
                fontSize = 50.sp,
                fontWeight = FontWeight.ExtraLight,
                color = Color(0xFF388E3C)  // Deep green
            )

            Spacer(Modifier.height(80.dp))

            Text(
                "Would you like to scan and donate?",
                fontSize = 18.sp,
                color = Color.DarkGray
            )

            Spacer(Modifier.height(24.dp))

            // Donate Now Button
            Button(
                onClick = {onClearDetectedDate() // Call the function to clear detectedDate
                    onDonateClick()},
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF388E3C)),
                modifier = Modifier
                    .widthIn(max = 300.dp)  // Max width for better control on tablets
                    .padding(horizontal = 32.dp)
            ) {
                Text("Donate Now", fontSize = 18.sp,
                    fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}