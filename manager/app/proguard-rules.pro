# Protobuf
-shrinkunusedprotofields

# Commons-compress
-dontwarn com.github.luben.zstd.**

# The AI feature provider is resolved reflectively by class name.
-keep class com.sukisu.ultra.ai.AiFeatureProviderImpl { *; }
-keep interface com.sukisu.ultra.ai.api.** { *; }
