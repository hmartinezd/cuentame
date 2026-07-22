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
import androidx.compose.material3.TextButton
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

    val suggestedAreaLabels = uiState.suggestedAreas.associate { it.key to stringResource(it.labelResId) }
    val suggestedCategoryLabels = uiState.suggestedCategories.associate { it.key to stringResource(it.labelResId) }

    OnboardingScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onRestaurantNameChanged = viewModel::onRestaurantNameChanged,
        onCurrencySelected = viewModel::onCurrencySelected,
        onLocaleSelected = viewModel::onLocaleSelected,
        onSuggestedAreaToggled = viewModel::onSuggestedAreaToggled,
        onCustomAreaAdded = viewModel::onCustomAreaAdded,
        onCustomAreaRemoved = viewModel::onCustomAreaRemoved,
        onCustomAreaRenamed = viewModel::onCustomAreaRenamed,
        onAreaMovedUp = viewModel::onAreaMovedUp,
        onAreaMovedDown = viewModel::onAreaMovedDown,
        onSuggestedCategoryToggled = viewModel::onSuggestedCategoryToggled,
        onCustomCategoryAdded = viewModel::onCustomCategoryAdded,
        onCustomCategoryRemoved = viewModel::onCustomCategoryRemoved,
        onCustomCategoryRenamed = viewModel::onCustomCategoryRenamed,
        onCategoryMovedUp = viewModel::onCategoryMovedUp,
        onCategoryMovedDown = viewModel::onCategoryMovedDown,
        onNext = viewModel::onNext,
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
    onSuggestedAreaToggled: (String) -> Unit,
    onCustomAreaAdded: (String) -> Unit,
    onCustomAreaRemoved: (String) -> Unit,
    onCustomAreaRenamed: (String, String) -> Unit,
    onAreaMovedUp: (String) -> Unit,
    onAreaMovedDown: (String) -> Unit,
    onSuggestedCategoryToggled: (String) -> Unit,
    onCustomCategoryAdded: (String) -> Unit,
    onCustomCategoryRemoved: (String) -> Unit,
    onCustomCategoryRenamed: (String, String) -> Unit,
    onCategoryMovedUp: (String) -> Unit,
    onCategoryMovedDown: (String) -> Unit,
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
                            onNameChanged = onRestaurantNameChanged,
                            onCurrencySelected = onCurrencySelected,
                            onLocaleSelected = onLocaleSelected
                        )
                        OnboardingStep.AREAS -> AreasStep(
                            suggested = uiState.suggestedAreas,
                            custom = uiState.customAreas,
                            onToggleSuggested = onSuggestedAreaToggled,
                            onAddCustom = onCustomAreaAdded,
                            onRemoveCustom = onCustomAreaRemoved,
                            onRenameCustom = onCustomAreaRenamed,
                            onMoveUp = onAreaMovedUp,
                            onMoveDown = onAreaMovedDown
                        )
                        OnboardingStep.CATEGORIES -> CategoriesStep(
                            suggested = uiState.suggestedCategories,
                            custom = uiState.customCategories,
                            onToggleSuggested = onSuggestedCategoryToggled,
                            onAddCustom = onCustomCategoryAdded,
                            onRemoveCustom = onCustomCategoryRemoved,
                            onRenameCustom = onCustomCategoryRenamed,
                            onMoveUp = onCategoryMovedUp,
                            onMoveDown = onCategoryMovedDown
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
fun AreasStep(
    suggested: List<com.miara.cuentame.feature.onboarding.model.SelectableTemplateUiModel>,
    custom: List<com.miara.cuentame.feature.onboarding.model.EditableNameUiModel>,
    onToggleSuggested: (String) -> Unit,
    onAddCustom: (String) -> Unit,
    onRemoveCustom: (String) -> Unit,
    onRenameCustom: (String, String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = stringResource(R.string.onboarding_areas_title), style = MaterialTheme.typography.headlineSmall)
        Text(text = stringResource(R.string.onboarding_areas_desc))

        suggested.forEach { area ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(checked = area.isSelected, onCheckedChange = { onToggleSuggested(area.key) })
                Text(text = stringResource(area.labelResId))
            }
        }
        
        // Custom Area Input
        var customName by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = customName,
                onValueChange = { customName = it },
                label = { Text(stringResource(R.string.onboarding_add_area)) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            Button(onClick = { 
                onAddCustom(customName)
                customName = ""
            }) {
                Text(stringResource(R.string.action_add))
            }
        }

        custom.forEach { area ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = area.name,
                    onValueChange = { onRenameCustom(area.id, it) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(onClick = { onMoveUp(area.id) }) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null)
                }
                IconButton(onClick = { onMoveDown(area.id) }) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = null)
                }
                IconButton(onClick = { onRemoveCustom(area.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                }
            }
        }
    }
}

@Composable
fun CategoriesStep(
    suggested: List<com.miara.cuentame.feature.onboarding.model.SelectableTemplateUiModel>,
    custom: List<com.miara.cuentame.feature.onboarding.model.EditableNameUiModel>,
    onToggleSuggested: (String) -> Unit,
    onAddCustom: (String) -> Unit,
    onRemoveCustom: (String) -> Unit,
    onRenameCustom: (String, String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = stringResource(R.string.onboarding_categories_title), style = MaterialTheme.typography.headlineSmall)
        Text(text = stringResource(R.string.onboarding_categories_desc))

        suggested.forEach { cat ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(checked = cat.isSelected, onCheckedChange = { onToggleSuggested(cat.key) })
                Text(text = stringResource(cat.labelResId))
            }
        }

        // Custom Category Input
        var customName by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = customName,
                onValueChange = { customName = it },
                label = { Text(stringResource(R.string.onboarding_add_category)) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            Button(onClick = { 
                onAddCustom(customName)
                customName = ""
            }) {
                Text(stringResource(R.string.action_add))
            }
        }

        custom.forEach { cat ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = cat.name,
                    onValueChange = { onRenameCustom(cat.id, it) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(onClick = { onMoveUp(cat.id) }) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null)
                }
                IconButton(onClick = { onMoveDown(cat.id) }) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = null)
                }
                IconButton(onClick = { onRemoveCustom(cat.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                }
            }
        }

        Text(text = stringResource(R.string.onboarding_category_recommend), style = MaterialTheme.typography.bodySmall)
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
            val areaNames = state.suggestedAreas.filter { it.isSelected }.map { stringResource(it.labelResId) } +
                    state.customAreas.map { it.name }
            Text(text = areaNames.joinToString(", "))
        }

        ReviewSection(
            label = stringResource(R.string.settings_categories),
            onEdit = { onEditStep(OnboardingStep.CATEGORIES) }
        ) {
            val catNames = state.suggestedCategories.filter { it.isSelected }.map { stringResource(it.labelResId) } +
                    state.customCategories.map { it.name }
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
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (canBack) {
            OutlinedButton(onClick = onBack, enabled = !isSubmitting) {
                Text(stringResource(R.string.action_back))
            }
        } else {
            Box(modifier = Modifier.width(1.dp))
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
