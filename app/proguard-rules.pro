# ZXing
-keep class com.google.zxing.** { *; }
# ML Kit
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
# Room
-keep class androidx.room.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(); }
