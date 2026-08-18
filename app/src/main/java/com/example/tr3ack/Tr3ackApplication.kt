package com.example.tr3ack

import android.app.Application
import com.example.tr3ack.data.database.Tr3ackDatabase
import com.example.tr3ack.repository.Tr3ackRepository

class Tr3ackApplication : Application() {
    val database by lazy { Tr3ackDatabase.getDatabase(this) }
    val repository by lazy {
        Tr3ackRepository(
            database.exerciseDao(),
            database.bodyWeightDao(),
            database.workoutSetDao()
        )
    }
}
