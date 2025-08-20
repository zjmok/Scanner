package com.example.scanner

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.blankj.utilcode.util.UriUtils
import com.example.scanner.databinding.ActivityMainBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.WriterException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.permissionx.guolindev.PermissionX
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents == null) {
            Toast.makeText(this, getString(R.string.cancel_scan), Toast.LENGTH_SHORT).show()
        } else {
            binding.tvFormat.text = result.formatName
            binding.tvColon.text = " : "
            binding.tv.text = result.contents
            binding.et.setText(result.contents)
        }
    }

    private val pickLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_CANCELED) {
                Toast.makeText(this, getString(R.string.cancel_image_selection), Toast.LENGTH_SHORT).show()
            } else if (result.resultCode == RESULT_OK) {
                val intent = result.data
                intent?.data?.let { uri ->
                    parseFromUri(uri)
                } ?: {
                    Toast.makeText(this, getString(R.string.error_occurred), Toast.LENGTH_SHORT).show()
                }
            }
        }

    private fun parseFromUri(uri: Uri) {
        decodeCode(uri) {
            if (it == null) {
                Toast.makeText(this, getString(R.string.parse_error), Toast.LENGTH_SHORT).show()
            } else {
                binding.tvFormat.text = "${it.barcodeFormat}"
                binding.tvColon.text = " : "
                binding.tv.text = it.text
                binding.et.setText(it.text)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnScan.setOnClickListener {
            scanLauncher.launch(ScanOptions().apply {
                setOrientationLocked(false)
                setBeepEnabled(false)
            })
        }

        binding.btnPick.setOnClickListener {
            pickLauncher.launch(Intent(Intent.ACTION_PICK).apply {
                type = "image/*"
            })
        }

        binding.btnCopy.setOnClickListener {
            if (binding.tv.text.isNullOrBlank()) {
                Toast.makeText(this, getString(R.string.no_content), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboardManager.setPrimaryClip(ClipData.newPlainText(packageName, binding.tv.text))
            Toast.makeText(this, "${getString(R.string.copy_success)}:\n${binding.tv.text}", Toast.LENGTH_LONG).show()
        }

        binding.btnCopyEditor.setOnClickListener {
            if (binding.et.text.isNullOrBlank()) {
                Toast.makeText(this, getString(R.string.no_content), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboardManager.setPrimaryClip(ClipData.newPlainText(packageName, binding.et.text))
            Toast.makeText(this, "${getString(R.string.copy_success)}:\n${binding.et.text}", Toast.LENGTH_LONG).show()
        }

        binding.btnCopyGenerate.setOnClickListener {
            if (binding.et.text.isNullOrBlank()) {
                Toast.makeText(this, getString(R.string.no_content), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            generateQRCode(binding.et.text.toString()) {
                binding.iv.setImageBitmap(it)
                binding.iv.tag = true
            }
        }
        binding.iv.setOnLongClickListener {
            if (binding.iv.tag != true || binding.iv.drawable == null) {
                return@setOnLongClickListener true
            }
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.confirmation_prompt))
                .setMessage(getString(R.string.save_image_prompt))
                .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                    val bitmap = (binding.iv.drawable as BitmapDrawable).bitmap
                    saveBitmapToPublicGallery(bitmap, this)
                }
                .setNegativeButton(getString(R.string.cancel)) { _, _ -> }
                .show()
            true
        }

        binding.btnClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.confirmation_prompt))
                .setMessage(getString(R.string.clear_content_warning))
                .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                    binding.tvFormat.text = ""
                    binding.tvColon.text = ""
                    binding.tv.text = ""
                    binding.et.setText("")
                    binding.iv.setImageBitmap(null)
                    binding.iv.tag = null
                }
                .setNegativeButton(getString(R.string.cancel)) { _, _ -> }
                .show()
        }

        val isEnabled = true
        // 使用 OnBackPressedDispatcher
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(isEnabled) {
            override fun handleOnBackPressed() {
                // 在这里处理返回按钮事件
                // 如果需要，可以调用 isEnabled 来控制回调是否有效
                // 如果不想处理返回事件，可以调用 remove() 方法
                onMultiClick({
                    Toast.makeText(this@MainActivity, getString(R.string.press_to_exit, it), Toast.LENGTH_SHORT).show()
                }, {
                    finish()
                })
            }
        })

        setSendingIntent()
    }

    private fun setSendingIntent() {
        // 检查 Intent 类型和数据
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {

            val imageUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }

            imageUri?.let { uri ->
                parseFromUri(uri)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun decodeCode(imageUri: Uri, resultListener: (Result?) -> Unit) {
        Log.d("MainActivity", "decodeQRCode: ${imageUri.path}")
        try {
            val inputStream = contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)

            binding.iv.setImageBitmap(bitmap)
            binding.iv.tag = true

            val intArray = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(intArray, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

            val source = RGBLuminanceSource(bitmap.width, bitmap.height, intArray)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            val reader = MultiFormatReader()

            val result = reader.decode(binaryBitmap)

            resultListener.invoke(result)
        } catch (e: Exception) {
            binding.tvFormat.text = ""
            binding.tvColon.text = ""
            binding.tv.text = ""
            binding.et.setText("")
            binding.iv.setImageBitmap(null)
            binding.iv.tag = null

            if (e is NotFoundException) {
                binding.tv.text = "${getString(R.string.error)} ${getString(R.string.code_not_detected)}"
            } else {
                binding.tv.text = "${getString(R.string.error)} ${e.message}"
                e.printStackTrace()
            }
            resultListener.invoke(null)
        }
    }

    private fun decodeQRCodeFromUri(imageUri: Uri, resultListener: (Result?) -> Unit) {
        var result: Result? = null
        val inputStream: InputStream? = contentResolver.openInputStream(imageUri)
        inputStream?.use {
            val bitmap = BitmapFactory.decodeStream(it)
            if (bitmap != null) {
                val intArray = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(intArray, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

                val source: LuminanceSource = RGBLuminanceSource(bitmap.width, bitmap.height, intArray)
                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

                val reader = MultiFormatReader()

                try {
                    result = reader.decode(binaryBitmap)
                } catch (e: NotFoundException) {
                    // 处理未找到二维码的情况
                } catch (e: Exception) {
                    // 处理其他异常
                }
            }
        }
        resultListener.invoke(result)
    }

    private fun generateQRCode(content: String, result: (Bitmap?) -> Unit) {
        val qrCodeWriter = QRCodeWriter()
        try {
            val bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, 300, 300)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = createBitmap(width, height)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap[x, y] = if (bitMatrix[x, y]) {
                        0xFF000000
                    } else {
                        0xFFFFFFFF
                    }.toInt()
                }
            }
            result.invoke(bitmap)
        } catch (e: WriterException) {
            e.printStackTrace()
        }
    }

    private fun saveBitmapToJpeg(bitmap: Bitmap, context: Context) {
        val publicDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val dir = File(publicDirectory, packageName)
        if (dir.exists().not() && dir.mkdirs().not()) {
            Log.d("TAG", "saveBitmapToJpeg: mkdirs failed")
            Toast.makeText(context, getString(R.string.directory_creation_failed), Toast.LENGTH_SHORT).show()
            return
        }
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val dateString = dateFormat.format(Date())
        val filePath = File(dir, "saved_qrcode_image_$dateString.jpg")
        try {
            FileOutputStream(filePath).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                Log.d("TAG", "saveBitmapToJpeg: filePath = ${filePath.absolutePath}")
                Toast.makeText(
                    context,
                    "${getString(R.string.image_saved)}: ${filePath.absolutePath}",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(context, getString(R.string.save_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBitmapToPublicGallery(bitmap: Bitmap, context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用 MediaStore 在公有目录写入文件
            val contentValues = ContentValues().apply {
                val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                val dateString = dateFormat.format(Date())
                put(MediaStore.Images.Media.DISPLAY_NAME, "saved_qrcode_image_${dateString}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/${packageName}") // 存储目录
            }
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)?.let { uri ->
                val file = UriUtils.uri2File(uri)
                context.contentResolver.openOutputStream(uri).use { outputStream ->
                    if (outputStream != null) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                        Log.d("TAG", "saveBitmapToPublicGallery: uri = $uri")
                        Log.d("TAG", "saveBitmapToPublicGallery: filePath = ${file.absolutePath}")
                        Toast.makeText(
                            context,
                            "${getString(R.string.image_saved)}: ${file.absolutePath}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } ?: {
                Toast.makeText(context, getString(R.string.save_failed), Toast.LENGTH_SHORT).show()
            }
        } else {
            // 处理 Android 10 以下版本的情况
            // 这里可以使用旧的方法直接写入外部存储，需要权限 WRITE_EXTERNAL_STORAGE
            PermissionX.init(this)
                .permissions(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .request { allGranted, grantedList, deniedList ->
                    if (allGranted) {
                        // 有权限后 写入文件
                        saveBitmapToJpeg(bitmap, context)
                    } else {
                        Toast.makeText(this, "These permissions are denied: $deniedList", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            val view = currentFocus
            if (!isInputArea(view, ev)) {
                hideSoftKeyboard()
                view?.clearFocus()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun isInputArea(view: View?, event: MotionEvent): Boolean {
        if (view != null && view is EditText) {
            val area = intArrayOf(0, 0)
            view.getLocationInWindow(area)
            val left = area[0]
            val top = area[1]
            val bottom = top + view.getHeight()
            val right = left + view.getWidth()
            return event.x > left && event.x < right && event.y > top && event.y < bottom
        }
        return false
    }

    private fun hideSoftKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val view = currentFocus
        if (view != null) {
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    companion object {
        private const val SIZE: Int = 2
        private const val INTERVAL: Long = 500
    }

    private var mHints = LongArray(SIZE)

    private fun onMultiClick(continueListener: (Int) -> Unit, doneListener: () -> Unit) {
        System.arraycopy(mHints, 1, mHints, 0, mHints.size - 1) // 每次点击时，数组向前移动一位
        mHints[mHints.size - 1] = SystemClock.uptimeMillis() // 为数组最后一位赋值
        val time = INTERVAL * SIZE
        if (SystemClock.uptimeMillis() - mHints[0] <= time) { // 连续点击之间有效间隔
            mHints = LongArray(SIZE)
            doneListener.invoke()
        } else {
            for (i in 0 until SIZE) {
                if (SystemClock.uptimeMillis() - mHints[i] <= time) {
                    continueListener.invoke(i)
                    break
                }
            }
        }
    }

}