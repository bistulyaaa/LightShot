# LightShot - 轻简相机

一个为**极致抓拍**而生的轻量化 Android 相机应用。

## 项目概述

| 属性 | 值 |
|------|-----|
| **应用名称** | 轻简相机 (LightShot) |
| **包名** | com.example.testandlearn01 |
| **版本** | 1.4.1-260206 |
| **最低SDK** | Android 7.0 (API 24) |
| **目标SDK** | Android 14 (API 36) |
| **开发语言** | Kotlin |
| **UI框架** | Jetpack Compose |

### 开发愿景

在现代智能手机中，原生相机应用正变得越来越臃肿。复杂的计算摄影算法、繁琐的模式切换以及日益增加的启动延迟，让很多珍贵的瞬间在等待中流逝。**LightShot 的目标只有一个：在按下图标后的毫秒级时间内，为你捕捉到最真实、最清晰的瞬间。**

### 核心特色

- **瞬时响应**: 优化相机初始化时机，实现比原生相机更快的预览启动速度
- **零计算摄影**: 抛弃过度修饰的后处理算法，还原传感器最原始的数据
- **现代UI**: 完全基于 Jetpack Compose 构建，Material 3 设计
- **专业输出**: 支持 JPEG 与 14-bit RAW (DNG) 格式
- **音量键快门**: 支持音量键拍照，抓拍更便捷

---

## 项目结构

```
app/src/main/java/com/example/testandlearn01/
├── MainActivity.kt              # 应用入口，权限处理，音量键监听
├── GalleryScreen.kt             # 相册界面
├── PhotoUtils.kt                # 照片加载工具
│
├── data/                        # 数据层
│   └── camera/
│       ├── CameraRepositoryImpl.kt      # 相机仓库实现（核心功能）
│       └── Camera2RawCaptureManager.kt  # Camera2 RAW捕获管理
│
├── di/                          # 依赖注入
│   └── CameraModule.kt          # Hilt模块（待配置）
│
├── domain/                      # 领域层
│   ├── model/
│   │   └── CameraModels.kt      # 数据模型定义
│   └── repository/
│       └── CameraRepository.kt  # 仓库接口定义
│
├── presentation/                # 表现层
│   ├── ui/
│   │   ├── CameraScreen.kt      # 相机主界面
│   │   └── components/
│   │       ├── CameraControls.kt    # 相机控制组件
│   │       └── CameraPreview.kt     # 相机预览组件
│   └── viewmodel/
│       └── CameraViewModel.kt   # 相机状态管理
│
├── ui/                          # UI主题
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
│
└── util/                        # 工具类
    └── VolumeKeyShutter.kt      # 音量键快门功能
```

---

## 架构设计

项目采用 **MVVM + Clean Architecture** 架构模式：

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                        │
│  ┌─────────────────┐    ┌─────────────────────────────────┐ │
│  │   CameraScreen  │    │       CameraViewModel           │ │
│  │   GalleryScreen │◄───│  (状态管理、业务逻辑协调)         │ │
│  │   Components    │    └─────────────────────────────────┘ │
│  └─────────────────┘                   │                     │
└─────────────────────────────────────────┼───────────────────┘
                                          │
┌─────────────────────────────────────────┼───────────────────┐
│                         Domain Layer    │                    │
│  ┌──────────────────────┐    ┌──────────▼────────────────┐  │
│  │   CameraRepository   │    │      CameraModels         │  │
│  │     (接口定义)        │    │  CameraState, CaptureMode │  │
│  └──────────────────────┘    │  FlashMode, CaptureResult │  │
│          ▲                   └───────────────────────────┘  │
└──────────┼──────────────────────────────────────────────────┘
           │
┌──────────┼──────────────────────────────────────────────────┐
│          │         Data Layer                                │
│  ┌───────┴──────────────────┐    ┌────────────────────────┐  │
│  │  CameraRepositoryImpl    │    │ Camera2RawCaptureMgr   │  │
│  │  (相机功能实现)           │◄───│  (Camera2 RAW捕获)      │  │
│  └──────────────────────────┘    └────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

### 数据流

```
用户操作 → CameraScreen → CameraViewModel → CameraRepository → CameraX/Camera2 API
                                    ↓
                            StateFlow状态更新
                                    ↓
                            CameraScreen UI重组
```

---

## 核心模块说明

### 1. 相机模块 (Camera)

| 文件 | 功能 |
|------|------|
| `CameraRepositoryImpl.kt` | 相机核心功能实现 |
| `CameraViewModel.kt` | 相机状态管理 |
| `CameraScreen.kt` | 相机界面 |
| `CameraControls.kt` | 控制组件（曝光、闪光灯、快门） |

### 2. 拍摄模式

| 模式 | 说明 | 实现方法 |
|------|------|----------|
| **JPEG** | 标准JPEG格式拍摄 | `captureJPEG()` 使用 CameraX ImageCapture |
| **RAW** | DNG原始格式拍摄 | `captureRAW()` 使用 Camera2 API |
| **RAW+JPEG** | 双格式同时保存 | `captureRAWAndJPEG()` 并行捕获 |

### 3. 相机控制功能

| 功能 | 说明 | 实现位置 |
|------|------|----------|
| 闪光灯控制 | 自动/开启/关闭 | `setFlashMode()` |
| 曝光补偿 | -6 到 +6 EV | `setExposureCompensation()` |
| 触摸对焦 | 点击屏幕对焦 | `focusOnPoint()` |
| 音量键快门 | 音量键触发拍照 | `VolumeKeyShutter` |

### 4. 相册模块

| 文件 | 功能 |
|------|------|
| `GalleryScreen.kt` | 照片网格展示、预览、删除 |
| `PhotoUtils.kt` | MediaStore照片查询 |

---

## 数据模型

### CameraState

```kotlin
data class CameraState(
    val captureMode: CaptureMode = CaptureMode.JPEG,  // 拍摄模式
    val flashMode: FlashMode = FlashMode.AUTO,        // 闪光灯模式
    val exposureCompensation: Float = 0f,             // 曝光补偿
    val isRawSupported: Boolean = false,              // RAW支持状态
    val isCapturing: Boolean = false,                 // 拍摄中状态
    val focusPoint: Pair<Float, Float>? = null        // 对焦点
)
```

### 枚举定义

```kotlin
enum class CaptureMode { JPEG, RAW, RAW_AND_JPEG }
enum class FlashMode { AUTO, ON, OFF }
```

### 拍摄结果

```kotlin
sealed class CaptureResult {
    data class Success(val uri: Uri) : CaptureResult()
    data class Error(val message: String) : CaptureResult()
}
```

---

## 技术栈

### 核心技术

| 技术 | 版本 | 用途 |
|------|------|------|
| Kotlin | 2.0.21 | 开发语言 |
| Jetpack Compose | BOM 2024.09.00 | 声明式UI框架 |
| Material 3 | - | 设计系统 |
| CameraX | 1.4.0 | 相机功能核心 |
| Coroutines & Flow | - | 异步与响应式 |

### 主要依赖

| 依赖 | 用途 |
|------|------|
| `androidx.camera:*` | 相机功能（预览、拍摄、生命周期） |
| `androidx.lifecycle:*` | ViewModel、生命周期管理 |
| `io.coil-kt:coil-compose` | 图片加载 |
| `androidx.compose.material3` | Material 3 组件 |

---

## 开发指南

### 环境要求

- Android Studio Hedgehog 或更高版本
- JDK 11+
- Android SDK (minSdk 24, targetSdk 36)
- Gradle 8.x

### 构建项目

```bash
# 调试版本
./gradlew assembleDebug

# 发布版本
./gradlew assembleRelease
```

### 代码规范

1. **命名规范**
   - 类名：大驼峰（PascalCase）
   - 函数/变量：小驼峰（camelCase）
   - 常量：全大写下划线分隔（UPPER_SNAKE_CASE）

2. **Compose规范**
   - Composable函数使用大驼峰命名
   - 状态提升（State Hoisting）模式
   - 单一职责原则

3. **架构规范**
   - ViewModel不持有Activity/Fragment引用
   - Repository负责数据获取与缓存
   - UseCase封装复杂业务逻辑

### 添加新功能

1. 在 `domain/model/` 定义数据模型
2. 在 `domain/repository/` 定义接口
3. 在 `data/` 实现接口
4. 在 `presentation/viewmodel/` 添加ViewModel逻辑
5. 在 `presentation/ui/` 实现UI

---

## 功能清单

### 已实现

- [x] 相机预览与基础拍摄
- [x] JPEG格式拍摄
- [x] RAW (DNG) 格式拍摄
- [x] RAW+JPEG 双格式拍摄
- [x] 闪光灯控制（自动/开/关）
- [x] 曝光补偿（-6 到 +6 EV）
- [x] 触摸对焦
- [x] 音量键快门
- [x] 相册浏览
- [x] 照片删除

### 开发中

- [ ] 实时直方图
- [ ] 零快门延迟 (ZSL)
- [ ] 网格线辅助
- [ ] 水平仪
- [ ] 自定义存储路径

---

## 已知问题

1. **Dagger Hilt 未配置**: `CameraModule.kt` 使用了 Hilt 注解但未在 `build.gradle.kts` 中配置 Hilt 插件
2. **重复文件**: 存在 `CameraScreen.kt` 的重复定义

### 修复 Hilt 配置

在 `app/build.gradle.kts` 中添加：

```kotlin
plugins {
    alias(libs.plugins.hilt)  // 需要在 libs.versions.toml 中添加版本
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
```

---

## 文件索引

### 核心文件

| 文件 | 路径 | 说明 |
|------|------|------|
| MainActivity | `MainActivity.kt` | 应用入口 |
| CameraViewModel | `presentation/viewmodel/CameraViewModel.kt` | 相机状态管理 |
| CameraRepositoryImpl | `data/camera/CameraRepositoryImpl.kt` | 相机功能实现 |
| CameraScreen | `presentation/ui/CameraScreen.kt` | 相机界面 |
| CameraControls | `presentation/ui/components/CameraControls.kt` | 控制组件 |
| CameraModels | `domain/model/CameraModels.kt` | 数据模型 |

### 配置文件

| 文件 | 说明 |
|------|------|
| `app/build.gradle.kts` | 模块构建配置 |
| `build.gradle.kts` | 项目构建配置 |
| `settings.gradle.kts` | 项目设置、仓库配置 |
| `gradle/libs.versions.toml` | 依赖版本目录 |
| `AndroidManifest.xml` | 应用清单 |

---

## 贡献指南

欢迎提交 Issue 和 Pull Request。

### 提交规范

```
feat: 添加新功能
fix: 修复bug
docs: 文档更新
refactor: 代码重构
style: 代码格式调整
test: 测试相关
```

---

## 许可证

本项目仅供学习交流使用。

---

> "原生相机给你的也许是'好照片'，但 LightShot 给你的是'拍得到'。"
