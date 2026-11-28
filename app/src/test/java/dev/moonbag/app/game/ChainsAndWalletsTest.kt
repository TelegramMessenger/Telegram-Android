package dev.moonbag.app.game

import kotlin.random.Random
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChainsAndWalletsTest {

    @After fun cleanUp() = Chains.sync(emptyList())

    private fun rich(cash: Double = 1_000_000.0) = GameState.new(1_000L, Random(1)).copy(cash = cash)

    private fun spec(symbol: String = "ZED") = ChainSpec(name = "Zed Net", symbol = symbol, color = 0xFF2979FF)

    private val cheapest = ChainSpec(
        name = "Min", symbol = "MIN", color = 0, consensus = Consensus.POW, blockTime = 0, capacity = 0,
        validators = 0, audit = 0, fee = 2, supply = 0,
    )

    @Test fun cheapestNetworkCostsExactlyTheMinimum() {
        assertEquals(100_000.0, cheapest.price(), 1e-9)
    }

    @Test fun everySettingRaisesThePrice() {
        val base = cheapest.price()
        assertTrue(cheapest.copy(consensus = Consensus.DPOS).price() > base)
        assertTrue(cheapest.copy(blockTime = 3).price() > base)
        assertTrue(cheapest.copy(capacity = 3).price() > base)
        assertTrue(cheapest.copy(validators = 3).price() > base)
        assertTrue(cheapest.copy(audit = 2).price() > base)
        assertTrue(cheapest.copy(fee = 0).price() > base)
        assertTrue(cheapest.copy(supply = 2).price() > base)
        assertTrue(cheapest.copy(privacy = true).price() > base)
        assertTrue(cheapest.copy(contracts = true).price() > base)
        assertTrue(cheapest.copy(bridge = true).price() > base)
        assertTrue(cheapest.copy(feeBurn = true).price() > base)
        assertTrue(cheapest.copy(explorer = true).price() > base)
    }

    @Test fun buildingTakesThePriceAndGivesACoin() {
        val before = rich()
        val s = spec()
        val (after, result) = ChainBuilder.build(before, s, 5_000L)
        val built = result as ChainResult.Built
        assertEquals(before.cash - s.price(), after.cash, 1e-6)
        assertNotNull(Chains.get(built.chain.id))
        assertEquals(built.chain.id, Chains.of(after.coin(built.nativeCoin.id)!!).id)
        assertTrue(after.holdings.containsKey(built.nativeCoin.id))
        assertEquals(1, after.customChains.size)
    }

    @Test fun buildingNeedsTheMoneyAndAFreshTicker() {
        val poor = ChainBuilder.build(rich(50_000.0), spec(), 1L).second
        assertEquals(ChainError.NOT_ENOUGH_CASH, (poor as ChainResult.Failed).error)
        val taken = ChainBuilder.build(rich(), spec("BTC"), 1L).second
        assertEquals(ChainError.SYMBOL_TAKEN, (taken as ChainResult.Failed).error)
    }

    @Test fun coinsOnYourNetworkPayItsListingFeeAndLiveThere() {
        val (withChain, r) = ChainBuilder.build(rich(), spec(), 1L)
        val chain = (r as ChainResult.Built).chain
        val (after, listed) = Listing.create(withChain, "Moon Cat", "MCAT", "paw", 0xFF7C4DFF, CoinStyle.MEME, 2L, chainId = chain.id)
        val coin = (listed as ListingResult.Listed).coin
        assertEquals(withChain.cash - ChainSpec.LISTING_FEE, after.cash, 1e-6)
        assertEquals(chain.id, Chains.of(after.coin(coin.id)!!).id)
    }

    @Test fun builtNetworksSurviveSaving() {
        val (s, r) = ChainBuilder.build(rich(), spec().copy(consensus = Consensus.DPOS, privacy = true, fee = 0), 1L)
        val chain = (r as ChainResult.Built).chain
        Chains.sync(emptyList())
        val back = GameJson.fromJson(GameJson.toJson(s))
        assertEquals(1, back.customChains.size)
        assertEquals(chain.spec, back.customChains[0].spec)
        assertNotNull(Chains.get(chain.id))
        assertTrue(back.customCoins.any { it.native && it.chainId == chain.id })
    }

    @Test fun switchingWalletsSwapsTheCoins() {
        val s = rich().copy(holdings = mapOf("btc" to Holding(1.0, 90_000.0)), activeWalletId = "w1")
        val b = Wallets.switchTo(s, "w1", "w2")
        assertTrue(b.holdings.isEmpty())
        assertEquals("w2", b.activeWalletId)
        assertEquals(1.0, b.walletStash.getValue("w1").getValue("btc").amount, 1e-9)
        assertEquals(s.netWorth(), b.netWorth(), 1e-6)
        val back = Wallets.switchTo(b, "w2", "w1")
        assertEquals(1.0, back.holdings.getValue("btc").amount, 1e-9)
        assertTrue(back.walletStash.isEmpty() || back.walletStash.values.all { it.isEmpty() })
    }

    @Test fun theFirstWalletKeepsTheCoinsThatWereAlreadyThere() {
        val s = rich().copy(holdings = mapOf("btc" to Holding(1.0, 90_000.0)))
        val a = Wallets.switchTo(s, "", "w1")
        assertEquals("w1", a.activeWalletId)
        assertEquals(1.0, a.holdings.getValue("btc").amount, 1e-9)
    }

    @Test fun sendingToYourOtherWalletMovesTheCoins() {
        val chain = Chains.of(Coins.get("btc")!!)
        val s = rich(10.0).copy(holdings = mapOf("btc" to Holding(1.0, 90_000.0)), activeWalletId = "w1")
        val to = Wallet.rivalAddress("me2", chain)
        val (next, r) = Transfers.send(s, "btc", 0.4, to, emptySet(), 5L, toWalletId = "w2")
        assertTrue(r is SendResult.Ok)
        assertEquals(0.6, next.holdings.getValue("btc").amount, 1e-9)
        assertEquals(0.4, next.walletStash.getValue("w2").getValue("btc").amount, 1e-9)
        // Nothing left the player: only the network fee is gone.
        assertEquals(s.netWorth() - chain.feeUsd, next.netWorth(), 1e-6)
        assertEquals(s.totalProfit() - chain.feeUsd, next.totalProfit(), 1e-6)
    }

    @Test fun droppingAWalletIsNotATradingLoss() {
        val s = rich().copy(holdings = mapOf("btc" to Holding(1.0, 90_000.0)), activeWalletId = "w1")
        val b = Wallets.switchTo(s, "w1", "w2")
        val d = Wallets.drop(b, "w1")
        assertEquals(b.totalProfit(), d.totalProfit(), 1e-6)
        assertTrue(d.walletStash.isEmpty())
    }

    @Test fun walletsAndStashSurviveSaving() {
        val s = rich().copy(holdings = mapOf("btc" to Holding(1.0, 90_000.0)), activeWalletId = "w1")
        val b = Wallets.switchTo(s, "w1", "w2")
        val back = GameJson.fromJson(GameJson.toJson(b))
        assertEquals("w2", back.activeWalletId)
        assertEquals(1.0, back.walletStash.getValue("w1").getValue("btc").amount, 1e-9)
    }

    @Test fun walletListRoundTrips() {
        val words = Wallet.generate(java.util.Random(3))
        val list = listOf(WalletEntry("w1", "Main", WalletEntry.COLORS[2], words, true))
        assertEquals(list, WalletsJson.fromJson(WalletsJson.toJson(list)))
    }
}
