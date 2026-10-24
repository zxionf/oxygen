# JNI 通过 Java_<包>_<类>_<方法> 的名字查找入口，R8 不能改名。
-keep class top.zxionf.shio.kernel.NativeShio { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
