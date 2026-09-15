package com.example.expensetracker

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.expensetracker.core.designsystem.theme.ExpenseTrackerTheme
import com.example.expensetracker.core.navigation.AppScaffold
import com.example.expensetracker.core.preferences.PreferencesRepository
import com.example.expensetracker.core.preferences.UserPreferences
import com.example.expensetracker.feature.security.AppLockMode
import com.example.expensetracker.feature.security.BiometricAuthStatus
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
            val lockMode by securityManager.lockMode.collectAsState(initial = AppLockMode.PIN)
            val hasPin by securityManager.hasPin.collectAsState(initial = false)
            val hasPassword by securityManager.hasPassword.collectAsState(initial = false)
            val canUseBiometric = remember {
                securityManager.checkBiometricStatus() == BiometricAuthStatus.AVAILABLE
            }

            LaunchedEffect(isLocked, lockMode) {
                if (isLocked && lockMode == AppLockMode.BIOMETRIC) {
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
                        lockMode = lockMode,
                        hasPin = hasPin,
                        hasPassword = hasPassword,
                        canUseBiometric = canUseBiometric,
                        onBiometricClick = {
                            securityManager.authenticate(
                                activity = this@MainActivity,
                                onSuccess = {},
                                onError = { _, _ -> },
                                onFailed = {}
                            )
                        },
                        onVerifyPin = { pin ->
                            securityManager.verifyAndUnlockWithPin(pin)
                        },
                        onVerifyPassword = { password ->
                            securityManager.verifyAndUnlockWithPassword(password)
                        },
                        onFallbackUnlock = {
                            securityManager.unlock()
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
    lockMode: AppLockMode,
    hasPin: Boolean,
    hasPassword: Boolean,
    canUseBiometric: Boolean,
    onBiometricClick: () -> Unit,
    onVerifyPin: suspend (String) -> Boolean,
    onVerifyPassword: suspend (String) -> Boolean,
    onFallbackUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (lockMode) {
            AppLockMode.PIN -> {
                PinLockScreen(
                    hasPin = hasPin,
                    canUseBiometric = canUseBiometric,
                    onVerifyPin = onVerifyPin,
                    onBiometricClick = onBiometricClick,
                    onFallbackUnlock = onFallbackUnlock
                )
            }
            AppLockMode.PASSWORD -> {
                PasswordLockScreen(
                    hasPassword = hasPassword,
                    canUseBiometric = canUseBiometric,
                    onVerifyPassword = onVerifyPassword,
                    onBiometricClick = onBiometricClick,
                    onFallbackUnlock = onFallbackUnlock
                )
            }
            AppLockMode.BIOMETRIC -> {
                BiometricLockScreen(
                    onUnlockClick = onBiometricClick
                )
            }
        }
    }
}

@Composable
private fun PinLockScreen(
    hasPin: Boolean,
    canUseBiometric: Boolean,
    onVerifyPin: suspend (String) -> Boolean,
    onBiometricClick: () -> Unit,
    onFallbackUnlock: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 32.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "App Locked",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Enter PIN",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (hasPin) "Enter your 4-6 digit PIN to unlock" else "No PIN configured yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Pin Dots
            val pinDisplayCount = 4
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until maxOf(pinDisplayCount, enteredPin.length)) {
                    val isFilled = i < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFilled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (!hasPin) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Text(
                    text = "App Lock is enabled but no PIN is saved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onFallbackUnlock) {
                    Text("Unlock & Setup PIN")
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("action", "0", "backspace")
                )

                keys.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEach { key ->
                            when (key) {
                                "action" -> {
                                    if (canUseBiometric) {
                                        IconButton(
                                            onClick = onBiometricClick,
                                            modifier = Modifier.size(72.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Fingerprint,
                                                contentDescription = "Unlock with Biometrics",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    } else {
                                        TextButton(
                                            onClick = {
                                                enteredPin = ""
                                                errorMessage = null
                                            },
                                            modifier = Modifier.size(72.dp)
                                        ) {
                                            Text("CLR", style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }
                                "backspace" -> {
                                    IconButton(
                                        onClick = {
                                            if (enteredPin.isNotEmpty()) {
                                                enteredPin = enteredPin.dropLast(1)
                                                errorMessage = null
                                            }
                                        },
                                        modifier = Modifier.size(72.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                                            contentDescription = "Backspace",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                else -> {
                                    Surface(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                if (enteredPin.length < 6) {
                                                    val newPin = enteredPin + key
                                                    enteredPin = newPin
                                                    errorMessage = null
                                                    scope.launch {
                                                        val valid = onVerifyPin(newPin)
                                                        if (!valid && newPin.length == 6) {
                                                            errorMessage = "Incorrect PIN"
                                                            enteredPin = ""
                                                        }
                                                    }
                                                }
                                            },
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = key,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (enteredPin.length in 4..5) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            scope.launch {
                                val valid = onVerifyPin(enteredPin)
                                if (!valid) {
                                    errorMessage = "Incorrect PIN"
                                    enteredPin = ""
                                }
                            }
                        }
                    ) {
                        Text("Unlock with ${enteredPin.length}-digit PIN")
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordLockScreen(
    hasPassword: Boolean,
    canUseBiometric: Boolean,
    onVerifyPassword: suspend (String) -> Boolean,
    onBiometricClick: () -> Unit,
    onFallbackUnlock: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "App Locked",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Enter Password",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (hasPassword) "Enter your alphanumeric password to unlock" else "No password configured yet",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (!hasPassword) {
            Button(onClick = onFallbackUnlock) {
                Text("Unlock & Set Password")
            }
        } else {
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = null
                },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        scope.launch {
                            val valid = onVerifyPassword(password)
                            if (!valid) {
                                errorMessage = "Incorrect password"
                            }
                        }
                    }
                ),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    scope.launch {
                        val valid = onVerifyPassword(password)
                        if (!valid) {
                            errorMessage = "Incorrect password"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Unlock")
            }

            if (canUseBiometric) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onBiometricClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock with Biometrics")
                }
            }
        }
    }
}

@Composable
private fun BiometricLockScreen(
    onUnlockClick: () -> Unit
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
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Unlock")
        }
    }
}
