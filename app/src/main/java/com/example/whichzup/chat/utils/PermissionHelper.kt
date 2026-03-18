/* Begin, prompt: Generate helper classes and logic to handle runtime permissions for modern Android versions */
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionHelper {

    val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    val cameraPermissions = arrayOf(
        Manifest.permission.CAMERA
    )

    val audioPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO
    )

    fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasLocationPermission(context: Context): Boolean = hasPermissions(context, locationPermissions)
    fun hasCameraPermission(context: Context): Boolean = hasPermissions(context, cameraPermissions)
    fun hasAudioPermission(context: Context): Boolean = hasPermissions(context, audioPermissions)
}
/* End */