package com.miara.cuentame.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.miara.cuentame.core.presentation.navigation.TopLevelDestination
import com.miara.cuentame.feature.areas.navigation.areasGraph
import com.miara.cuentame.feature.categories.navigation.categoriesGraph
import com.miara.cuentame.feature.counts.navigation.countsGraph
import com.miara.cuentame.feature.home.navigation.homeGraph
import com.miara.cuentame.feature.ingredients.navigation.ingredientsGraph
import com.miara.cuentame.feature.purchases.navigation.purchasesGraph
import com.miara.cuentame.feature.reports.navigation.reportsGraph
import com.miara.cuentame.feature.settings.navigation.settingsGraph
import com.miara.cuentame.feature.suppliers.navigation.suppliersGraph
import com.miara.cuentame.feature.waste.navigation.wasteGraph

@Composable
fun CuentameNavHost(
    navController: NavHostController,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    startDestination: String = TopLevelDestination.HOME.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        homeGraph(navController)
        ingredientsGraph(navController)
        countsGraph(navController)
        purchasesGraph(navController)
        wasteGraph(navController)
        reportsGraph(navController)
        settingsGraph(navController, onBackClick)
        areasGraph()
        categoriesGraph()
        suppliersGraph(navController)
    }
}

@Composable
fun PlaceholderScreen(destination: TopLevelDestination) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(
                when (destination) {
                    TopLevelDestination.REPORTS -> "reports_placeholder"
                    else -> ""
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = stringResource(destination.titleTextId), style = MaterialTheme.typography.headlineMedium)
            Text(text = "Feature placeholder")
        }
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, style = MaterialTheme.typography.headlineMedium)
            Text(text = "Feature placeholder")
        }
    }
}
