package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.model.GeneratedMedia

@Database(
    entities = [ChatSession::class, ChatMessage::class, GeneratedMedia::class],
    version = 1,
    exportSchema = false
)
abstract class HitutDatabase : RoomDatabase() {
    abstract fun hitutDao(): HitutDao

    companion object {
        @Volatile
        private var INSTANCE: HitutDatabase? = null

        fun getDatabase(context: Context): HitutDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HitutDatabase::class.java,
                    "hitut_ai_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
