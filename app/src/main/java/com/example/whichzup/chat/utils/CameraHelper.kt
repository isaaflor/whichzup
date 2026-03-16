/* Begin, prompt: Generate helper classes to take a picture from within the app and save it locally */
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraHelper(private val context: Context) {

    // Ensure you have a FileProvider defined in your AndroidManifest.xml matching this authority
    private val authority = "${context.packageName}.fileprovider"

    fun createTempImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File = context.cacheDir // No storage permissions needed for cacheDir

        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        )
    }

    fun getUriForFile(file: File): Uri {
        return FileProvider.getUriForFile(context, authority, file)
    }
}
/* End */