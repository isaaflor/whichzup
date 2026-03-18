/* Begin, prompt: Generate helper classes and logic to use GPS to get current location to send in a chat */
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationHelper(private val context: Context) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission") // Ensure PermissionHelper is used before calling this
    suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { continuation ->
        if (!PermissionHelper.hasLocationPermission(context)) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                continuation.resume(location)
            }
            .addOnFailureListener {
                continuation.resume(null)
            }
            .addOnCanceledListener {
                continuation.cancel()
            }
    }
}
/* End */