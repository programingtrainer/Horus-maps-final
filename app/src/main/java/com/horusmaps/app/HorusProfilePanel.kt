package com.horusmaps.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private enum class ProfileDestination { PROFILE, SETTINGS }

@Composable
fun ProfilePanel(
    firstName: String,
    lastName: String,
    email: String,
    phone: String,
    imageUri: String?,
    isGuest: Boolean,
    is3DMode: Boolean,
    accounts: List<HorusSavedAccount>,
    currentEmail: String?,
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    onOpenWelcome: () -> Unit,
    onAddAccount: () -> Unit,
    onSwitchAccount: (String) -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: (String) -> Unit,
    onToggle3D: (Boolean) -> Unit
) {
    var destination by remember { mutableStateOf(ProfileDestination.PROFILE) }
    var drawerVisible by remember { mutableStateOf(true) }
    var openWelcomeAfterDismiss by remember { mutableStateOf(false) }
    val requestDismiss: () -> Unit = { drawerVisible = false }
    val requestWelcome: () -> Unit = { openWelcomeAfterDismiss = true; drawerVisible = false }

    BackHandler {
        if (destination == ProfileDestination.SETTINGS) destination = ProfileDestination.PROFILE
        else requestDismiss()
    }
    LaunchedEffect(drawerVisible) {
        if (!drawerVisible) {
            delay(340)
            if (openWelcomeAfterDismiss) onOpenWelcome() else onDismiss()
        }
    }

    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.Canvas(
            Modifier.fillMaxSize().clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) { requestDismiss() }
        ) {
            drawRect(Color(0x99071324))
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = drawerVisible,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(
                animationSpec = tween(420, easing = FastOutSlowInEasing),
                initialOffsetX = { it }
            ) + fadeIn(tween(260)),
            exit = slideOutHorizontally(
                animationSpec = tween(320, easing = FastOutSlowInEasing),
                targetOffsetX = { it }
            ) + fadeOut(tween(220))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.90f)
                    .widthIn(max = 390.dp),
                shape = RoundedCornerShape(topStart = 30.dp, bottomStart = 30.dp),
                color = Color(0xFFF7F9FC),
                shadowElevation = 24.dp
            ) {
                AnimatedContent(
                    targetState = destination,
                    transitionSpec = {
                        (slideInHorizontally(tween(300)) { it / 5 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(240)) { -it / 6 } + fadeOut(tween(180)))
                    },
                    label = "profileDrawerPages"
                ) { page ->
                    when (page) {
                        ProfileDestination.PROFILE -> ProfileOverview(
                            firstName = firstName,
                            lastName = lastName,
                            email = email,
                            phone = phone,
                            imageUri = imageUri,
                            isGuest = isGuest,
                            accounts = accounts,
                            currentEmail = currentEmail,
                            onDismiss = requestDismiss,
                            onPickImage = onPickImage,
                            onOpenSettings = { destination = ProfileDestination.SETTINGS },
                            onOpenWelcome = requestWelcome,
                            onAddAccount = onAddAccount,
                            onSwitchAccount = onSwitchAccount
                        )
                        ProfileDestination.SETTINGS -> ProfileSettings(
                            is3DMode = is3DMode,
                            onToggle3D = onToggle3D,
                            onLogout = onLogout,
                            onDeleteAccount = onDeleteAccount,
                            onBack = { destination = ProfileDestination.PROFILE }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileOverview(
    firstName: String,
    lastName: String,
    email: String,
    phone: String,
    imageUri: String?,
    isGuest: Boolean,
    accounts: List<HorusSavedAccount>,
    currentEmail: String?,
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWelcome: () -> Unit,
    onAddAccount: () -> Unit,
    onSwitchAccount: (String) -> Unit
) {
    val fullName = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
    val initials = (firstName.firstOrNull()?.uppercaseChar()?.toString() ?: "H") +
        (lastName.firstOrNull()?.uppercaseChar()?.toString() ?: "")
    val context = LocalContext.current
    var profileBitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(imageUri) {
        profileBitmap = if (imageUri.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(imageUri)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val longestSide = maxOf(bounds.outWidth, bounds.outHeight)
                val sampleSize = if (longestSide > 1200) (longestSide / 1200).coerceAtLeast(1) else 1
                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }.getOrNull()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("HORUS MAPS", color = Color(0xFF087FE5), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                Text("الملف الشخصي", color = Color(0xFF172B45), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF64748B))
            }
        }

        Spacer(Modifier.height(28.dp))
        Box(Modifier.size(126.dp), contentAlignment = Alignment.BottomEnd) {
            Box(
                Modifier.size(116.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFDCEEFF), Color(0xFFB9D9F8)))),
                contentAlignment = Alignment.Center
            ) {
                if (profileBitmap != null) {
                    Image(
                        bitmap = profileBitmap!!.asImageBitmap(),
                        contentDescription = "صورة الملف الشخصي",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(initials, color = Color(0xFF087FE5), fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Surface(
                modifier = Modifier.size(38.dp).clickable(onClick = onPickImage),
                shape = CircleShape,
                color = Color(0xFF087FE5),
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "إضافة صورة", tint = Color.White, modifier = Modifier.size(19.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(if (imageUri.isNullOrBlank()) "أضف صورة لحسابك" else "تغيير صورة الملف الشخصي", color = Color(0xFF087FE5), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onPickImage).padding(6.dp))
        Spacer(Modifier.height(15.dp))

        Text(
            if (isGuest) "مرحبًا بك في HorusMaps" else fullName.ifBlank { "مستخدم HorusMaps" },
            color = Color(0xFF172B45), fontSize = 20.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Surface(shape = CircleShape, color = if (isGuest) Color(0xFFFFF2D8) else Color(0xFFE5F5ED)) {
            Text(
                if (isGuest) "وضع الزائر" else "حساب نشط",
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
                color = if (isGuest) Color(0xFF9A6816) else Color(0xFF16845B),
                fontSize = 11.sp, fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(26.dp))
        if (isGuest) {
            ProfileInfoCard(icon = Icons.Default.Person, title = "نوع الاستخدام", value = "تستخدم HorusMaps كزائر")
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onOpenWelcome,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087FE5))
            ) { Text("إنشاء حساب أو تسجيل الدخول", fontWeight = FontWeight.Bold) }
        } else {
            ProfileInfoCard(icon = Icons.Default.Email, title = "البريد الإلكتروني", value = email)
            if (phone.isNotBlank()) {
                Spacer(Modifier.height(11.dp))
                ProfileInfoCard(icon = Icons.Default.Phone, title = "رقم الهاتف", value = phone)
            }
            Spacer(Modifier.height(22.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onOpenSettings),
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(42.dp), shape = RoundedCornerShape(13.dp), color = Color(0xFFEAF3FC)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF087FE5))
                        }
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                        Text("الإعدادات", color = Color(0xFF172B45), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("تخصيص تجربة HorusMaps", color = Color(0xFF8290A2), fontSize = 11.sp)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF8290A2))
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SavedAccountsSection(
            accounts = accounts,
            currentEmail = currentEmail,
            onAddAccount = onAddAccount,
            onSwitchAccount = onSwitchAccount
        )

        Spacer(Modifier.height(20.dp))
        Text("HorusMaps  •  استكشف العالم بطريقتك", color = Color(0xFF9AA6B5), fontSize = 10.sp)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ProfileInfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String
) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(38.dp), shape = RoundedCornerShape(12.dp), color = Color(0xFFF0F5FA)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = Color(0xFF66809B), modifier = Modifier.size(19.dp))
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, color = Color(0xFF8290A2), fontSize = 10.sp)
                Spacer(Modifier.height(3.dp))
                Text(value, color = Color(0xFF26364A), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SavedAccountsSection(
    accounts: List<HorusSavedAccount>,
    currentEmail: String?,
    onAddAccount: () -> Unit,
    onSwitchAccount: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("الحسابات", color = Color(0xFF172B45), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("إدارة حساباتك", color = Color(0xFF8290A2), fontSize = 10.sp)
        }
        Spacer(Modifier.height(10.dp))
        accounts.forEach { account ->
            val active = account.email.equals(currentEmail, true)
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .clickable(enabled = !active) { onSwitchAccount(account.email) },
                shape = RoundedCornerShape(15.dp),
                color = Color.White
            ) {
                Row(Modifier.padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(
                        imageUri = account.imageUri,
                        firstName = account.firstName,
                        lastName = account.lastName,
                        modifier = Modifier.size(42.dp)
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                        Text(account.displayName, color = Color(0xFF26364A), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(account.email, color = Color(0xFF8290A2), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (active) {
                        Surface(shape = CircleShape, color = Color(0xFFE5F5ED)) {
                            Text("الحالي", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = Color(0xFF16845B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Icon(Icons.Default.ChevronRight, contentDescription = "التبديل للحساب", tint = Color(0xFF8290A2))
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).clickable(onClick = onAddAccount),
            shape = RoundedCornerShape(15.dp),
            color = Color(0xFFEAF3FC)
        ) {
            Row(Modifier.padding(horizontal = 15.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF087FE5))
                Text("إضافة حساب آخر", modifier = Modifier.weight(1f).padding(horizontal = 12.dp), color = Color(0xFF1767B2), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF087FE5))
            }
        }
    }
}

@Composable
fun ProfileAvatar(
    imageUri: String?,
    firstName: String,
    lastName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(imageUri) {
        bitmap = if (imageUri.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(imageUri)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
                val sample = if (maxSide > 1000) (maxSide / 1000).coerceAtLeast(1) else 1
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }.getOrNull()
        }
    }
    val initials = (firstName.firstOrNull()?.uppercaseChar()?.toString() ?: "H") +
        (lastName.firstOrNull()?.uppercaseChar()?.toString() ?: "")
    Box(
        modifier = modifier.clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFDCEEFF), Color(0xFFB9D9F8)))),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), contentDescription = "صورة الحساب", modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
        } else {
            Text(initials, color = Color(0xFF087FE5), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun ProfileSettings(
    is3DMode: Boolean,
    onToggle3D: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: (String) -> Unit,
    onBack: () -> Unit
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
    val context = LocalContext.current
    val appVersion = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
        }.getOrDefault("—")
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color(0xFF52677E))
            }
            Column(Modifier.weight(1f)) {
                Text("تخصيص التطبيق", color = Color(0xFF087FE5), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                Text("الإعدادات", color = Color(0xFF172B45), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(26.dp))
        Text("إعدادات الخريطة", color = Color(0xFF8290A2), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color.White) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(42.dp), shape = RoundedCornerShape(13.dp), color = Color(0xFFEAF3FC)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Map, contentDescription = null, tint = Color(0xFF087FE5)) }
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("عرض المباني ثلاثي الأبعاد", color = Color(0xFF172B45), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("إمالة الخريطة وإظهار منظور 3D", color = Color(0xFF8290A2), fontSize = 10.sp)
                }
                Switch(checked = is3DMode, onCheckedChange = onToggle3D)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("حول التطبيق", color = Color(0xFF8290A2), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color.White) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(42.dp), shape = RoundedCornerShape(13.dp), color = Color(0xFFEAF3FC)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF087FE5)) }
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("HorusMaps", color = Color(0xFF172B45), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("الإصدار $appVersion", color = Color(0xFF8290A2), fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEAF3FC), contentColor = Color(0xFF1767B2))
        ) {
            Text("تسجيل الخروج", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { showDeleteConfirmation = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFE8E8), contentColor = Color(0xFFB42318))
        ) {
            Text("حذف الحساب نهائيًا", fontWeight = FontWeight.Bold)
        }
        if (showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = { Text("حذف الحساب نهائيًا؟", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("سيتم حذف حسابك وبياناته من خوادم HorusMaps نهائيًا، ولن تتمكن من استعادته. أدخل كلمة المرور لتأكيد هويتك.")
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = deletePassword,
                            onValueChange = { deletePassword = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("كلمة المرور") },
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = deletePassword.isNotBlank(),
                        onClick = {
                            val password = deletePassword
                            deletePassword = ""
                            showDeleteConfirmation = false
                            onDeleteAccount(password)
                        }
                    ) { Text("حذف نهائيًا", color = Color(0xFFB42318), fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false; deletePassword = "" }) { Text("إلغاء") }
                }
            )
        }
    }
}
