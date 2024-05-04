package com.example.donoapp

import android.bluetooth.*
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.navigation.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.util.*
import androidx.compose.runtime.Composable


@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "welcome") {
        composable("welcome") {
            WelcomeScreen(onDonateClick = { navController.navigate("scanner") })
        }
        composable("scanner") {
            Scanner(onNext = { navController.navigate("form") })  // Assuming `onNext` triggers the next screen
        }
        composable("form") {
            FormScreen(onSubmit = {  // Assuming `onSubmit` handles form submission
                navController.navigate("thankYou")
            })
        }
        composable("thankYou") {
            ThankYouScreen(onFinish = {  // Assuming `onFinish` is the action for 'Done'
                navController.popBackStack("welcome", inclusive = false)
            })
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppNavigation()
        }
    }
}
