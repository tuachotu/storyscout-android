package app.storyscout.android

import android.app.Application
import androidx.room.Room
import app.storyscout.android.data.RecordingRepository
import app.storyscout.android.data.local.SessionStore
import app.storyscout.android.data.local.StoryScoutDatabase
import app.storyscout.android.data.remote.ApiClient
import app.storyscout.android.data.remote.ApiConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StoryScoutApplication : Application() {
    lateinit var repository: RecordingRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(this, StoryScoutDatabase::class.java, "storyscout.db").build()
        val baseUrl = ApiConfiguration.normalizedBaseUrl(BuildConfig.API_BASE_URL, BuildConfig.REJECT_LOOPBACK)
        repository = RecordingRepository(ApiClient(baseUrl), database.recordings(), SessionStore(this))
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            database.recordings().recoverInterruptedCaptures()
        }
    }
}
