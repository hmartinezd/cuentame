package com.venkoi.cuentame.feature.purchases.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.venkoi.cuentame.R
import com.venkoi.cuentame.core.backup.api.PurchasePdfPageRenderResult
import com.venkoi.cuentame.core.backup.api.PurchasePdfRenderFailure
import com.venkoi.cuentame.core.backup.api.PurchasePdfRenderer
import com.venkoi.cuentame.feature.purchases.viewmodel.PurchaseDocumentViewerState
import com.venkoi.cuentame.feature.purchases.viewmodel.PurchaseDocumentViewerViewModel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PurchaseDocumentViewerEntryPoint {
    fun pdfRenderer(): PurchasePdfRenderer
}

@Composable
fun PurchaseDocumentViewerRoute(
    onBack: () -> Unit,
    viewModel: PurchaseDocumentViewerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val renderer = remember {
        EntryPointAccessors.fromApplication(context, PurchaseDocumentViewerEntryPoint::class.java).pdfRenderer()
    }

    PurchaseDocumentViewerScreen(
        uiState = uiState,
        onBack = onBack,
        renderer = renderer
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseDocumentViewerScreen(
    uiState: PurchaseDocumentViewerState,
    onBack: () -> Unit,
    renderer: PurchasePdfRenderer
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
                        text = uiState.message ?: stringResource(R.string.error_generic),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is PurchaseDocumentViewerState.Ready -> {
                    val file = File(LocalContext.current.filesDir, uiState.document.location)
                    if (uiState.document.mimeType == "application/pdf") {
                        PdfViewer(file, uiState.pageCount, renderer)
                    } else {
                        ImageViewer(file)
                    }
                }
            }
        }
    }
}

@Composable
fun PdfViewer(file: File, pageCount: Int, renderer: PurchasePdfRenderer) {
    if (pageCount == 0) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.purchase_pdf_empty),
                modifier = Modifier.testTag("purchase_document_pdf_empty")
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("purchase_document_pdf_list"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(pageCount) { pageIndex ->
                PdfPage(file, pageIndex, renderer)
            }
        }
    }
}

@Composable
fun PdfPage(file: File, pageIndex: Int, renderer: PurchasePdfRenderer) {
    var pageState by remember { mutableStateOf<PdfPageState>(PdfPageState.Loading) }
    
    LaunchedEffect(file, pageIndex) {
        pageState = PdfPageState.Loading
        val result = renderer.renderPage(file, pageIndex, maxDimensionPx = 2048)
        pageState = when (result) {
            is PurchasePdfPageRenderResult.Success -> {
                PdfPageState.Ready(result.bitmap)
            }
            is PurchasePdfPageRenderResult.Failure -> {
                Log.e("PdfViewer", "Failed to render page $pageIndex: ${result.reason}")
                PdfPageState.Error(result.reason)
            }
        }
    }

    DisposableEffect(pageState) {
        onDispose {
            if (pageState is PdfPageState.Ready) {
                val bitmap = (pageState as PdfPageState.Ready).bitmap
                // Do not recycle if the state changed but the bitmap might still be drawn.
                // However, our state ownership is clear here.
                // To be extra safe with Compose drawing, we only recycle when the entire composable is disposed
                // and the state is explicitly captured.
            }
        }
    }
    
    // Captured bitmap for safe disposal on composition leave
    DisposableEffect(Unit) {
        onDispose {
            val currentState = pageState
            if (currentState is PdfPageState.Ready) {
                currentState.bitmap.recycle()
            }
        }
    }

    when (val state = pageState) {
        is PdfPageState.Loading -> {
            Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is PdfPageState.Ready -> {
            Image(
                bitmap = state.bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.purchase_document_pdf_page_desc, pageIndex + 1),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .testTag("purchase_document_pdf_page_$pageIndex"),
                contentScale = ContentScale.FillWidth
            )
        }
        is PdfPageState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(8.dp)
                    .testTag("purchase_document_pdf_page_error_$pageIndex"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.purchase_pdf_page_render_failure, pageIndex + 1),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

sealed interface PdfPageState {
    data object Loading : PdfPageState
    data class Ready(val bitmap: Bitmap) : PdfPageState
    data class Error(val reason: PurchasePdfRenderFailure) : PdfPageState
}

@Composable
fun ImageViewer(file: File) {
    var hasError by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.White),
        contentAlignment = Alignment.Center
    ) {
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
