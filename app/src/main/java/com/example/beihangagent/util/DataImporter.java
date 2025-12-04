package com.example.beihangagent.util;

import android.content.Context;
import com.example.beihangagent.model.AppDatabase;
import com.example.beihangagent.model.User;
import com.example.beihangagent.model.Class;
import com.example.beihangagent.model.ClassMember;
import com.example.beihangagent.model.UserDao;
import com.example.beihangagent.model.ClassDao;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DataImporter {
    
    private final AppDatabase database;
    private final UserDao userDao;
    private final ClassDao classDao;
    private final ExecutorService executor;
    
    public DataImporter(Context context) {
        database = AppDatabase.getDatabase(context);
        userDao = database.userDao();
        classDao = database.classDao();
        executor = Executors.newSingleThreadExecutor();
    }
    
    public void importTestData() {
        executor.execute(() -> {
            try {
                // 首先检查是否已有数据
                User existingTeacher = userDao.checkUser("00001");
                User existingStudent = userDao.checkUser("11");
                
                if (existingTeacher != null && existingStudent != null) {
                    System.out.println("数据已存在，跳过导入");
                    return;
                }
                
                System.out.println("开始导入测试数据...");
                
                // 创建老师账户
                User teacher = userDao.checkUser("00001");
                if (teacher == null) {
                    teacher = new User("00001", "admin123", 1); // 1 = Teacher
                    userDao.insert(teacher);
                    System.out.println("创建老师账户: 00001");
                } else {
                    System.out.println("老师账户已存在: 00001");
                }
                
                // 重新获取老师信息（获取自动生成的ID）
                teacher = userDao.checkUser("00001");
                if (teacher == null) {
                    System.out.println("无法获取老师信息");
                    return;
                }
                
                // 创建学生用户 11-20
                for (int i = 11; i <= 20; i++) {
                    String username = String.valueOf(i);
                    String password = "admin123";
                    
                    User existingUser = userDao.checkUser(username);
                    if (existingUser == null) {
                        User newUser = new User(username, password, 0); // 0 = Student
                        userDao.insert(newUser);
                        System.out.println("插入学生用户: " + username);
                    } else {
                        System.out.println("学生用户已存在: " + username);
                    }
                }
                
                // 创建或获取班级信息
                Class class2306 = classDao.getClassByCode("K2HJEB");
                Class class2406 = classDao.getClassByCode("QID5E9");
                
                // 如果班级不存在，创建班级
                if (class2306 == null) {
                    Class newClass2306 = new Class("2306班级", "K2HJEB", teacher.uid);
                    long classId = classDao.insertClass(newClass2306);
                    class2306 = classDao.getClassById((int) classId);
                    System.out.println("创建2306班级，邀请码: K2HJEB");
                }
                
                if (class2406 == null) {
                    Class newClass2406 = new Class("2406班级", "QID5E9", teacher.uid);
                    long classId = classDao.insertClass(newClass2406);
                    class2406 = classDao.getClassById((int) classId);
                    System.out.println("创建2406班级，邀请码: QID5E9");
                }
                
                // 添加学生11-15到2306班级
                for (int i = 11; i <= 15; i++) {
                    String username = String.valueOf(i);
                    User user = userDao.checkUser(username);
                    if (user != null && class2306 != null) {
                        ClassMember existing = classDao.getMembership(class2306.classId, user.uid);
                        if (existing == null) {
                            ClassMember member = new ClassMember(class2306.classId, user.uid);
                            classDao.insertMember(member);
                            System.out.println("添加学生 " + username + " 到2306班级");
                        } else {
                            System.out.println("学生 " + username + " 已在2306班级中");
                        }
                    }
                }
                
                // 添加学生16-20到2406班级
                for (int i = 16; i <= 20; i++) {
                    String username = String.valueOf(i);
                    User user = userDao.checkUser(username);
                    if (user != null && class2406 != null) {
                        ClassMember existing = classDao.getMembership(class2406.classId, user.uid);
                        if (existing == null) {
                            ClassMember member = new ClassMember(class2406.classId, user.uid);
                            classDao.insertMember(member);
                            System.out.println("添加学生 " + username + " 到2406班级");
                        } else {
                            System.out.println("学生 " + username + " 已在2406班级中");
                        }
                    }
                }
                
                System.out.println("测试数据导入完成！");
                System.out.println("教师账户: 00001/admin123");
                System.out.println("学生账户: 11-20/admin123");
                System.out.println("2306班级(K2HJEB): 学生11-15");
                System.out.println("2406班级(QID5E9): 学生16-20");
                
            } catch (Exception e) {
                System.out.println("数据导入失败: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    public void shutdown() {
        executor.shutdown();
    }
}