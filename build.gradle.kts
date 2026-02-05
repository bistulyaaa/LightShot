// 根目录 build.gradle.kts - 不在此定义仓库，统一在 settings.gradle.kts 中管理

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
