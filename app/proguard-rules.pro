# Only used if you enable isMinifyEnabled in app/build.gradle.kts.
-keepattributes *Annotation*, InnerClasses
-dontwarn org.slf4j.**
-keep,includedescriptorclasses class com.familymoney.app.**$$serializer { *; }
-keepclassmembers class com.familymoney.app.** { *** Companion; }
-keepclasseswithmembers class com.familymoney.app.** { kotlinx.serialization.KSerializer serializer(...); }
