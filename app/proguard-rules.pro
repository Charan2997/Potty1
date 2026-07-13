# SQLCipher specific rules
-keep class net.zetetic.database.sqlcipher.** { *; }
-keep class net.zetetic.database.** { *; }

# Room specific rules
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public <init>(...);
}

# General hardening
-repackageclasses ''
-allowaccessmodification
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
