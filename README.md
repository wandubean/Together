# Together

한양대 학사일정 + 과제 알리미 Android 앱 (LMSalert)

## 개발 환경

| 항목 | 버전 |
| --- | --- |
| Android Studio | 2026.1.2 |
| Gradle (Wrapper) | 9.5.0 |
| Android Gradle Plugin (AGP) | 9.3.3 |
| Kotlin | 2.2.10 (AGP 내장 Kotlin 사용) |
| Gradle Daemon JDK | 21 (foojay로 자동 다운로드) |
| Java 소스/타깃 호환성 | 11 |
| UI | Jetpack Compose (BOM 2026.02.01), Material 3 |

## 앱 설정

| 항목 | 값 |
| --- | --- |
| Application ID / Namespace | `com.together.lmsalert` |
| compileSdk | 37 (core-ktx 1.19 / lifecycle 2.11 / OkHttp 5.5 요구사항) |
| targetSdk | 36 |
| minSdk | 26 (Android 8.0) |

## 주요 라이브러리

| 라이브러리 | 버전 |
| --- | --- |
| androidx.core:core-ktx | 1.19.0 |
| androidx.lifecycle:lifecycle-runtime-ktx | 2.11.0 |
| androidx.activity:activity-compose | 1.13.0 |
| junit | 4.13.2 |
| androidx.test.ext:junit | 1.3.0 |
| androidx.test.espresso:espresso-core | 3.7.0 |
| com.squareup.okhttp3:okhttp | 5.5.1 |
| org.jetbrains.kotlinx:kotlinx-serialization-json | 1.9.0 |
| androidx.work:work-runtime-ktx | 2.11.2 |

버전은 [`gradle/libs.versions.toml`](gradle/libs.versions.toml)에서 한 곳에서 관리합니다.

## 시작하기

1. 저장소 클론
   ```bash
   git clone <repo-url>
   ```
2. Android Studio **2026.1.2 이상**에서 `Together` 폴더 열기
3. Gradle Sync 완료 대기 (`local.properties`는 SDK 경로에 맞게 자동 생성됨)
4. 에뮬레이터 또는 기기(Android 8.0 이상) 선택 후 Run

커맨드라인 빌드:

```bash
./gradlew assembleDebug
```

## 프로젝트 구조

```
Together/
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/com/together/lmsalert/   # Kotlin 소스 (lms, sync, notification, ui/theme ...)
│       │   ├── res/                          # 리소스
│       │   └── AndroidManifest.xml
│       ├── test/                             # 로컬 단위 테스트
│       └── androidTest/                      # 기기 테스트
├── gradle/
│   ├── libs.versions.toml                    # 버전 카탈로그
│   └── wrapper/
├── build.gradle.kts
└── settings.gradle.kts
```
