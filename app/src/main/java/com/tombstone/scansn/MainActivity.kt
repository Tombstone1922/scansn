package com.tombstone.scansn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tombstone.scansn.data.DeviceInfoRepository
import com.tombstone.scansn.data.ProductRepository
import com.tombstone.scansn.data.ScanSNDatabase
import com.tombstone.scansn.ui.ScanSNApp
import com.tombstone.scansn.ui.ScanSNViewModel
import com.tombstone.scansn.ui.theme.ScanSNTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.tombstone.scansn.data.Catalog.init(this)
        val database = ScanSNDatabase.get(this)
        val repository = ProductRepository(database.productDao())
        val deviceInfoRepository = DeviceInfoRepository(database.deviceInfoDao())
        setContent {
            ScanSNTheme {
                ScanSNApp(viewModel(factory = ScanSNViewModel.Factory(repository, deviceInfoRepository)))
            }
        }
    }
}
