import org.gradle.gradlebuild.unittestandcompile.ModuleType

/**
 * Logging infrastructure.
 */
plugins {
    `java-library`
    id("gradlebuild.classycle")
}

dependencies {
    api(project(":baseServices"))
    api(project(":messaging"))
    api(project(":cli"))
    api(project(":buildOption"))
    api(library("slf4j_api"))

    implementation(project(":native"))
    implementation(library("jul_to_slf4j"))
    implementation(library("ant"))
    implementation(library("commons_lang"))
    implementation(library("guava"))
    implementation(library("jansi"))
    implementation(library("jcip"))

    runtimeOnly(library("log4j_to_slf4j"))
    runtimeOnly(library("jcl_to_slf4j"))

    testImplementation(project(":internalTesting"))
}

gradlebuildJava {
    moduleType = ModuleType.ENTRY_POINT
}

testFixtures {
    from(":core")
}

classycle {
    excludePatterns.set(listOf("org/gradle/internal/featurelifecycle/**"))
}
