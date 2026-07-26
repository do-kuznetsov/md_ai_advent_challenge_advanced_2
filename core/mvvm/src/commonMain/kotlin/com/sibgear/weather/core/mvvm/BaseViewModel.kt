package com.sibgear.weather.core.mvvm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

public abstract class BaseViewModel<State, Event, Effect> : ViewModel() {

    private val mutableEffect: MutableSharedFlow<Effect> = MutableSharedFlow()

    public val effect: SharedFlow<Effect> = mutableEffect.asSharedFlow()

    public fun onViewEventOccurred(event: Event) {
        viewModelScope.launch {
            handleViewEvent(event)
        }
    }

    protected suspend fun emitEffect(effect: Effect) {
        mutableEffect.emit(effect)
    }

    protected abstract suspend fun handleViewEvent(event: Event)
}
