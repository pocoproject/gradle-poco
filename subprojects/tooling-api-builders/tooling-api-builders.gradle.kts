import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    gradlebuild.`strict-compile`
    gradlebuild.classycle
}

dependencies {
    compile(project(":core"))
    compile(project(":testingJvm"))
    compile(project(":launcher"))
    compile(project(":toolingApi"))
    compile(project(":compositeBuilds"))
    compile(project(":workers"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

strictCompile {
    ignoreDeprecations = true
}
