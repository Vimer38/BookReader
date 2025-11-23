package com.example.bookreader.data.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferNetworkLossHandler
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.amazonaws.services.s3.model.ObjectMetadata
import com.example.bookreader.BuildConfig
import java.io.File
import java.io.OutputStream
import java.io.InputStream
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.DEFAULT_BUFFER_SIZE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext


class YandexStorageManager(context: Context) {

    private val appContext = context.applicationContext
    private val bucket = BuildConfig.YC_BUCKET
    private val endpoint = BuildConfig.YC_ENDPOINT.trimEnd('/')
    private val s3Region = resolveRegion(BuildConfig.YC_REGION)
    private val credentials = BasicAWSCredentials(BuildConfig.YC_ACCESS_KEY, BuildConfig.YC_SECRET_KEY)
    private val s3Client = AmazonS3Client(
        BasicAWSCredentials(BuildConfig.YC_ACCESS_KEY, BuildConfig.YC_SECRET_KEY),
        Region.getRegion(Regions.EU_CENTRAL_1)
    ).apply {
        setEndpoint("https://storage.yandexcloud.net")
    }
    private val transferUtility: TransferUtility = TransferUtility.builder()
        .context(appContext)
        .s3Client(s3Client)
        .build()

    init {
        Log.d("YandexStorage", "Access Key preview: ${BuildConfig.YC_ACCESS_KEY.take(4)}...${BuildConfig.YC_ACCESS_KEY.takeLast(4)}")
        Log.d("YandexStorage", "Secret Key preview: ${BuildConfig.YC_SECRET_KEY.take(4)}...${BuildConfig.YC_SECRET_KEY.takeLast(4)}")
        require(bucket.isNotBlank()) { "YC_BUCKET is not configured" }
        require(BuildConfig.YC_ACCESS_KEY.isNotBlank() && BuildConfig.YC_SECRET_KEY.isNotBlank()) {
            "Yandex Cloud credentials are not configured"
        }
        TransferNetworkLossHandler.getInstance(appContext)
        Log.d(
            "YandexStorage",
            "Init bucket=$bucket endpoint=$endpoint region=${s3Region.name} accessKeySuffix=${BuildConfig.YC_ACCESS_KEY.takeLast(4)}"
        )
    }

    suspend fun upload(
        uri: Uri,
        contentResolver: ContentResolver,
        objectKey: String,
        onProgress: (Float) -> Unit
    ): String {
        val tempFile = copyUriToTemp(uri, contentResolver)
        val metadata = ObjectMetadata().apply {
            contentType = contentResolver.getType(uri) ?: "application/octet-stream"
        }
        return suspendCancellableCoroutine { continuation ->
            val observer = transferUtility.upload(
                bucket,
                objectKey,
                tempFile,
                metadata,
                CannedAccessControlList.PublicRead
            )
            observer.setTransferListener(object : TransferListener {
                override fun onStateChanged(id: Int, state: TransferState?) {
                    when (state) {
                        TransferState.COMPLETED -> {
                            tempFile.delete()
                            if (!continuation.isCompleted) {
                                continuation.resume(buildPublicUrl(objectKey))
                            }
                        }

                        TransferState.CANCELED, TransferState.FAILED -> {
                            tempFile.delete()
                            if (!continuation.isCompleted) {
                                continuation.resumeWithException(
                                    IllegalStateException("Upload failed: $state")
                                )
                            }
                        }

                        else -> Unit
                    }
                }

                override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
                    if (bytesTotal > 0) {
                        val fraction = bytesCurrent.toFloat() / bytesTotal.toFloat()
                        onProgress(fraction)
                    }
                }

                override fun onError(id: Int, ex: Exception?) {
                    tempFile.delete()
                    if (!continuation.isCompleted) {
                        continuation.resumeWithException(ex ?: IllegalStateException("Unknown error"))
                    }
                }
            })

            continuation.invokeOnCancellation {
                observer.cleanTransferListener()
                tempFile.delete()
            }
        }
    }

    suspend fun download(
        fileUrl: String,
        destination: File,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val connection = URL(fileUrl).openConnection()
        val total = connection.contentLengthLong
        connection.getInputStream().use { input ->
            destination.outputStream().use { output ->
                copyStream(input, output, total, onProgress)
            }
        }
    }

    private fun buildPublicUrl(objectKey: String): String =
        "$endpoint/$bucket/$objectKey"

    private fun resolveRegion(regionName: String): Regions {
        val normalized = regionName.lowercase().trim()
        return when (normalized) {
            "ru-central1", "ru-central1-a", "ru-central1-b", "ru-central1-c" -> Regions.EU_CENTRAL_1
            "ru-central", "moscow" -> Regions.EU_CENTRAL_1
            else -> try {
                Regions.fromName(regionName)
            } catch (e: IllegalArgumentException) {
                Regions.DEFAULT_REGION
            }
        }
    }

    private fun copyUriToTemp(uri: Uri, resolver: ContentResolver): File {
        val tempFile = File.createTempFile("yc_upload_", null, appContext.cacheDir)
        resolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("Не удалось открыть выбранный файл")
        return tempFile
    }

    private fun copyStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        totalBytes: Long,
        onProgress: (Int) -> Unit
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var bytesRead: Int
        var transferred = 0L
        while (true) {
            bytesRead = inputStream.read(buffer)
            if (bytesRead <= 0) break
            outputStream.write(buffer, 0, bytesRead)
            transferred += bytesRead
            if (totalBytes > 0) {
                val percent = ((transferred * 100) / totalBytes).toInt()
                onProgress(percent)
            }
        }
        onProgress(100)
    }
}

fun AmazonS3Client.setV4Signer() {
    try {
        val method = this.javaClass.getMethod("setSignerOverride", String::class.java)
        method.invoke(this, "AWSS3V4SignerType")
        Log.d("S3", "V4 signer successfully set")
    } catch (e: Exception) {
        Log.e("S3", "Failed to set V4 signer", e)
        throw RuntimeException("Could not set S3 V4 signer. This is required for Yandex Cloud.", e)
    }
}
