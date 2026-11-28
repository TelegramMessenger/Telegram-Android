package dev.moonbag.app.game

/** Moving between the player's wallets. Cash and NFTs are shared; coins belong to one wallet. */
object Wallets {

    /** Makes [toId] the wallet whose coins are in [GameState.holdings]. [fromId] is the wallet that was active. */
    fun switchTo(state: GameState, fromId: String, toId: String): GameState {
        if (fromId == toId && state.activeWalletId == toId) return state
        val current = state.activeWalletId.ifEmpty { fromId }
        // The very first wallet takes over the coins that were already there.
        if (current.isEmpty()) return state.copy(activeWalletId = toId)
        if (current == toId) return state.copy(activeWalletId = toId)
        val stash = state.walletStash + (current to state.holdings)
        return state.copy(
            holdings = stash[toId].orEmpty(),
            walletStash = stash - toId,
            activeWalletId = toId,
        )
    }

    /** Forgets a wallet that is not active. Its coins are gone; they do not count as a loss. */
    fun drop(state: GameState, id: String): GameState {
        val lost = state.walletStash[id]?.let { state.stashValue(it) } ?: 0.0
        return state.copy(walletStash = state.walletStash - id, granted = state.granted - lost)
    }
}
