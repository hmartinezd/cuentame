package com.miara.cuentame.feature.sales
import androidx.navigation.*
import androidx.navigation.compose.composable
import com.miara.cuentame.core.presentation.navigation.*
fun NavGraphBuilder.salesGraph(nav:NavHostController){
 composable(Destination.SALES_IMPORT_LIST.route){SalesRoute(onBack={nav.popBackStack()},onImport={nav.navigate(AppRoutes.salesImportDetail(it))})}
 composable(Destination.SALES_IMPORT_DETAIL.route,arguments=listOf(navArgument("exportId"){type=NavType.StringType})){SalesImportDetailRoute(onBack={nav.popBackStack()},onTransaction={t,x->nav.navigate(AppRoutes.salesTransactionDetail(t,x))})}
 composable(Destination.SALES_TRANSACTION_DETAIL.route,arguments=listOf(navArgument("terminalId"){type=NavType.StringType},navArgument("transactionId"){type=NavType.StringType})){SalesTransactionDetailRoute(onBack={nav.popBackStack()})}
}
