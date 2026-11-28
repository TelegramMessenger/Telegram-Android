package dev.moonbag.app.game

enum class AddressStyle { HEX, BASE58, BECH }

/** How a network you build reaches agreement. Costs more or less to launch, and changes how risky its coin is. */
enum class Consensus(val cost: Double) {
    POW(0.0), POS(30_000.0), DPOS(45_000.0), POA(15_000.0)
}

/**
 * Everything you choose when you build your own network. [price] adds up the cost of every choice on top of
 * [ChainSpec.BASE_PRICE].
 */
data class ChainSpec(
    val name: String,
    val symbol: String,
    val color: Long,
    val style: AddressStyle = AddressStyle.HEX,
    val consensus: Consensus = Consensus.POS,
    /** Index into [BLOCK_TIMES]. */
    val blockTime: Int = 0,
    /** Index into [CAPACITY]. */
    val capacity: Int = 0,
    /** Index into [VALIDATORS]. */
    val validators: Int = 0,
    /** 0 = none, 1 = basic, 2 = full security audit. */
    val audit: Int = 0,
    /** Index into [FEES]. */
    val fee: Int = 2,
    /** Index into [SUPPLIES]. */
    val supply: Int = 1,
    val privacy: Boolean = false,
    val contracts: Boolean = false,
    val bridge: Boolean = false,
    val feeBurn: Boolean = false,
    val explorer: Boolean = false,
) {
    fun price(): Double = BASE_PRICE +
        consensus.cost +
        BLOCK_TIME_COST[blockTime.coerceIn(0, BLOCK_TIMES.lastIndex)] +
        CAPACITY_COST[capacity.coerceIn(0, CAPACITY.lastIndex)] +
        VALIDATOR_COST[validators.coerceIn(0, VALIDATORS.lastIndex)] +
        AUDIT_COST[audit.coerceIn(0, 2)] +
        FEE_COST[fee.coerceIn(0, FEES.lastIndex)] +
        SUPPLY_COST[supply.coerceIn(0, SUPPLIES.lastIndex)] +
        (if (privacy) 60_000.0 else 0.0) +
        (if (contracts) 50_000.0 else 0.0) +
        (if (bridge) 40_000.0 else 0.0) +
        (if (feeBurn) 20_000.0 else 0.0) +
        (if (explorer) 15_000.0 else 0.0)

    /** Network fee of one transfer, in play dollars. */
    fun feeUsd(): Double = FEES[fee.coerceIn(0, FEES.lastIndex)]

    /** 1 (shaky) to 5 (very solid); drives how wild the native coin is. */
    fun security(): Int {
        var s = 1
        s += audit
        if (validators >= 2) s += 1
        if (consensus == Consensus.POS || consensus == Consensus.DPOS) s += 0 else if (consensus == Consensus.POA) s -= 1
        if (validators == 0) s -= 1
        return s.coerceIn(1, 5)
    }

    companion object {
        const val BASE_PRICE = 100_000.0
        const val MIN_NAME = 2
        const val MAX_NAME = 20
        const val MAX_CHAINS = 3
        /** Value of the founder allocation of the native coin, at its starting price. */
        const val FOUNDER_USD = 10_000.0
        /** What a coin launched on a network you built pays as a listing fee. */
        const val LISTING_FEE = 500.0

        val BLOCK_TIMES = listOf(60, 12, 3, 1)
        val BLOCK_TIME_COST = listOf(0.0, 15_000.0, 40_000.0, 90_000.0)
        val CAPACITY = listOf(100, 1_000, 10_000, 100_000)
        val CAPACITY_COST = listOf(0.0, 25_000.0, 70_000.0, 180_000.0)
        val VALIDATORS = listOf(10, 100, 1_000, 10_000)
        val VALIDATOR_COST = listOf(0.0, 20_000.0, 60_000.0, 150_000.0)
        val AUDIT_COST = listOf(0.0, 25_000.0, 80_000.0)
        /** Transfer fee choices in play dollars; the cheaper the fee, the more you pay to keep it subsidised. */
        val FEES = listOf(0.001, 0.01, 0.05, 0.4)
        val FEE_COST = listOf(30_000.0, 10_000.0, 0.0, 0.0)
        val SUPPLIES = listOf(1e6, 1e9, 1e12)
        val SUPPLY_COST = listOf(0.0, 10_000.0, 30_000.0)
    }
}

/** A network. Each coin lives on one of them and moving coins costs that network's fee. */
data class Chain(
    val id: String,
    val name: String,
    val symbol: String,
    val color: Long,
    /** Network fee of one transfer, in play dollars. */
    val feeUsd: Double,
    val style: AddressStyle,
    /** What it costs to list a coin on this network. */
    val listingFeeUsd: Double = 2000.0,
    /** Set for networks the player built. */
    val spec: ChainSpec? = null,
) {
    val custom: Boolean get() = spec != null
}

object Chains {

    /** The built-in networks. Built-in coins are spread over these only. */
    val base: List<Chain> = listOf(
        Chain("moon", "Moonchain", "MNC", 0xFF7C4DFF, 0.40, AddressStyle.HEX, 2000.0),
        Chain("lunar", "Lunar L2", "LNR", 0xFF26A69A, 0.03, AddressStyle.HEX, 1500.0),
        Chain("orbit", "Orbit", "ORB", 0xFFFFA726, 0.002, AddressStyle.BASE58, 1200.0),
        Chain("nova", "Nova", "NVA", 0xFF42A5F5, 0.08, AddressStyle.BECH, 2500.0),
    )

    /** Networks built by the player; kept in step with the game state by [sync]. */
    @Volatile private var extra: List<Chain> = emptyList()

    val all: List<Chain> get() = base + extra

    fun sync(chains: List<Chain>) {
        extra = chains
    }

    fun get(id: String): Chain? = base.firstOrNull { it.id == id } ?: extra.firstOrNull { it.id == id }

    /** The network a coin lives on. Built-in coins are spread over the base networks; your own coins live on the one they chose. */
    fun of(coin: CoinDef): Chain =
        if (coin.kind == CoinKind.CUSTOM) (get(coin.chainId) ?: base[0]) else base[Math.floorMod(coin.id.hashCode(), base.size)]

    fun fromSpec(id: String, spec: ChainSpec): Chain =
        Chain(id, spec.name, spec.symbol, spec.color, spec.feeUsd(), spec.style, ChainSpec.LISTING_FEE, spec)
}

enum class ChainError { BAD_NAME, BAD_SYMBOL, SYMBOL_TAKEN, LIMIT_REACHED, NOT_ENOUGH_CASH }

sealed interface ChainResult {
    data class Built(val chain: Chain, val nativeCoin: CustomCoin) : ChainResult
    data class Failed(val error: ChainError) : ChainResult
}

/** Building your own network: a one-off price that depends on every setting, a native coin and a founder allocation. */
object ChainBuilder {

    fun cleanSymbol(input: String): String = Listing.cleanTicker(input)

    fun build(state: GameState, spec: ChainSpec, now: Long): Pair<GameState, ChainResult> {
        fun fail(e: ChainError) = state to ChainResult.Failed(e)
        val name = spec.name.trim().replace(Regex("\\s+"), " ")
        val symbol = cleanSymbol(spec.symbol)
        if (name.length < ChainSpec.MIN_NAME || name.length > ChainSpec.MAX_NAME) return fail(ChainError.BAD_NAME)
        if (symbol.length < Listing.MIN_TICKER || symbol.none { it in 'A'..'Z' }) return fail(ChainError.BAD_SYMBOL)
        if (state.allCoins.any { it.symbol == symbol } || Chains.all.any { it.symbol == symbol } || state.customChains.any { it.symbol == symbol }) {
            return fail(ChainError.SYMBOL_TAKEN)
        }
        if (state.customChains.size >= ChainSpec.MAX_CHAINS) return fail(ChainError.LIMIT_REACHED)
        val price = spec.price()
        if (state.cash + 1e-9 < price) return fail(ChainError.NOT_ENOUGH_CASH)

        val serial = state.coinsListed + 1
        val chainId = "x$serial"
        val clean = spec.copy(name = name, symbol = symbol)
        val chain = Chains.fromSpec(chainId, clean)
        val coinStyle = if (clean.security() >= 3) CoinStyle.MEME else CoinStyle.MOON
        val coin = CustomCoin(
            id = "n$serial", symbol = symbol, name = name, icon = "star", color = clean.color,
            style = coinStyle, serial = serial, createdAt = now, chainId = chainId, native = true,
        )
        val coinPrice = coinStyle.startPrice
        val allocation = ChainSpec.FOUNDER_USD / coinPrice
        val next = state.copy(
            cash = (state.cash - price).coerceAtLeast(0.0),
            customChains = state.customChains + chain,
            customCoins = state.customCoins + coin,
            coinsListed = serial,
            prices = state.prices + (coin.id to coinPrice),
            history = state.history + (coin.id to listOf(coinPrice)),
            holdings = state.holdings + (coin.id to Holding(allocation, ChainSpec.FOUNDER_USD)),
            granted = state.granted + ChainSpec.FOUNDER_USD,
        )
        Chains.sync(next.customChains)
        return next to ChainResult.Built(chain, coin)
    }
}
