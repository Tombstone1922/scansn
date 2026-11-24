package com.tombstone.scansn.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun ScanSNApp(viewModel: ScanSNViewModel) {
    val navController = rememberNavController()
    val products by viewModel.products.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        NavHost(
            navController = navController,
            startDestination = "products",
            modifier = Modifier.padding(padding),
        ) {
            composable("products") {
                ProductsScreen(
                    products = products,
                    onAdd = {
                        viewModel.newProduct()
                        navController.navigate("product/0")
                    },
                    onEdit = {
                        viewModel.editProduct(it)
                        navController.navigate("product/$it")
                    },
                    onBarcodes = { navController.navigate("barcodes/$it") },
                    onExport = { navController.navigate("export") },
                    onUpdates = { navController.navigate("updates") },
                    onDelete = viewModel::delete,
                )
            }
            composable("device-info") { DeviceInfoScreen(viewModel = viewModel, onBack = { navController.popBackStack() }) }
            composable("updates") {
                UpdatesScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = "product/{productId}",
                arguments = listOf(navArgument("productId") { type = NavType.LongType }),
            ) {
                ProductFormScreen(
                    viewModel = viewModel,
                    products = products,
                    onBack = { navController.popBackStack() },
                    onScan = { target -> navController.navigate("scanner/${target.name}") },
                    onDeviceInfo = { navController.navigate("device-info") },
                )
            }
            composable("scanner/{target}") { entry ->
                val target = ScanTarget.valueOf(entry.arguments?.getString("target") ?: "SN")
                ScannerScreen(
                    target = target,
                    onClose = { navController.popBackStack() },
                    onCode = {
                        viewModel.applyScan(target, it)
                        navController.popBackStack()
                    },
                )
            }
            composable(
                route = "barcodes/{productId}",
                arguments = listOf(navArgument("productId") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("productId") ?: 0L
                products.firstOrNull { it.id == id }?.let { product ->
                    BarcodeScreen(product = product, onBack = { navController.popBackStack() })
                }
            }
            composable("export") {
                ExportScreen(
                    products = products,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
