package com.englishcar.voicecoach.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishcar.voicecoach.history.FeedbackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    private val feedbackRepository: FeedbackRepository
) : ViewModel() {
    val entries = feedbackRepository.entries.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            feedbackRepository.cleanInvalidEntries()
        }
    }

    fun clear() {
        viewModelScope.launch {
            feedbackRepository.clear()
        }
    }
}
