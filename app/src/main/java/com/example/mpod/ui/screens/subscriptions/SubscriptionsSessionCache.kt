package com.example.mpod.ui.screens.subscriptions

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionsSessionCache @Inject constructor() {
    @Volatile
    private var snapshot: SubscriptionsSnapshot? = null

    fun read(): SubscriptionsSnapshot? = snapshot

    fun write(state: SubscriptionsUiState) {
        snapshot = SubscriptionsSnapshot(
            podcasts = state.podcasts,
            actionErrorMessage = state.actionErrorMessage
        )
    }

    fun clear() {
        snapshot = null
    }
}

data class SubscriptionsSnapshot(
    val podcasts: List<SubscriptionPodcastUi>,
    val actionErrorMessage: String?
)
