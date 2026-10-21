package id.diran.lisbon1944.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.diran.lisbon1944.R
import id.diran.lisbon1944.engine.model.District
import id.diran.lisbon1944.engine.model.EscapeRouteTile
import id.diran.lisbon1944.session.Choices
import id.diran.lisbon1944.session.RentTable
import id.diran.lisbon1944.ui.common.PaperCard
import id.diran.lisbon1944.ui.common.Palette
import id.diran.lisbon1944.ui.common.SectionTitle
import id.diran.lisbon1944.ui.common.Stamp
import id.diran.lisbon1944.ui.common.ToggleTag
import id.diran.lisbon1944.ui.common.districtPattern
import id.diran.lisbon1944.ui.theme.LisbonColors

/**
 * Properti Saya (BAGIAN 12.2 no. 7): akta per distrik, tombol bangun/jual/gadai/tebus (hanya bila sah sekarang),
 * indikator Sel Mata-Mata (distrik lengkap), pratinjau sewa per level.
 */
@Composable
fun PropertiesPanel(ctx: DecisionCtx) {
    val v = ctx.view
    val content = ctx.content
    val names = ctx.names
    val ops = Choices.tileOps(ctx.legal)
    LazyColumn(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                if (ops.isEmpty()) stringResource(R.string.props_hint_view) else stringResource(R.string.props_hint_act),
                style = MaterialTheme.typography.bodySmall,
                color = LisbonColors.PaperMuted,
            )
        }
        for (d in District.entries) {
            val tiles = content.propertiesIn(d)
            val mine = tiles.count { v.ownership[it.index]?.ownerId == ctx.me }
            if (mine == 0) continue
            item(key = d.name) {
                PaperCard(Modifier.fillMaxWidth(), accent = Palette.district(d)) {
                    Box(Modifier.fillMaxWidth().height(10.dp).background(Palette.district(d)).districtPattern(d, LisbonColors.OldPaper))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Text("${Palette.districtIcon(d)} ${names.district(d)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("$mine / ${tiles.size}", fontWeight = FontWeight.Bold)
                    }
                    if (mine == tiles.size) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Stamp(stringResource(R.string.props_spy_cell), color = LisbonColors.Olive)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.props_spy_cell_hint), style = MaterialTheme.typography.bodySmall, color = LisbonColors.InkMuted)
                        }
                    }
                    tiles.forEach { t ->
                        val own = v.ownership[t.index]
                        val isMine = own?.ownerId == ctx.me
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(names.tile(t.index), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), color = if (isMine) LisbonColors.Ink else LisbonColors.InkMuted)
                                when {
                                    own == null -> Text(stringResource(R.string.props_unowned), fontSize = 11.sp, color = LisbonColors.InkMuted)
                                    !isMine -> Text(ctx.player(own.ownerId), fontSize = 11.sp, color = LisbonColors.InkMuted)
                                    own.mortgaged -> Stamp(stringResource(R.string.stamp_mortgaged))
                                }
                            }
                            if (isMine) {
                                val table = RentTable.property(t, content.balance)
                                val level = own?.level ?: 0
                                val cur = if (level == 0) (if (mine == tiles.size) 1 else 0) else level + 1
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    table.forEachIndexed { i, r ->
                                        Text(
                                            names.money(r).removeSuffix(" esc"),
                                            fontSize = 10.sp,
                                            modifier = Modifier.weight(1f)
                                                .background(if (i == cur) LisbonColors.StreetLamp else LisbonColors.PaperShade)
                                                .padding(vertical = 2.dp),
                                            color = LisbonColors.Ink,
                                            fontWeight = if (i == cur) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                        )
                                    }
                                }
                                Text(stringResource(R.string.props_rent_legend), fontSize = 9.sp, color = LisbonColors.InkMuted)
                                val op = ops[t.index]
                                if (op != null && op.any) {
                                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        op.build?.let { ToggleTag(stringResource(R.string.op_build), false, { ctx.act(it) }, Modifier.weight(1f)) }
                                        op.sell?.let { ToggleTag(stringResource(R.string.op_sell), false, { ctx.act(it) }, Modifier.weight(1f)) }
                                        op.mortgage?.let { ToggleTag(stringResource(R.string.op_mortgage), false, { ctx.act(it) }, Modifier.weight(1f)) }
                                        op.unmortgage?.let { ToggleTag(stringResource(R.string.op_unmortgage), false, { ctx.act(it) }, Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        val routes = content.escapeRoutes.filter { v.ownership[it.index]?.ownerId == ctx.me }
        if (routes.isNotEmpty()) {
            item {
                PaperCard(Modifier.fillMaxWidth(), accent = LisbonColors.TagusBlue) {
                    SectionTitle(stringResource(R.string.tile_escape), color = LisbonColors.TagusBlue)
                    val rent = RentTable.escape(routes.first() as EscapeRouteTile, content.balance).getOrNull(routes.size - 1) ?: 0
                    Text(stringResource(R.string.props_routes, routes.size, names.money(rent)), style = MaterialTheme.typography.bodyMedium)
                    routes.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                            Text(names.tile(r.index), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (v.ownership[r.index]?.mortgaged == true) Stamp(stringResource(R.string.stamp_mortgaged))
                            ops[r.index]?.mortgage?.let { ToggleTag(stringResource(R.string.op_mortgage), false, { ctx.act(it) }) }
                            ops[r.index]?.unmortgage?.let { ToggleTag(stringResource(R.string.op_unmortgage), false, { ctx.act(it) }) }
                        }
                    }
                }
            }
        }
        if (v.ownership.none { it.value.ownerId == ctx.me }) {
            item { Text(stringResource(R.string.props_none), color = LisbonColors.PaperMuted, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
