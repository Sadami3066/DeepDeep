package io.github.whywood.deepdeep.vscreen

internal object VirtualScreenTransportLog {
    fun event(name: String, vararg fields: Pair<String, Any?>): String = buildString {
        append("event=").append(name)
        for ((key, value) in fields) {
            append(' ').append(key).append('=').append(value?.toString() ?: NULL_VALUE)
        }
    }

    fun failure(
        kind: TransportFailureKind,
        backend: String?,
        helperPid: Int?,
        displayId: Int?,
        detail: String,
    ): String = event(
        "transport_lost",
        "reason" to kind.code,
        "backend" to backend,
        "helperPid" to helperPid,
        "displayId" to displayId,
        "detail" to detail,
    )

    private const val NULL_VALUE = "-"
}
