package com.miara.cuentame.feature.menu.navigation
import androidx.navigation.*
import androidx.navigation.compose.composable
import com.miara.cuentame.core.common.ids.MenuRecipeId
import com.miara.cuentame.core.presentation.navigation.*
import com.miara.cuentame.feature.menu.ui.*
fun NavGraphBuilder.menuGraph(nav:NavHostController,onBack:()->Unit,enabled:Boolean=true){
 composable(Destination.MENU_RECIPE_LIST.route){if(enabled)MenuListRoute(onBack,{nav.navigate(AppRoutes.menuRecipeDetail(it))})else MenuModuleDisabled(nav)}
 composable(Destination.MENU_RECIPE_DETAIL.route,arguments=listOf(navArgument("menuRecipeId"){type=NavType.StringType})){if(enabled)MenuDetailRoute(onBack={nav.popBackStack()})else MenuModuleDisabled(nav)}
 composable(Destination.MENU_CATALOG_LIST.route){if(enabled)MenuCatalogListRoute(onBack,{nav.navigate(AppRoutes.menuCatalogDetail(it))})else MenuModuleDisabled(nav)}
 composable(Destination.MENU_CATALOG_DETAIL.route,arguments=listOf(navArgument("menuId"){type=NavType.StringType})){if(enabled)MenuCatalogDetailRoute(onBack={nav.popBackStack()},onOpenMenuItem={nav.navigate(AppRoutes.menuRecipeDetail(it))})else MenuModuleDisabled(nav)}
}

@androidx.compose.runtime.Composable
private fun MenuModuleDisabled(nav: NavHostController) {
 androidx.compose.runtime.LaunchedEffect(Unit) {
  nav.navigate(TopLevelDestination.HOME.route) { popUpTo(TopLevelDestination.HOME.route); launchSingleTop = true }
 }
}
