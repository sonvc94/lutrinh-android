package vn.lutrinh.app.ui.picker

import androidx.compose.foundation.clickable
import vn.lutrinh.app.ui.theme.AtlasIcons
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.lutrinh.app.R
import vn.lutrinh.app.domain.usecase.PlaceHit
import vn.lutrinh.app.domain.usecase.PlaceKind
import vn.lutrinh.app.ui.theme.hairline

/** R23: the search field at the top of "Chọn vị trí". */
@Composable
fun PlaceSearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = query,
        onValueChange = { onQuery(it.take(80)) },
        placeholder = { Text(stringResource(R.string.picker_search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(AtlasIcons.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) { Icon(AtlasIcons.Close, stringResource(R.string.picker_search_clear)) }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // the keyboard's search key only hides the keyboard: results are already listed as you type
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        shape = MaterialTheme.shapes.large,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = modifier.testTag("place_search"),
    )
}

/** R23: the ranked results; a tap moves the pin there. */
@Composable
fun PlaceResults(results: List<PlaceHit>, loading: Boolean, tooShort: Boolean, onPick: (PlaceHit) -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest, border = hairline, modifier = modifier) {
        Column {
            when {
                tooShort -> {}
                // while a newer query runs, the previous results stay (no flicker while typing)
                results.isEmpty() && loading -> Message(stringResource(R.string.picker_search_loading))
                results.isEmpty() -> Message(stringResource(R.string.picker_search_empty))
                else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(results, key = { "${it.place.name}|${it.place.location}|${it.place.kind}" }) { hit ->
                        ResultRow(hit, onPick)
                        HorizontalDivider()
                    }
                }
            }
            Text(
                stringResource(R.string.picker_search_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
}

@Composable
private fun ResultRow(hit: PlaceHit, onPick: (PlaceHit) -> Unit) {
    val p = hit.place
    val kind = stringResource(kindLabel(p.kind))
    val where = if (p.kind == PlaceKind.PROVINCE) {
        listOf(kind) + listOfNotNull(p.region.formerNames.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let { stringResource(R.string.picker_search_former, it) })
    } else {
        listOf(kind, p.region.name + (hit.viaFormer?.let { " (${stringResource(R.string.picker_search_former, it)})" } ?: ""))
    }
    Column(Modifier.fillMaxWidth().clickable { onPick(hit) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            where.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

fun kindLabel(k: PlaceKind): Int = when (k) {
    PlaceKind.PROVINCE -> R.string.place_kind_province
    PlaceKind.CITY -> R.string.place_kind_city
    PlaceKind.TOWN -> R.string.place_kind_town
    PlaceKind.COMMUNE -> R.string.place_kind_commune
    PlaceKind.BAY -> R.string.place_kind_bay
    PlaceKind.SIGHT -> R.string.place_kind_sight
    PlaceKind.VIEWPOINT -> R.string.place_kind_viewpoint
    PlaceKind.PEAK -> R.string.place_kind_peak
    PlaceKind.PASS -> R.string.place_kind_pass
    PlaceKind.WATERFALL -> R.string.place_kind_waterfall
    PlaceKind.CAVE -> R.string.place_kind_cave
    PlaceKind.BEACH -> R.string.place_kind_beach
    PlaceKind.ISLAND -> R.string.place_kind_island
    PlaceKind.LAKE -> R.string.place_kind_lake
    PlaceKind.HERITAGE -> R.string.place_kind_heritage
    PlaceKind.WORSHIP -> R.string.place_kind_worship
    PlaceKind.CAMP -> R.string.place_kind_camp
    PlaceKind.VILLAGE -> R.string.place_kind_village
    PlaceKind.HAMLET -> R.string.place_kind_hamlet
}
