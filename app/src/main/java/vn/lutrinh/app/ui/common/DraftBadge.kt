package vn.lutrinh.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import vn.lutrinh.app.R

/** "Nháp" label of a draft step (R20): timeline, step detail and map cards. The "moderate" badge. */
@Composable
fun DraftBadge(modifier: Modifier = Modifier) {
    TopoBadge(stringResource(R.string.step_draft_badge), BadgeTone.Moderate, modifier)
}
