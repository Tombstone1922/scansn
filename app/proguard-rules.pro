# Room keeps generated implementations through its own consumer rules.
-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(...); }
-dontwarn com.google.errorprone.annotations.**
