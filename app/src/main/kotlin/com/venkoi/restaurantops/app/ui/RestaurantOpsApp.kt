package com.venkoi.restaurantops.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.venkoi.restaurantops.app.navigation.RestaurantOpsNavHost
import com.venkoi.restaurantops.core.backup.api.RestoreStartupState
import com.venkoi.restaurantops.core.presentation.navigation.Destination
import com.venkoi.restaurantops.core.presentation.navigation.TopLevelDestination
import com.venkoi.restaurantops.core.domain.usecase.AppStartState
import com.venkoi.restaurantops.core.domain.model.startup.SaaSStartupState
import com.venkoi.restaurantops.feature.auth.AuthRoute
import com.venkoi.restaurantops.feature.auth.AuthViewModel
import com.venkoi.restaurantops.feature.onboarding.ui.OnboardingRoute
import com.venkoi.restaurantops.app.ui.theme.AppSpacing
import com.venkoi.restaurantops.app.ui.theme.AppTheme

@Composable
fun RestaurantOpsApp(
    windowSizeClass: WindowSizeClass,
    viewModel: AppViewModel = hiltViewModel()
) {
    val startState by viewModel.startState.collectAsStateWithLifecycle()
    val recoveryState by viewModel.recoveryState.collectAsStateWithLifecycle()
    val saasStartupState by viewModel.saasStartupState.collectAsStateWithLifecycle()
    val preferencesState by viewModel.preferencesState.collectAsStateWithLifecycle()

    when (resolveRootDestination(recoveryState, saasStartupState, startState)) {
        RootDestination.LOADING -> {
            LoadingContent(stringResource(com.venkoi.restaurantops.R.string.state_loading_desc))
        }
        RootDestination.RECOVERY_REQUIRED -> {
            RecoveryRequiredContent(onRetry = { viewModel.retryRecovery() })
        }
        RootDestination.AUTH -> AuthRoute()
        RootDestination.ONBOARDING -> OnboardingFlow()
        RootDestination.CLOUD_LOCAL_SETUP -> {
            val state = saasStartupState as SaaSStartupState.RequiresLocalSetup
            OnboardingRoute(restaurantAccess = state.restaurantAccess)
        }
        RootDestination.MAIN -> MainAppContent(
            windowSizeClass = windowSizeClass,
            menuManagementEnabled = (preferencesState as? AppPreferencesState.Ready)
                ?.preferences?.menuManagementEnabled ?: true
        )
        RootDestination.SETUP_REQUIRED -> StartupMessageContent(
            title = stringResource(com.venkoi.restaurantops.R.string.saas_setup_required_title),
            message = stringResource(com.venkoi.restaurantops.R.string.saas_setup_required_message),
            tag = "saas_setup_required"
        )
        RootDestination.NETWORK_REQUIRED -> StartupMessageContent(
            title = stringResource(com.venkoi.restaurantops.R.string.saas_network_required_title),
            message = stringResource(com.venkoi.restaurantops.R.string.saas_network_required_message),
            tag = "saas_network_required"
        )
        RootDestination.DEVICE_REVOKED -> DeviceRevokedRoute()
        RootDestination.TENANT_MISMATCH -> StartupMessageContent(
            stringResource(com.venkoi.restaurantops.R.string.saas_access_mismatch_title),
            stringResource(com.venkoi.restaurantops.R.string.saas_access_mismatch_message),
            "saas_tenant_mismatch"
        )
        RootDestination.MULTIPLE_UNSUPPORTED -> StartupMessageContent(
            stringResource(com.venkoi.restaurantops.R.string.saas_multiple_title),
            stringResource(com.venkoi.restaurantops.R.string.saas_multiple_message),
            "saas_multiple_unsupported"
        )
        RootDestination.ERROR -> StartupMessageContent(
            stringResource(com.venkoi.restaurantops.R.string.saas_error_title),
            stringResource(com.venkoi.restaurantops.R.string.saas_error_message),
            "saas_startup_error"
        )
    }
}

internal enum class RootDestination {
    LOADING, RECOVERY_REQUIRED, AUTH, ONBOARDING, CLOUD_LOCAL_SETUP, MAIN, SETUP_REQUIRED,
    NETWORK_REQUIRED, DEVICE_REVOKED, TENANT_MISMATCH, MULTIPLE_UNSUPPORTED, ERROR
}

internal fun resolveRootDestination(
    recovery: RestoreStartupState,
    saas: SaaSStartupState,
    local: AppStartState
): RootDestination {
    if (recovery is RestoreStartupState.NotStarted || recovery is RestoreStartupState.Recovering) return RootDestination.LOADING
    if (recovery is RestoreStartupState.RecoveryRequired) return RootDestination.RECOVERY_REQUIRED
    return when (saas) {
        SaaSStartupState.Loading -> RootDestination.LOADING
        SaaSStartupState.RequiresAuthentication -> RootDestination.AUTH
        is SaaSStartupState.ReadyOnline, SaaSStartupState.ReadyOffline -> when (local) {
            AppStartState.Loading -> RootDestination.LOADING
            AppStartState.RequiresOnboarding -> RootDestination.ONBOARDING
            AppStartState.Ready -> RootDestination.MAIN
        }
        is SaaSStartupState.RequiresTenantSetup -> RootDestination.SETUP_REQUIRED
        is SaaSStartupState.RequiresLocalSetup -> RootDestination.CLOUD_LOCAL_SETUP
        SaaSStartupState.NetworkRequired -> RootDestination.NETWORK_REQUIRED
        SaaSStartupState.DeviceRevoked -> RootDestination.DEVICE_REVOKED
        SaaSStartupState.TenantAccessMismatch -> RootDestination.TENANT_MISMATCH
        SaaSStartupState.MultipleRestaurantsUnsupported -> RootDestination.MULTIPLE_UNSUPPORTED
        SaaSStartupState.Error -> RootDestination.ERROR
    }
}

@Composable
private fun StartupMessageContent(title: String, message: String, tag: String, action: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(24.dp).testTag(tag), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 560.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge)
            action?.let { Spacer(Modifier.height(16.dp)); it() }
        }
    }
}

@Composable
private fun DeviceRevokedRoute(viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StartupMessageContent(
        stringResource(com.venkoi.restaurantops.R.string.saas_device_revoked_title),
        stringResource(com.venkoi.restaurantops.R.string.saas_device_revoked_message),
        "saas_device_revoked"
    ) {
        androidx.compose.material3.Button(onClick = viewModel::signOut, enabled = !state.submitting) {
            Text(stringResource(com.venkoi.restaurantops.R.string.auth_sign_out))
        }
    }
}

@Composable
fun LoadingContent(message: String) {
    Box(modifier = Modifier.fillMaxSize().testTag("app_loading"), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun RecoveryRequiredContent(onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(com.venkoi.restaurantops.R.string.restore_recovery_required_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(com.venkoi.restaurantops.R.string.restore_recovery_required_message),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.Button(onClick = onRetry) {
                Text(text = stringResource(com.venkoi.restaurantops.R.string.action_retry_desc))
            }
        }
    }
}

@Composable
fun OnboardingFlow() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Destination.ONBOARDING.route) {
        composable(Destination.ONBOARDING.route) {
            OnboardingRoute()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    windowSizeClass: WindowSizeClass,
    menuManagementEnabled: Boolean = true,
    navController: NavHostController = rememberNavController()
) {
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination

    val currentTopLevelDestination = TopLevelDestination.entries.firstOrNull { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }
    
    val isTopLevelDestination = currentTopLevelDestination != null
    val isSettingsRoot = currentDestination?.route == Destination.SETTINGS.route
    
    val shouldShowBottomBar = isCompact && isTopLevelDestination
    val shouldShowNavRail = !isCompact && !isExpanded && isTopLevelDestination
    val shouldShowSidebar = isExpanded && isTopLevelDestination

    Scaffold(
        topBar = {
            if (isTopLevelDestination || isSettingsRoot) {
                TopAppBar(
                    title = {
                        Text(
                            text = if (isSettingsRoot) {
                                stringResource(com.venkoi.restaurantops.R.string.nav_settings)
                            } else {
                                currentTopLevelDestination?.let { stringResource(it.titleTextId) }
                                    ?: stringResource(com.venkoi.restaurantops.R.string.app_name)
                            }
                        )
                    },
                    navigationIcon = {
                        if (isSettingsRoot) {
                            IconButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier.testTag("settings_back")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = stringResource(com.venkoi.restaurantops.R.string.action_back)
                                )
                            }
                        }
                    },
                    actions = {
                        if (isTopLevelDestination && !shouldShowSidebar) {
                            IconButton(
                                onClick = {
                                    navController.navigate(Destination.SETTINGS.route) {
                                        launchSingleTop = true
                                    }
                                },
                                modifier = Modifier.testTag("nav_settings")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = stringResource(com.venkoi.restaurantops.R.string.nav_settings)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                )
            }
        },
        bottomBar = {
            if (shouldShowBottomBar) {
                RestaurantOpsBottomBar(
                    destinations = TopLevelDestination.entries,
                    onNavigateToDestination = { destination ->
                        navigateToTopLevelDestination(navController, destination)
                    },
                    currentDestination = currentDestination
                )
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Row(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            if (shouldShowNavRail) {
                RestaurantOpsNavRail(
                    destinations = TopLevelDestination.entries,
                    onNavigateToDestination = { destination ->
                        navigateToTopLevelDestination(navController, destination)
                    },
                    currentDestination = currentDestination
                )
            }
            if (shouldShowSidebar) {
                AppNavigationSidebar(
                    destinations = TopLevelDestination.entries,
                    onNavigateToDestination = { destination ->
                        navigateToTopLevelDestination(navController, destination)
                    },
                    onSettingsClick = {
                        navController.navigate(Destination.SETTINGS.route) { launchSingleTop = true }
                    },
                    currentDestination = currentDestination
                )
            }

            RestaurantOpsNavHost(
                navController = navController,
                onBackClick = { navController.popBackStack() },
                menuManagementEnabled = menuManagementEnabled,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun AppNavigationSidebar(
    destinations: List<TopLevelDestination>,
    onNavigateToDestination: (TopLevelDestination) -> Unit,
    onSettingsClick: () -> Unit,
    currentDestination: NavDestination?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(240.dp).fillMaxSize().testTag("top_level_navigation_sidebar"),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, AppTheme.semanticColors.divider),
    ) {
        Column(Modifier.padding(AppSpacing.md)) {
            Text(
                stringResource(com.venkoi.restaurantops.R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.lg),
            )
            destinations.forEach { destination ->
                val selected = currentDestination.isTopLevelDestinationInHierarchy(destination)
                NavigationDrawerItem(
                    selected = selected,
                    onClick = { onNavigateToDestination(destination) },
                    icon = { Icon(if (selected) destination.selectedIcon else destination.unselectedIcon, null) },
                    label = { Text(stringResource(destination.iconTextId)) },
                    modifier = Modifier.padding(vertical = AppSpacing.xs).testTag(destination.testTag),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = AppTheme.semanticColors.selected,
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
            Spacer(Modifier.weight(1f))
            HorizontalDivider(color = AppTheme.semanticColors.divider)
            NavigationDrawerItem(
                selected = false,
                onClick = onSettingsClick,
                icon = { Icon(Icons.Default.Settings, null) },
                label = { Text(stringResource(com.venkoi.restaurantops.R.string.nav_settings)) },
                modifier = Modifier.padding(top = AppSpacing.sm).testTag("nav_settings"),
            )
        }
    }
}


@Composable
private fun RestaurantOpsBottomBar(
    destinations: List<TopLevelDestination>,
    onNavigateToDestination: (TopLevelDestination) -> Unit,
    currentDestination: NavDestination?,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.testTag("top_level_bottom_bar"),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        destinations.forEach { destination ->
            val selected = currentDestination.isTopLevelDestinationInHierarchy(destination)
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigateToDestination(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(destination.iconTextId)) },
                modifier = Modifier.testTag(destination.testTag)
            )
        }
    }
}

@Composable
private fun RestaurantOpsNavRail(
    destinations: List<TopLevelDestination>,
    onNavigateToDestination: (TopLevelDestination) -> Unit,
    currentDestination: NavDestination?,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier.testTag("top_level_navigation_rail"),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        destinations.forEach { destination ->
            val selected = currentDestination.isTopLevelDestinationInHierarchy(destination)
            NavigationRailItem(
                selected = selected,
                onClick = { onNavigateToDestination(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(destination.iconTextId)) },
                modifier = Modifier.testTag(destination.testTag)
            )
        }
    }
}

private fun NavDestination?.isTopLevelDestinationInHierarchy(destination: TopLevelDestination) =
    this?.hierarchy?.any {
        it.route?.contains(destination.route, ignoreCase = true) ?: false
    } ?: false

private fun navigateToTopLevelDestination(
    navController: NavHostController,
    topLevelDestination: TopLevelDestination
) {
    if (topLevelDestination == TopLevelDestination.HOME) {
        navController.popBackStack(TopLevelDestination.HOME.route, inclusive = false)
        return
    }
    val topLevelNavOptions = navOptions {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }

    navController.navigate(topLevelDestination.route, topLevelNavOptions)
}
