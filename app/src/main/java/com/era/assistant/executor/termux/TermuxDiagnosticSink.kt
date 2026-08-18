package com.era.assistant.executor.termux

internal interface TermuxDiagnosticSink {
    fun event(name: String, detail: String? = null)
    fun callback(envelope: TermuxResultEnvelope)
}
