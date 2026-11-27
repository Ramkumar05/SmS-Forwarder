package com.autosmsforwarder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autosmsforwarder.data.ForwardDatabase
import com.autosmsforwarder.data.QueuedMessage
import com.autosmsforwarder.data.WebDeliveryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class QueueViewModel(application: Application) : AndroidViewModel(application) {
    val messages: StateFlow<List<QueuedMessage>> =
        WebDeliveryRepository(ForwardDatabase.get(application)).observeQueue()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
