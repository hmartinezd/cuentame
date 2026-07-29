plugins {
    id("cuentame.kotlin.library")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:domain"))

    implementation(libs.junit)
    implementation(libs.truth)
    implementation(libs.mockk)
    implementation(libs.kotlinx.coroutines.test)
}
