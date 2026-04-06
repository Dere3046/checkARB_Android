package com.dere3046.checkarb

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object ManualScan : Screen("manual_scan")
    object AutoScan : Screen("auto_scan")
    object Detail : Screen("detail/{slotSuffix}") {
        fun createRoute(slotSuffix: String) = "detail/$slotSuffix"
    }
    object ArbDatabase : Screen("arb_database")
    object XblExtractor : Screen("xbl_extractor")
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit,
    onNavigateToArbStats: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route,
        modifier = modifier
    ) {
        composable(Screen.Main.route) {
            MainScreenContent(
                viewModel = viewModel,
                onNavigateToManual = {
                    navController.navigate(Screen.ManualScan.route)
                },
                onNavigateToAuto = {
                    navController.navigate(Screen.AutoScan.route)
                },
                onNavigateToDetail = { slot ->
                    navController.navigate(Screen.Detail.createRoute(slot.suffix))
                },
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToArbStats = onNavigateToArbStats,
                onNavigateToArbDatabase = {
                    navController.navigate(Screen.ArbDatabase.route)
                },
                onNavigateToXblExtractor = {
                    navController.navigate(Screen.XblExtractor.route)
                }
            )
        }

        composable(Screen.ManualScan.route) {
            ManualScanScreen(
                viewModel = viewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.AutoScan.route) {
            AutoScanScreen(
                viewModel = viewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.Detail.route,
            arguments = listOf(
                navArgument("slotSuffix") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val slotSuffix = backStackEntry.arguments?.getString("slotSuffix") ?: ""
            val slotInfo = when (slotSuffix) {
                "_a" -> viewModel.slotA.value
                "_b" -> viewModel.slotB.value
                else -> viewModel.slotA.value
            }
            val currentDevicePath = when (slotSuffix) {
                "_a" -> viewModel.slotADevicePath.value
                "_b" -> viewModel.slotBDevicePath.value
                else -> null
            }

            DetailScreen(
                slotInfo = slotInfo,
                onRefresh = { },
                onBack = {
                    navController.popBackStack()
                },
                onSelectDevice = { path ->
                    viewModel.setSlotDevicePath(slotSuffix, path)
                },
                availableDevices = viewModel.availableDevices.value,
                currentDevicePath = currentDevicePath
            )
        }

        composable(Screen.ArbDatabase.route) {
            ArbDatabaseScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.XblExtractor.route) {
            val viewModel: XblExtractorViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = XblExtractorViewModelFactory(navController.context.applicationContext as android.app.Application)
            )
            XblExtractorScreen(
                onBack = {
                    navController.popBackStack()
                },
                viewModel = viewModel
            )
        }
    }
}