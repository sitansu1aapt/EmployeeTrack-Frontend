// Utility to convert Bitmap to MultipartBody.Part for selfie upload
package com.yatri.checkin

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ImageUploadUtil {
    fun bitmapToMultipart(context: Context, bitmap: Bitmap, partName: String = "file"): MultipartBody.Part {
        val file = File(context.cacheDir, "selfie.jpg")
        val fos = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
        fos.flush()
        fos.close()
        val reqFile = RequestBody.create("image/jpeg".toMediaTypeOrNull(), file)
        return MultipartBody.Part.createFormData(partName, file.name, reqFile)
    }

    fun uriToMultipart(context: Context, uri: Uri, partName: String = "file"): MultipartBody.Part {
        val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        return bitmapToMultipart(context, bitmap, partName)
    }
}
