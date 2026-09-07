package app.storyscout.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [RecordingEntity::class], version = 1, exportSchema = true)
abstract class StoryScoutDatabase : RoomDatabase() {
    abstract fun recordings(): RecordingDao
}
