-dontwarn org.json.**

# R8 strict mode (AGP 9) drops the generated Room constructors that WorkManager instantiates reflectively.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
