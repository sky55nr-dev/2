package com.roundwise.app.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roundwise.app.pleos.PleosBridge

class MainActivity : ComponentActivity() {
    private val vm: RoundwiseViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.pleos.startLocation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val granted = ContextCompat.checkSelfPermission(this, PleosBridge.LOCATION_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
        if (savedInstanceState == null) {
            vm.pleos.start(granted)
            if (!granted) runCatching { permissionLauncher.launch(PleosBridge.LOCATION_PERMISSION) }
        }
        setContent {
            val ui by vm.ui.collectAsStateWithLifecycle()
            val sdk by vm.pleos.status.collectAsStateWithLifecycle()
            LaunchedEffect(ui.toast) {
                ui.toast?.let {
                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                    vm.consumeToast()
                }
            }
            RoundwiseTheme { RoundwiseScreen(ui, sdk, vm) }
        }
    }
}
