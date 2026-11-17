
/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */

package dev.asfakul.yumeiro.ui.screens.extras

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.security.Privacy
import kotlinx.coroutines.delay

@Composable
fun AppLockGate(activity: Activity) {
    val locked by Privacy.locked.collectAsState()

    if (!locked) return

    BackHandler(enabled = true) {
        activity.moveTaskToBack(true)
    }

    val hasPin = remember {
        Privacy.hasPin(activity)
    }

    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    var remainingMillis by remember {
        mutableStateOf(Privacy.pinLockoutRemainingMillis(activity))
    }

    // Keep the countdown updated while the lock screen is visible.
    LaunchedEffect(locked, hasPin) {
        while (locked && hasPin) {
            remainingMillis = Privacy.pinLockoutRemainingMillis(activity)
            delay(1000L)
        }
    }

    val secondsRemaining = (remainingMillis + 999L) / 1000L
    val minutes = secondsRemaining / 60L
    val seconds = secondsRemaining % 60L

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "App locked",
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = if (hasPin) "Enter your app PIN" else "Create your app PIN",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (hasPin) {
                "Enter your 6-digit PIN to continue."
            } else {
                "Create a 6-digit PIN to protect your app."
            },
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = {
                if (it.length <= 6 && it.all(Char::isDigit)) {
                    pin = it
                    error = ""
                }
            },
            enabled = !hasPin || remainingMillis <= 0L,
            label = { Text("6-digit PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword
            )
        )

        if (!hasPin) {
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = confirmPin,
                onValueChange = {
                    if (it.length <= 6 && it.all(Char::isDigit)) {
                        confirmPin = it
                        error = ""
                    }
                },
                label = { Text("Confirm PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (hasPin && remainingMillis > 0L) {
            Text(
                text = "Too many incorrect attempts. Try again in %02d:%02d."
                    .format(minutes, seconds),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(12.dp))
        } else if (error.isNotEmpty()) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        Button(
            onClick = {
                if (pin.length != 6) {
                    error = "Enter all 6 digits."
                    return@Button
                }

                if (hasPin) {
                    if (!Privacy.verifyPin(activity, pin)) {
                        pin = ""
                        remainingMillis =
                            Privacy.pinLockoutRemainingMillis(activity)

                        error = if (remainingMillis > 0L) {
                            ""
                        } else {
                            "Incorrect PIN. Try again."
                        }
                    }
                } else {
                    if (confirmPin.length != 6) {
                        error = "Confirm all 6 digits."
                    } else if (pin != confirmPin) {
                        error = "PINs do not match."
                        confirmPin = ""
                    } else if (!Privacy.setPin(activity, pin)) {
                        error = "Could not save your PIN. Try again."
                    }
                }
            },
            enabled = pin.length == 6 &&
                (hasPin && remainingMillis <= 0L ||
                    !hasPin && confirmPin.length == 6)
        ) {
            Text(if (hasPin) "Unlock" else "Create PIN")
        }
    }
}
