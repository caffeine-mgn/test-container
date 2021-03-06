import kotlinx.coroutines.withTimeout
import pw.binom.eachKotlinCompile
import java.util.*
import java.time.Duration
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("kotlinx-serialization")
}

apply {
    plugin(pw.binom.plugins.BinomPublishPlugin::class.java)
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions {
//                jvmTarget = "11"
            }
        }
    }

    linuxX64 { // Use your target instead.
        binaries {
            staticLib()
        }
    }

    linuxArm32Hfp {
        binaries {
            staticLib()
        }
    }

//    linuxArm64 {
//        binaries {
//            staticLib()
//        }
//    }

//    linuxMips32 {
//        binaries {
//            staticLib()
//        }
//    }
//
//    linuxMipsel32 {
//        binaries {
//            staticLib()
//        }
//    }

    mingwX64 { // Use your target instead.
        binaries {
            staticLib()
        }
    }

    mingwX86 { // Use your target instead.
        binaries {
            staticLib()
        }
    }

    macosX64 {
        binaries {
            framework()
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(kotlin("stdlib-common"))
                api("pw.binom.io:concurrency:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:logger:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:docker-api:${pw.binom.Versions.DOCKER_API_VERSION}")
            }
        }

        val linuxX64Main by getting {
            dependsOn(commonMain)
            kotlin.srcDir("src/linuxX64Main/kotlin")
        }
//        val linuxArm64Main by getting {
//            dependsOn(commonMain)
//            kotlin.srcDir("src/linuxX64Main/kotlin")
//        }
        val linuxArm32HfpMain by getting {
            dependsOn(commonMain)
            kotlin.srcDir("src/linuxX64Main/kotlin")
        }

//        val linuxMips32Main by getting {
//            dependsOn(commonMain)
//            kotlin.srcDir("src/linuxX64Main/kotlin")
//        }
//
//        val linuxMipsel32Main by getting {
//            dependsOn(commonMain)
//            kotlin.srcDir("src/linuxX64Main/kotlin")
//        }

        val mingwX64Main by getting {
            dependsOn(commonMain)
        }
        val mingwX86Main by getting {
            dependsOn(commonMain)
            kotlin.srcDir("src/mingwX64Main/kotlin")
        }

        val macosX64Main by getting {
            dependsOn(commonMain)
            kotlin.srcDir("src/linuxX64Main/kotlin")
        }

        val commonTest by getting {
            dependencies {
                api(kotlin("test-common"))
                api(kotlin("test-annotations-common"))
                api("pw.binom.io:postgresql-async:${pw.binom.Versions.BINOM_VERSION}")
            }
        }
        val jvmTest by getting {
            dependsOn(commonTest)
            dependencies {
                api(kotlin("test-junit"))
            }
        }
        val linuxX64Test by getting {
            dependsOn(commonTest)
        }
    }
}

apply<pw.binom.plugins.DocsPlugin>()