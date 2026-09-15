package com.example.expensetracker

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.expensetracker.core.designsystem.theme.ExpenseTrackerTheme
import com.example.expensetracker.core.navigation.AppScaffold
import com.example.expensetracker.core.preferences.PreferencesRepository
import com.example.expensetracker.core.preferences.UserPreferences
import com.example.expensetracker.feature.security.SecurityManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var securityManager: SecurityManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val userPreferences by preferencesRepository.userPreferences.collectAsState(
                initial = UserPreferences.DEFAULT
            )
            val themeState = rememberThemeState(preferences = userPreferences)
            val isLocked by securityManager.isLocked.collectAsState()

            LaunchedEffect(isLocked) {
                if (isLocked) {
                    securityManager.authenticate(
                        activity = this@MainActivity,
                        onSuccess = {},
                        onError = { _, _ -> },
                        onFailed = {}
                    )
                }
            }

            ExpenseTrackerTheme(
                darkTheme = themeState.darkTheme,
                dynamicColor = themeState.useDynamicColor,
                customAccentColor = themeState.customAccentColor,
                fontScale = themeState.fontScale
            ) {
                if (isLocked) {
                    LockGateScreen(
                        onUnlockClick = {
                            securityManager.authenticate(
                                activity = this@MainActivity,
                                onSuccess = {},
                                onError = { _, _ -> },
                                onFailed = {}
                            )
                        }
                    )
                } else {
                    AppScaffold()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            securityManager.onAppForegrounded()
        }
    }

    override fun onStop() {
        super.onStop()
        securityManager.onAppBackgrounded()
    }
}

@Composable
private fun LockGateScreen(
    onUnlockClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "App Locked",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Expense Tracker is Locked",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Confirm your biometric or device credential to view your financial records.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onUnlockClick,
                modifier = Modifier.fillMaxWidth(0.7f)
            ) {
                Text("Unlock")
            }
        }
    }
}
