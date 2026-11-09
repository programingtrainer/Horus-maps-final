package com.horusmaps.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HorusMapSearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    loading: Boolean,
    error: String?,
    selectedCategory: HorusSearchCategory?,
    geographyResults: List<HorusGeoSearchResult>,
    nearbyResults: List<HorusNearbyPlace>,
    onCategorySelected: (HorusSearchCategory?) -> Unit,
    onSearch: () -> Unit,
    onSelectGeography: (HorusGeoSearchResult) -> Unit,
    onSelectPlace: (HorusNearbyPlace) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f).onFocusChanged {
                        if (it.isFocused) onExpandedChange(true)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("ابحث عن دولة أو قارة أو مكان...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFF287FE8))
                    },
                    trailingIcon = {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(19.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF287FE8)
                            )
                        } else {
                            IconButton(onClick = onSearch) {
                                Icon(Icons.Filled.Search, contentDescription = "بحث", tint = Color(0xFF287FE8))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() })
                )
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "إغلاق البحث", tint = Color(0xFF667085))
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            null to "الكل",
                            HorusSearchCategory.MOSQUE to "مساجد",
                            HorusSearchCategory.SUPERMARKET to "سوبر ماركت",
                            HorusSearchCategory.PHARMACY to "صيدليات"
                        ).forEach { (category, label) ->
                            ElevatedFilterChip(
                                selected = selectedCategory == category,
                                onClick = { onCategorySelected(category) },
                                label = { Text(label, fontSize = 10.sp, maxLines = 1) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            HorusSearchCategory.HOSPITAL to "مستشفيات",
                            HorusSearchCategory.RESTAURANT to "مطاعم",
                            HorusSearchCategory.FUEL to "وقود"
                        ).forEach { (category, label) ->
                            ElevatedFilterChip(
                                selected = selectedCategory == category,
                                onClick = { onCategorySelected(category) },
                                label = { Text(label, fontSize = 10.sp, maxLines = 1) }
                            )
                        }
                    }

                    if (!error.isNullOrBlank()) {
                        Text(
                            error,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color(0xFFB42318),
                            fontSize = 12.sp
                        )
                    }

                    if (!loading && selectedCategory == null && geographyResults.isEmpty() && query.length >= 2 && error.isNullOrBlank()) {
                        Text(
                            "اضغط زر البحث لعرض النتائج الجغرافية.",
                            modifier = Modifier.padding(horizontal = 8.dp),
                            color = Color(0xFF667085),
                            fontSize = 11.sp
                        )
                    }

                    if (!loading && selectedCategory != null && nearbyResults.isEmpty() && error.isNullOrBlank() && query.isNotBlank()) {
                        Text(
                            "لا توجد نتائج قريبة مطابقة داخل دولتك. جرّب اسمًا آخر أو فئة مختلفة.",
                            modifier = Modifier.padding(horizontal = 8.dp),
                            color = Color(0xFF667085),
                            fontSize = 11.sp
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (selectedCategory == null) {
                            items(geographyResults, key = { it.id }) { result ->
                                SearchResultRow(
                                    title = result.name,
                                    subtitle = result.displayName,
                                    trailing = "موقع جغرافي",
                                    onClick = { onSelectGeography(result) }
                                )
                            }
                        } else {
                            items(nearbyResults, key = { it.id }) { result ->
                                SearchResultRow(
                                    title = result.name,
                                    subtitle = result.address.ifBlank { result.brand.ifBlank { selectedCategory.title } },
                                    trailing = formatDistance(result.distanceMeters),
                                    onClick = { onSelectPlace(result) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    title: String,
    subtitle: String,
    trailing: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(Color(0xFFF7F9FC))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(34.dp),
            shape = CircleShape,
            color = Color(0xFFEAF3FF)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Color(0xFF287FE8), modifier = Modifier.size(19.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF172B45), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 10.sp, color = Color(0xFF667085), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(trailing, fontSize = 10.sp, color = Color(0xFF287FE8), fontWeight = FontWeight.Medium)
    }
}

private fun formatDistance(meters: Int): String = when {
    meters < 1000 -> "$meters م"
    else -> String.format(java.util.Locale.getDefault(), "%.1f كم", meters / 1000.0)
}
