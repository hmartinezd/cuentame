package com.miara.cuentame.feature.menu.navigation
import androidx.navigation.*
import androidx.navigation.compose.composable
import com.miara.cuentame.core.common.ids.MenuRecipeId
import com.miara.cuentame.core.presentation.navigation.*
import com.miara.cuentame.feature.menu.ui.*
fun NavGraphBuilder.menuGraph(nav:NavHostController,onBack:()->Unit){
 composable(Destination.MENU_RECIPE_LIST.route){MenuListRoute(onBack,{nav.navigate(AppRoutes.menuRecipeDetail(it))})}
 composable(Destination.MENU_RECIPE_DETAIL.route,arguments=listOf(navArgument("menuRecipeId"){type=NavType.StringType})){MenuDetailRoute(onBack={nav.popBackStack()})}
}
