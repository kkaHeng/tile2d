# 仅保留被 @Type 标注的类自己的字段，内部类字段不继承
-keepclassmembers @com.kkaheng.ui.layout.annotation.Type class * {
    <fields>;
}

# 保留注解接口
-keep interface com.kkaheng.ui.layout.annotation.Type
-keepattributes *Annotation*
