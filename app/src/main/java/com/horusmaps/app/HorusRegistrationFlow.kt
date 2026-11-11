package com.horusmaps.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import kotlinx.coroutines.delay
import java.util.Locale

private data class HorusCountry(val region: String, val name: String, val dialCode: Int, val flag: String)
private enum class PasswordLevel { WEAK, MEDIUM, STRONG }

private fun flagForRegion(region: String): String {
    if (region.length != 2) return "🌐"
    val base = 0x1F1E6
    return String(Character.toChars(base + region[0].uppercaseChar().code - 65)) +
        String(Character.toChars(base + region[1].uppercaseChar().code - 65))
}

private fun passwordLevel(value: String): PasswordLevel {
    val groups = listOf(
        value.any { it.isLowerCase() }, value.any { it.isUpperCase() },
        value.any { it.isDigit() }, value.any { !it.isLetterOrDigit() }
    ).count { it }
    return when {
        value.length < 8 || groups < 2 -> PasswordLevel.WEAK
        value.length >= 12 && groups >= 3 -> PasswordLevel.STRONG
        else -> PasswordLevel.MEDIUM
    }
}

@Composable
fun HorusRegistrationFlow(onBack: () -> Unit, onLoginRequested: () -> Unit, onAccountCreated: (String, String, String, String, String) -> Unit) {
    val phoneUtil = remember { PhoneNumberUtil.getInstance() }
    val countries = remember {
        val locale = Locale.getDefault()
        phoneUtil.getSupportedRegions().mapNotNull { region ->
            val code = phoneUtil.getCountryCodeForRegion(region)
            if (code <= 0) null else HorusCountry(region, Locale("", region).getDisplayCountry(locale).ifBlank { region }, code, flagForRegion(region))
        }.distinctBy { it.region }.sortedBy { it.name }
    }
    var selectedCountry by remember { mutableStateOf(countries.firstOrNull { it.region == "SA" } ?: countries.first()) }
    var showCountryPicker by remember { mutableStateOf(false) }
    var countryQuery by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf(-1) }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var emailAvailable by remember { mutableStateOf<Boolean?>(null) }
    var emailMessage by remember { mutableStateOf("") }
    var checkingEmail by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("جاري توجيهك إلى صفحة إنشاء الحساب...") }
    var errorMessage by remember { mutableStateOf("") }
    var acceptedPolicies by remember { mutableStateOf(false) }
    var legalDocument by remember { mutableStateOf<HorusLegalDocument?>(null) }

    val strength = passwordLevel(password)
    val usernameNormalized = username.trim().lowercase(Locale.ROOT)
    val validUsername = Regex("^[a-z0-9](?:[a-z0-9._-]{1,22}[a-z0-9])$").matches(usernameNormalized) &&
        !usernameNormalized.contains("..") && !usernameNormalized.contains(".-") && !usernameNormalized.contains("-.")

    LaunchedEffect(Unit) {
        delay(850)
        stage = 0
    }

    LaunchedEffect(usernameNormalized, stage) {
        checkingEmail = false
        if (stage != 1) return@LaunchedEffect
        emailAvailable = null
        emailMessage = ""
        if (!validUsername) return@LaunchedEffect
        checkingEmail = true
        delay(500)
        try {
            val result = HorusAuthApi.checkEmail(usernameNormalized)
            emailAvailable = result.available
            emailMessage = result.message
        } catch (_: Exception) {
            emailAvailable = false
            emailMessage = "تعذر الاتصال بالسيرفر. تحقق من الإنترنت وحاول مجددًا."
        } finally { checkingEmail = false }
    }

    fun phoneAsE164(): String? {
        if (phone.isBlank()) return null
        val parsed = phoneUtil.parse(phone.trim(), selectedCountry.region)
        require(phoneUtil.isValidNumber(parsed)) { "رقم الهاتف غير مكتمل أو غير صالح لهذا البلد." }
        return phoneUtil.format(parsed, PhoneNumberFormat.E164)
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF071A30), Color(0xFF0B3C64), Color(0xFFF3F7FC)))).navigationBarsPadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (submitting) return@IconButton
                    if (stage == 0) onBack() else { stage = 0; errorMessage = "" }
                }) { Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White) }
                Spacer(Modifier.weight(1f))
                Text("HORUS", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                Spacer(Modifier.weight(1f))
                Box(Modifier.width(48.dp))
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.size(58.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                Text("𓅃", color = Color(0xFFFFD27A), fontSize = 31.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when (stage) { -1 -> "جاري توجيهك"; 0 -> "أنشئ حسابك"; 1 -> "بريدك في عالم حورس"; else -> "مرحبًا بك في HorusMaps" },
                color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when (stage) { -1 -> "نجهّز لك صفحة إنشاء الحساب"; 0 -> "ابدأ رحلتك واكتشف العالم بحسابك الخاص"; 1 -> "اختر اسم بريدك الإلكتروني الخاص بك"; else -> "نجهّز حسابك ونأخذك إلى الخريطة" },
                color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(14.dp)) {
                AnimatedContent(
                    targetState = if (submitting) 2 else stage,
                    transitionSpec = {
                        (slideInHorizontally { it / 5 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 5 } + fadeOut()) using SizeTransform(clip = false)
                    },
                    label = "horus-registration-stage"
                ) { visibleStage ->
                    when (visibleStage) {
                        0 -> Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                            Text("بياناتك الشخصية", color = Color(0xFF172B45), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text("اكتب بياناتك الأساسية لإنشاء حسابك.", color = Color(0xFF7B8797), fontSize = 12.sp)
                            AuthInput(firstName, { firstName = it; errorMessage = "" }, "الاسم الأول")
                            AuthInput(lastName, { lastName = it; errorMessage = "" }, "اسم العائلة")
                            Text("رقم الهاتف (اختياري)", color = Color(0xFF33445B), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(Modifier.clip(RoundedCornerShape(14.dp)).clickable { showCountryPicker = true }, color = Color(0xFFF1F5FA)) {
                                    Row(Modifier.padding(horizontal = 10.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(selectedCountry.flag, fontSize = 18.sp)
                                        Spacer(Modifier.width(5.dp))
                                        Text("+${selectedCountry.dialCode}", color = Color(0xFF263B55), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Icon(Icons.Default.ExpandMore, null, tint = Color(0xFF60748B), modifier = Modifier.size(17.dp))
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = phone,
                                    onValueChange = { phone = it.filter { ch -> ch.isDigit() || ch == ' ' || ch == '-' || ch == '(' || ch == ')' } },
                                    modifier = Modifier.weight(1f), placeholder = { Text("رقم الهاتف") },
                                    singleLine = true, shape = RoundedCornerShape(14.dp), colors = authFieldColors()
                                )
                            }
                            AuthPasswordInput(password, { password = it.take(128); errorMessage = "" }, "كلمة المرور", showPassword) { showPassword = !showPassword }
                            PasswordStrengthIndicator(strength, password.isNotEmpty())
                            AuthPasswordInput(confirmPassword, { confirmPassword = it.take(128); errorMessage = "" }, "تأكيد كلمة المرور", showConfirmPassword) { showConfirmPassword = !showConfirmPassword }
                            if (confirmPassword.isNotEmpty()) {
                                Text(
                                    if (password == confirmPassword) "كلمتا المرور متطابقتان ✓" else "كلمتا المرور غير متطابقتين",
                                    color = if (password == confirmPassword) Color(0xFF16845B) else Color(0xFFD34C55), fontSize = 12.sp
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = acceptedPolicies,
                                    onCheckedChange = {
                                        acceptedPolicies = it
                                        if (it && errorMessage.contains("الموافقة")) errorMessage = ""
                                    }
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("أوافق على ", color = Color(0xFF52647A), fontSize = 11.sp)
                                        Text(
                                            "سياسة الخصوصية",
                                            modifier = Modifier.clickable { legalDocument = HorusLegalDocument.PRIVACY },
                                            color = Color(0xFF087FE5),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("و", color = Color(0xFF52647A), fontSize = 11.sp)
                                        Text(
                                            "شروط الاستخدام",
                                            modifier = Modifier.clickable { legalDocument = HorusLegalDocument.TERMS },
                                            color = Color(0xFF087FE5),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            if (!acceptedPolicies) {
                                Text(
                                    "الموافقة على السياستين مطلوبة للمتابعة وإنشاء الحساب.",
                                    color = Color(0xFF8994A3),
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                            if (errorMessage.isNotBlank()) AuthError(errorMessage)
                            Button(
                                onClick = {
                                    errorMessage = when {
                                        !acceptedPolicies -> "يجب الموافقة على سياسة الخصوصية وشروط الاستخدام للمتابعة."
                                        firstName.trim().length < 2 -> "اكتب الاسم الأول بشكل صحيح."
                                        lastName.trim().length < 2 -> "اكتب اسم العائلة بشكل صحيح."
                                        password.length > 128 -> "كلمة المرور يجب ألا تتجاوز 128 حرفًا."
                                        strength == PasswordLevel.WEAK -> "كلمة المرور ضعيفة. استخدم 8 أحرف على الأقل ومزيجًا من الحروف والأرقام أو الرموز."
                                        password != confirmPassword -> "تأكيد كلمة المرور غير مطابق."
                                        else -> ""
                                    }
                                    if (errorMessage.isBlank()) try { phoneAsE164(); stage = 1 } catch (e: Exception) {
                                        errorMessage = e.message ?: "رقم الهاتف غير صالح."
                                    }
                                },
                                Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087FE5))
                            ) { Text("انتقل لإنشاء إيميل حورس", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                            Text("بياناتك تُرسل عبر اتصال آمن، وكلمة المرور لا تُحفظ كنص عادي.", color = Color(0xFF8994A3), fontSize = 10.sp, textAlign = TextAlign.Center)
                        }
                        1 -> Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                            Text("اختر اسم بريدك", color = Color(0xFF172B45), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text("سيكون هذا عنوانك داخل HorusMaps.", color = Color(0xFF7B8797), fontSize = 12.sp)
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color(0xFFF3F6FA)).padding(start = 12.dp, end = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { username = it.lowercase(Locale.ROOT).filter { ch -> ch.isLetterOrDigit() || ch == '.' || ch == '_' || ch == '-' }; errorMessage = "" },
                                    modifier = Modifier.weight(1f), placeholder = { Text("اسم البريد") },
                                    singleLine = true, shape = RoundedCornerShape(14.dp), colors = authFieldColors()
                                )
                                    Text("@horus.com", color = Color(0xFF087FE5), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                            when {
                                checkingEmail -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF087FE5))
                                    Spacer(Modifier.width(8.dp))
                                    Text("جاري التحقق من توفر البريد...", color = Color(0xFF65758B), fontSize = 12.sp)
                                }
                                emailAvailable == true -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF16845B), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(7.dp))
                                    Text(emailMessage, color = Color(0xFF16845B), fontSize = 12.sp)
                                }
                                emailMessage.isNotBlank() -> Text(emailMessage, color = Color(0xFFD34C55), fontSize = 12.sp)
                                username.isNotBlank() && !validUsername -> Text("استخدم 3 إلى 24 حرفًا إنجليزيًا أو رقمًا، ويمكنك استخدام النقطة والشرطة.", color = Color(0xFFD34C55), fontSize = 12.sp)
                            }
                            Text("معاينة البريد", color = Color(0xFF7B8797), fontSize = 12.sp)
                            Text((usernameNormalized.ifBlank { "yourname" }) + "@horus.com", color = Color(0xFF087FE5), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            if (errorMessage.isNotBlank()) AuthError(errorMessage)
                            Button(
                                onClick = {
                                    if (!validUsername) errorMessage = "اسم البريد غير صالح."
                                    else if (emailAvailable != true) errorMessage = "انتظر حتى نتأكد أن البريد متاح."
                                    else { submitting = true; errorMessage = "" }
                                },
                                enabled = validUsername && emailAvailable == true && !checkingEmail,
                                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087FE5), disabledContainerColor = Color(0xFFB8C9D9))
                            ) { Text("إنشاء الحساب", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                            Text("سيتم فحص البريد مرة أخرى أثناء الإنشاء لضمان عدم حجزه من شخص آخر.", color = Color(0xFF8994A3), fontSize = 10.sp, textAlign = TextAlign.Center)
                        }
                        else -> Column(
                            Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 44.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(color = Color(0xFF087FE5), strokeWidth = 4.dp, modifier = Modifier.size(54.dp))
                            Text(loadingMessage, color = Color(0xFF172B45), fontWeight = FontWeight.Bold, fontSize = 17.sp, textAlign = TextAlign.Center)
                            Text("يرجى عدم إغلاق التطبيق أثناء تجهيز حسابك.", color = Color(0xFF7B8797), fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("لديك حساب بالفعل؟ ", color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp)
                Text("تسجيل الدخول", color = Color(0xFFFFD27A), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onLoginRequested).padding(4.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("HORUS MAPS  •  EXPLORE WITHOUT LIMITS", color = Color.White.copy(alpha = 0.64f), fontSize = 9.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(14.dp))
        }

        if (legalDocument != null) {
            HorusLegalDocumentPage(
                document = legalDocument!!,
                onBack = { legalDocument = null }
            )
        }

        if (showCountryPicker && legalDocument == null) {
            AlertDialog(
                onDismissRequest = { showCountryPicker = false; countryQuery = "" },
                title = { Text("اختر الدولة ومفتاح الاتصال", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        OutlinedTextField(countryQuery, { countryQuery = it }, Modifier.fillMaxWidth(), placeholder = { Text("ابحث عن دولة") }, singleLine = true, shape = RoundedCornerShape(13.dp))
                        Spacer(Modifier.height(8.dp))
                        Column(Modifier.fillMaxWidth().height(360.dp).verticalScroll(rememberScrollState())) {
                            countries.filter {
                                it.name.contains(countryQuery, ignoreCase = true) || it.region.contains(countryQuery, ignoreCase = true) || it.dialCode.toString().contains(countryQuery)
                            }.forEach { country ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { selectedCountry = country; showCountryPicker = false; countryQuery = "" }.padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(country.flag, fontSize = 19.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(country.name, Modifier.weight(1f), color = Color(0xFF26364A), fontSize = 13.sp)
                                    Text("+${country.dialCode}", color = Color(0xFF087FE5), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showCountryPicker = false; countryQuery = "" }) { Text("إغلاق") } }
            )
        }
    }

    LaunchedEffect(submitting) {
        if (!submitting) return@LaunchedEffect
        loadingMessage = "جاري إنشاء حسابك بأمان..."
        try {
            val result = HorusAuthApi.register(firstName.trim(), lastName.trim(), phoneAsE164(), usernameNormalized, password)
            if (result.success && !result.email.isNullOrBlank()) {
                loadingMessage = "تم إنشاء حسابك بنجاح! جاري توجيهك إلى الخريطة..."
                delay(1200)
                onAccountCreated(result.email, firstName.trim(), lastName.trim(), phoneAsE164().orEmpty(), result.sessionToken.orEmpty())
            } else {
                submitting = false
                stage = 1
                errorMessage = result.message
            }
        } catch (e: Exception) {
            submitting = false
            stage = 1
            errorMessage = e.message ?: "تعذر الاتصال بالسيرفر. حاول مرة أخرى."
        }
    }
}

@Composable
private fun AuthInput(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = authFieldColors())
}

@Composable
private fun AuthPasswordInput(value: String, onValueChange: (String) -> Unit, label: String, visible: Boolean, onToggleVisibility: () -> Unit) {
    OutlinedTextField(
        value, onValueChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        shape = RoundedCornerShape(14.dp),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (visible) "إخفاء كلمة المرور" else "إظهار كلمة المرور", tint = Color(0xFF60748B))
            }
        },
        colors = authFieldColors()
    )
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFF087FE5), unfocusedBorderColor = Color(0xFFDCE4ED),
    focusedLabelColor = Color(0xFF087FE5), cursorColor = Color(0xFF087FE5)
)

@Composable
private fun PasswordStrengthIndicator(level: PasswordLevel, visible: Boolean) {
    if (!visible) return
    val count = when (level) { PasswordLevel.WEAK -> 1; PasswordLevel.MEDIUM -> 2; PasswordLevel.STRONG -> 3 }
    val color = when (level) { PasswordLevel.WEAK -> Color(0xFFE15B62); PasswordLevel.MEDIUM -> Color(0xFFE6A33B); PasswordLevel.STRONG -> Color(0xFF19A477) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { index ->
                Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(if (index < count) color else Color(0xFFE7ECF2)))
            }
        }
        Text(
            when (level) {
                PasswordLevel.WEAK -> "قوة كلمة المرور: ضعيفة — غير مقبولة"
                PasswordLevel.MEDIUM -> "قوة كلمة المرور: متوسطة — مقبولة"
                PasswordLevel.STRONG -> "قوة كلمة المرور: قوية — ممتازة"
            },
            color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AuthError(message: String) {
    Text(message, color = Color(0xFFD34C55), fontSize = 12.sp, lineHeight = 18.sp)
}
