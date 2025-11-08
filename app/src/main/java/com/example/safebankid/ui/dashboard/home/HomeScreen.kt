package com.example.safebankid.ui.dashboard.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// --- PESTAÑA 1: HOME (Solo Dinero) ---

@Composable
fun HomeScreen() {
    var balanceVisible by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Card de Saldo
        item {
            BalanceCard(
                balance = "10,450.50",
                isVisible = balanceVisible,
                onToggleVisibility = { balanceVisible = !balanceVisible }
            )
        }

        // Botones de Acción Rápida
        item { ActionButtonsRow() }

        // Título de Movimientos
        item {
            Text(
                text = "Movimientos Recientes",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        // Lista Falsa de Movimientos
        items(5) { index ->
            MovementItem(
                isIncome = index % 2 == 0,
                name = if (index % 2 == 0) "Plin de Ana" else "Pago en Tambo",
                amount = (index + 1) * 42.50
            )
        }
    }
}


// --- COMPOSABLES HIJOS DE HOME ---

@Composable
fun BalanceCard(balance: String, isVisible: Boolean, onToggleVisibility: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Mi Saldo",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isVisible) "S/ $balance" else "S/ ••••••",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Ocultar Saldo"
                    )
                }
            }
        }
    }
}

@Composable
fun ActionButtonsRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        ActionButton(icon = Icons.Default.Send, text = "Transferir")
        ActionButton(icon = Icons.Default.QrCodeScanner, text = "Pagar QR")
        ActionButton(icon = Icons.Default.ArrowDownward, text = "Recargar")
    }
}

@Composable
fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = { /* TODO */ },
            shape = CircleShape,
            modifier = Modifier.size(64.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(icon, contentDescription = text, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun MovementItem(isIncome: Boolean, name: String, amount: Double) {
    val color = if (isIncome) Color(0xFF008D41) else MaterialTheme.colorScheme.onBackground
    val icon = if (isIncome) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = name,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
            tint = color
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Medium)
            Text("Hoy, 02:19 PM", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Text(
            text = "${if (isIncome) "+" else "-"} S/ %.2f".format(amount),
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}