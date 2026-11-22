package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.auth.GoogleSignInButton
import com.example.data.auth.normalizeE164PhoneNumber
import com.example.data.auth.sendPhoneVerificationOtp
import com.example.data.auth.verifyPhoneOtpCode
import com.example.ui.components.EditorialStudyHeroBanner
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.delay

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onContinueAsGuest: () -> Unit,
    showBackToGuest: Boolean = false,
    onBackToGuest: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (showBackToGuest) {
        BackHandler { onBackToGuest() }
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var countryCode by rememberSaveable { mutableStateOf("+91") }
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    var displayNameInput by rememberSaveable { mutableStateOf("") }
    var otpCode by rememberSaveable { mutableStateOf("") }
    var verificationId by rememberSaveable { mutableStateOf<String?>(null) }
    var sentPhoneDisplay by rememberSaveable { mutableStateOf("") }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var isPhoneBusy by remember { mutableStateOf(false) }
    var resendSeconds by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(verificationId, resendSeconds) {
        if (verificationId != null && resendSeconds > 0) {
            delay(1000L)
            resendSeconds -= 1
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoStories,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    TextButton(
                        onClick = if (showBackToGuest) onBackToGuest else onContinueAsGuest,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("auth_skip_guest_top_button"),
                    ) {
                        Text(
                            text = if (showBackToGuest) "Back to Guest" else stringResource(R.string.skip_sign_in),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                EditorialStudyHeroBanner()

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.auth_headline),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.auth_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FeatureRow(
                        icon = Icons.Rounded.Style,
                        tint = MaterialTheme.colorScheme.secondary,
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        title = "Active-Recall Flashcards",
                        subtitle = "Flip cards, reveal hints, and cycle missed concepts until mastered.",
                    )
                    FeatureRow(
                        icon = Icons.Rounded.Quiz,
                        tint = MaterialTheme.colorScheme.primary,
                        container = MaterialTheme.colorScheme.primaryContainer,
                        title = "Interactive Quizzes with Explanations",
                        subtitle = "Instant feedback with instructor notes after every question.",
                    )
                    FeatureRow(
                        icon = Icons.Rounded.Insights,
                        tint = MaterialTheme.colorScheme.tertiary,
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        title = "Student & Class Progress Tracking",
                        subtitle = "Share a join code and monitor mastery scores in real time.",
                    )
                }
            }

            Column(
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                errorMessage?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.testTag("auth_error_card"),
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }

                // Phone Number + OTP Sign-In Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhoneAndroid,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = stringResource(R.string.phone_login_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        if (verificationId == null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = countryCode,
                                    onValueChange = { if (it.length <= 5) countryCode = it },
                                    label = { Text(stringResource(R.string.phone_country_code_label)) },
                                    singleLine = true,
                                    enabled = !isPhoneBusy,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Phone,
                                        imeAction = ImeAction.Next,
                                    ),
                                    modifier = Modifier
                                        .width(92.dp)
                                        .testTag("phone_country_code_input"),
                                )
                                OutlinedTextField(
                                    value = phoneNumber,
                                    onValueChange = { if (it.length <= 16) phoneNumber = it },
                                    label = { Text(stringResource(R.string.phone_number_label)) },
                                    placeholder = { Text(stringResource(R.string.phone_number_placeholder)) },
                                    singleLine = true,
                                    enabled = !isPhoneBusy,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Phone,
                                        imeAction = ImeAction.Next,
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("phone_number_input"),
                                )
                            }

                            OutlinedTextField(
                                value = displayNameInput,
                                onValueChange = { if (it.length <= 60) displayNameInput = it },
                                label = { Text("Your Name (optional)") },
                                placeholder = { Text("e.g., Aarav Sharma") },
                                singleLine = true,
                                enabled = !isPhoneBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("phone_name_input"),
                            )

                            FilledTonalButton(
                                onClick = {
                                    val e164 = normalizeE164PhoneNumber(countryCode, phoneNumber)
                                    if (e164 == null) {
                                        errorMessage = "Enter a valid mobile number with country code (e.g., +91 9876543210)."
                                        return@FilledTonalButton
                                    }
                                    errorMessage = null
                                    isPhoneBusy = true
                                    sendPhoneVerificationOtp(
                                        context = context,
                                        e164PhoneNumber = e164,
                                        resendToken = null,
                                        scope = scope,
                                        onCodeSent = { verId, token ->
                                            isPhoneBusy = false
                                            verificationId = verId
                                            resendToken = token
                                            sentPhoneDisplay = e164
                                            otpCode = ""
                                            resendSeconds = 60
                                        },
                                        onInstantAuthSuccess = {
                                            isPhoneBusy = false
                                            onAuthSuccess()
                                        },
                                        onAuthError = { msg ->
                                            isPhoneBusy = false
                                            errorMessage = msg
                                        },
                                    )
                                },
                                enabled = !isPhoneBusy && phoneNumber.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("phone_send_otp_button"),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                if (isPhoneBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Text(stringResource(R.string.phone_send_otp))
                                }
                            }
                        } else {
                            AnimatedVisibility(visible = verificationId != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "OTP sent to $sentPhoneDisplay",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        TextButton(
                                            onClick = {
                                                verificationId = null
                                                otpCode = ""
                                                resendSeconds = 0
                                            },
                                            enabled = !isPhoneBusy,
                                            modifier = Modifier.testTag("phone_change_number_button"),
                                        ) {
                                            Text(stringResource(R.string.phone_change_number))
                                        }
                                    }

                                    OutlinedTextField(
                                        value = otpCode,
                                        onValueChange = { next ->
                                            val digits = next.filter { it.isDigit() }
                                            if (digits.length <= 6) otpCode = digits
                                        },
                                        label = { Text(stringResource(R.string.phone_otp_label)) },
                                        placeholder = { Text("123456") },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Lock, contentDescription = null)
                                        },
                                        singleLine = true,
                                        enabled = !isPhoneBusy,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                            imeAction = ImeAction.Done,
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("phone_otp_input"),
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Button(
                                            onClick = {
                                                val verId = verificationId ?: return@Button
                                                errorMessage = null
                                                isPhoneBusy = true
                                                verifyPhoneOtpCode(
                                                    verificationId = verId,
                                                    otpCode = otpCode,
                                                    displayName = displayNameInput,
                                                    scope = scope,
                                                    onAuthSuccess = {
                                                        isPhoneBusy = false
                                                        onAuthSuccess()
                                                    },
                                                    onAuthError = { msg ->
                                                        isPhoneBusy = false
                                                        errorMessage = msg
                                                    },
                                                )
                                            },
                                            enabled = !isPhoneBusy && otpCode.length == 6,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(50.dp)
                                                .testTag("phone_verify_otp_button"),
                                            shape = MaterialTheme.shapes.medium,
                                        ) {
                                            if (isPhoneBusy) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                )
                                            } else {
                                                Text(stringResource(R.string.phone_verify_otp))
                                            }
                                        }

                                        TextButton(
                                            onClick = {
                                                val targetPhone = sentPhoneDisplay
                                                if (targetPhone.isBlank()) return@TextButton
                                                errorMessage = null
                                                isPhoneBusy = true
                                                sendPhoneVerificationOtp(
                                                    context = context,
                                                    e164PhoneNumber = targetPhone,
                                                    resendToken = resendToken,
                                                    scope = scope,
                                                    onCodeSent = { verId, token ->
                                                        isPhoneBusy = false
                                                        verificationId = verId
                                                        resendToken = token
                                                        resendSeconds = 60
                                                    },
                                                    onInstantAuthSuccess = {
                                                        isPhoneBusy = false
                                                        onAuthSuccess()
                                                    },
                                                    onAuthError = { msg ->
                                                        isPhoneBusy = false
                                                        errorMessage = msg
                                                    },
                                                )
                                            },
                                            enabled = !isPhoneBusy && resendSeconds == 0,
                                            modifier = Modifier
                                                .height(50.dp)
                                                .testTag("phone_resend_otp_button"),
                                        ) {
                                            Text(
                                                text = if (resendSeconds > 0) {
                                                    "Resend (${resendSeconds}s)"
                                                } else {
                                                    stringResource(R.string.phone_resend_otp)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        text = "OR",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                GoogleSignInButton(
                    onAuthSuccess = {
                        errorMessage = null
                        onAuthSuccess()
                    },
                    onAuthError = { msg -> errorMessage = msg },
                )

                OutlinedButton(
                    onClick = if (showBackToGuest) onBackToGuest else onContinueAsGuest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("continue_without_sign_in_button"),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = if (showBackToGuest) "Return to Guest Mode" else stringResource(R.string.continue_without_sign_in),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Text(
                    text = "Sign in for cloud sync, or run immediately in Guest Mode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    tint: Color,
    container: Color,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            color = container,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
