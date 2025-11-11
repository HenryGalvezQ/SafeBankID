package com.example.safebankid.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.safebankid.ui.dashboard.home.HomeScreen
import com.example.safebankid.ui.dashboard.security.SecurityScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,                 // 👈 lo recibimos aquí
    viewModel: DashboardViewModel = viewModel()
) {
    var selectedTab by rememberSaveable { mutableStateOf("Home") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SafeBank ID", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { /* ... */ }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notificaciones")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Home") },
                    selected = selectedTab == "Home",
                    onClick = { selectedTab = "Home" }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Security, null) },
                    label = { Text("Seguridad") },
                    selected = selectedTab == "Seguridad",
                    onClick = { selectedTab = "Seguridad" }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (selectedTab) {
                "Home" -> HomeScreen()
                "Seguridad" -> SecurityScreen(
                    navController = navController, // 👈 aquí lo pasamos
                    viewModel = viewModel
                )
            }
        }
    }
}
