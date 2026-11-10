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
    val longitude: Double
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
                listOf("driving" to "سيارة", "walking" to "مشي", "transit" to "قطار", "flight" to "طيران").forEach { (mode, label) ->
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
            if (selectedMode == "transit") {
                Text(
                    "مسارات القطارات تعتمد على توفر جداول ومعلومات النقل العام في المنطقة، ولم نفعّل مصدرًا موثوقًا لها بعد.",
                    color = Color(0xFF667085),
                    fontSize = 10.sp
                )
            } else if (selectedMode == "flight") {
                Text(
                    "الطيران سيُعرض كتقدير للمسافة والوقت الجوي، وليس كمسار ملاحة على الطرق.",
                    color = Color(0xFF667085),
                    fontSize = 10.sp
                )
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
            if (!navigating) {
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
