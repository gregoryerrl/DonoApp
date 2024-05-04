package com.example.donoapp

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*

class Beneficiary : ComponentActivity() {
    @Composable
    fun FormScreen(onSubmission: (String, String) -> Unit) {
        var beneficiary by remember { mutableStateOf("") }
        var fullName by remember { mutableStateOf("") }

        Column {
            TextField(value = beneficiary, onValueChange = { beneficiary = it }, label = { Text("Beneficiary") })
            TextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Your Full Name") })
            Button(onClick = { onSubmission(beneficiary, fullName) }) {
                Text("Submit")
            }
        }
    }

}