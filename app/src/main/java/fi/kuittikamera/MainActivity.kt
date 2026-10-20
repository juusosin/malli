package fi.kuittikamera

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import fi.kuittikamera.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Kameran kirjoittama alkuperäinen kuva, odottaa käsittelyä. */
    private var pendingRawFile: File? = null

    /** Pienennetty ja oikein päin käännetty kuva, joka liitetään sähköpostiin. */
    private var receiptFile: File? = null

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val raw = pendingRawFile ?: return@registerForActivityResult
            pendingRawFile = null
            if (success && raw.length() > 0) processPhoto(raw) else raw.delete()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            view.updatePadding(top = bars.top, bottom = bars.bottom, left = bars.left, right = bars.right)
            insets
        }

        binding.emailInput.setText(prefs().getString(KEY_EMAIL, ""))

        savedInstanceState?.let { state ->
            pendingRawFile = state.getString(STATE_PENDING_RAW)?.let(::File)
            receiptFile = state.getString(STATE_RECEIPT)?.let(::File)?.takeIf { it.exists() }
        }
        showReceipt()

        binding.takePhotoButton.setOnClickListener { launchCamera() }
        binding.sendButton.setOnClickListener { sendEmail() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PENDING_RAW, pendingRawFile?.absolutePath)
        outState.putString(STATE_RECEIPT, receiptFile?.absolutePath)
    }

    private fun launchCamera() {
        val raw = File(receiptsDir(), "raw_${System.currentTimeMillis()}.jpg")
        pendingRawFile = raw
        try {
            takePicture.launch(uriFor(raw))
        } catch (e: ActivityNotFoundException) {
            pendingRawFile = null
            toast(R.string.error_no_camera)
        }
    }

    private fun processPhoto(raw: File) {
        binding.takePhotoButton.isEnabled = false
        lifecycleScope.launch {
            val stamp = LocalDateTime.now().format(FILE_TIMESTAMP)
            val out = File(receiptsDir(), "kuitti_$stamp.jpg")
            val ok = withContext(Dispatchers.IO) {
                runCatching { shrinkAndRotate(raw, out) }.isSuccess.also { raw.delete() }
            }
            binding.takePhotoButton.isEnabled = true
            if (ok) {
                receiptFile?.takeIf { it != out }?.delete()
                receiptFile = out
            } else {
                toast(R.string.error_processing)
            }
            showReceipt()
        }
    }

    private fun showReceipt() {
        val file = receiptFile
        if (file != null) {
            binding.preview.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
            binding.noPhotoText.visibility = View.GONE
            binding.takePhotoButton.setText(R.string.retake_photo)
        } else {
            binding.preview.setImageDrawable(null)
            binding.noPhotoText.visibility = View.VISIBLE
            binding.takePhotoButton.setText(R.string.take_photo)
        }
        binding.sendButton.isEnabled = file != null
    }

    private fun sendEmail() {
        val file = receiptFile ?: return
        val email = binding.emailInput.text?.toString()?.trim().orEmpty()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInput.error = getString(R.string.error_invalid_email)
            return
        }
        prefs().edit { putString(KEY_EMAIL, email) }

        val date = LocalDateTime.now().format(DISPLAY_DATE)
        val note = binding.noteInput.text?.toString()?.trim().orEmpty()
        val body = buildString {
            append(getString(R.string.email_body, date))
            if (note.isNotEmpty()) append(getString(R.string.email_body_note, note))
        }
        val subject = if (note.isNotEmpty()) {
            getString(R.string.email_subject, "$date – $note")
        } else {
            getString(R.string.email_subject, date)
        }
        val uri = uriFor(file)

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Rajaa valinnan sähköpostisovelluksiin (ei esim. WhatsAppia).
            selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        }
        try {
            startActivity(Intent.createChooser(send, getString(R.string.chooser_title)))
        } catch (e: ActivityNotFoundException) {
            toast(R.string.error_no_email_app)
        }
    }

    private fun receiptsDir() = File(cacheDir, "receipts").apply { mkdirs() }

    private fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

    private fun prefs() = getSharedPreferences("settings", MODE_PRIVATE)

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_LONG).show()

    companion object {
        private const val KEY_EMAIL = "email"
        private const val STATE_PENDING_RAW = "pendingRaw"
        private const val STATE_RECEIPT = "receipt"

        /** Pidempi sivu pikseleinä: kuitti pysyy luettavana, mutta liite on alle ~1 Mt. */
        private const val MAX_DIMENSION = 2000
        private const val JPEG_QUALITY = 85

        private val FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")
        private val DISPLAY_DATE = DateTimeFormatter.ofPattern("d.M.yyyy")

        /**
         * Pienentää kameran täysikokoisen kuvan ja kääntää sen EXIF-tiedon mukaan oikein päin,
         * jotta liite on kevyt ja näkyy oikein kaikissa sähköpostiohjelmissa.
         */
        private fun shrinkAndRotate(source: File, target: File) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, bounds)
            val longest = max(bounds.outWidth, bounds.outHeight)
            require(longest > 0) { "Kuvaa ei voitu lukea" }

            var sample = 1
            while (longest / (sample * 2) >= MAX_DIMENSION) sample *= 2
            val decoded = BitmapFactory.decodeFile(
                source.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: error("Kuvaa ei voitu lukea")

            val scale = minOf(1f, MAX_DIMENSION.toFloat() / max(decoded.width, decoded.height))
            val matrix = Matrix().apply {
                postScale(scale, scale)
                postRotate(exifRotation(source).toFloat())
            }
            val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

            target.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            if (result !== decoded) result.recycle()
            decoded.recycle()
        }

        private fun exifRotation(file: File): Int =
            when (ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
    }
}
