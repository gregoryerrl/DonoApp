package com.example.donoapp

import androidx.activity.ComponentActivity
import androidx.compose.material.*
import androidx.compose.runtime.*
class Confirmation : ComponentActivity() {
    @Composable
    fun ThankYouScreen(onDone: () -> Unit) {
        Text("Thank you for your donation!")
        Button(onClick = onDone) {
            Text("Done")
        }
    }

}