// settings.gradle.kts - 完整版本
pluginManagement {
    repositories {
        // 阿里云插件仓库（优先）
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")

        // 官方插件仓库（备用）
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 阿里云镜像（依赖仓库）
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")  // 可选

        // 官方仓库（备用）
        google()
        mavenCentral()

        // 其他特殊仓库
        maven("https://jitpack.io")
    }
}

rootProject.name = "YourAppName"
include(":app")