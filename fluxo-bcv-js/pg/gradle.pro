
#########################################
# ProGuard/R8 rules for Gradle plugins. #
#########################################

# Keep tasks functioning.
-keep @interface org.gradle.api.tasks.**
-keepclassmembers,includedescriptorclasses class * implements org.gradle.api.Task {
    <init>();
    @org.gradle.api.tasks.** <methods>;
    @org.gradle.api.tasks.** <fields>;
}

# Keep unused InputFiles for cacheable Gradle tasks.
# Needed for cache invalidation and proper work.
-keepclassmembers,allowoptimization,includedescriptorclasses class * {
    @org.gradle.api.tasks.InputFile <methods>;
    @org.gradle.api.tasks.InputFiles <methods>;
}

# Keep services functioning.
-keep,allowobfuscation,includedescriptorclasses class * implements org.gradle.api.services.BuildService {
    <init>(...);
}
-keep,allowobfuscation,includedescriptorclasses class * implements org.gradle.api.services.BuildServiceParameters

# Keep injecting constructors and injected getters, they are called at runtime.
# 'allowoptimization' breaks injection here, so don't use it.
# 'includedescriptorclasses' is to avoid notes "the configuration keeps the entry point A, but not the descriptor class B".
-keepclassmembers,includedescriptorclasses class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject abstract <methods>;
    @org.gradle.api.tasks.Input abstract <methods>;
    abstract org.gradle.api.** *(...);
}

