@file:OptIn(ExperimentalMaterial3Api::class)

package dev.moonbag.app.ui.market

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.moonbag.app.R
import dev.moonbag.app.game.AddressStyle
import dev.moonbag.app.game.Chain
import dev.moonbag.app.game.ChainBuilder
import dev.moonbag.app.game.ChainError
import dev.moonbag.app.game.ChainResult
import dev.moonbag.app.game.ChainSpec
import dev.moonbag.app.game.Chains
import dev.moonbag.app.game.Consensus
import dev.moonbag.app.game.GameState
import dev.moonbag.app.ui.formatCompact
import dev.moonbag.app.ui.formatPrice
import dev.moonbag.app.ui.formatUsd

private val CHAIN_COLORS = listOf(
    0xFF7C4DFF, 0xFF00BFA5, 0xFFFF6D00, 0xFFE91E63, 0xFF2979FF, 0xFFFFC400, 0xFF43A047, 0xFFEF5350,
)

/** Full-screen builder for your own blockchain. The price changes with every setting; the minimum is $100,000. */
@Composable
fun ChainBuilderDialog(
    state: GameState,
    onBuild: (ChainSpec) -> ChainResult,
    onDismiss: () -> Unit,
    onBuilt: (Chain) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            ChainBuilderContent(state, onBuild, onDismiss, onBuilt)
        }
    }
}

@Composable
private fun ChainBuilderContent(
    state: GameState,
    onBuild: (ChainSpec) -> ChainResult,
    onBack: () -> Unit,
    onBuilt: (Chain) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var symbol by rememberSaveable { mutableStateOf("") }
    var colorIndex by rememberSaveable { mutableIntStateOf(0) }
    var style by rememberSaveable { mutableStateOf(AddressStyle.HEX) }
    var consensus by rememberSaveable { mutableStateOf(Consensus.POS) }
    var blockTime by rememberSaveable { mutableIntStateOf(0) }
    var capacity by rememberSaveable { mutableIntStateOf(0) }
    var validators by rememberSaveable { mutableIntStateOf(0) }
    var audit by rememberSaveable { mutableIntStateOf(0) }
    var fee by rememberSaveable { mutableIntStateOf(2) }
    var supply by rememberSaveable { mutableIntStateOf(1) }
    var privacy by rememberSaveable { mutableStateOf(false) }
    var contracts by rememberSaveable { mutableStateOf(false) }
    var bridge by rememberSaveable { mutableStateOf(false) }
    var feeBurn by rememberSaveable { mutableStateOf(false) }
    var explorer by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<ChainError?>(null) }

    val clean = ChainBuilder.cleanSymbol(symbol)
    val spec = ChainSpec(
        name = name, symbol = clean, color = CHAIN_COLORS[colorIndex], style = style, consensus = consensus,
        blockTime = blockTime, capacity = capacity, validators = validators, audit = audit, fee = fee, supply = supply,
        privacy = privacy, contracts = contracts, bridge = bridge, feeBurn = feeBurn, explorer = explorer,
    )
    val price = spec.price()
    val nameOk = name.trim().length in ChainSpec.MIN_NAME..ChainSpec.MAX_NAME
    val taken = clean.isNotEmpty() && (state.allCoins.any { it.symbol == clean } || Chains.all.any { it.symbol == clean })
    val symbolOk = clean.length >= 2 && clean.any { it in 'A'..'Z' } && !taken
    val enough = state.cash + 1e-9 >= price
    val limit = state.customChains.size >= ChainSpec.MAX_CHAINS
    val can = nameOk && symbolOk && enough && !limit

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cb_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!enough) {
                    Text(stringResource(R.string.need_more, formatUsd(price - state.cash)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (limit) Text(stringResource(R.string.cb_limit, ChainSpec.MAX_CHAINS), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = {
                        when (val r = onBuild(spec)) {
                            is ChainResult.Built -> onBuilt(r.chain)
                            is ChainResult.Failed -> error = r.error
                        }
                    },
                    enabled = can,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(stringResource(R.string.cb_build, formatUsd(price)), style = MaterialTheme.typography.titleMedium) }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.cb_intro, formatUsd(ChainSpec.BASE_PRICE)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Live summary
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(Color(spec.color)), contentAlignment = Alignment.Center) {
                        Text(clean.take(3).ifEmpty { "?" }, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(name.trim().ifEmpty { stringResource(R.string.cb_preview_name) }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.cb_summary, spec.security(), formatPrice(spec.feeUsd())),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = name, onValueChange = { name = it.take(ChainSpec.MAX_NAME); error = null },
                label = { Text(stringResource(R.string.cb_name)) }, singleLine = true,
                isError = error == ChainError.BAD_NAME,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = symbol, onValueChange = { symbol = ChainBuilder.cleanSymbol(it); error = null },
                label = { Text(stringResource(R.string.cb_symbol)) }, singleLine = true,
                isError = taken || error == ChainError.BAD_SYMBOL || error == ChainError.SYMBOL_TAKEN,
                supportingText = if (taken) {{ Text(stringResource(R.string.err_ticker_taken)) }} else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CHAIN_COLORS.forEachIndexed { i, c ->
                    Box(Modifier.size(38.dp).clip(CircleShape).background(Color(c)).clickable { colorIndex = i }, contentAlignment = Alignment.Center) {
                        if (i == colorIndex) Icon(Icons.Filled.Check, null, tint = Color.White)
                    }
                }
            }

            Section(R.string.cb_consensus, R.string.cb_consensus_hint)
            Chips(
                Consensus.entries.map { it to consensusLabel(it) + tag(it.cost) },
                consensus,
            ) { consensus = it }

            Section(R.string.cb_block_time, R.string.cb_block_time_hint)
            Chips(
                ChainSpec.BLOCK_TIMES.mapIndexed { i, s -> i to stringResource(R.string.cb_seconds, s) + tag(ChainSpec.BLOCK_TIME_COST[i]) },
                blockTime,
            ) { blockTime = it }

            Section(R.string.cb_capacity, R.string.cb_capacity_hint)
            Chips(
                ChainSpec.CAPACITY.mapIndexed { i, c -> i to stringResource(R.string.cb_tps, formatCompact(c.toDouble())) + tag(ChainSpec.CAPACITY_COST[i]) },
                capacity,
            ) { capacity = it }

            Section(R.string.cb_validators, R.string.cb_validators_hint)
            Chips(
                ChainSpec.VALIDATORS.mapIndexed { i, v -> i to formatCompact(v.toDouble()) + tag(ChainSpec.VALIDATOR_COST[i]) },
                validators,
            ) { validators = it }

            Section(R.string.cb_audit, R.string.cb_audit_hint)
            Chips(
                listOf(
                    0 to stringResource(R.string.cb_audit_none) + tag(ChainSpec.AUDIT_COST[0]),
                    1 to stringResource(R.string.cb_audit_basic) + tag(ChainSpec.AUDIT_COST[1]),
                    2 to stringResource(R.string.cb_audit_full) + tag(ChainSpec.AUDIT_COST[2]),
                ),
                audit,
            ) { audit = it }

            Section(R.string.cb_fee, R.string.cb_fee_hint)
            Chips(
                ChainSpec.FEES.mapIndexed { i, f -> i to formatPrice(f) + tag(ChainSpec.FEE_COST[i]) },
                fee,
            ) { fee = it }

            Section(R.string.cb_supply, R.string.cb_supply_hint)
            Chips(
                ChainSpec.SUPPLIES.mapIndexed { i, s -> i to formatCompact(s) + tag(ChainSpec.SUPPLY_COST[i]) },
                supply,
            ) { supply = it }

            Section(R.string.cb_address, R.string.cb_address_hint)
            Chips(
                listOf(AddressStyle.HEX to "0x… (hex)", AddressStyle.BASE58 to "Base58", AddressStyle.BECH to "Bech32"),
                style,
            ) { style = it }

            Section(R.string.cb_features, null)
            Toggle(R.string.cb_privacy, R.string.cb_privacy_hint, 60_000.0, privacy) { privacy = it }
            Toggle(R.string.cb_contracts, R.string.cb_contracts_hint, 50_000.0, contracts) { contracts = it }
            Toggle(R.string.cb_bridge, R.string.cb_bridge_hint, 40_000.0, bridge) { bridge = it }
            Toggle(R.string.cb_burn, R.string.cb_burn_hint, 20_000.0, feeBurn) { feeBurn = it }
            Toggle(R.string.cb_explorer, R.string.cb_explorer_hint, 15_000.0, explorer) { explorer = it }

            // Price breakdown
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.cb_price), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    PriceLine(stringResource(R.string.cb_base), ChainSpec.BASE_PRICE)
                    val extras = price - ChainSpec.BASE_PRICE
                    PriceLine(stringResource(R.string.cb_options), extras)
                    PriceLine(stringResource(R.string.cb_total), price, bold = true)
                    Text(stringResource(R.string.cb_includes, formatUsd(ChainSpec.FOUNDER_USD), formatUsd(ChainSpec.LISTING_FEE)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun consensusLabel(c: Consensus): String = stringResource(
    when (c) {
        Consensus.POW -> R.string.cb_pow
        Consensus.POS -> R.string.cb_pos
        Consensus.DPOS -> R.string.cb_dpos
        Consensus.POA -> R.string.cb_poa
    },
)

private fun tag(cost: Double): String = if (cost <= 0.0) "" else "  +" + formatCompact(cost)

@Composable
private fun Section(title: Int, hint: Int?) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (hint != null) Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun <T> Chips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
private fun Toggle(title: Int, hint: Int, cost: Double, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title) + tag(cost), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PriceLine(label: String, value: Double, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else null)
        Text(formatUsd(value), style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else null)
    }
}
