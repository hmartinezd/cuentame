package com.miara.cuentame.feature.onboarding.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miara.cuentame.R
import com.miara.cuentame.feature.onboarding.model.OnboardingItemUiModel
import com.miara.cuentame.feature.onboarding.model.OnboardingStep
import com.miara.cuentame.feature.onboarding.viewmodel.OnboardingEvent
import com.miara.cuentame.feature.onboarding.viewmodel.OnboardingUiState
import com.miara.cuentame.feature.onboarding.viewmodel.OnboardingViewModel

@Composable
fun OnboardingRoute(
    onOnboardingFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is OnboardingEvent.NavigateToHome -> onOnboardingFinished()
                is OnboardingEvent.ShowError -> {
                    snackbarHostState.showSnackbar(event.error.message ?: context.getString(R.string.error_generic))
                }
            }
        }
    }

    val suggestedAreaLabels = uiState.areas.filter { it.isSuggested }.associate { it.templateKey!! to stringResource(it.labelResId!!) }
    val suggestedCategoryLabels = uiState.categories.filter { it.isSuggested }.associate { it.templateKey!! to stringResource(it.labelResId!!) }

    OnboardingScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onRestaurantNameChanged = viewModel::onRestaurantNameChanged,
        onCurrencySelected = viewModel::onCurrencySelected,
        onLocaleSelected = viewModel::onLocaleSelected,
        onToggleItem = viewModel::onToggleItem,
        onAddItem = viewModel::onAddItem,
        onRemoveItem = viewModel::onRemoveItem,
        onRenameItem = viewModel::onRenameCustomItem,
        onMoveItem = viewModel::onMoveItem,
        onNext = { viewModel.onNext(suggestedAreaLabels, suggestedCategoryLabels) },
        onBack = viewModel::onBack,
        onEditStep = viewModel::onEditStep,
        onFinish = { 
            viewModel.onCompleteClicked(suggestedAreaLabels, suggestedCategoryLabels)
        }
    )
}

@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    snackbarHostState: SnackbarHostState,
    onRestaurantNameChanged: (String) -> Unit,
    onCurrencySelected: (String) -> Unit,
    onLocaleSelected: (String) -> Unit,
    onToggleItem: (Boolean, String) -> Unit,
    onAddItem: (Boolean, String) -> Unit,
    onRemoveItem: (Boolean, String) -> Unit,
    onRenameItem: (Boolean, String, String) -> Unit,
    onMoveItem: (Boolean, String, Boolean) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onEditStep: (OnboardingStep) -> Unit,
    onFinish: () -> Unit
) {
    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        modifier = Modifier.testTag("onboarding_screen_root"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            OnboardingBottomBar(
                currentStep = uiState.currentStep,
                canBack = uiState.canGoBack,
                canNext = uiState.canContinue,
                isSubmitting = uiState.isSubmitting,
                onBack = onBack,
                onNext = onNext,
                onFinish = onFinish
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                AnimatedContent(targetState = uiState.currentStep, label = "OnboardingStep") { step ->
                    when (step) {
                        OnboardingStep.WELCOME -> WelcomeStep(onSetupAction = onNext)
                        OnboardingStep.RESTAURANT -> RestaurantStep(
                            name = uiState.restaurantName,
                            currency = uiState.currencyCode,
                            locale = uiState.localeTag,
                            errorResId = uiState.validationErrors["restaurantName"],
                            onNameChanged = onRestaurantNameChanged,
                            onCurrencySelected = onCurrencySelected,
                            onLocaleSelected = onLocaleSelected
                        )
                        OnboardingStep.AREAS -> ItemsStep(
                            isArea = true,
                            titleResId = R.string.onboarding_areas_title,
                            descResId = R.string.onboarding_areas_desc,
                            addLabelResId = R.string.onboarding_add_area,
                            items = uiState.areas,
                            errorResId = uiState.validationErrors["areas"],
                            onToggle = onToggleItem,
                            onAdd = onAddItem,
                            onRemove = onRemoveItem,
                            onRename = onRenameItem,
                            onMove = onMoveItem
                        )
                        OnboardingStep.CATEGORIES -> ItemsStep(
                            isArea = false,
                            titleResId = R.string.onboarding_categories_title,
                            descResId = R.string.onboarding_categories_desc,
                            addLabelResId = R.string.onboarding_add_category,
                            items = uiState.categories,
                            errorResId = uiState.validationErrors["categories"],
                            onToggle = onToggleItem,
                            onAdd = onAddItem,
                            onRemove = onRemoveItem,
                            onRename = onRenameItem,
                            onMove = onMoveItem
                        )
                        OnboardingStep.REVIEW -> ReviewStep(uiState, onEditStep)
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeStep(onSetupAction: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag("onboarding_welcome_content")
    ) {
        Text(text = stringResource(R.string.onboarding_welcome_title), style = MaterialTheme.typography.headlineMedium)
        Text(text = stringResource(R.string.onboarding_welcome_desc), style = MaterialTheme.typography.bodyLarge)
        Text(text = stringResource(R.string.onboarding_welcome_note), style = MaterialTheme.typography.bodySmall)
        
        Button(
            onClick = onSetupAction,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("onboarding_setup_button")
        ) {
            Text(text = stringResource(R.string.onboarding_setup_action))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantStep(
    name: String,
    currency: String,
    locale: String,
    errorResId: Int?,
    onNameChanged: (String) -> Unit,
    onCurrencySelected: (String) -> Unit,
    onLocaleSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = stringResource(R.string.onboarding_restaurant_title), style = MaterialTheme.typography.headlineSmall)
        
        OutlinedTextField(
            value = name,
            onValueChange = onNameChanged,
            label = { Text(stringResource(R.string.onboarding_field_name)) },
            modifier = Modifier.fillMaxWidth().testTag("onboarding_restaurant_name"),
            singleLine = true,
            isError = errorResId != null,
            supportingText = errorResId?.let { { Text(stringResource(it)) } },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            )
        )

        // Currency Selector
        var expanded by remember { mutableStateOf(false) }
        val currencies = listOf("USD", "EUR", "GBP", "CAD", "MXN")
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = currency,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.onboarding_field_currency)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                currencies.forEach { code ->
                    DropdownMenuItem(
                        text = { Text(code) },
                        onClick = {
                            onCurrencySelected(code)
                            expanded = false
                        }
                    )
                }
            }
        }

        // Language Selector
        var langExpanded by remember { mutableStateOf(false) }
        val locales = mapOf("en-US" to stringResource(R.string.lang_en), "es-US" to stringResource(R.string.lang_es))
        ExposedDropdownMenuBox(
            expanded = langExpanded,
            onExpandedChange = { langExpanded = !langExpanded }
        ) {
            OutlinedTextField(
                value = locales[locale] ?: locale,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.onboarding_field_language)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = langExpanded,
                onDismissRequest = { langExpanded = false }
            ) {
                locales.forEach { (tag, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onLocaleSelected(tag)
                            langExpanded = false
                        }
                    )
                }
            }
        }
        
        Text(text = stringResource(R.string.onboarding_currency_note), style = MaterialTheme.typography.bodySmall)
        Text(text = stringResource(R.string.onboarding_language_note), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ItemsStep(
    isArea: Boolean,
    titleResId: Int,
    descResId: Int,
    addLabelResId: Int,
    items: List<OnboardingItemUiModel>,
    errorResId: Int?,
    onToggle: (Boolean, String) -> Unit,
    onAdd: (Boolean, String) -> Unit,
    onRemove: (Boolean, String) -> Unit,
    onRename: (Boolean, String, String) -> Unit,
    onMove: (Boolean, String, Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(titleResId),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.testTag(if (isArea) "onboarding_areas_title" else "onboarding_categories_title")
        )
        Text(text = stringResource(descResId))

        if (errorResId != null) {
            Text(text = stringResource(errorResId), color = MaterialTheme.colorScheme.error)
        }

        // Input for adding custom items
        var customName by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = customName,
                onValueChange = { customName = it },
                label = { Text(stringResource(addLabelResId)) },
                modifier = Modifier.weight(1f).testTag(if (isArea) "onboarding_add_area_input" else "onboarding_add_category_input"),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                )
            )
            Button(
                onClick = { 
                    onAdd(isArea, customName)
                    customName = ""
                },
                modifier = Modifier.testTag(if (isArea) "onboarding_add_area_button" else "onboarding_add_category_button")
            ) {
                Text(stringResource(R.string.action_add))
            }
        }

        // Combined list of suggestions and custom items
        items.forEachIndexed { index, item ->
            ListItem(
                headlineContent = {
                    if (item.isSuggested) {
                        Text(text = stringResource(item.labelResId!!))
                    } else {
                        OutlinedTextField(
                            value = item.customName ?: "",
                            onValueChange = { onRename(isArea, item.id, it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                leadingContent = {
                    Checkbox(checked = item.isSelected, onCheckedChange = { onToggle(isArea, item.id) })
                },
                trailingContent = {
                    Row {
                        IconButton(onClick = { onMove(isArea, item.id, true) }, enabled = index > 0) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.move_up, if (item.isSuggested) stringResource(item.labelResId!!) else item.customName ?: ""))
                        }
                        IconButton(onClick = { onMove(isArea, item.id, false) }, enabled = index < items.size - 1) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.move_down, if (item.isSuggested) stringResource(item.labelResId!!) else item.customName ?: ""))
                        }
                        if (!item.isSuggested) {
                            IconButton(onClick = { onRemove(isArea, item.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_remove))
                            }
                        }
                    }
                }
            )
            HorizontalDivider()
        }
        
        if (!isArea) {
            Text(text = stringResource(R.string.onboarding_category_recommend), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ReviewStep(state: OnboardingUiState, onEditStep: (OnboardingStep) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = stringResource(R.string.onboarding_review_title), style = MaterialTheme.typography.headlineSmall)
        
        val locales = mapOf("en-US" to stringResource(R.string.lang_en), "es-US" to stringResource(R.string.lang_es))
        
        ReviewSection(
            label = stringResource(R.string.onboarding_restaurant_title),
            onEdit = { onEditStep(OnboardingStep.RESTAURANT) }
        ) {
            ReviewItem(label = stringResource(R.string.onboarding_field_name), value = state.restaurantName)
            ReviewItem(label = stringResource(R.string.onboarding_field_currency), value = state.currencyCode)
            ReviewItem(label = stringResource(R.string.onboarding_field_language), value = locales[state.localeTag] ?: state.localeTag)
        }

        ReviewSection(
            label = stringResource(R.string.settings_areas),
            onEdit = { onEditStep(OnboardingStep.AREAS) }
        ) {
            val areaNames = state.areas.filter { it.isSelected }.map { 
                if (it.isSuggested) stringResource(it.labelResId!!) else it.customName!!
            }
            Text(text = areaNames.joinToString(", "))
        }

        ReviewSection(
            label = stringResource(R.string.settings_categories),
            onEdit = { onEditStep(OnboardingStep.CATEGORIES) }
        ) {
            val catNames = state.categories.filter { it.isSelected }.map { 
                if (it.isSuggested) stringResource(it.labelResId!!) else it.customName!!
            }
            if (catNames.isEmpty()) {
                Text(text = stringResource(R.string.state_empty_desc))
            } else {
                Text(text = catNames.joinToString(", "))
            }
        }
    }
}

@Composable
fun ReviewSection(label: String, onEdit: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
            }
        }
        content()
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun ReviewItem(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun OnboardingBottomBar(
    currentStep: OnboardingStep,
    canBack: Boolean,
    canNext: Boolean,
    isSubmitting: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (canBack) {
            OutlinedButton(onClick = onBack, enabled = !isSubmitting) {
                Text(stringResource(R.string.action_back))
            }
        } else {
            Box(Modifier.padding(1.dp))
        }

        if (currentStep == OnboardingStep.REVIEW) {
            Button(
                onClick = onFinish,
                enabled = canNext && !isSubmitting,
                modifier = Modifier.testTag("onboarding_finish_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                }
                Text(stringResource(R.string.onboarding_finish_action))
            }
        } else {
            Button(
                onClick = onNext,
                enabled = canNext,
                modifier = Modifier.testTag("onboarding_next_button")
            ) {
                Text(stringResource(R.string.action_next))
            }
        }
    }
}
