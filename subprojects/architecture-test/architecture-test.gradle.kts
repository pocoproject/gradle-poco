import org.gradle.gradlebuild.ProjectGroups.publicProjects
import org.gradle.gradlebuild.unittestandcompile.ModuleType
import org.gradle.gradlebuild.PublicApi

plugins {
    `java-library`
}

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

dependencies {
    api(project(":distributionsDependencies"))
    testImplementation("com.tngtech.archunit:archunit-junit4:0.9.1")
    testImplementation(library("jsr305"))
    testImplementation(project(":baseServices"))
    publicProjects.forEach {
        testRuntime(it)
    }
}

tasks.withType<Test> {
    systemProperty("org.gradle.public.api.includes", PublicApi.includes.joinToString(":"))
    systemProperty("org.gradle.public.api.excludes", PublicApi.excludes.joinToString(":"))
}
