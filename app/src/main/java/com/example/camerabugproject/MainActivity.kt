package com.example.camerabugproject

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.camerabugproject.ui.theme.CameraBugProjectTheme
import android.Manifest


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        this.actionBar?.hide()

        val permissionViewModel by viewModels<PermissionViewModel>()
        val dialogQueue = permissionViewModel.visiblePermissionDialogQueue
        val permissionsToRequest = permissionViewModel.photosPermissionsToRequest

        permissionViewModel.setAllPermissionsGranted(permissionsToRequest.fold(true) { acc, permission ->
            acc && isPermissionGranted(this, permission)
        })

        setContent {
            CameraBugProjectTheme {
                val multiplePermissionResultLauncher =
                    managedActivityResultLauncher(permissionViewModel)
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)){
                        CameraRoute(requestPerm = {
                            multiplePermissionResultLauncher.launch(permissionsToRequest)
                        })
                        DisplayPermissionDialogs(
                            dialogQueue, permissionViewModel, multiplePermissionResultLauncher
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun managedActivityResultLauncher(permissionViewModel: PermissionViewModel): ManagedActivityResultLauncher<Array<String>, Map<String, @JvmSuppressWildcards Boolean>> {
        val multiplePermissionResultLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
            onResult = { perms ->
                permissionViewModel.photosPermissionsToRequest.forEach { permission ->
                    permissionViewModel.onPermissionResult(
                        permission = permission, isGranted = perms[permission] == true
                    )
                }
            })
        return multiplePermissionResultLauncher
    }

    @Composable
    private fun DisplayPermissionDialogs(
        dialogQueue: SnapshotStateList<String>,
        permissionViewModel: PermissionViewModel,
        multiplePermissionResultLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, @JvmSuppressWildcards Boolean>>
    ) {
        dialogQueue.reversed().forEach { permission ->
            PermissionDialog(
                permissionTextProvider = when (permission) {
                    android.Manifest.permission.CAMERA -> {
                        CameraPermissionTextProvider()
                    }

                    else -> return@forEach
                },
                isPermanentlyDeclined = !shouldShowRequestPermissionRationale(permission),
                onDismiss = permissionViewModel::dismissDialog,
                onOkClick = {
                    permissionViewModel.dismissDialog()
                    multiplePermissionResultLauncher.launch(
                        arrayOf(permission)
                    )
                },
                onGoToAppSettingsClick = ::openAppSettings
            )
        }
    }

}

private fun isPermissionGranted(context: Context, permission: String) =
    (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)

private fun Activity.openAppSettings() {
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)
    ).also(::startActivity)
}

class PermissionViewModel : ViewModel() {

    var photosPermissionsToRequest =
        arrayOf(
            Manifest.permission.CAMERA,
        )

    private val permissionsMap = mutableMapOf(
        Pair(Manifest.permission.CAMERA, false),
    )
    private val _allPermissionsGranted = MutableStateFlow(false)
    val allPermissionsGranted = _allPermissionsGranted.asStateFlow()
    val visiblePermissionDialogQueue = mutableStateListOf<String>()

    init {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            photosPermissionsToRequest = arrayOf(
                Manifest.permission.CAMERA,
            )
        }
    }

    fun dismissDialog() {
        visiblePermissionDialogQueue.removeRange(0,0)
    }

    fun onPermissionResult(
        permission: String, isGranted: Boolean
    ) {
        if (!isGranted && !visiblePermissionDialogQueue.contains(permission)) {
            visiblePermissionDialogQueue.add(permission)
        } else {
            permissionsMap[permission] = isGranted
            _allPermissionsGranted.value = permissionsMap.values.all { it }
        }
    }

    fun setAllPermissionsGranted(value: Boolean) = run { _allPermissionsGranted.value = value }
}