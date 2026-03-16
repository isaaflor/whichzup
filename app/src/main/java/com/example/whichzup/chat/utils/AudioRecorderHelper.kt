/* Begin, prompt: Generate helper classes to record a short audio message and save it locally */
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException

class AudioRecorderHelper(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null

    fun startRecording(): Result<File> {
        if (!PermissionHelper.hasAudioPermission(context)) {
            return Result.failure(SecurityException("Audio permission not granted"))
        }

        val fileName = "AUDIO_${System.currentTimeMillis()}.m4a"
        currentOutputFile = File(context.cacheDir, fileName)

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(currentOutputFile?.absolutePath)

            try {
                prepare()
                start()
            } catch (e: IOException) {
                return Result.failure(e)
            } catch (e: IllegalStateException) {
                return Result.failure(e)
            }
        }

        return Result.success(currentOutputFile!!)
    }

    fun stopRecording(): Result<File> {
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            if (currentOutputFile != null && currentOutputFile!!.exists()) {
                Result.success(currentOutputFile!!)
            } else {
                Result.failure(Exception("Audio file was not created successfully"))
            }
        } catch (e: Exception) {
            mediaRecorder?.release()
            mediaRecorder = null
            Result.failure(e)
        }
    }
}
/* End */