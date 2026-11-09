# MBTV release rules. The YTXTube module brings its own (NewPipe, Rhino, jsoup).

# Release logs are not user-visible.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
}
