package com.example.expensetracker.feature.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.expensetracker.core.model.money.Currency
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * High-performance, visual Statistics & Analytics screen.
 * Displays income, expense, and net metrics, category spending donut chart,
 * spending-over-time trend line, and largest expense highlights.
 */
@Composable
fun StatisticsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    StatisticsContent(
        uiState = uiState,
        modifier = modifier,
        onSelectPreset = viewModel::selectDateRangePreset,
        onCustomDateRangeSelected = viewModel::setCustomDateRange,
        onDismissDatePicker = viewModel::dismissCustomDatePicker,
        onSelectCurrency = viewModel::selectCurrency,
        onToggleCurrencyMenu = viewModel::toggleCurrencyMenu
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsContent(
    uiState: StatisticsUiState,
    modifier: Modifier = Modifier,
    onSelectPreset: (DateRangePreset) -> Unit,
    onCustomDateRangeSelected: (LocalDate, LocalDate) -> Unit,
    onDismissDatePicker: () -> Unit,
    onSelectCurrency: (Currency) -> Unit,
    onToggleCurrencyMenu: (Boolean) -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Analytics",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    // Currency selector menu
                    Box {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onToggleCurrencyMenu(true) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${uiState.selectedCurrency.code} (${uiState.selectedCurrency.symbol})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select currency",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = uiState.isCurrencyMenuOpen,
                            onDismissRequest = { onToggleCurrencyMenu(false) }
                        ) {
                            val displayCurrencies = if (uiState.availableCurrencies.isNotEmpty()) {
                                uiState.availableCurrencies.map { it.currency }
                            } else {
                                listOf(uiState.selectedCurrency)
                            }

                            displayCurrencies.forEach { curr ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("${curr.code} - ${curr.displayName} (${curr.symbol})")
                                            if (curr == uiState.selectedCurrency) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = { onSelectCurrency(curr) }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Date Range Chips Row
                item {
                    DateRangeChips(
                        selectedPreset = uiState.selectedDateRange.preset,
                        selectedDateRange = uiState.selectedDateRange,
                        onSelectPreset = onSelectPreset
                    )
                }

                // 2. Multi-currency Exclusion Notice
                if (uiState.hasExcludedCurrencies) {
                    item {
                        MultiCurrencyNotice(
                            excludedCount = uiState.excludedCurrenciesCount,
                            currentCurrency = uiState.selectedCurrency,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                // 3. Summary Metric Cards (Income, Expense, Net Change)
                item {
                    SummaryMetricsSection(
                        uiState = uiState,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // 4. Income vs Expense Ratio Comparison
                item {
                    IncomeVsExpenseSection(
                        uiState = uiState,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // 5. Category Breakdown Donut Chart & List
                item {
                    CategorySpendingSection(
                        categoryBreakdown = uiState.categoryBreakdown,
                        totalExpense = uiState.totalExpense.toFormattedString(),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // 6. Spending Over Time Trend Line Chart
                item {
                    SpendingTrendSection(
                        timeSeries = uiState.timeSeries,
                        currency = uiState.selectedCurrency,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // 7. Largest Expense Highlight Card
                if (uiState.largestExpense != null) {
                    item {
                        LargestExpenseSection(
                            largestExpense = uiState.largestExpense,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }

        // Custom Date Range Picker Dialog
        if (uiState.isCustomDatePickerOpen) {
            CustomDateRangePickerModal(
                initialStartDate = uiState.selectedDateRange.startDate,
                initialEndDate = uiState.selectedDateRange.endDate,
                onDismiss = onDismissDatePicker,
                onConfirm = onCustomDateRangeSelected
            )
        }
    }
}

/**
 * Horizontally scrollable date range preset filter chips.
 */
@Composable
fun DateRangeChips(
    selectedPreset: DateRangePreset,
    selectedDateRange: DateRangeFilter,
    onSelectPreset: (DateRangePreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = DateTimeFormatter.ofPattern("MMM d")
    val presets = listOf(
        DateRangePreset.THIS_WEEK to "This Week",
        DateRangePreset.THIS_MONTH to "This Month",
        DateRangePreset.LAST_MONTH to "Last Month",
        DateRangePreset.LAST_3_MONTHS to "Last 3 Months",
        DateRangePreset.THIS_YEAR to "This Year",
        DateRangePreset.CUSTOM to if (selectedPreset == DateRangePreset.CUSTOM) {
            "${selectedDateRange.startDate.format(dateFormatter)} – ${selectedDateRange.endDate.format(dateFormatter)}"
        } else {
            "Custom"
        }
    )

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(presets) { (preset, label) ->
            val isSelected = selectedPreset == preset
            FilterChip(
                selected = isSelected,
                onClick = { onSelectPreset(preset) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

/**
 * Notice card alerting user that transactions in other currencies are isolated and excluded.
 */
@Composable
fun MultiCurrencyNotice(
    excludedCount: Int,
    currentCurrency: Currency,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Multi-Currency Isolation: $excludedCount transaction(s) in different currencies are excluded to guarantee zero conversion distortion. Switch currencies above to view them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                lineHeight = 16.sp
            )
        }
    }
}

/**
 * 3-Card Summary section for Income, Expenses, and Net Flow.
 */
@Composable
fun SummaryMetricsSection(
    uiState: StatisticsUiState,
    modifier: Modifier = Modifier
) {
    val positiveColor = MaterialTheme.colorScheme.primary
    val negativeColor = MaterialTheme.colorScheme.error
    val netIsPositive = uiState.netChange.amount >= BigDecimal.ZERO
    val netColor = if (netIsPositive) positiveColor else negativeColor

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Summary (${uiState.transactionCount} transactions)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = uiState.selectedCurrency.code,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Income Card
                MetricBox(
                    label = "Income",
                    amountText = uiState.totalIncome.toFormattedString(),
                    accentColor = positiveColor,
                    icon = Icons.Default.Add,
                    modifier = Modifier.weight(1f)
                )

                // Expense Card
                MetricBox(
                    label = "Expenses",
                    amountText = uiState.totalExpense.toFormattedString(),
                    accentColor = negativeColor,
                    icon = Icons.Default.ShoppingCart,
                    modifier = Modifier.weight(1f)
                )
            }

            // Net Change Banner
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = netColor.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Net Savings",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val signPrefix = if (netIsPositive) "+" else ""
                        Text(
                            text = "$signPrefix${uiState.netChange.toFormattedString()}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = netColor
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = netColor.copy(alpha = 0.2f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (netIsPositive) Icons.Default.Check else Icons.Default.Info,
                            contentDescription = null,
                            tint = netColor,
                            modifier = Modifier
                                .padding(8.dp)
                                .fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBox(
    label: String,
    amountText: String,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = amountText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Dual bar comparing Income vs Expense proportion.
 */
@Composable
fun IncomeVsExpenseSection(
    uiState: StatisticsUiState,
    modifier: Modifier = Modifier
) {
    val totalIncome = uiState.totalIncome.amount
    val totalExpense = uiState.totalExpense.amount
    val totalSum = totalIncome + totalExpense

    val incomeRatio = if (totalSum > BigDecimal.ZERO) {
        totalIncome.toDouble() / totalSum.toDouble()
    } else 0.5

    val expenseRatio = if (totalSum > BigDecimal.ZERO) {
        totalExpense.toDouble() / totalSum.toDouble()
    } else 0.5

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Income vs Expense Flow",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val incomeColor = MaterialTheme.colorScheme.primary
            val expenseColor = MaterialTheme.colorScheme.error

            // Visual comparative dual bar
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                val width = size.width
                val height = size.height

                val incomeWidth = (width * incomeRatio).toFloat()
                val expenseWidth = width - incomeWidth

                if (totalSum == BigDecimal.ZERO) {
                    drawRect(color = Color.LightGray.copy(alpha = 0.5f), size = size)
                } else {
                    drawRect(
                        color = incomeColor,
                        size = Size(incomeWidth, height)
                    )
                    drawRect(
                        color = expenseColor,
                        topLeft = Offset(incomeWidth, 0f),
                        size = Size(expenseWidth, height)
                    )
                }
            }

            // Legend labels below bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Income: ${(incomeRatio * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Expense: ${(expenseRatio * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Category spending breakdown with Donut Chart and ranked list.
 */
@Composable
fun CategorySpendingSection(
    categoryBreakdown: List<CategorySpending>,
    totalExpense: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Spending by Category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (categoryBreakdown.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No category expense data for this period",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Donut Chart
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CategoryDonutChart(
                        categoryBreakdown = categoryBreakdown,
                        modifier = Modifier.size(190.dp)
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Total Spent",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = totalExpense,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Category items list
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    categoryBreakdown.forEachIndexed { index, item ->
                        CategorySpendingRow(
                            item = item,
                            index = index
                        )
                    }
                }
            }
        }
    }
}

/**
 * Native Compose Canvas Donut Chart for category distributions.
 */
@Composable
fun CategoryDonutChart(
    categoryBreakdown: List<CategorySpending>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 26.dp.toPx()
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset(
            x = (size.width - diameter) / 2f,
            y = (size.height - diameter) / 2f
        )
        val arcSize = Size(diameter, diameter)

        var startAngle = -90f

        categoryBreakdown.forEachIndexed { index, category ->
            val sweepAngle = (category.percentage * 3.60f).coerceAtLeast(0.5f)
            val color = resolveCategoryColor(category.colorKey, index)

            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )

            startAngle += sweepAngle
        }
    }
}

/**
 * Individual Category spending breakdown row.
 */
@Composable
fun CategorySpendingRow(
    item: CategorySpending,
    index: Int,
    modifier: Modifier = Modifier
) {
    val categoryColor = resolveCategoryColor(item.colorKey, index)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(categoryColor, CircleShape)
                    )
                    Text(
                        text = item.categoryName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = categoryColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${item.percentage}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = categoryColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = item.amount.toFormattedString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Visual linear progress indicator
            LinearProgressIndicator(
                progress = { (item.percentage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = categoryColor,
                trackColor = categoryColor.copy(alpha = 0.2f),
            )
        }
    }
}

/**
 * Spending Over Time Trend Area Chart.
 */
@Composable
fun SpendingTrendSection(
    timeSeries: List<TimeSeriesPoint>,
    currency: Currency,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Spending Trend",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Daily Expense",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (timeSeries.isEmpty() || timeSeries.all { it.totalExpense.amount == BigDecimal.ZERO }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No spending activity in this period",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val maxExpense = timeSeries.maxOf { it.totalExpense.amount }
                val maxLabel = "${currency.symbol}${maxExpense.toPlainString()}"

                Column {
                    Text(
                        text = "Peak: $maxLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val chartColor = MaterialTheme.colorScheme.primary

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    ) {
                        val width = size.width
                        val height = size.height
                        val paddingBottom = 20.dp.toPx()
                        val chartHeight = height - paddingBottom

                        val maxValue = maxExpense.toFloat().coerceAtLeast(1f)
                        val pointsCount = timeSeries.size

                        if (pointsCount > 1) {
                            val stepX = width / (pointsCount - 1)

                            val path = Path()
                            val fillPath = Path()

                            val coordinates = timeSeries.mapIndexed { index, point ->
                                val x = index * stepX
                                val y = chartHeight - (point.totalExpense.amount.toFloat() / maxValue) * (chartHeight - 10f)
                                Offset(x, y)
                            }

                            // Build smooth path
                            path.moveTo(coordinates[0].x, coordinates[0].y)
                            fillPath.moveTo(coordinates[0].x, chartHeight)
                            fillPath.lineTo(coordinates[0].x, coordinates[0].y)

                            for (i in 0 until coordinates.size - 1) {
                                val p0 = coordinates[i]
                                val p1 = coordinates[i + 1]
                                val controlX = (p0.x + p1.x) / 2f
                                path.cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
                                fillPath.cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
                            }

                            fillPath.lineTo(coordinates.last().x, chartHeight)
                            fillPath.close()

                            // Draw gradient area
                            val areaBrush = Brush.verticalGradient(
                                colors = listOf(
                                    chartColor.copy(alpha = 0.35f),
                                    chartColor.copy(alpha = 0.02f)
                                ),
                                startY = 0f,
                                endY = chartHeight
                            )
                            drawPath(path = fillPath, brush = areaBrush)

                            // Draw line stroke
                            drawPath(
                                path = path,
                                color = chartColor,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Draw dot markers
                            coordinates.forEach { coord ->
                                drawCircle(
                                    color = chartColor,
                                    radius = 3.dp.toPx(),
                                    center = coord
                                )
                            }
                        }
                    }

                    // X-Axis labels (start, middle, end)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = timeSeries.first().label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (timeSeries.size > 2) {
                            Text(
                                text = timeSeries[timeSeries.size / 2].label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = timeSeries.last().label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modern Card presenting the single largest expense in the selected period.
 */
@Composable
fun LargestExpenseSection(
    largestExpense: LargestTransaction,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Largest Single Expense",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Peak Expense",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = resolveCategoryColor(largestExpense.categoryColorKey, 0).copy(alpha = 0.2f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = resolveCategoryColor(largestExpense.categoryColorKey, 0),
                                modifier = Modifier
                                    .padding(10.dp)
                                    .fillMaxSize()
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = largestExpense.categoryName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (largestExpense.note.isNotBlank()) {
                                Text(
                                    text = largestExpense.note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${largestExpense.accountName} • ${largestExpense.date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = largestExpense.amount.toFormattedString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * Material 3 Date Range Picker Dialog modal for Custom range selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDateRangePickerModal(
    initialStartDate: LocalDate,
    initialEndDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit
) {
    val dateRangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
        initialSelectedEndDateMillis = initialEndDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val start = dateRangePickerState.selectedStartDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    val end = dateRangePickerState.selectedEndDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    if (start != null && end != null) {
                        onConfirm(start, end)
                    }
                },
                enabled = dateRangePickerState.selectedStartDateMillis != null &&
                    dateRangePickerState.selectedEndDateMillis != null
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DateRangePicker(
            state = dateRangePickerState,
            title = {
                Text(
                    text = "Select Date Range",
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            showModeToggle = false
        )
    }
}

/**
 * Maps category colorKey string to a vibrant, curated Compose Color.
 */
private fun resolveCategoryColor(colorKey: String, fallbackIndex: Int): Color {
    return when (colorKey) {
        "category_orange" -> Color(0xFFF97316)
        "category_blue" -> Color(0xFF3B82F6)
        "category_purple" -> Color(0xFFA855F7)
        "category_red" -> Color(0xFFEF4444)
        "category_amber" -> Color(0xFFF59E0B)
        "category_pink" -> Color(0xFFEC4899)
        "category_teal" -> Color(0xFF14B8A6)
        "category_cyan" -> Color(0xFF06B6D4)
        "category_indigo" -> Color(0xFF6366F1)
        "category_green" -> Color(0xFF22C55E)
        "category_emerald" -> Color(0xFF10B981)
        "category_yellow" -> Color(0xFFEAB308)
        "category_deep_purple" -> Color(0xFF7C3AED)
        "category_light_green" -> Color(0xFF84CC16)
        "category_gray" -> Color(0xFF64748B)
        else -> {
            val palette = listOf(
                Color(0xFF3B82F6),
                Color(0xFFF97316),
                Color(0xFF10B981),
                Color(0xFFA855F7),
                Color(0xFFEC4899),
                Color(0xFF06B6D4),
                Color(0xFFF59E0B),
                Color(0xFF6366F1),
                Color(0xFF84CC16),
                Color(0xFFEF4444)
            )
            palette[fallbackIndex % palette.size]
        }
    }
}
