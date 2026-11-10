package vn.lutrinh.app.ui.tracks

import android.content.Intent
import vn.lutrinh.app.ui.theme.TopoIcons
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vn.lutrinh.app.LuTrinhApp
import vn.lutrinh.app.R
import vn.lutrinh.app.domain.usecase.BatteryGuide
import vn.lutrinh.app.domain.usecase.DeviceMaker
import vn.lutrinh.app.ui.theme.Palette
import vn.lutrinh.app.ui.common.TopoCard
import vn.lutrinh.app.ui.theme.numerals

private class GuideSection(val title: Int, val steps: List<Int>)

private val General = GuideSection(
    R.string.battery_guide_general_title,
    listOf(R.string.battery_guide_general_1, R.string.battery_guide_general_2, R.string.battery_guide_general_3, R.string.battery_guide_general_4),
)

private fun sectionOf(maker: DeviceMaker): GuideSection? = when (maker) {
    DeviceMaker.XIAOMI -> GuideSection(R.string.battery_guide_xiaomi_title, listOf(R.string.battery_guide_xiaomi_1, R.string.battery_guide_xiaomi_2, R.string.battery_guide_xiaomi_3))
    DeviceMaker.OPPO -> GuideSection(R.string.battery_guide_oppo_title, listOf(R.string.battery_guide_oppo_1, R.string.battery_guide_oppo_2, R.string.battery_guide_oppo_3))
    DeviceMaker.SAMSUNG -> GuideSection(R.string.battery_guide_samsung_title, listOf(R.string.battery_guide_samsung_1, R.string.battery_guide_samsung_2, R.string.battery_guide_samsung_3))
    DeviceMaker.VIVO -> GuideSection(R.string.battery_guide_vivo_title, listOf(R.string.battery_guide_vivo_1, R.string.battery_guide_vivo_2, R.string.battery_guide_vivo_3))
    DeviceMaker.OTHER -> null
}

/**
 * "Ghi hành trình ổn định" (R22): what to change so the phone does not stop a recording while the
 * screen is off. The device's own section comes first; the button opens the system battery list
 * (no battery-exemption permission is requested). Opening the screen counts as "seen".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = (context.applicationContext as LuTrinhApp).container.prefs
    LaunchedEffect(Unit) { prefs.batteryGuideOnce.markSeen() }
    val maker = remember { BatteryGuide.makerOf(Build.MANUFACTURER, Build.BRAND) }
    val own = remember(maker) { sectionOf(maker) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.battery_guide_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(TopoIcons.Back, stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(TopoIcons.Battery, null, Modifier.size(28.dp), tint = Palette.SignalDeep)
                Text(stringResource(R.string.battery_guide_intro), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
            }
            if (own != null) {
                SectionCard(stringResource(R.string.battery_guide_your_device, stringResource(own.title)), own.steps, highlighted = true)
            }
            SectionCard(stringResource(General.title), General.steps, highlighted = own == null)
            Text(
                stringResource(R.string.battery_guide_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(shape = MaterialTheme.shapes.small, onClick = { openBatterySettings(context) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(stringResource(R.string.battery_guide_open_settings))
            }
            OutlinedButton(shape = MaterialTheme.shapes.small, onClick = onBack, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(stringResource(R.string.battery_guide_done))
            }
        }
    }
}

/** A guide section as a topo card; the steps are numbered on forest discs (the device's own section) or sand discs. */
@Composable
private fun SectionCard(title: String, steps: List<Int>, highlighted: Boolean) {
    TopoCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        steps.forEachIndexed { i, res ->
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(26.dp).background(
                        if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    ),
                    Alignment.Center,
                ) {
                    Text(
                        "${i + 1}",
                        style = MaterialTheme.typography.labelLarge.numerals(),
                        color = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(stringResource(res), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp, top = 2.dp))
            }
        }
    }
}

/** The system list of battery optimisation (needs no permission); the app's details page if a device lacks it. */
private fun openBatterySettings(context: android.content.Context) {
    val intents = listOf(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )
    for (intent in intents) {
        if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
}
