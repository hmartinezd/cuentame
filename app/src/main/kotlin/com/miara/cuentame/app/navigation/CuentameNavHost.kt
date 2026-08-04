package com.miara.cuentame.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.miara.cuentame.core.presentation.navigation.TopLevelDestination
import com.miara.cuentame.feature.activity.navigation.activityGraph
import com.miara.cuentame.feature.areas.navigation.areasGraph
import com.miara.cuentame.feature.categories.navigation.categoriesGraph
import com.miara.cuentame.feature.counts.navigation.countsGraph
import com.miara.cuentame.feature.home.navigation.homeGraph
import com.miara.cuentame.feature.ingredients.navigation.ingredientsGraph
import com.miara.cuentame.feature.preparations.navigation.preparationsGraph
import com.miara.cuentame.feature.production.navigation.productionGraph
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
        activityGraph(navController)
        ingredientsGraph(navController)
        countsGraph(navController)
        purchasesGraph(navController)
        wasteGraph(navController)
        reportsGraph(navController)
        settingsGraph(navController, onBackClick)
        preparationsGraph(navController, onBackClick)
        productionGraph(navController, onBackClick)
        areasGraph(navController)
        categoriesGraph()
        suppliersGraph(navController)
    }
}
