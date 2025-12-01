package com.manoj.eventclassifier.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.manoj.eventclassifier.ui.feature.energyMeter.EnergyMeterScreen
import com.manoj.eventclassifier.ui.feature.flowMeter.FlowMeterScreen
import com.manoj.eventclassifier.ui.feature.temperature.TemperatureSensorScreen

enum class Screen(val title: String) {
    Home("Event Classification"),
    TemperatureSensor("Temperature Sensor"),
    FlowMeter("Flow Meter"),
    EnergyMeter("Energy Meter")
}

@Composable
fun EventClassificationApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentScreen = Screen.valueOf(backStackEntry?.destination?.route ?: Screen.Home.name)
    Scaffold(topBar = {
        EventClassificationAppBar(
            currentScreen = currentScreen,
            canNavigateBack = navController.previousBackStackEntry != null,
            navigateUp = { navController.navigateUp() }
        )
    }) {
        NavGraph(
            navController = navController,
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventClassificationAppBar(
    currentScreen: Screen,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    CenterAlignedTopAppBar(
        title = {
            Text(currentScreen.title)
        },
        colors = TopAppBarDefaults.mediumTopAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = modifier,
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = navigateUp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back Button"
                    )
                }
            }
        }
    )
}

@Composable
fun NavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.name,
        modifier = modifier
    ) {
        composable(Screen.Home.name) {
            HomeScreen(
                onTemperatureSensorClick = { navController.navigate(Screen.TemperatureSensor.name) },
                onFlowMeterClick = { navController.navigate(Screen.FlowMeter.name) },
                onEnergyMeterClick = { navController.navigate(Screen.EnergyMeter.name) },
            )
        }
        composable(Screen.TemperatureSensor.name) {
            TemperatureSensorScreen()
        }
        composable(Screen.FlowMeter.name) {
            FlowMeterScreen()
        }
        composable(Screen.EnergyMeter.name) {
            EnergyMeterScreen()
        }
    }
}