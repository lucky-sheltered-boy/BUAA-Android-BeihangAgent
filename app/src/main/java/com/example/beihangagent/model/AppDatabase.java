package com.example.beihangagent.model;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.beihangagent.util.UserProfileDao;

@Database(entities = {User.class, QuestionStat.class, ChatMessage.class, Conversation.class, Class.class, ClassMember.class, Post.class, Comment.class, PostLike.class, Notification.class, UserProfile.class, ConversationRecord.class}, version = 17, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract UserDao userDao();
    public abstract QuestionStatDao questionStatDao();
    public abstract ChatMessageDao chatMessageDao();
    public abstract ConversationDao conversationDao();
    public abstract ClassDao classDao();
    public abstract PostDao postDao();
    public abstract CommentDao commentDao();
    public abstract PostLikeDao postLikeDao();
    public abstract NotificationDao notificationDao();
    public abstract UserProfileDao userProfileDao();

    private static volatile AppDatabase INSTANCE;

    // Migration from version 13 to 14
    static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Version 14 is just a version bump for consistency - no schema changes needed
        }
    };

    // Migration from version 11 (avatar only) to 14 - Add forum tables
    static final Migration MIGRATION_11_14 = new Migration(11, 14) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Create forum tables (skipping avatar fields as they already exist in version 11)
            database.execSQL("CREATE TABLE IF NOT EXISTS `posts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `class_id` INTEGER NOT NULL, `user_id` INTEGER NOT NULL, `title` TEXT, `content` TEXT, `timestamp` INTEGER NOT NULL, `is_pinned` INTEGER NOT NULL, FOREIGN KEY(`class_id`) REFERENCES `classes`(`classId`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`user_id`) REFERENCES `users`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_posts_class_id` ON `posts` (`class_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_posts_user_id` ON `posts` (`user_id`)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `comments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `post_id` INTEGER NOT NULL, `user_id` INTEGER NOT NULL, `content` TEXT, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`post_id`) REFERENCES `posts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`user_id`) REFERENCES `users`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_comments_post_id` ON `comments` (`post_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_comments_user_id` ON `comments` (`user_id`)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `post_likes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `post_id` INTEGER NOT NULL, `user_id` INTEGER NOT NULL, FOREIGN KEY(`post_id`) REFERENCES `posts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`user_id`) REFERENCES `users`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_post_likes_post_id` ON `post_likes` (`post_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_post_likes_user_id` ON `post_likes` (`user_id`)");

            // Create notifications table (version 12 schema)
            database.execSQL("CREATE TABLE IF NOT EXISTS `notifications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `user_id` INTEGER NOT NULL, `content` TEXT, `timestamp` INTEGER NOT NULL, `is_read` INTEGER NOT NULL, FOREIGN KEY(`user_id`) REFERENCES `users`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_user_id` ON `notifications` (`user_id`)");
            
            // Update notifications table to version 13 schema
            database.execSQL("CREATE TABLE IF NOT EXISTS `notifications_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `user_id` INTEGER NOT NULL, `sender_id` INTEGER NOT NULL, `related_post_id` INTEGER NOT NULL, `type` TEXT, `message` TEXT, `timestamp` INTEGER NOT NULL, `is_read` INTEGER NOT NULL, FOREIGN KEY(`user_id`) REFERENCES `users`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("INSERT INTO `notifications_new` (`id`, `user_id`, `sender_id`, `related_post_id`, `type`, `message`, `timestamp`, `is_read`) SELECT `id`, `user_id`, -1, -1, 'unknown', `content`, `timestamp`, `is_read` FROM `notifications`");
            database.execSQL("DROP TABLE `notifications`");
            database.execSQL("ALTER TABLE `notifications_new` RENAME TO `notifications`");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_user_id` ON `notifications` (`user_id`)");
        }
    };

    // Migration from version 12 to 13
    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `notifications_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `user_id` INTEGER NOT NULL, `sender_id` INTEGER NOT NULL, `related_post_id` INTEGER NOT NULL, `type` TEXT, `message` TEXT, `timestamp` INTEGER NOT NULL, `is_read` INTEGER NOT NULL, FOREIGN KEY(`user_id`) REFERENCES `users`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("INSERT INTO `notifications_new` (`id`, `user_id`, `sender_id`, `related_post_id`, `type`, `message`, `timestamp`, `is_read`) SELECT `id`, `user_id`, -1, -1, 'unknown', `content`, `timestamp`, `is_read` FROM `notifications`");
            database.execSQL("DROP TABLE `notifications`");
            database.execSQL("ALTER TABLE `notifications_new` RENAME TO `notifications`");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_user_id` ON `notifications` (`user_id`)");
        }
    };

    // Migration from version 11 to 12
    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `notifications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `user_id` INTEGER NOT NULL, `content` TEXT, `timestamp` INTEGER NOT NULL, `is_read` INTEGER NOT NULL, FOREIGN KEY(`user_id`) REFERENCES `users`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_user_id` ON `notifications` (`user_id`)");
        }
    };

    // Migration from version 10 to 11 - Combined: Add avatar fields AND forum tables
    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add avatar fields to users table
            database.execSQL("ALTER TABLE users ADD COLUMN avatar_path TEXT DEFAULT ''");
            database.execSQL("ALTER TABLE users ADD COLUMN avatar_type INTEGER DEFAULT 0");
            
            // Add forum tables
            database.execSQL("CREATE TABLE IF NOT EXISTS `posts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `class_id` INTEGER NOT NULL, `user_id` INTEGER NOT NULL, `title` TEXT, `content` TEXT, `timestamp` INTEGER NOT NULL, `is_pinned` INTEGER NOT NULL, FOREIGN KEY(`class_id`) REFERENCES `classes`(`classId`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`user_id`) REFERENCES `users`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_posts_class_id` ON `posts` (`class_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_posts_user_id` ON `posts` (`user_id`)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `comments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `post_id` INTEGER NOT NULL, `user_id` INTEGER NOT NULL, `content` TEXT, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`post_id`) REFERENCES `posts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`user_id`) REFERENCES `users`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_comments_post_id` ON `comments` (`post_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_comments_user_id` ON `comments` (`user_id`)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `post_likes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `post_id` INTEGER NOT NULL, `user_id` INTEGER NOT NULL, FOREIGN KEY(`post_id`) REFERENCES `posts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`user_id`) REFERENCES `users`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_post_likes_post_id` ON `post_likes` (`post_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_post_likes_user_id` ON `post_likes` (`user_id`)");
        }
    };

    // Migration from version 8 to 10 (preserving data)
    static final Migration MIGRATION_8_10 = new Migration(8, 10) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Create new question_stats table with userId column
            database.execSQL("CREATE TABLE IF NOT EXISTS `question_stats_new` (" +
                    "`topic` TEXT NOT NULL, " +
                    "`count` INTEGER NOT NULL, " +
                    "`userId` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`topic`, `userId`))");
            
            // Copy data from old table, assigning userId = -1 for existing data
            database.execSQL("INSERT INTO `question_stats_new` (`topic`, `count`, `userId`) " +
                    "SELECT `topic`, `count`, -1 FROM `question_stats`");
            
            // Drop old table and rename new table
            database.execSQL("DROP TABLE `question_stats`");
            database.execSQL("ALTER TABLE `question_stats_new` RENAME TO `question_stats`");
        }
    };
    
    // Migration from version 9 to 10 (if anyone upgraded to version 9)
    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Same migration as 8->10 since version 9 might have same structure
            database.execSQL("CREATE TABLE IF NOT EXISTS `question_stats_new` (" +
                    "`topic` TEXT NOT NULL, " +
                    "`count` INTEGER NOT NULL, " +
                    "`userId` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`topic`, `userId`))");
            
            database.execSQL("INSERT INTO `question_stats_new` (`topic`, `count`, `userId`) " +
                    "SELECT `topic`, `count`, -1 FROM `question_stats`");
            
            database.execSQL("DROP TABLE `question_stats`");
            database.execSQL("ALTER TABLE `question_stats_new` RENAME TO `question_stats`");
        }
    };

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "beihang_agent_db")
                            .addMigrations(MIGRATION_8_10, MIGRATION_9_10, MIGRATION_10_11, 
                                         MIGRATION_11_12, MIGRATION_11_14, MIGRATION_12_13, MIGRATION_13_14)
                            .fallbackToDestructiveMigration()  // 允许破坏性迁移
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
