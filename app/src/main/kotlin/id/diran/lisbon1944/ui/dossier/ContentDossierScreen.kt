package id.diran.lisbon1944.ui.dossier

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.diran.lisbon1944.R
import id.diran.lisbon1944.engine.content.ContentLoader
import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.engine.model.EvidenceType
import id.diran.lisbon1944.ui.theme.LisbonColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface DossierState {
    data object Loading : DossierState
    data class Ready(val content: GameContent) : DossierState
    data class Failed(val message: String) : DossierState
}

/**
 * Layar Tahap 1: membuka konten JSON bawaan lewat engine dan menampilkan ringkasannya,
 * membuktikan bahwa :app → :engine → resource konten tersambung di perangkat.
 */
@Composable
fun ContentDossierScreen() {
    var state by remember { mutableStateOf<DossierState>(DossierState.Loading) }
    LaunchedEffect(Unit) {
        state = withContext(Dispatchers.IO) {
            runCatching { ContentLoader.loadDefault() }
                .fold({ DossierState.Ready(it) }, { DossierState.Failed(it.message ?: it.toString()) })
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LisbonColors.Ink)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.title_main), style = MaterialTheme.typography.displayLarge, color = LisbonColors.OldPaper)
            Text(stringResource(R.string.subtitle_main), style = MaterialTheme.typography.headlineMedium, color = LisbonColors.StreetLamp)
            Spacer(Modifier.height(24.dp))
            DossierCard(state)
        }
    }
}

@Composable
private fun DossierCard(state: DossierState) {
    val isLandscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(LisbonColors.OldPaper, RoundedCornerShape(4.dp))
            .border(1.dp, LisbonColors.PaperShade, RoundedCornerShape(4.dp))
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.dossier_title), style = MaterialTheme.typography.titleMedium, color = LisbonColors.Ink)
            when (state) {
                DossierState.Loading -> Text(stringResource(R.string.dossier_loading), style = MaterialTheme.typography.bodyMedium)
                is DossierState.Failed -> Text(
                    stringResource(R.string.dossier_error, state.message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LisbonColors.FolderRed,
                )
                is DossierState.Ready -> ReadyBody(state.content, compact = !isLandscape)
            }
        }
        Text(
            text = stringResource(R.string.stamp_secret),
            style = MaterialTheme.typography.labelLarge,
            color = LisbonColors.FolderRed,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .rotate(-12f)
                .border(2.dp, LisbonColors.FolderRed, RoundedCornerShape(2.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ReadyBody(c: GameContent, compact: Boolean) {
    val body = MaterialTheme.typography.bodyMedium
    val combos = EvidenceType.entries.fold(1) { acc, t -> acc * c.evidenceOfType(t).size }
    Text(stringResource(R.string.dossier_board, c.board.size, c.properties.size, c.escapeRoutes.size), style = body)
    Text(stringResource(R.string.dossier_evidence, c.evidence.size, combos), style = body)
    Text(stringResource(R.string.dossier_fronts, c.fronts.size, c.fronts.first().month, c.fronts.last().month), style = body)
    Text(stringResource(R.string.dossier_decks, c.cipher.size, c.telegram.size), style = body)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.dossier_factions), style = MaterialTheme.typography.titleMedium)
    val rows = if (compact) c.factions.chunked(1) else c.factions.chunked(2)
    rows.forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { f ->
                val accent = parseHex(f.accentColor)
                val badgeDescription = stringResource(R.string.cd_faction_badge, f.organization)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp)
                        .semantics { contentDescription = badgeDescription },
                ) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .background(accent, CircleShape)
                            .border(1.dp, LisbonColors.Ink, CircleShape),
                    )
                    Spacer(Modifier.size(8.dp))
                    Column {
                        Text(f.organization, style = MaterialTheme.typography.labelLarge, color = LisbonColors.Ink)
                        Text(f.abilityText, style = body, color = LisbonColors.InkSoft)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.dossier_stage), style = body, color = LisbonColors.Olive)
}

private fun parseHex(hex: String): Color = Color(0xFF000000 or hex.removePrefix("#").toLong(16))
