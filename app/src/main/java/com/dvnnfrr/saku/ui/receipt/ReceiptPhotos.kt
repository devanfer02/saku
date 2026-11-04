package com.dvnnfrr.saku.ui.receipt

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.sakuViewModelFactory
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme

/** Opens the system camera or the photo picker for a receipt photo. */
class ReceiptPhotoLaunchers(val camera: () -> Unit, val gallery: () -> Unit)

/**
 * The camera and gallery launchers, neither needing a permission: the system camera
 * writes into a file Saku lends it, and the photo picker grants one image.
 *
 * Some devices report the photo picker as present yet have nothing to handle it (the
 * Pixel 8 API 35 emulator image, for one); the document picker is the fallback. A camera
 * closed without a photo calls [onCameraCancelled], so an empty file it left is dropped.
 */
@Composable
fun rememberReceiptPhotoLaunchers(
    newCameraUri: () -> Uri,
    onCameraCancelled: () -> Unit,
    onPhoto: (Uri) -> Unit,
): ReceiptPhotoLaunchers {
    // The URI must survive the camera activity, which can outlive this composition.
    var cameraTarget by rememberSaveable { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val target = cameraTarget
        if (taken && target != null) onPhoto(target) else onCameraCancelled()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        picked?.let(onPhoto)
    }
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        picked?.let(onPhoto)
    }
    return remember(camera, gallery, documents) {
        ReceiptPhotoLaunchers(
            camera = {
                val target = newCameraUri()
                cameraTarget = target
                // No camera app at all: nothing opens and nothing changes.
                try { camera.launch(target) } catch (_: ActivityNotFoundException) { }
            },
            gallery = {
                try {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } catch (_: ActivityNotFoundException) {
                    documents.launch(arrayOf("image/*"))
                }
            },
        )
    }
}

/** The two-way choice under Scan and Add next part. No receipt-or-screenshot choice: one reading handles both. */
@Composable
fun ReceiptPhotoMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
) {
    val colors = SakuTheme.colors
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, containerColor = colors.surface) {
        Text(
            text = stringResource(R.string.receipt_both_kinds),
            style = SakuTheme.typography.caption,
            color = colors.ink2,
            modifier = Modifier.padding(horizontal = SakuTheme.spacing.md, vertical = SakuTheme.spacing.xs),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.receipt_camera), color = colors.ink) },
            leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = colors.ink2) },
            onClick = { onDismiss(); onCamera() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.receipt_gallery), color = colors.ink) },
            leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = colors.ink2) },
            onClick = { onDismiss(); onGallery() },
        )
    }
}

/** Lends the camera a file to write into, and drops it when no photo came back. */
class ReceiptCameraViewModel(
    val newCameraUri: () -> Uri,
    val discard: () -> Unit,
) : ViewModel() {
    companion object {
        val Factory = sakuViewModelFactory { ReceiptCameraViewModel(receipts::newCameraUri, receipts::clearCache) }
    }
}

/**
 * The Scan button on Trans, a small FAB beside +, so it sits in thumb reach (owner's
 * option B). A picked or taken photo goes to [onPhoto]; backing out changes nothing.
 */
@Composable
fun ReceiptScanButton(
    onPhoto: (Uri) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReceiptCameraViewModel = viewModel(factory = ReceiptCameraViewModel.Factory),
) {
    val launchers = rememberReceiptPhotoLaunchers(viewModel.newCameraUri, viewModel.discard, onPhoto)
    ReceiptScanButton(onCamera = launchers.camera, onGallery = launchers.gallery, modifier = modifier)
}

@Composable
fun ReceiptScanButton(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier) {
        SmallFloatingActionButton(
            onClick = { menuOpen = true },
            containerColor = SakuTheme.colors.surfaceAlt,
            contentColor = SakuTheme.colors.accent,
        ) {
            Icon(Icons.Outlined.DocumentScanner, contentDescription = stringResource(R.string.receipt_scan))
        }
        ReceiptPhotoMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            onCamera = onCamera,
            onGallery = onGallery,
        )
    }
}

@SakuPreview
@Composable
private fun ReceiptScanButtonPreview() {
    SakuPreviewSurface { ReceiptScanButton(onCamera = {}, onGallery = {}) }
}
