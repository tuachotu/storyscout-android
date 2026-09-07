package app.storyscout.android.upload

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.storyscout.android.StoryScoutApplication
import app.storyscout.android.data.UploadOutcome

class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_LOCAL_ID) ?: return Result.failure()
        val repository = (applicationContext as StoryScoutApplication).repository
        return when (repository.upload(id)) {
            UploadOutcome.COMPLETE -> Result.success()
            UploadOutcome.RETRY -> Result.retry()
            UploadOutcome.SESSION_EXPIRED, UploadOutcome.UPLOAD_URL_EXPIRED, UploadOutcome.FILE_MISSING -> Result.failure()
        }
    }

    companion object {
        private const val KEY_LOCAL_ID = "local_recording_id"
        fun enqueue(context: Context, localId: String) {
            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(Data.Builder().putString(KEY_LOCAL_ID, localId).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "recording-upload-$localId", ExistingWorkPolicy.KEEP, request,
            )
        }
    }
}
