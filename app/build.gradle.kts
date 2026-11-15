plugins { id("com.android.application") }
android {
    namespace = "com.riskmemory.map"
    compileSdk = 35
    providers.gradleProperty("demoDebugKeystore").orNull?.let { keyPath -> signingConfigs.getByName("debug") { storeFile = rootProject.file(keyPath) } }
    defaultConfig {
        applicationId = "com.riskmemory.map"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
providers.gradleProperty("demoBuildDir").orNull?.let { layout.buildDirectory.set(layout.projectDirectory.dir(it)) }