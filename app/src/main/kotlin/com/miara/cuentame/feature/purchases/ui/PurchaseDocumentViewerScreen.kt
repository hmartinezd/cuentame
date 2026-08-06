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
import com.miara.cuentame.R
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.feature.purchases.viewmodel.PurchaseDocumentViewerState
import com.miara.cuentame.feature.purchases.viewmodel.PurchaseDocumentViewerViewModel
import kotlinx.coroutines.Dispatchers
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
            Text(stringResource(R.string.purchase_pdf_render_failure))
        }
    } else if (renderer != null) {
        val pageCount = renderer!!.pageCount
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("purchase_document_pdf_list"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(pageCount) { pageIndex ->
                PdfPage(renderer!!, pageIndex)
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
fun PdfPage(renderer: PdfRenderer, pageIndex: Int) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(pageIndex) {
        withContext(Dispatchers.IO) {
            try {
                val page = renderer.openPage(pageIndex)
                // Render at a reasonable scale.
                // We use a fixed width for now, but in a real app we might want to adapt to screen width.
                val width = 1024
                val height = (width * page.height) / page.width
                val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap = b
                page.close()
            } catch (_: Exception) {}
        }
    }
    
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Page ${pageIndex + 1}",
            modifier = Modifier.fillMaxWidth().padding(8.dp),
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
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = file,
            contentDescription = stringResource(R.string.purchase_invoice_document),
            modifier = Modifier.fillMaxSize().testTag("purchase_document_image"),
            contentScale = ContentScale.Fit,
            onError = {
                // handle error
            }
        )
    }
}
