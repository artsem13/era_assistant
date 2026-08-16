package com.era.assistant.core.blackbox

enum class BlackBoxEndReason(val wireName: String) {
    TIMER_FINISHED("TIMER_FINISHED"),
    USER_STOPPED("USER_STOPPED"),
    APP_SHUTDOWN("APP_SHUTDOWN"),
    ERROR("ERROR")
}
