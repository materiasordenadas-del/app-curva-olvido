package com.curvadeolvido.app.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {
            TopicEntity.class,
            ReviewEventEntity.class,
            ScheduledReviewEntity.class
        },
        version = 1,
        exportSchema = false)
public abstract class StudyDatabase extends RoomDatabase {
    private static volatile StudyDatabase INSTANCE;

    public abstract StudyDao studyDao();

    public static StudyDatabase get(Context context) {
        if (INSTANCE == null) {
            synchronized (StudyDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE =
                            Room.databaseBuilder(
                                            context.getApplicationContext(),
                                            StudyDatabase.class,
                                            "curva_de_olvido.db")
                                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
