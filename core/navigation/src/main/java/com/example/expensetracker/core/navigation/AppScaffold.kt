package com.example.expensetracker.core.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/**
 * Root application scaffold providing the Material 3 top-level layout,
 * floating bottom navigation bar with a smooth sliding indicator covering both
 * icon and text, and contextual Floating Action Button.
 *
 * @param modifier The layout modifier.
 * @param navController The root [NavHostController].
 */
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Identify current tab
    val currentTab = AppTab.entries.firstOrNull { tab ->
        currentDestination?.hierarchy?.any { it.hasRoute(tab.destination::class) } == true
    }

    val isTopLevelDestination = currentTab != null

    // Show FAB on Home and Transactions tabs
    val showFab = currentDestination?.hasRoute(AppDestination.Home::class) == true ||
        currentDestination?.hasRoute(AppDestination.Transactions::class) == true

    Box(modifier = modifier.fillMaxSize()) {
        // App content fills the full screen so content scrolls under and behind the floating bar
        AppNavHost(
            navController = navController,
            modifier = Modifier.fillMaxSize()
        )

        // Floating Action Button sits directly above the floating bottom navigation bar
        if (showFab) {
            FloatingActionButton(
                onClick = {
                    navController.navigate(AppDestination.AddTransaction) {
                        launchSingleTop = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(bottom = 90.dp, end = 20.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Transaction"
                )
            }
        }

        // Floating bottom navigation bar with sliding whole-item indicator
        if (isTopLevelDestination && currentTab != null) {
            FloatingBottomBar(
                tabs = AppTab.entries,
                selectedTab = currentTab,
                onTabSelected = { tab ->
                    if (tab != currentTab) {
                        navController.navigate(tab.destination) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
fun FloatingBottomBar(
    tabs: List<AppTab>,
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "TabIndicatorSlide"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp)
        ) {
            val tabWidth = maxWidth / tabs.size

            // Sliding Indicator Pill that covers the WHOLE item (icon + text together)
            Box(
                modifier = Modifier
                    .offset(x = tabWidth * animatedIndex)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )

            // Tabs Row
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = index == selectedIndex

                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(durationMillis = 200),
                        label = "TabContentColor"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(26.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onTabSelected(tab) }
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.contentDescription,
                            tint = contentColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = contentColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
