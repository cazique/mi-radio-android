plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.miradio.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.miradio.app"
        minSdk = 24
        targetSdk = 35
        // El número de ejecución de GitHub Actions sube solo en cada build de
        // CI (1, 2, 3...), que es justo lo que necesita la actualización
        // dentro de la app para saber si hay una versión más reciente. Con un
        // valor fijo, "Buscar actualizaciones" nunca vería nada nuevo.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        // El número de build va también en el nombre visible (Ajustes > Acerca
        // de) para poder comprobar a simple vista qué compilación tienes
        // instalada sin mirar el registro de diagnóstico.
        versionName = "1.0.0-build" + (System.getenv("GITHUB_RUN_NUMBER") ?: "dev")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // URL por defecto del catálogo remoto de emisoras (JSON). Se puede
        // cambiar en tiempo de ejecución desde Ajustes sin publicar una nueva
        // versión. Si mueves el JSON a tu propio hosting, actualiza esta URL
        // o cámbiala desde Ajustes > Catálogo remoto de emisoras sin tener
        // que recompilar.
        buildConfigField(
            "String",
            "DEFAULT_REMOTE_STATIONS_URL",
            "\"https://raw.githubusercontent.com/cazique/mi-radio-android/main/remote-example/stations-espana-completo.json\""
        )
    }

    signingConfigs {
        getByName("debug") {
            // Clave de depuración fija y compartida entre todas las
            // compilaciones de CI (ver keystore/debug.keystore). Sin esto,
            // cada ejecutor de GitHub Actions genera una clave aleatoria
            // nueva y Android rechaza instalar la "actualización" por firma
            // distinta, obligando a desinstalar la app cada vez.
            storeFile = file("../keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Clave de release propia (privada, nunca en el repo). Solo existe
        // cuando CI la aporta vía variables de entorno (ver
        // .github/workflows/build-apk.yml); en un checkout normal sin esas
        // variables, "release" simplemente se compila sin firmar.
        val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (System.getenv("RELEASE_KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Room (vía KSP) vuelca aquí un JSON con el esquema exacto de cada versión
// de la base de datos. Sin esto no hay forma de escribir ni probar
// migraciones reales: una Migration(from, to) necesita saber exactamente
// cómo era el esquema "from" para poder comprobarse contra él.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core / lifecycle
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    // FragmentActivity, requerido por MediaRouteButton (botón de Cast) para
    // poder mostrar su diálogo de selección de dispositivo.
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    // Compose UI + Material 3
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Coil (logos de emisoras)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Theme.MaterialComponents, requerido por androidx.mediarouter.app.MediaRouteButton
    // (botón de Cast) para poder inflarse sin lanzar excepción.
    implementation("com.google.android.material:material:1.12.0")

    // Media3 / ExoPlayer + sesión multimedia + Cast bridge
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-cast:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.4.1")

    // Google Cast
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")

    // Room (persistencia local de emisoras)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore (última emisora, último dispositivo cast, ajustes)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Serialización JSON (catálogo remoto de emisoras)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Corrutinas
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Networking para el catálogo remoto
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
