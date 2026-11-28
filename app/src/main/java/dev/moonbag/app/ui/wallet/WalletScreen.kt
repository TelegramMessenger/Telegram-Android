package dev.moonbag.app.ui.wallet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import dev.moonbag.app.game.WalletEntry
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.moonbag.app.R
import dev.moonbag.app.game.Chains
import dev.moonbag.app.game.GameState
import dev.moonbag.app.game.Nfts
import dev.moonbag.app.game.TxKind
import dev.moonbag.app.game.TradeSide
import dev.moonbag.app.game.Wallet
import dev.moonbag.app.game.WalletInfo
import dev.moonbag.app.ui.components.ChangeChip
import dev.moonbag.app.ui.components.CoinAvatar
import dev.moonbag.app.ui.components.FlashText
import dev.moonbag.app.ui.formatAmount
import dev.moonbag.app.ui.formatSignedUsd
import dev.moonbag.app.ui.formatTime
import dev.moonbag.app.ui.formatUsd
import dev.moonbag.app.ui.nft.NftArt
import dev.moonbag.app.ui.theme.MarketColors

/** One line of the activity list, whatever it came from. */
private data class Activity(val time: Long, val icon: ImageVector, val title: String, val detail: String, val amount: String, val positive: Boolean?)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WalletScreen(
    state: GameState,
    wallet: WalletInfo,
    wallets: List<WalletEntry>,
    onSwitchWallet: (String) -> Unit,
    onAddWallet: () -> Unit,
    onSend: () -> Unit,
    onReceive: () -> Unit,
    onSwap: () -> Unit,
    onEarn: () -> Unit,
    onOpenCoin: (String) -> Unit,
    onAnalytics: () -> Unit,
    onSetup: () -> Unit,
    onBackup: () -> Unit,
) {
    val net = state.netWorth()
    val profit = state.totalProfit()
    val held = state.holdings.keys
        .mapNotNull { state.coin(it) }
        .sortedByDescending { state.holdings.getValue(it.id).amount * state.price(it.id) }
    val activity = activityOf(state)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "cards") {
            if (wallets.isEmpty()) {
                EmptyCard(onSetup)
            } else {
                val activeIndex = wallets.indexOfFirst { it.id == wallet.id }.coerceAtLeast(0)
                val pageCount = wallets.size + if (wallets.size < WalletEntry.MAX) 1 else 0
                val pager = rememberPagerState(initialPage = activeIndex) { pageCount }
                // Follow the active wallet when it changes from somewhere else (the wallet list, for example).
                LaunchedEffect(activeIndex, wallets.size) {
                    if (pager.currentPage != activeIndex && pager.currentPage < wallets.size) pager.animateScrollToPage(activeIndex)
                }
                // Swiping to another card makes that wallet the active one.
                LaunchedEffect(pager, wallets) {
                    snapshotFlow { pager.settledPage }.collect { page ->
                        val target = wallets.getOrNull(page)
                        if (target != null && target.id != wallet.id) onSwitchWallet(target.id)
                    }
                }
                HorizontalPager(
                    state = pager,
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    pageSpacing = 12.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) { page ->
                    val entry = wallets.getOrNull(page)
                    if (entry == null) {
                        AddCard(onAddWallet)
                    } else {
                        val own = entry.id == wallet.id
                        val value = state.walletValue(entry.id) + if (own) state.cash else 0.0
                        WalletCard(entry, value, onReceive = if (own) onReceive else null)
                    }
                }
            }
        }

        item(key = "net") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.net_worth_short, formatUsd(net)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "  " + formatSignedUsd(profit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MarketColors.forChange(profit),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onAnalytics),
                )
            }
        }

        item(key = "actions") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                ActionButton(Icons.Filled.Add, stringResource(R.string.receive), Modifier.weight(1f), onReceive)
                ActionButton(Icons.Filled.ArrowUpward, stringResource(R.string.send), Modifier.weight(1f), onSend)
                ActionButton(Icons.Filled.SwapHoriz, stringResource(R.string.swap), Modifier.weight(1f), onSwap)
                ActionButton(Icons.Filled.Bolt, stringResource(R.string.earn), Modifier.weight(1f), onEarn)
            }
        }

        if (!wallet.exists) {
            item(key = "setup") {
                Banner(
                    title = stringResource(R.string.create_wallet),
                    body = stringResource(R.string.no_wallet_body),
                    button = stringResource(R.string.create_wallet),
                    container = MaterialTheme.colorScheme.primaryContainer,
                    onClick = onSetup,
                )
            }
        } else if (!wallet.backedUp) {
            item(key = "backup") {
                Banner(
                    title = stringResource(R.string.backup_banner_title),
                    body = stringResource(R.string.backup_banner_body),
                    button = stringResource(R.string.backup_now),
                    container = MaterialTheme.colorScheme.errorContainer,
                    onClick = onBackup,
                )
            }
        }

        // Tokens
        item(key = "tokens") {
            SectionCard(null) {
                TokenLine(
                    avatar = {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF2E7D32).copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                            Text("$", fontWeight = FontWeight.Bold, color = Color(0xFF43A047))
                        }
                    },
                    title = stringResource(R.string.usd_token),
                    subtitle = stringResource(R.string.usd_token_desc),
                    value = formatUsd(state.cash),
                    detail = null,
                    onClick = onEarn,
                )
                held.forEach { coin ->
                    val h = state.holdings.getValue(coin.id)
                    val change = state.change(coin.id)
                    TokenLine(
                        avatar = { CoinAvatar(coin) },
                        title = coin.name,
                        subtitle = coin.symbol + " · " + Chains.of(coin).name,
                        titleExtra = if (h.staked > 0) {
                            {
                                Text(
                                    stringResource(R.string.staked_chip),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MarketColors.up(),
                                    modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(6.dp)).background(MarketColors.up().copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        } else null,
                        value = formatUsd(h.amount * state.price(coin.id)),
                        detail = { Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(formatAmount(h.amount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ChangeChip(change)
                        } },
                        onClick = { onOpenCoin(coin.id) },
                    )
                }
                if (held.isEmpty()) Hint(stringResource(R.string.tokens_empty))
            }
        }

        if (state.nfts.isNotEmpty()) {
            item(key = "nfts") {
                SectionCard(stringResource(R.string.nfts_tab)) {
                    state.nfts.take(9).chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (nft in row) {
                                Column(Modifier.weight(1f)) {
                                    NftArt(nft, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)))
                                    Text(formatUsd(Nfts.value(state, nft)), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }

        item(key = "feed") {
            SectionCard(stringResource(R.string.activity)) {
                if (activity.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.activity_empty_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.activity_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
                activity.take(12).forEach { a ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) { Icon(a.icon, null, Modifier.size(20.dp)) }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(a.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                formatTime(a.time) + if (a.detail.isNotEmpty()) " · " + a.detail else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            a.amount,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (a.positive == true) MarketColors.up() else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** A rounded panel with a blue heading, like the sections of a wallet app. */
@Composable
private fun SectionCard(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 6.dp))
            }
            content()
        }
    }
}

/** The big gradient card of one wallet: balance in the middle, name and QR at the bottom. */
@Composable
private fun WalletCard(entry: WalletEntry, value: Double, onReceive: (() -> Unit)?) {
    val base = Color(entry.color)
    val light = lerp(base, Color.White, 0.35f)
    val dark = lerp(base, Color.Black, 0.35f)
    val sparkles = remember(entry.id) {
        val r = java.util.Random(entry.id.hashCode().toLong())
        List(14) { Triple(r.nextFloat(), r.nextFloat(), 1.5f + r.nextFloat() * 3f) }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.linearGradient(listOf(light, base, dark))),
    ) {
        Canvas(Modifier.matchParentSize()) {
            for ((x, y, r) in sparkles) {
                drawCircle(Color.White.copy(alpha = 0.55f), r * density, Offset(x * size.width, y * size.height))
            }
        }
        Text(
            formatUsd(value),
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(entry.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.85f))
            if (onReceive != null) {
                Icon(
                    Icons.Filled.QrCode2, null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onReceive),
                )
            }
        }
    }
}

@Composable
private fun AddCard(onAdd: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onAdd),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, null)
            }
            Text(stringResource(R.string.add_wallet), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun EmptyCard(onSetup: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(210.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onSetup),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.create_wallet), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun activityOf(state: GameState): List<Activity> {
    val out = ArrayList<Activity>()
    val sent = stringResource(R.string.tx_sent)
    val got = stringResource(R.string.tx_received)
    val airdrop = stringResource(R.string.tx_airdrop)
    val swapped = stringResource(R.string.tx_swap)
    val bought = stringResource(R.string.tx_bought)
    val sold = stringResource(R.string.tx_sold)
    for (t in state.walletTxs) {
        val coin = state.coin(t.coinId)?.symbol ?: t.coinId
        val other = state.coin(t.toCoinId)?.symbol ?: t.toCoinId
        when (t.kind) {
            TxKind.SEND -> out += Activity(t.time, Icons.Filled.ArrowUpward, sent.format(coin), Wallet.shorten(t.counterparty), "-" + formatAmount(t.amount), false)
            TxKind.RECEIVE -> out += Activity(t.time, Icons.Filled.ArrowDownward, got.format(coin), "", "+" + formatAmount(t.amount), true)
            TxKind.AIRDROP -> out += Activity(t.time, Icons.Filled.CardGiftcard, airdrop.format(coin), formatUsd(t.valueUsd), "+" + formatAmount(t.amount), true)
            TxKind.SWAP -> out += Activity(t.time, Icons.Filled.SwapHoriz, swapped.format(coin, other), formatUsd(t.valueUsd), "+" + formatAmount(t.toAmount), true)
        }
    }
    for (t in state.trades) {
        val coin = state.coin(t.coinId)?.symbol ?: t.coinId
        out += when (t.side) {
            TradeSide.BUY -> Activity(t.time, Icons.Filled.ShoppingCart, bought.format(coin), formatUsd(t.usd), "+" + formatAmount(t.amount), true)
            TradeSide.SELL -> Activity(t.time, Icons.Filled.Sell, sold.format(coin), formatUsd(t.usd), "-" + formatAmount(t.amount), false)
        }
    }
    return out.sortedByDescending { it.time }.take(60)
}

@Composable
private fun TokenLine(
    avatar: @Composable () -> Unit,
    title: String,
    subtitle: String,
    value: String,
    detail: (@Composable () -> Unit)?,
    onClick: () -> Unit,
    titleExtra: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        avatar()
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                titleExtra?.invoke()
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            detail?.invoke()
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(32.dp),
    )
}

@Composable
private fun Banner(title: String, body: String, button: String, container: Color, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onClick) { Text(button) }
        }
    }
}
