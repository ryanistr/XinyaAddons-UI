package com.rianixia.settings.overlay.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
// [FIXED] Updated import to AutoMirrored
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.rianixia.settings.overlay.R
import com.rianixia.settings.overlay.data.AppPreferences
import com.rianixia.settings.overlay.ui.components.MaterialGlassScaffold
import com.rianixia.settings.overlay.ui.components.frostedGlass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
// [FIXED] Removed deprecated hazeChild import if unused, or replaced usage below
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WizardPage(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SetupWizardScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 4 })
    val hazeState = remember { HazeState() }
    
    var showInLauncher by remember { mutableStateOf(false) }
    var safetyModeEnabled by remember { mutableStateOf(true) }

    // Sync with system state
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val currentIconState = AppPreferences.getLauncherIconState(context)
            val currentSafetyState = AppPreferences.getSafetyMode()
            withContext(Dispatchers.Main) {
                showInLauncher = currentIconState
                safetyModeEnabled = currentSafetyState
            }
        }
    }

    val pages = listOf(
        WizardPage(
            stringResource(R.string.setup_welcome_title),
            stringResource(R.string.setup_welcome_desc),
            Icons.Rounded.AutoAwesome,
            Color(0xFF8B5CF6)
        ),
        WizardPage(
            stringResource(R.string.setup_dashboard_title),
            stringResource(R.string.setup_dashboard_desc),
            Icons.Rounded.Speed,
            Color(0xFF06B6D4)
        ),
        WizardPage(
            stringResource(R.string.setup_system_title),
            stringResource(R.string.setup_system_desc),
            Icons.Rounded.SettingsSuggest,
            Color(0xFFEC4899)
        ),
        WizardPage(
            stringResource(R.string.setup_preferences_title),
            stringResource(R.string.setup_preferences_desc),
            Icons.Rounded.Tune,
            Color(0xFF10B981)
        )
    )

    val currentPage = pages.getOrElse(pagerState.currentPage) { pages[0] }
    
    val animatedColor by animateColorAsState(
        targetValue = currentPage.accentColor,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "pageColor"
    )

    BackHandler(enabled = true) {
        if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        }
    }

    MaterialGlassScaffold {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            animatedColor.copy(alpha = 0.15f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            
            // Main Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) { pageIndex ->
                    val page = pages[pageIndex]
                    
                    when (pageIndex) {
                        0 -> WelcomeHeroPage(page, animatedColor)
                        3 -> PreferencesPage(
                            page, animatedColor,
                            showInLauncher, { showInLauncher = it },
                            safetyModeEnabled, { safetyModeEnabled = it }
                        )
                        else -> StandardFeaturePage(page, animatedColor)
                    }
                }
            }

            // Bottom Navigation with Haze
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    // [FIXED] hazeChild -> hazeEffect
                    .hazeEffect(state = hazeState)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Page Indicators
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(pages.size) { index ->
                            PageIndicator(
                                isActive = pagerState.currentPage == index,
                                color = animatedColor
                            )
                        }
                    }

                    // Action Button
                    Button(
                        onClick = {
                            if (pagerState.currentPage < pages.size - 1) {
                                scope.launch { 
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1) 
                                }
                            } else {
                                scope.launch(Dispatchers.IO) {
                                    AppPreferences.setLauncherIconState(context, showInLauncher)
                                    AppPreferences.setSafetyMode(safetyModeEnabled)
                                    AppPreferences.setFirstRunComplete(context)
                                    withContext(Dispatchers.Main) {
                                        navController.navigate("root_pager") {
                                            popUpTo("setup_wizard") { inclusive = true }
                                        }
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = animatedColor,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                    ) {
                        Text(
                            text = if (pagerState.currentPage == pages.size - 1) 
                                stringResource(R.string.setup_btn_get_started) 
                            else 
                                stringResource(R.string.setup_btn_next),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            if (pagerState.currentPage == pages.size - 1) 
                                Icons.Rounded.Check 
                            else 
                                Icons.AutoMirrored.Rounded.ArrowForward, // [FIXED]
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// WELCOME HERO PAGE
// ==========================================
@Composable
fun WelcomeHeroPage(page: WizardPage, color: Color) {
    val backgroundColor = MaterialTheme.colorScheme.background

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Half - Static Image with Fade Out
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.55f)
        ) {
            Image(
                painter = painterResource(id = R.drawable.xinya_toolkit_welcome), 
                contentDescription = stringResource(R.string.cd_welcome_image),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter
            )

            // Gradient Fade Out Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                // Fade to transparent to blend with the static background below
                                backgroundColor.copy(alpha = 0.8f),
                                backgroundColor
                            )
                        )
                    )
            )
        }

        // Bottom Half - Content (Transparent Background)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.45f)
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = stringResource(R.string.setup_hero_title),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                text = page.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
            
            Spacer(Modifier.height(32.dp))
            
            // Feature Pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                FeaturePill(stringResource(R.string.setup_pill_performance), Icons.Rounded.Speed, color)
                FeaturePill(stringResource(R.string.setup_pill_control), Icons.Rounded.Tune, color)
            }
        }
    }
}

// ==========================================
// STANDARD FEATURE PAGE
// ==========================================
@Composable
fun StandardFeaturePage(page: WizardPage, color: Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon with Frosted Glass
        Box(
            modifier = Modifier
                .size(140.dp)
                .frostedGlass(
                    backgroundColor = color.copy(alpha = 0.15f),
                    borderColor = color.copy(alpha = 0.3f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                modifier = Modifier.size(70.dp),
                tint = color
            )
        }

        Spacer(Modifier.height(40.dp))

        // Title
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(16.dp))

        // Description Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .frostedGlass(
                    backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(24.dp)
        ) {
            Text(
                text = page.description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp
            )
        }
    }
}

// ==========================================
// PREFERENCES PAGE
// ==========================================
@Composable
fun PreferencesPage(
    page: WizardPage,
    color: Color,
    showInLauncher: Boolean,
    onShowInLauncherChange: (Boolean) -> Unit,
    safetyModeEnabled: Boolean,
    onSafetyModeChange: (Boolean) -> Unit
) {
    var showSafetyWarning by remember { mutableStateOf(false) }

    if (showSafetyWarning) {
        AlertDialog(
            onDismissRequest = { showSafetyWarning = false },
            icon = { 
                Icon(
                    Icons.Rounded.Warning, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.error 
                ) 
            },
            title = { Text(stringResource(R.string.setup_dialog_safety_title)) },
            text = { 
                Column {
                    Text(stringResource(R.string.setup_dialog_safety_risk_info))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.setup_dialog_safety_warning),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSafetyModeChange(false)
                        showSafetyWarning = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.setup_dialog_safety_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSafetyWarning = false }
                ) {
                    Text(stringResource(R.string.setup_dialog_safety_cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .frostedGlass(
                    backgroundColor = color.copy(alpha = 0.15f),
                    borderColor = color.copy(alpha = 0.3f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = color
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(40.dp))

        // Settings Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .frostedGlass(
                    backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Show in Launcher
            GlassToggleItem(
                title = stringResource(R.string.setup_pref_launcher_title),
                subtitle = stringResource(R.string.setup_pref_launcher_desc),
                icon = Icons.Rounded.AppRegistration,
                checked = showInLauncher,
                onCheckedChange = onShowInLauncherChange,
                color = color
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            // Safety Mode
            GlassToggleItem(
                title = stringResource(R.string.setup_pref_safety_title),
                subtitle = stringResource(R.string.setup_pref_safety_desc),
                icon = Icons.Rounded.Security,
                checked = safetyModeEnabled,
                onCheckedChange = { isChecked ->
                    if (!isChecked) {
                        showSafetyWarning = true
                    } else {
                        onSafetyModeChange(true)
                    }
                },
                color = Color(0xFF10B981)
            )
        }
    }
}

// ==========================================
// HELPER COMPONENTS
// ==========================================
@Composable
fun PageIndicator(isActive: Boolean, color: Color) {
    val width by animateDpAsState(
        targetValue = if (isActive) 32.dp else 8.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "indicator"
    )
    
    Box(
        modifier = Modifier
            .height(8.dp)
            .width(width)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isActive) color else MaterialTheme.colorScheme.outlineVariant
            )
    )
}

@Composable
fun FeaturePill(text: String, icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier
            .frostedGlass(
                backgroundColor = color.copy(alpha = 0.1f),
                borderColor = color.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = color
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun GlassToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = color,
                checkedTrackColor = color.copy(alpha = 0.5f)
            ),
            modifier = Modifier.scale(0.9f)
        )
    }
}