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
import com.example.safebankid.ui.dashboard.home.HomeScreen
import com.example.safebankid.ui.dashboard.security.SecurityScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    // 1. Recibe el ViewModel
    viewModel: DashboardViewModel = viewModel()
) {
    var selectedTab by rememberSaveable { mutableStateOf("Home") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SolesPay", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notificaciones")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = selectedTab == "Home",
                    onClick = { selectedTab = "Home" }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Security, contentDescription = "Seguridad") },
                    label = { Text("Seguridad") },
                    selected = selectedTab == "Seguridad",
                    onClick = { selectedTab = "Seguridad" }
                )
            }
        }
    ) { paddingValues ->

        // 2. Contenido modularizado
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                "Home" -> HomeScreen()
                "Seguridad" -> SecurityScreen(viewModel = viewModel) // 3. Pasa el ViewModel
            }
        }
    }
}