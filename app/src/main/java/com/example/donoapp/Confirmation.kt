package com.example.donoapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle

@Composable
fun ConfirmationScreen(onSubmit: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE8F5E9))  // Light green background
            .padding(32.dp),  // Provide ample padding around the box
        contentAlignment = Alignment.Center  // Center the contents of the box
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Confirmation",
                modifier = Modifier.size(100.dp),
                tint = Color(0xFF4CAF50)  // A deep green tint for the icon
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Please put your items in the proper slot. Thank you!",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF388E3C)  // Text color is a shade of green to fit the theme
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onSubmit,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF81C784))  // Button with a theme-matching color
            ) {
                Text("Done", color = Color.White, fontSize = 18.sp)
            }
        }
    }
}
