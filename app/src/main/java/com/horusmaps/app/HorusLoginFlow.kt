package com.horusmaps.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private enum class LoginScreen { REDIRECT, FORM, AUTHENTICATING, WELCOME }

@Composable
fun HorusLoginFlow(
    onBack: () -> Unit,
    onLoginSuccess: (String, String, String, String) -> Unit
) {
    var screen by remember { mutableStateOf(LoginScreen.REDIRECT) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var welcomeName by remember { mutableStateOf("") }
    var successfulEmail by remember { mutableStateOf("") }
    var successfulFirstName by remember { mutableStateOf("") }
    var successfulLastName by remember { mutableStateOf("") }
    var successfulPhone by remember { mutableStateOf("") }

    val normalizedEmail = email.trim().lowercase()
    val emailValid = Regex("^[a-z0-9](?:[a-z0-9._-]{1,22}[a-z0-9])@horus\\.com$")
        .matches(normalizedEmail) &&
        !normalizedEmail.contains("..") &&
        !normalizedEmail.contains(".-") &&
        !normalizedEmail.contains("-.")

    LaunchedEffect(Unit) {
        delay(850)
        screen = LoginScreen.FORM
    }

    LaunchedEffect(screen) {
        if (screen == LoginScreen.AUTHENTICATING) {
            try {
                val result = HorusAuthApi.login(normalizedEmail, password)
                if (result.success && !result.email.isNullOrBlank()) {
                    successfulEmail = result.email
                    successfulFirstName = result.firstName
                    successfulLastName = result.lastName
                    successfulPhone = result.phone
                    welcomeName = result.firstName.ifBlank { result.email.substringBefore("@") }
                    screen = LoginScreen.WELCOME
                } else {
                    errorMessage = result.message
                    screen = LoginScreen.FORM
                }
            } catch (_: Exception) {
                errorMessage = "تعذر الاتصال بالخادم. تحقق من الإنترنت وحاول مرة أخرى."
                screen = LoginScreen.FORM
            }
        } else if (screen == LoginScreen.WELCOME) {
            delay(1600)
            onLoginSuccess(successfulEmail, successfulFirstName, successfulLastName, successfulPhone)
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF07182B), Color(0xFF102D4B), Color(0xFF07182B)))
            ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = screen,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "horusLoginStages"
            ) { current ->
                when (current) {
                    LoginScreen.REDIRECT -> LoginLoadingState(
                        title = "جاري توجيهك...",
                        subtitle = "نجهّز صفحة تسجيل الدخول الآمنة"
                    )
                    LoginScreen.AUTHENTICATING -> LoginLoadingState(
                        title = "جارٍ التحقق من بياناتك...",
                        subtitle = "نتأكد من بيانات حسابك بأمان"
                    )
                    LoginScreen.WELCOME -> LoginLoadingState(
                        title = "مرحبًا مجددًا يا $welcomeName",
                        subtitle = "جاري توجيهك للخريطة..."
                    )
                    LoginScreen.FORM -> {
                        Column(
                            Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                                }
                                Text("تسجيل الدخول", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(18.dp))
                            Card(
                                Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(28.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(15.dp)
                                ) {
                                    Text("أهلًا بعودتك إلى HorusMaps", color = Color(0xFF172B45), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                                    Text("سجّل الدخول باستخدام بريد Horus وكلمة المرور الخاصة بحسابك.", color = Color(0xFF718096), fontSize = 12.sp, lineHeight = 19.sp)
                                    OutlinedTextField(
                                        value = email,
                                        onValueChange = { email = it.trim().replace(" ", ""); errorMessage = "" },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("البريد الإلكتروني") },
                                        placeholder = { Text("username@horus.com") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = loginFieldColors()
                                    )
                                    Text(
                                        "يجب أن ينتهي البريد بـ @horus.com",
                                        color = if (email.isBlank() || emailValid) Color(0xFF8492A5) else Color(0xFFD34C55),
                                        fontSize = 10.sp
                                    )
                                    OutlinedTextField(
                                        value = password,
                                        onValueChange = { password = it; errorMessage = "" },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("كلمة المرور") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                                Icon(
                                                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = if (passwordVisible) "إخفاء كلمة المرور" else "إظهار كلمة المرور",
                                                    tint = Color(0xFF60748B)
                                                )
                                            }
                                        },
                                        colors = loginFieldColors()
                                    )
                                    if (errorMessage.isNotBlank()) {
                                        Text(errorMessage, color = Color(0xFFD34C55), fontSize = 12.sp, lineHeight = 18.sp)
                                    }
                                    Button(
                                        onClick = {
                                            errorMessage = ""
                                            screen = LoginScreen.AUTHENTICATING
                                        },
                                        enabled = emailValid && password.isNotEmpty(),
                                        modifier = Modifier.fillMaxWidth().height(54.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF087FE5),
                                            disabledContainerColor = Color(0xFFB8C9D9)
                                        )
                                    ) {
                                        Text("تسجيل الدخول", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                }
                            }
                            Spacer(Modifier.height(18.dp))
                            Text("HORUS MAPS  •  YOUR WORLD, YOUR WAY", color = Color.White.copy(alpha = 0.62f), fontSize = 9.sp, letterSpacing = 1.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginLoadingState(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(94.dp).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF55B6FF), strokeWidth = 4.dp, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(subtitle, color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFF087FE5),
    unfocusedBorderColor = Color(0xFFDCE4ED),
    focusedLabelColor = Color(0xFF087FE5),
    cursorColor = Color(0xFF087FE5)
)
