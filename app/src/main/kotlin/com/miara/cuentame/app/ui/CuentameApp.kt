package com.miara.cuentame.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import com.miara.cuentame.app.navigation.CuentameNavHost
import com.miara.cuentame.core.backup.api.RestoreStartupState
import com.miara.cuentame.core.presentation.navigation.Destination
import com.miara.cuentame.core.presentation.navigation.TopLevelDestination
import com.miara.cuentame.core.domain.usecase.AppStartState
import com.miara.cuentame.feature.onboarding.ui.OnboardingRoute

@Composable
fun CuentameApp(
    windowSizeClass: WindowSizeClass,
    viewModel: AppViewModel = hiltViewModel()
) {
    val startState by viewModel.startState.collectAsStateWithLifecycle()
    val recoveryState by viewModel.recoveryState.collectAsStateWithLifecycle()

    when {
        recoveryState is RestoreStartupState.NotStarted || recoveryState is RestoreStartupState.Recovering -> {
            LoadingContent(stringResource(com.miara.cuentame.R.string.state_loading_desc))
        }
        recoveryState is RestoreStartupState.RecoveryRequired -> {
            RecoveryRequiredContent(onRetry = { viewModel.retryRecovery() })
        }
        else -> {
            when (startState) {
                AppStartState.Loading -> {
                    LoadingContent(stringResource(com.miara.cuentame.R.string.state_loading_desc))
                }
                AppStartState.RequiresOnboarding -> {
                    OnboardingFlow()
                }
                AppStartState.Ready -> {
                    MainAppContent(windowSizeClass = windowSizeClass)
                }
            }
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
                text = stringResource(com.miara.cuentame.R.string.restore_recovery_required_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(com.miara.cuentame.R.string.restore_recovery_required_message),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.Button(onClick = onRetry) {
                Text(text = stringResource(com.miara.cuentame.R.string.action_retry_desc))
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
    navController: NavHostController = rememberNavController()
) {
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination

    val currentTopLevelDestination = TopLevelDestination.entries.firstOrNull { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }
    
    val isTopLevelDestination = currentTopLevelDestination != null
    val isSettingsRoot = currentDestination?.route == Destination.SETTINGS.route
    
    val shouldShowBottomBar = isCompact && isTopLevelDestination
    val shouldShowNavRail = !isCompact && isTopLevelDestination

    Scaffold(
        topBar = {
            if (isTopLevelDestination || isSettingsRoot) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = if (isSettingsRoot) {
                                stringResource(com.miara.cuentame.R.string.nav_settings)
                            } else {
                                currentTopLevelDestination?.let { stringResource(it.titleTextId) }
                                    ?: stringResource(com.miara.cuentame.R.string.app_name)
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
                                    contentDescription = stringResource(com.miara.cuentame.R.string.action_back)
                                )
                            }
                        }
                    },
                    actions = {
                        if (isTopLevelDestination) {
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
                                    contentDescription = stringResource(com.miara.cuentame.R.string.nav_settings)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
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
                CuentameBottomBar(
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
                CuentameNavRail(
                    destinations = TopLevelDestination.entries,
                    onNavigateToDestination = { destination ->
                        navigateToTopLevelDestination(navController, destination)
                    },
                    currentDestination = currentDestination
                )
            }

            CuentameNavHost(
                navController = navController,
                onBackClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}


@Composable
private fun CuentameBottomBar(
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
private fun CuentameNavRail(
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
    val topLevelNavOptions = navOptions {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }

    navController.navigate(topLevelDestination.route, topLevelNavOptions)
}
