package com.horusmaps.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class HorusNavigationDestination(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val isLocalPlace: Boolean = false
)

@Composable
fun HorusNavigationPanel(
    destination: HorusNavigationDestination,
    selectedMode: String,
    route: HorusRouteResult?,
    calculating: Boolean,
    navigating: Boolean,
    error: String?,
    onModeSelected: (String) -> Unit,
    onCalculate: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    travelDate: String = "",
    travelTime: String = "",
    transportLoading: Boolean = false,
    transportMessage: String? = null,
    transportOptions: List<String> = emptyList(),
    onSearchTransport: () -> Unit = {},
    onPickDate: () -> Unit = {},
    allowIntercityModes: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(0.94f),
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        shadowElevation = 14.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(destination.name, color = Color(0xFF172B45), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (destination.address.isNotBlank()) {
                Text(destination.address, color = Color(0xFF667085), fontSize = 11.sp, maxLines = 2)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (if (allowIntercityModes) listOf("driving" to "سيارة", "walking" to "مشي", "transit" to "قطار", "flight" to "طيران") else listOf("driving" to "سيارة", "walking" to "مشي")).forEach { (mode, label) ->
                    val selected = selectedMode == mode
                    Text(
                        text = label,
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (selected) Color(0xFFE7F1FF) else Color(0xFFF4F6F9),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onModeSelected(mode) }
                            .padding(vertical = 10.dp),
                        color = if (selected) Color(0xFF1769C2) else Color(0xFF475467),
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
            if (selectedMode == "transit" || selectedMode == "flight") {
                Text(
                    if (selectedMode == "transit") "موعد مغادرة القطار: $travelDate • $travelTime"
                    else "تاريخ المغادرة: $travelDate",
                    modifier = Modifier.clickable(onClick = onPickDate).padding(vertical = 4.dp),
                    color = Color(0xFF1769C2), fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
                Button(
                    onClick = onSearchTransport,
                    enabled = !transportLoading,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087FE5))
                ) {
                    Text(if (transportLoading) "جارٍ البحث عن الرحلات..." else if (selectedMode == "transit") "ابحث عن القطارات" else "ابحث عن رحلات الطيران")
                }
                if (!transportMessage.isNullOrBlank()) {
                    Text(transportMessage, color = if (transportOptions.isEmpty()) Color(0xFFB54708) else Color(0xFF475467), fontSize = 11.sp)
                }
                if (transportOptions.isNotEmpty()) {
                    Column(
                        modifier = Modifier.heightIn(max = 190.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        transportOptions.take(12).forEach { line ->
                            Text(line, color = Color(0xFF344054), fontSize = 10.sp, lineHeight = 15.sp)
                        }
                    }
                }
            }
            if (route != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column {
                        Text("المسافة", color = Color(0xFF667085), fontSize = 10.sp)
                        Text(formatNavigationDistance(route.distanceMeters), color = Color(0xFF172B45), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("الوقت المتوقع", color = Color(0xFF667085), fontSize = 10.sp)
                        Text(formatNavigationDuration(route.durationSeconds), color = Color(0xFF172B45), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (!error.isNullOrBlank()) {
                Text(error, color = Color(0xFFB42318), fontSize = 11.sp)
            }
            if (!navigating && selectedMode in setOf("driving", "walking")) {
                Button(
                    onClick = if (route == null) onCalculate else onStart,
                    enabled = !calculating && selectedMode in setOf("driving", "walking"),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087FE5))
                ) {
                    Text(
                        when {
                            calculating -> "جارٍ حساب الطريق..."
                            route == null -> "احسب المسافة والطريق"
                            else -> "ابدأ وجهتك"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Text("الملاحة نشطة", color = Color(0xFF16834A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Text(
                "إلغاء",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCancel)
                    .padding(vertical = 5.dp),
                color = Color(0xFFB42318),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun formatNavigationDistance(meters: Int): String =
    if (meters < 1000) "$meters متر"
    else String.format(java.util.Locale.getDefault(), "%.1f كم", meters / 1000.0)

private fun formatNavigationDuration(seconds: Int): String {
    val minutes = (seconds / 60).coerceAtLeast(1)
    return if (minutes < 60) "$minutes دقيقة"
    else "${minutes / 60} ساعة و ${minutes % 60} دقيقة"
}
@Composable
fun HorusNavigationProgressCard(
    remainingMeters: Int,
    remainingSeconds: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF102A43),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("المتبقي للوصول", color = Color(0xFFB8D5F2), fontSize = 10.sp)
                Text(formatNavigationDistance(remainingMeters), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Column {
                Text("الوقت المتبقي", color = Color(0xFFB8D5F2), fontSize = 10.sp)
                Text(formatNavigationDuration(remainingSeconds), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}


private fun maneuverArabicLabel(step: HorusRouteStep): String {
    val modifier = step.modifier.lowercase()
    val direction = when (modifier) {
        "left" -> "يسارًا"
        "slight_left" -> "يسارًا بشكل خفيف"
        "sharp_left" -> "يسارًا حادًا"
        "right" -> "يمينًا"
        "slight_right" -> "يمينًا بشكل خفيف"
        "sharp_right" -> "يمينًا حادًا"
        "uturn" -> "استدر للخلف"
        "straight" -> "مستقيمًا"
        else -> ""
    }
    val action = when (step.type.lowercase()) {
        "turn" -> if (direction.isBlank()) "انعطف" else "انعطف $direction"
        "continue" -> if (direction.isBlank()) "واصل السير" else "واصل $direction"
        "merge" -> "اندُمج مع الطريق"
        "ramp" -> if (direction.isBlank()) "اسلك المنحدر" else "اسلك المنحدر $direction"
        "exit" -> if (direction.isBlank()) "اسلك المخرج" else "اسلك المخرج $direction"
        "roundabout" -> if (modifier == "exit") "اخرج من الدوار" else "ادخل الدوار"
        "ferry" -> if (modifier == "exit") "غادر العبّارة" else "استقل العبّارة"
        else -> step.instruction.ifBlank { "تابع السير" }
    }
    return if (step.name.isBlank()) action else "$action في ${step.name}"
}

@Composable
fun HorusNavigationManeuverCard(
    maneuver: HorusRouteStep,
    nextManeuver: HorusRouteStep?,
    distanceToManeuverMeters: Int,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        color = Color.White, shadowElevation = 8.dp) {
        Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp)) {
            Text("المناورة القادمة", color = Color(0xFF667085), fontSize = 10.sp,
                fontWeight = FontWeight.Medium)
            Text(maneuverArabicLabel(maneuver), color = Color(0xFF172B45),
                fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                if (distanceToManeuverMeters < 1000) "بعد $distanceToManeuverMeters متر"
                else String.format(java.util.Locale.getDefault(), "بعد %.1f كم", distanceToManeuverMeters / 1000.0),
                color = Color(0xFF1769C2), fontSize = 12.sp, fontWeight = FontWeight.SemiBold
            )
            if (nextManeuver != null) {
                Spacer(modifier = Modifier.height(5.dp))
                Text("ثم: ${maneuverArabicLabel(nextManeuver)}", color = Color(0xFF667085), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun HorusNavigationActiveHud(
    remainingMeters: Int,
    remainingSeconds: Int,
    maneuver: HorusRouteStep?,
    distanceToManeuverMeters: Int?,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(0.94f),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF102A43),
        shadowElevation = 10.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column {
                    Text("متبقي للوصول", color = Color(0xFFB8D5F2), fontSize = 10.sp)
                    Text(formatNavigationDistance(remainingMeters), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("الوقت المتبقي", color = Color(0xFFB8D5F2), fontSize = 10.sp)
                    Text(formatNavigationDuration(remainingSeconds), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "إنهاء",
                    modifier = Modifier
                        .background(Color(0xFF29465F), RoundedCornerShape(10.dp))
                        .clickable(onClick = onStop)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (maneuver != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("التالي", color = Color(0xFFB8D5F2), fontSize = 9.sp)
                        Text(maneuverArabicLabel(maneuver), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        distanceToManeuverMeters?.let {
                            Text(
                                if (it < 1000) "بعد $it متر" else String.format(java.util.Locale.getDefault(), "بعد %.1f كم", it / 1000.0),
                                color = Color(0xFF8FC5FF), fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
