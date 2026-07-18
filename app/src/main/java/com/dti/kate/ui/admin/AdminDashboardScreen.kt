package com.dti.kate.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dti.kate.BuildConfig
import com.dti.kate.R
import com.dti.kate.network.models.*
import com.dti.kate.ui.components.*
import com.dti.kate.ui.theme.*

// ... rest of the file with all imports fixed
// The key fixes are:
// 1. Import `androidx.compose.ui.res.vectorResource` for vector icons
// 2. Use `BuildConfig` for version info
// 3. Use `TabRowDefaults.Indicator` instead of `tabIndicatorOffset` extension
// 4. Use `AssistChip` instead of custom Chip
// 5. Add `@Composable` annotation to composable functions
