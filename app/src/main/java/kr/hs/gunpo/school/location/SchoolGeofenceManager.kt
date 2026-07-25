package kr.hs.gunpo.school.location

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Address
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kr.hs.gunpo.school.data.SettingsRepository
import kr.hs.gunpo.school.notification.SchoolNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

object SchoolGeofenceManager {
    private const val GEOFENCE_ID = "gunpo_high_school"

    suspend fun enable(context: Context): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return false
        val address = suspendCancellableCoroutine<Address?> { continuation ->
            Geocoder(context, Locale.KOREA).getFromLocationName("군포고등학교 경기도 군포시 오금로 118", 1) { results ->
                if (continuation.isActive) continuation.resume(results.firstOrNull())
            }
        } ?: return false
        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(address.latitude, address.longitude, 200f)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()
        return suspendCancellableCoroutine { continuation ->
            LocationServices.getGeofencingClient(context).addGeofences(request, pendingIntent(context))
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(true) }
                .addOnFailureListener { if (continuation.isActive) continuation.resume(false) }
        }
    }

    fun disable(context: Context) {
        LocationServices.getGeofencingClient(context).removeGeofences(pendingIntent(context))
    }

    private fun pendingIntent(context: Context) = PendingIntent.getBroadcast(
        context,
        7731,
        Intent(context, SchoolGeofenceReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
}

class SchoolGeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = SettingsRepository(context)
                when (event.geofenceTransition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> {
                        repository.updateLiveUpdates(true)
                        SchoolNotificationManager.refresh(context)
                    }
                    Geofence.GEOFENCE_TRANSITION_EXIT -> {
                        repository.updateLiveUpdates(false)
                        SchoolNotificationManager.stop(context)
                    }
                }
            } finally { pending.finish() }
        }
    }
}
