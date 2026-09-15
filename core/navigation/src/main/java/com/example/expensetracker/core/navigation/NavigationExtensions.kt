package com.example.expensetracker.core.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination

/**
 * Navigates to a top-level destination with standard back stack behavior:
 * popping up to the start destination of the graph, saving state, and launching single top.
 */
fun NavController.navigateToTopLevelDestination(destination: Any) {
    navigate(destination) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
