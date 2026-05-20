package com.lightxin.feature.update.download

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.lightxin.core.settings.UpdatePrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApkDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updatePrefs: UpdatePrefs,
) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    suspend fun startDownload(downloadUrl: String, versionName: String): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(context, "请授权后重新点击更新", Toast.LENGTH_LONG).show()
            return false
        }

        val apkFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "lightxin-update.apk",
        )
        if (apkFile.exists()) apkFile.delete()

        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle("轻小信更新")
            setDescription("正在下载 v$versionName")
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "lightxin-update.apk")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        }

        val downloadId = downloadManager.enqueue(request)
        updatePrefs.savePendingDownloadId(downloadId)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != downloadId) return
                context.unregisterReceiver(this)
                onDownloadComplete(downloadId)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        return true
    }

    /** 关于页进入时调用，兜底恢复已完成的下载 */
    suspend fun checkPendingDownload() {
        val pendingId = updatePrefs.pendingDownloadId.first()
        if (pendingId <= 0) return

        val query = DownloadManager.Query().setFilterById(pendingId)
        val cursor = downloadManager.query(query)
        if (cursor != null && cursor.moveToFirst()) {
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            cursor.close()
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    installApk()
                    updatePrefs.clearPendingDownloadId()
                }
                DownloadManager.STATUS_FAILED -> {
                    updatePrefs.clearPendingDownloadId()
                }
                // STATUS_RUNNING / STATUS_PAUSED / STATUS_PENDING → 保留 pendingId，不清理
            }
        } else {
            // query 不存在（下载被系统清理）
            cursor?.close()
            updatePrefs.clearPendingDownloadId()
        }
    }

    private fun onDownloadComplete(downloadId: Long) {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        if (cursor != null && cursor.moveToFirst()) {
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            cursor.close()
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                installApk()
            }
        } else {
            cursor?.close()
        }
        CoroutineScope(Dispatchers.IO).launch { updatePrefs.clearPendingDownloadId() }
    }

    private fun installApk() {
        val apkFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "lightxin-update.apk",
        )
        if (!apkFile.exists()) return

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
