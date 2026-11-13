package io.github.whywood.deepdeep.plugin.host.extensions.data

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import io.github.whywood.deepdeep.plugin.api.LocationService
import io.github.whywood.deepdeep.plugin.api.PluginGeocode
import io.github.whywood.deepdeep.plugin.api.PluginLocation
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal fun pluginLocation(location: Location): PluginLocation = PluginLocation(
    latitude = location.latitude,
    longitude = location.longitude,
    accuracyM = location.accuracy,
    provider = location.provider.orEmpty(),
    timeMs = location.time,
    altitudeM = location.altitude.takeIf { location.hasAltitude() },
    speedMps = location.speed.takeIf { location.hasSpeed() },
    bearingDeg = location.bearing.takeIf { location.hasBearing() },
)

@SuppressLint("MissingPermission")
internal class LocationPluginServiceImpl(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LocationService {
    private val locationManager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    override val available: Boolean
        get() {
            val manager = locationManager ?: return false
            return runCatching {
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }.getOrDefault(false)
        }

    override val permissionGranted: Boolean
        get() = DataPermissions.locationGranted(context)

    override suspend fun lastKnown(): PluginLocation? = withContext(ioDispatcher) {
        requirePermission()
        val manager = locationManager ?: return@withContext null
        lastKnownCandidates(manager)
            .filterNotNull()
            .maxByOrNull { it.time }
            ?.let(::pluginLocation)
    }

    override suspend fun current(timeoutMs: Long): PluginLocation? {
        requirePermission()
        val manager = locationManager ?: return null
        val provider = bestProvider(manager) ?: return lastKnown()
        val timeout = timeoutMs.coerceIn(1L, MAX_TIMEOUT_MS)
        return withTimeoutOrNull(timeout) {
            callbackFlow {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        trySend(pluginLocation(location))
                        close()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit

                    override fun onProviderEnabled(provider: String) = Unit

                    override fun onProviderDisabled(provider: String) {
                        close()
                    }
                }
                manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                awaitClose { runCatching { manager.removeUpdates(listener) } }
            }.first()
        }
    }

    override fun updates(minIntervalMs: Long, minDistanceM: Float): Flow<PluginLocation> = callbackFlow {
        requirePermission()
        val manager = locationManager ?: run {
            close()
            return@callbackFlow
        }
        val provider = bestProvider(manager) ?: run {
            close()
            return@callbackFlow
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(pluginLocation(location))
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit
        }
        manager.requestLocationUpdates(
            provider,
            minIntervalMs.coerceAtLeast(0L),
            minDistanceM.coerceAtLeast(0f),
            listener,
            Looper.getMainLooper(),
        )
        awaitClose { runCatching { manager.removeUpdates(listener) } }
    }

    override suspend fun geocode(query: String, limit: Int): List<PluginGeocode> = withContext(ioDispatcher) {
        val text = query.trim()
        if (text.isEmpty() || !Geocoder.isPresent()) return@withContext emptyList()
        val geocoder = Geocoder(context, Locale.getDefault())
        runInterruptible {
            @Suppress("DEPRECATION")
            geocoder.getFromLocationName(text, limit.coerceIn(1, MAX_GEOCODE_RESULTS))
        }
            .orEmpty()
            .map(::pluginGeocode)
    }

    override suspend fun reverseGeocode(location: PluginLocation): PluginGeocode? = withContext(ioDispatcher) {
        if (!Geocoder.isPresent()) return@withContext null
        val geocoder = Geocoder(context, Locale.getDefault())
        runInterruptible {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(location.latitude, location.longitude, 1)
        }
            .orEmpty()
            .firstOrNull()
            ?.let(::pluginGeocode)
    }

    private fun bestProvider(manager: LocationManager): String? {
        val enabled = runCatching {
            manager.getProviders(true).filter {
                it == LocationManager.GPS_PROVIDER || it == LocationManager.NETWORK_PROVIDER
            }
        }.getOrDefault(emptyList())
        return enabled.firstOrNull { it == LocationManager.GPS_PROVIDER }
            ?: enabled.firstOrNull { it == LocationManager.NETWORK_PROVIDER }
    }

    private fun lastKnownCandidates(manager: LocationManager): List<Location?> = runCatching {
        listOf(
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER),
            manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER),
            manager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER),
        )
    }.getOrDefault(emptyList())

    private fun requirePermission() {
        if (!permissionGranted) throw SecurityException(DataPermissions.locationGrantedMessage())
    }

    private fun pluginGeocode(address: android.location.Address): PluginGeocode = PluginGeocode(
        latitude = address.latitude,
        longitude = address.longitude,
        label = address.featureName?.takeIf { it.isNotBlank() } ?: address.getAddressLine(0),
        country = address.countryName?.takeIf { it.isNotBlank() },
        adminArea = address.adminArea?.takeIf { it.isNotBlank() },
        locality = address.locality?.takeIf { it.isNotBlank() },
    )

    private companion object {
        const val MAX_TIMEOUT_MS = 60_000L

        const val MAX_GEOCODE_RESULTS = 10
    }
}
