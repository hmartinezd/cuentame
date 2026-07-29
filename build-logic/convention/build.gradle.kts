plugins {
    `kotlin-dsl`
}

group = "com.miara.cuentame.buildlogic"

dependencies {
    compileOnly("com.android.tools.build:gradle:8.7.0")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
    compileOnly("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.0.21-1.0.25")
}

gradlePlugin {
    plugins {
        register("kotlinLibrary") {
            id = "cuentame.kotlin.library"
            implementationClass = "CuentameKotlinLibraryPlugin"
        }
        register("androidLibrary") {
            id = "cuentame.android.library"
            implementationClass = "CuentameAndroidLibraryPlugin"
        }
    }
}
