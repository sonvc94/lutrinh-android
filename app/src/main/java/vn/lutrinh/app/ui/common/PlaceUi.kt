package vn.lutrinh.app.ui.common

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vn.lutrinh.app.R
import vn.lutrinh.app.domain.model.GeoPoint
import vn.lutrinh.app.domain.model.Step
import vn.lutrinh.app.domain.usecase.PlaceLabel

/**
 * The one "Chọn trên bản đồ" / "Đổi vị trí" control (F18): a text button with the pin icon, in
 * the step form and in the "Thêm từ ảnh" review alike.
 */
@Composable
fun ChangeLocationButton(hasLocation: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(Icons.Outlined.EditLocationAlt, null, Modifier.size(18.dp))
        Spacer(Modifier.padding(3.dp))
        Text(stringResource(if (hasLocation) R.string.step_location_change else R.string.step_location_pick))
    }
}

/**
 * A place as people read it (F15): "gần <điểm dừng gần nhất>, N km" as the main line, the
 * coordinates as the secondary one; coordinates alone when the trip has no other step.
 * Returns (main, secondary-or-null).
 */
fun placeLines(point: GeoPoint, tripSteps: List<Step>, excludeId: String? = null): Pair<String, String?> {
    val coords = formatCoord(point.latitude, point.longitude)
    val near = PlaceLabel.label(point, tripSteps, excludeId)
    return if (near != null) near to coords else coords to null
}
