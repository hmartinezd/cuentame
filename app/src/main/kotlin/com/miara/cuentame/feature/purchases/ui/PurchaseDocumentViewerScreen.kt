package com.miara.cuentame.feature.purchases.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.feature.purchases.viewmodel.PurchaseDocumentViewerState
import com.miara.cuentame.feature.purchases.viewmodel.PurchaseDocumentViewerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PurchaseDocumentViewerRoute(
    onBack: () -> Unit,
    viewModel: PurchaseDocumentViewerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PurchaseDocumentViewerScreen(
        uiState = uiState,
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseDocumentViewerScreen(
    uiState: PurchaseDocumentViewerState,
    onBack: () -> Unit
) {
    Scaffold(
        modifier = Modifier.testTag("purchase_document_viewer"),
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        when (uiState) {
                            is PurchaseDocumentViewerState.Ready -> uiState.document.displayName
                            else -> stringResource(R.string.purchase_invoice_document)
                        }
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("purchase_document_viewer_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (uiState) {
                is PurchaseDocumentViewerState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is PurchaseDocumentViewerState.NotFound -> {
                    Text(
                        text = stringResource(R.string.purchase_document_unavailable),
                        modifier = Modifier.align(Alignment.Center).testTag("purchase_document_unavailable")
                    )
                }
                is PurchaseDocumentViewerState.Error -> {
                    Text(
                        text = stringResource(R.string.error_generic),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is PurchaseDocumentViewerState.Ready -> {
                    val file = File(LocalContext.current.filesDir, uiState.document.location)
                    if (uiState.document.mimeType == "application/pdf") {
                        PdfViewer(file)
                    } else {
                        ImageViewer(file)
                    }
                }
            }
        }
    }
}

@Composable
fun PdfViewer(file: File) {
    var renderer by remember(file) { mutableStateOf<PdfRenderer?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val mutex = remember { Mutex() }

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
            } catch (e: Exception) {
                error = e
            }
        }
    }

    if (error != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.purchase_pdf_render_failure),
                modifier = Modifier.testTag("purchase_document_pdf_error")
            )
        }
    } else if (renderer != null) {
        val pageCount = renderer!!.pageCount
        if (pageCount == 0) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.purchase_pdf_empty))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("purchase_document_pdf_list"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(pageCount) { pageIndex ->
                    PdfPage(renderer!!, pageIndex, mutex)
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }

    DisposableEffect(renderer) {
        onDispose {
            renderer?.close()
        }
    }
}

@Composable
fun PdfPage(renderer: PdfRenderer, pageIndex: Int, mutex: Mutex) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<Boolean>(false) }
    
    LaunchedEffect(pageIndex) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val page = renderer.openPage(pageIndex)
                    try {
                        // Cap bitmap dimensions based on viewport and memory limits
                        val maxWidth = 2048 
                        val scale = if (page.width > maxWidth) maxWidth.toFloat() / page.width else 1.0f
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        
                        val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap = b
                    } finally {
                        page.close()
                    }
                } catch (e: Exception) {
                    error = true
                }
            }
        }
    }

    DisposableEffect(bitmap) {
        onDispose {
            // Bitmaps should be managed carefully to avoid OOM, but Compose's ImageBitmap handles this mostly.
            // If we manually created it, we should ideally recycle it if it's large.
            // bitmap?.recycle() // Be careful with recycling if Compose might still use it
        }
    }
    
    if (error) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(8.dp)
                .testTag("purchase_document_pdf_page_error_$pageIndex"),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.purchase_pdf_page_render_failure, pageIndex + 1), color = MaterialTheme.colorScheme.error)
        }
    } else if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = stringResource(R.string.purchase_document_pdf_page_desc, pageIndex + 1),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("purchase_document_pdf_page_$pageIndex"),
            contentScale = ContentScale.FillWidth
        )
    } else {
        Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun ImageViewer(file: File) {
    var hasError by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (hasError) {
            Text(
                text = stringResource(R.string.purchase_image_decode_failure),
                modifier = Modifier.testTag("purchase_document_image_error")
            )
        } else {
            SubcomposeAsyncImage(
                model = file,
                contentDescription = stringResource(R.string.purchase_invoice_document),
                modifier = Modifier.fillMaxSize().testTag("purchase_document_image"),
                contentScale = ContentScale.Fit,
                loading = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                },
                onError = {
                    hasError = true
                }
            )
        }
    }
}
