package com.example.sigaratakip

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sigaratakip.data.SigaraState
import com.example.sigaratakip.ui.SigaraViewModel
import com.example.sigaratakip.ui.theme.DangerRed
import com.example.sigaratakip.ui.theme.OceanBackground
import com.example.sigaratakip.ui.theme.OceanOnSurfaceMuted
import com.example.sigaratakip.ui.theme.OceanPrimary
import com.example.sigaratakip.ui.theme.OceanPrimaryDeep
import com.example.sigaratakip.ui.theme.OceanSecondary
import com.example.sigaratakip.ui.theme.OceanSurface
import com.example.sigaratakip.ui.theme.OceanSurfaceHigh
import com.example.sigaratakip.ui.theme.OceanSurfaceVariant
import com.example.sigaratakip.ui.theme.OceanTertiary
import com.example.sigaratakip.ui.theme.SigaraTakipTheme
import com.example.sigaratakip.ui.theme.SuccessGreen
import com.example.sigaratakip.ui.theme.SuccessGreenLight
import com.example.sigaratakip.ui.theme.WaitingAmber
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: SigaraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SigaraTakipTheme {
                SigaraTakipApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}

@Composable
fun SigaraTakipApp(viewModel: SigaraViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showCalibration by viewModel.showCalibration.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val msgCheckIn = stringRes(R.string.check_in_success)
    val msgAlready = stringRes(R.string.already_checked_in)
    val msgTooEarly = stringRes(R.string.too_early)
    val msgRelapse = stringRes(R.string.relapse_recorded)

    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            val text = when (msg) {
                SigaraViewModel.UiMessage.CheckInSuccess -> msgCheckIn
                SigaraViewModel.UiMessage.AlreadyChecked -> msgAlready
                SigaraViewModel.UiMessage.TooEarly -> msgTooEarly
                SigaraViewModel.UiMessage.RelapseRecorded -> msgRelapse
            }
            snackbarHostState.showSnackbar(text)
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        ensureExactAlarmPermission(context)
    }

    var showRelapseDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = OceanBackground
    ) { innerPadding ->
        HomeScreen(
            state = state,
            innerPadding = innerPadding,
            onCheckIn = viewModel::onCheckInClicked,
            onRelapseClick = { showRelapseDialog = true }
        )
    }

    if (showRelapseDialog) {
        RelapseConfirmDialog(
            onConfirm = {
                showRelapseDialog = false
                viewModel.onRelapseConfirmed()
            },
            onDismiss = { showRelapseDialog = false }
        )
    }

    if (showCalibration) {
        CalibrationDialog(
            initialDays = state.daysPerPeriod,
            initialPacks = state.packsPerPeriod,
            initialPrice = state.pricePerPack,
            onConfirm = viewModel::onCalibrationConfirmed,
            onDismiss = viewModel::onCalibrationDismissed
        )
    }
}

@Composable
private fun HomeScreen(
    state: SigaraState,
    innerPadding: PaddingValues,
    onCheckIn: () -> Unit,
    onRelapseClick: () -> Unit
) {
    val today = LocalDate.now()
    val now = LocalTime.now()
    val checkedIn = state.isCheckedInToday(today)
    val canCheckIn = !now.isBefore(SigaraViewModel.EARLIEST_CHECK_IN) && !checkedIn
    val displayed = state.displayedStreak(today)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        Text(
            text = stringRes(R.string.app_name),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = OceanTertiary,
            letterSpacing = 3.sp
        )

        Spacer(Modifier.height(4.dp))

        BigCircleButton(
            streak = displayed,
            canCheckIn = canCheckIn,
            checkedIn = checkedIn,
            onClick = onCheckIn
        )

        AnimatedVisibility(visible = !canCheckIn && !checkedIn) {
            Text(
                text = stringRes(R.string.check_in_locked),
                color = WaitingAmber,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }

        AnimatedVisibility(visible = !state.notificationsEnabled && !checkedIn) {
            Text(
                text = stringRes(R.string.notifications_paused),
                color = OceanOnSurfaceMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }

        StatsRow(state = state)

        SavedMoneyCard(savedMoney = state.savedMoney())

        RelapseButton(onClick = onRelapseClick)

        LastCheckInText(lastDate = state.lastPressedDate)
    }
}

@Composable
private fun BigCircleButton(
    streak: Int,
    canCheckIn: Boolean,
    checkedIn: Boolean,
    onClick: () -> Unit
) {
    val (centerColor, edgeColor, ringColor) = when {
        checkedIn -> Triple(SuccessGreenLight, SuccessGreen, SuccessGreenLight)
        canCheckIn -> Triple(OceanPrimary, OceanPrimaryDeep, OceanTertiary)
        else -> Triple(OceanSurfaceHigh, OceanSurface, OceanSurfaceVariant)
    }

    val gradient = Brush.radialGradient(
        colors = listOf(
            centerColor.copy(alpha = 0.85f),
            edgeColor.copy(alpha = 0.65f),
            OceanBackground
        )
    )

    Box(
        modifier = Modifier
            .size(260.dp)
            .clip(CircleShape)
            .background(gradient)
            .border(BorderStroke(2.dp, ringColor.copy(alpha = 0.55f)), CircleShape)
            .clickable(enabled = canCheckIn, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringRes(R.string.streak_label),
                fontSize = 12.sp,
                letterSpacing = 4.sp,
                color = Color.White.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = streak.toString(),
                fontSize = 100.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = stringRes(R.string.day_unit),
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    checkedIn -> stringRes(R.string.check_in_done)
                    canCheckIn -> stringRes(R.string.check_in_main)
                    else -> "—"
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.9f),
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun StatsRow(state: SigaraState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            title = stringRes(R.string.best_streak),
            value = state.bestStreak.toString(),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = stringRes(R.string.total_days),
            value = state.totalDays.toString(),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = OceanSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                color = OceanOnSurfaceMuted,
                textAlign = TextAlign.Center,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = OceanTertiary
            )
        }
    }
}

@Composable
private fun SavedMoneyCard(savedMoney: Double) {
    val formatter = remember {
        NumberFormat.getNumberInstance(Locale.forLanguageTag("tr")).apply {
            maximumFractionDigits = 0
        }
    }
    val gradient = Brush.linearGradient(
        colors = listOf(OceanSecondary.copy(alpha = 0.35f), OceanPrimaryDeep.copy(alpha = 0.4f))
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(vertical = 16.dp, horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringRes(R.string.saved_money).uppercase(Locale.forLanguageTag("tr")),
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatter.format(savedMoney),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = stringRes(R.string.saved_money_unit),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RelapseButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, DangerRed.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Text(
            text = stringRes(R.string.relapse_button),
            color = DangerRed,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LastCheckInText(lastDate: LocalDate?) {
    val formatter = remember {
        DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", Locale.forLanguageTag("tr"))
    }
    val text = lastDate?.format(formatter) ?: stringRes(R.string.never)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringRes(R.string.last_check_in),
            fontSize = 11.sp,
            color = OceanOnSurfaceMuted,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun RelapseConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.relapse_dialog_title)) },
        text = { Text(stringRes(R.string.relapse_dialog_msg)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringRes(R.string.relapse_dialog_confirm), color = DangerRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.relapse_dialog_cancel))
            }
        },
        containerColor = OceanSurface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = OceanOnSurfaceMuted
    )
}

@Composable
private fun CalibrationDialog(
    initialDays: Int,
    initialPacks: Int,
    initialPrice: Int,
    onConfirm: (Int, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var daysText by remember { mutableStateOf(initialDays.toString()) }
    var packsText by remember { mutableStateOf(initialPacks.toString()) }
    var priceText by remember { mutableStateOf(initialPrice.toString()) }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.calibration_title)) },
        text = {
            Column {
                Text(
                    text = stringRes(R.string.calibration_desc),
                    fontSize = 14.sp,
                    color = OceanOnSurfaceMuted
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = daysText,
                        onValueChange = { daysText = it.filter(Char::isDigit).take(3) },
                        label = { Text(stringRes(R.string.calibration_days_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = textFieldColors()
                    )
                    OutlinedTextField(
                        value = packsText,
                        onValueChange = { packsText = it.filter(Char::isDigit).take(3) },
                        label = { Text(stringRes(R.string.calibration_packs_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = textFieldColors()
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter(Char::isDigit).take(5) },
                    label = { Text(stringRes(R.string.calibration_price_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringRes(R.string.calibration_hint),
                    fontSize = 12.sp,
                    color = OceanOnSurfaceMuted
                )
                if (error) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringRes(R.string.calibration_invalid),
                        fontSize = 12.sp,
                        color = DangerRed
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val d = daysText.toIntOrNull() ?: 0
                val p = packsText.toIntOrNull() ?: 0
                val price = priceText.toIntOrNull() ?: 0
                if (d > 0 && p > 0 && price > 0) {
                    onConfirm(d, p, price)
                } else {
                    error = true
                }
            }) {
                Text(stringRes(R.string.calibration_confirm), color = OceanTertiary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.calibration_cancel))
            }
        },
        containerColor = OceanSurface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = OceanOnSurfaceMuted
    )
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = OceanTertiary,
    unfocusedBorderColor = OceanOnSurfaceMuted,
    focusedLabelColor = OceanTertiary,
    unfocusedLabelColor = OceanOnSurfaceMuted,
    cursorColor = OceanTertiary,
    focusedContainerColor = OceanSurfaceVariant.copy(alpha = 0.4f),
    unfocusedContainerColor = OceanSurfaceVariant.copy(alpha = 0.25f)
)

@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)

private fun ensureExactAlarmPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!am.canScheduleExactAlarms()) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = "package:${context.packageName}".toUri()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) { }
        }
    }
}
