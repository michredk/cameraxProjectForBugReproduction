package com.example.camerabugproject

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.camerabugproject.ui.theme.CameraBugProjectTheme
import kotlinx.coroutines.delay

@Composable
fun CameraRoute(requestPerm: () -> Unit) {
    val context = LocalContext.current

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }
    var photosTaken by remember {
        mutableIntStateOf(0)
    }
    var captureBtnEnabled by remember {
        mutableStateOf(true)
    }

    CameraScreen(
        cameraController = cameraController,
        onTakePictureClicked = {
            requestPerm()
            captureBtnEnabled = false
            cameraController.takePicture(ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        super.onCaptureSuccess(image)
                        val finalBitmap = scaleCropRotateBitmap(image)
                        captureBtnEnabled = true
                        photosTaken++
                    }
                })
        },
        photosTaken = photosTaken
    )
}

@Composable
fun CameraScreen(
    cameraController: LifecycleCameraController,
    onTakePictureClicked: () -> Unit,
    photosTaken: Int
) {

    var showFlash by remember {
        mutableStateOf(false)
    }
    LaunchedEffect(photosTaken) {
        if (photosTaken > 0) {
            showFlash = true
        }
        showFlash = false
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        CameraPreview(controller = cameraController, modifier = Modifier.fillMaxSize())
        if (showFlash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            )
        }
        CameraButtons(
            30.dp,
            40.dp,
            btnBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
            pressedCaptureBackgroundColor = MaterialTheme.colorScheme.primary,
            onTakePictureClicked = onTakePictureClicked,
        )
    }
}

@Composable
private fun BoxScope.CameraButtons(
    iconSize: Dp,
    btnSize: Dp,
    btnBackgroundColor: Color,
    pressedCaptureBackgroundColor: Color,
    onTakePictureClicked: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .padding(bottom = 68.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val captureIsPressed by interactionSource.collectIsPressedAsState()
        val finalBackgroundColor =
            if (captureIsPressed) pressedCaptureBackgroundColor else btnBackgroundColor
        Column(
            modifier = Modifier.width(100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CaptureButton(
                interactionSource = interactionSource,
                btnModifier = Modifier
                    .size(btnSize + 15.dp)
                    .background(finalBackgroundColor, shape = CircleShape),
                iconModifier = Modifier.size(iconSize + 10.dp),
                onTakePictureClicked = onTakePictureClicked,
            )
        }
    }
}

@Composable
private fun CaptureButton(
    btnModifier: Modifier,
    iconModifier: Modifier,
    onTakePictureClicked: () -> Unit,
    interactionSource: MutableInteractionSource,
) {
    IconButton(
        interactionSource = interactionSource,
        modifier = btnModifier,
        onClick = {
            onTakePictureClicked()
        }) {
        Icon(
            imageVector = Icons.Default.AddCircle,
            contentDescription = "Take photo",
            modifier = iconModifier
        )
    }
}

fun scaleCropRotateBitmap(
    image: ImageProxy
): Bitmap {
    val bitmap = image.toBitmap()

    val matrix = Matrix().apply {
        postRotate(image.imageInfo.rotationDegrees.toFloat())
    }

    val targetRatio = 4000f / 2024f

    // Calculate the target dimensions, ensuring the aspect ratio is maintained and no scaling/stretching occurs
    val (targetWidth, targetHeight) = if (bitmap.width.toFloat() / bitmap.height.toFloat() > targetRatio) {
        // Width is too large, so adjust the width to match the target aspect ratio
        val adjustedWidth = (bitmap.height * targetRatio).toInt()
        adjustedWidth to bitmap.height
    } else {
        // Height is too large, so adjust the height to match the target aspect ratio
        val adjustedHeight = (bitmap.width / targetRatio).toInt()
        bitmap.width to adjustedHeight
    }

    // Ensure target dimensions are even numbers
    val finalWidth = targetWidth - targetWidth % 2
    val finalHeight = targetHeight - targetHeight % 2

    // Calculate the x and y coordinates to center the crop
    val x = (bitmap.width - finalWidth) / 2
    val y = (bitmap.height - finalHeight) / 2

    // Create the cropped bitmap centered on the original image
    val croppedBitmap = Bitmap.createBitmap(
        bitmap, x,             // X coordinate to start the crop
        y,             // Y coordinate to start the crop
        finalWidth,    // Width of the cropped image
        finalHeight,   // Height of the cropped image
        matrix, true
    )
    return croppedBitmap
}

@Composable
fun CameraPreview(controller: LifecycleCameraController, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        factory = {
            PreviewView(it).apply {
                this.controller = controller
                controller.bindToLifecycle(lifecycleOwner)
            }
        },
        modifier = modifier
    )
}