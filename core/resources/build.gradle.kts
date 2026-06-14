plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "io.vscodex.net.resources"

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures { viewBinding = true }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.preference)
    implementation(libs.google.material)
    implementation(libs.androidx.nav.fragment)
    implementation(libs.androidx.nav.ui)
}
