# 保留行号
-keepattributes SourceFile,LineNumberTable

# JNI native 方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# 枚举
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
