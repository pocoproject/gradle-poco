import org.gradle.gradlebuild.unittestandcompile.ModuleType
import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty

plugins {
    id("gradlebuild.strict-compile")
    id("gradlebuild.classycle")
}

dependencies {
    implementation(library("asm"))
    implementation(library("asm_commons"))
    implementation(library("groovy"))

    compile(project(":core"))
    compile(project(":platformJvm"))
    compile(project(":languageJvm"))
    implementation(project(":snapshots"))

    // TODO - get rid of this cycle
    integTestRuntime(project(":plugins"))
}

gradlebuildJava {
    // Needs to run in the compiler daemon
    moduleType = ModuleType.ENTRY_POINT
}

testFixtures {
    from(":core")
    from(":languageJvm", "testFixtures")
    from(":platformBase")
    from(":launcher")
}

classycle {
    // These public packages have classes that are tangled with the corresponding internal package.
    excludePatterns.set(listOf(
        "org/gradle/api/tasks/compile/**",
        "org/gradle/external/javadoc/**"
    ))
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}
