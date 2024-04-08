import pw.binom.publish.allTargets
import pw.binom.publish.applyDefaultHierarchyBinomTemplate

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("kotlinx-serialization")
}

//apply {
//    plugin(pw.binom.plugins.BinomPublishPlugin::class.java)
//}

kotlin {
//    ifNotMac {
//        jvm()
//        linuxX64()
//        linuxArm64()
//        mingwX64()
//    }
//    macosX64()
    allTargets {
        -"js"
    }
//    applyDefaultHierarchyTemplate()
    applyDefaultHierarchyBinomTemplate()
    sourceSets {
        val commonMain by getting {
            dependencies {
                api("pw.binom.io:core:${pw.binom.Versions.BINOM_VERSION}")
                api("pw.binom.io:docker-api:${pw.binom.Versions.DOCKER_API_VERSION}")
            }
        }


        val commonTest by getting {
            dependencies {
                api(kotlin("test-common"))
                api(kotlin("test-annotations-common"))
                api("pw.binom.io:postgresql-async:${pw.binom.Versions.BINOM_VERSION}")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-test:${pw.binom.Versions.KOTLINX_COROUTINES_VERSION}")
            }
        }
        val jvmTest by getting {
            dependsOn(commonTest)
            dependencies {
                api(kotlin("test"))
            }
        }
    }
}

tasks {
    withType(Test::class) {
        useJUnitPlatform()
        testLogging.showStandardStreams = true
        testLogging.showCauses = true
        testLogging.showExceptions = true
        testLogging.showStackTraces = true
        testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
apply<pw.binom.publish.plugins.PrepareProject>()
//apply<pw.binom.plugins.DocsPlugin>()