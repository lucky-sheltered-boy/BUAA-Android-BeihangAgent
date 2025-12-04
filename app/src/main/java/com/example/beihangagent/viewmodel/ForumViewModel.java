package com.example.beihangagent.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.beihangagent.model.AppDatabase;
import com.example.beihangagent.model.Class;
import com.example.beihangagent.model.ClassDao;
import com.example.beihangagent.model.Comment;
import com.example.beihangagent.model.CommentDao;
import com.example.beihangagent.model.CommentWithUser;
import com.example.beihangagent.model.Notification;
import com.example.beihangagent.model.NotificationDao;
import com.example.beihangagent.model.Post;
import com.example.beihangagent.model.PostDao;
import com.example.beihangagent.model.PostLike;
import com.example.beihangagent.model.PostLikeDao;
import com.example.beihangagent.model.PostWithUser;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ForumViewModel extends AndroidViewModel {
    private final PostDao postDao;
    private final CommentDao commentDao;
    private final PostLikeDao postLikeDao;
    private final ClassDao classDao;
    private final NotificationDao notificationDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<Class> selectedClass = new MutableLiveData<>();

    public ForumViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getDatabase(application);
        postDao = db.postDao();
        commentDao = db.commentDao();
        postLikeDao = db.postLikeDao();
        classDao = db.classDao();
        notificationDao = db.notificationDao();
    }

    public void selectClass(Class clazz) {
        selectedClass.setValue(clazz);
    }

    public LiveData<Class> getSelectedClass() {
        return selectedClass;
    }

    public LiveData<List<PostWithUser>> getPosts(int classId) {
        return postDao.getPostsByClass(classId);
    }

    public LiveData<PostWithUser> getPost(int postId) {
        return postDao.getPostById(postId);
    }

    public LiveData<List<CommentWithUser>> getComments(int postId) {
        return commentDao.getCommentsByPost(postId);
    }

    public LiveData<Integer> getLikeCount(int postId) {
        return postDao.getLikeCount(postId);
    }

    public LiveData<Boolean> isLiked(int postId, int userId) {
        return postDao.isLikedByUser(postId, userId);
    }

    public void createPost(int classId, int userId, String title, String content) {
        executor.execute(() -> {
            Post post = new Post(classId, userId, title, content);
            postDao.insert(post);
        });
    }

    public void createComment(int postId, int userId, String content, Integer replyToUserId) {
        executor.execute(() -> {
            Comment comment = new Comment(postId, userId, content);
            commentDao.insert(comment);
            
            // Notify post author
            Post post = postDao.getPostByIdSync(postId);
            if (post != null && post.userId != userId) {
                Notification notification = new Notification(
                    post.userId,
                    userId,
                    postId,
                    "comment",
                    "有用户回复了你的帖子： " + post.title,
                    System.currentTimeMillis()
                );
                notificationDao.insert(notification);
            }
            
            // Notify replied user if exists and not self
            if (replyToUserId != null && replyToUserId != userId && (post == null || replyToUserId != post.userId)) {
                Notification notification = new Notification(
                    replyToUserId,
                    userId,
                    postId,
                    "reply",
                    "有用户在帖子中回复了你的评论",
                    System.currentTimeMillis()
                );
                notificationDao.insert(notification);
            }
        });
    }

    public void toggleLike(int postId, int userId, boolean isLiked) {
        executor.execute(() -> {
            if (isLiked) {
                postLikeDao.delete(postId, userId);
            } else {
                postLikeDao.insert(new PostLike(postId, userId));
                
                // Notify post author
                Post post = postDao.getPostByIdSync(postId);
                if (post != null && post.userId != userId) {
                    Notification notification = new Notification(
                        post.userId,
                        userId,
                        postId,
                        "like",
                        "有用户点赞了你的帖子： " + post.title,
                        System.currentTimeMillis()
                    );
                    notificationDao.insert(notification);
                }
            }
        });
    }

    public void deletePost(Post post) {
        executor.execute(() -> postDao.delete(post));
    }

    public void togglePin(Post post) {
        executor.execute(() -> {
            post.isPinned = !post.isPinned;
            postDao.update(post);
        });
    }
    
    public LiveData<List<Class>> getStudentClasses(int studentId) {
        return classDao.getClassesByStudent(studentId);
    }
    
    public LiveData<List<Class>> getTeacherClasses(int teacherId) {
        return classDao.getClassesByTeacher(teacherId);
    }

    public LiveData<List<Notification>> getNotifications(int userId) {
        return notificationDao.getNotificationsByUser(userId);
    }

    public void markNotificationsAsRead(int userId) {
        executor.execute(() -> notificationDao.markAllAsRead(userId));
    }

    public LiveData<Integer> getUnreadNotificationCount(int userId) {
        return notificationDao.getUnreadCount(userId);
    }

    public void markNotificationAsRead(int notificationId) {
        executor.execute(() -> notificationDao.markAsRead(notificationId));
    }

    public void enterPublicClass() {
        executor.execute(() -> {
            Class publicClass = classDao.getClassByCode("PUBLIC");
            if (publicClass == null) {
                publicClass = new Class("公共讨论区", "PUBLIC", -1);
                long id = classDao.insertClass(publicClass);
                publicClass.classId = (int) id;
            }
            selectedClass.postValue(publicClass);
        });
    }
}
