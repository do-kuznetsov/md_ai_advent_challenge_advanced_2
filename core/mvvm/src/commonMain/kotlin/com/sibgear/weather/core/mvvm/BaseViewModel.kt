package com.sibgear.weather.core.mvvm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow

public interface ViewState

public interface ViewEvent

public interface SideEffect

public abstract class BaseViewModel<State : ViewState, Event : ViewEvent, Effect : SideEffect> : ViewModel() {

    public abstract val state: StateFlow<State>

    private val effectChannel: Channel<Effect> = Channel(Channel.BUFFERED)
    private val events: MutableSharedFlow<Event> = MutableSharedFlow(extraBufferCapacity = EVENT_BUFFER_CAPACITY)

    public val effects: Flow<Effect> = effectChannel.receiveAsFlow()

    init {
        events.onEach(::handleViewEvent).launchIn(viewModelScope)
    }

    public fun onViewEventOccurred(event: Event) {
        events.tryEmit(event)
    }

    protected abstract suspend fun handleViewEvent(event: Event)

    protected suspend fun emitEffect(effect: Effect) {
        effectChannel.send(effect)
    }

    override fun onCleared() {
        effectChannel.close()
        super.onCleared()
    }

    private companion object {

        const val EVENT_BUFFER_CAPACITY: Int = 8
    }
}
