@file:OptIn(ExperimentalMaterial3Api::class)

package dev.moonbag.app.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.moonbag.app.R
import dev.moonbag.app.game.GameState
import dev.moonbag.app.game.Chains
import dev.moonbag.app.game.Wallet
import dev.moonbag.app.game.WalletEntry
import dev.moonbag.app.ui.formatUsd

/** All wallets: switch, rename, recolour, delete, add or import another one. */
@Composable
fun WalletsScreen(
    state: GameState,
    wallets: List<WalletEntry>,
    activeId: String,
    onSwitch: (String) -> Unit,
    onEdit: (id: String, name: String, color: Long) -> Unit,
    onDelete: (String) -> Boolean,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf<WalletEntry?>(null) }
    var deleting by remember { mutableStateOf<WalletEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wallets_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.tab_settings)) } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(wallets, key = { it.id }) { w ->
                val active = w.id == activeId
                val address = w.info().addresses[Chains.base.first().id].orEmpty()
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth().then(if (active) Modifier.border(2.dp, Color(w.color), RoundedCornerShape(22.dp)) else Modifier).clickable { onSwitch(w.id); onBack() },
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(Color(w.color)), contentAlignment = Alignment.Center) {
                            if (active) Icon(Icons.Filled.Check, null, tint = Color.White)
                            else Text(w.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(w.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Wallet.shorten(address), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                stringResource(if (w.backedUp) R.string.wallet_backed_up else R.string.wallet_not_backed_up),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (w.backedUp) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatUsd(state.walletValue(w.id)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Row {
                                IconButton(onClick = { editing = w }) { Icon(Icons.Filled.Edit, stringResource(R.string.wallet_edit), Modifier.size(20.dp)) }
                                if (!active && wallets.size > 1) {
                                    IconButton(onClick = { deleting = w }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.wallets_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
                Spacer(Modifier.height(6.dp))
                if (wallets.size < WalletEntry.MAX) {
                    OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.add_wallet), Modifier.padding(start = 8.dp))
                    }
                } else {
                    Text(stringResource(R.string.wallets_max, WalletEntry.MAX), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    editing?.let { w ->
        var name by remember(w.id) { mutableStateOf(w.name) }
        var color by remember(w.id) { mutableStateOf(w.color) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.wallet_edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(20) }, label = { Text(stringResource(R.string.wallet_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WalletEntry.COLORS.forEach { c ->
                            Box(
                                Modifier.size(38.dp).clip(CircleShape).background(Color(c)).clickable { color = c },
                                contentAlignment = Alignment.Center,
                            ) { if (c == color) Icon(Icons.Filled.Check, null, tint = Color.White) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onEdit(w.id, name, color); editing = null }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    deleting?.let { w ->
        val lost = state.walletValue(w.id)
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.wallet_delete_title, w.name)) },
            text = {
                Text(
                    stringResource(if (lost > 0.01) R.string.wallet_delete_body_coins else R.string.wallet_delete_body, formatUsd(lost)),
                )
            },
            confirmButton = { TextButton(onClick = { onDelete(w.id); deleting = null }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
