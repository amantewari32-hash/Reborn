
/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.extras

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.security.Privacy
import dev.asfakul.yumeiro.ui.theme.DarkBackground
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary

/** Full-screen app lock gate using a dedicated six-digit app PIN. */
@Composable
fun AppLockGate(activity: Activity) {
    val locked by Privacy.locked.collectAsState()
    if (!locked) return

    BackHandler { activity.moveTaskToBack(true) }

    val hasPin = remember { Privacy.hasPin(activity) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(28.dp)
            .testTag("app_lock"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = SpotifyGreen,
            modifier = Modifier.size(56.dp),
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Yumeiro is locked",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(8.dp))

        if (hasPin) {
            Text(
                text = "Enter your six-digit app PIN to continue.",
                color = TextSecondary,
                fontSize = 13.sp,
            )

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = {
                    if (it.length <= 6 && it.all(Char::isDigit)) {
                        pin = it
                        error = null
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("app_lock_pin"),
                label = { Text("Six-digit PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = TextSecondary, fontSize = 12.sp)
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    if (Privacy.verifyPin(activity, pin)) {
                        pin = ""
                    } else {
                        error = "Incorrect PIN. Try again."
                        pin = ""
                    }
                },
                enabled = pin.length == 6,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SpotifyGreen,
                    contentColor = OnAccent,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Unlock")
            }
        } else {
            Text(
                text = "Create an app-specific PIN to protect your privacy.",
                color = TextSecondary,
                fontSize = 13.sp,
            )

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = {
                    if (it.length <= 6 && it.all(Char::isDigit)) {
                        pin = it
                        error = null
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("app_lock_create_pin"),
                label = { Text("Create six-digit PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = confirmPin,
                onValueChange = {
                    if (it.length <= 6 && it.all(Char::isDigit)) {
                        confirmPin = it
                        error = null
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("app_lock_confirm_pin"),
                label = { Text("Confirm six-digit PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = TextSecondary, fontSize = 12.sp)
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    when {
                        pin.length != 6 -> error = "Enter all six digits."
                        confirmPin.length != 6 -> error = "Confirm all six digits."
                        pin != confirmPin -> {
                            error = "The PINs don't match."
                            confirmPin = ""
                        }
                        Privacy.setPin(activity, pin) -> {
                            pin = ""
                            confirmPin = ""
                        }
                        else -> error = "Couldn't save the PIN. Please try again."
                    }
                },
                enabled = pin.length == 6 && confirmPin.length == 6,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SpotifyGreen,
                    contentColor = OnAccent,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save PIN")
            }
        }
    }
}
