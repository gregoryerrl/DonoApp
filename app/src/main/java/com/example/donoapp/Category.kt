package com.example.donoapp

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiFoodBeverage
import androidx.compose.material.icons.filled.RamenDining
import androidx.compose.material.icons.filled.Sanitizer
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CategoryScreen(bluetoothManager: Scanner.BluetoothManager, navController: NavHostController) {
    var selectedCategory by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Select a Category",
            fontSize = 35.sp,
            fontWeight = FontWeight.ExtraLight,
            modifier = Modifier.padding(16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        CategoryButton(
            text = "Canned Products",
            icon = Icons.Filled.SetMeal,
            onClick = {
                selectedCategory = "Canned Products"
                navController.navigate("camera")
                if (bluetoothManager.isConnected.value) {
                    bluetoothManager.sendText("Category: $selectedCategory")
                }
            }
        )

        CategoryButton(
            text = "Instant Noodles",
            icon = Icons.Filled.RamenDining,
            onClick = {
                selectedCategory = "Instant Noodles"
                navController.navigate("camera")
                if (bluetoothManager.isConnected.value) {
                    bluetoothManager.sendText("Category: $selectedCategory")
                }
            }
        )

        CategoryButton(
            text = "Hygiene Products",
            icon = Icons.Filled.Sanitizer,
            onClick = {
                selectedCategory = "Hygiene Products"
                navController.navigate("camera")
                if (bluetoothManager.isConnected.value) {
                    bluetoothManager.sendText("Category: $selectedCategory")
                }
            }
        )
    }
}

@Composable
fun CategoryButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .widthIn(max = 600.dp)
            .padding(vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF388E3C))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(50.dp).padding(end = 8.dp)
        )
        Text(text, color = Color.White)
    }
}
