package io.github.whywood.deepdeep.ui.remote

import androidx.annotation.StringRes
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.remote.RemoteGatewayFailure
import io.github.whywood.deepdeep.data.remote.RemoteReachability

@StringRes
internal fun RemoteReachability.labelRes(): Int = when (this) {
    RemoteReachability.Unknown -> R.string.remote_state_unknown
    RemoteReachability.OnlineLan -> R.string.remote_state_online_lan
    RemoteReachability.OnlinePublic -> R.string.remote_state_online_remote
    RemoteReachability.Offline -> R.string.remote_state_offline
    RemoteReachability.NeedsPairing -> R.string.remote_state_needs_pairing
    RemoteReachability.CertChanged -> R.string.remote_state_cert_changed
}

@StringRes
internal fun RemoteGatewayFailure?.pairMessageRes(): Int = when (this) {
    RemoteGatewayFailure.Unauthorized -> R.string.remote_pair_expired
    RemoteGatewayFailure.Forbidden -> R.string.remote_pair_rejected
    else -> messageRes()
}

internal fun RemoteGatewayFailure?.requiresNewPairingCode(): Boolean =
    this == RemoteGatewayFailure.Unauthorized || this == RemoteGatewayFailure.Forbidden

@StringRes
internal fun RemoteGatewayFailure?.messageRes(): Int = when (this) {
    RemoteGatewayFailure.Unauthorized -> R.string.remote_failure_unauthorized
    RemoteGatewayFailure.Forbidden -> R.string.remote_failure_forbidden
    RemoteGatewayFailure.DeviceLimit -> R.string.remote_failure_device_limit
    RemoteGatewayFailure.RateLimited -> R.string.remote_failure_rate_limited
    RemoteGatewayFailure.Unreachable -> R.string.remote_failure_unreachable
    RemoteGatewayFailure.CertificateRejected -> R.string.remote_failure_certificate
    RemoteGatewayFailure.BadResponse -> R.string.remote_failure_bad_response
    null -> R.string.remote_failure_unknown
}
