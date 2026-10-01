# Archive libraries (7z, tar, rar) mention optional extras that are not bundled; none of them is used.
-dontwarn org.slf4j.**
-dontwarn org.apache.commons.compress.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.**
-dontwarn org.objectweb.asm.**
-dontwarn org.tukaani.**
-dontwarn com.github.junrar.**
-dontwarn javax.annotation.**

# The archive libraries look things up by reflection (an enum's values(), for example). The shrinker cannot see that and would
# remove or rename them, which showed up as "NoSuchMethodException: ...values" while unpacking. Keep them as they are.
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }
-keep class org.apache.commons.compress.** { *; }
-keep class org.tukaani.xz.** { *; }
-keep class com.github.junrar.** { *; }
