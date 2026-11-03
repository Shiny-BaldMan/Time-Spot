# 타임스팟 (Time Spot)

오늘의 기억을 저장하고 약속한 미래의 시간에 열어보는 Android 앱입니다.

현재는 오프라인 체험판으로, 캡슐 작성·로컬 저장·열람 시간 선택·잠금 카운트다운을 제공합니다.
로그인, 지도/GPS, 사진 첨부, 친구 공유는 아직 연결되지 않았습니다.

## 실행

1. Android Studio에서 프로젝트를 엽니다.
2. Gradle JDK를 JDK 17 또는 21로 설정하고 Android SDK 35를 설치합니다.
3. Gradle 동기화 후 Android 7.0(API 24) 이상 기기 또는 에뮬레이터에서 실행합니다.

Windows 명령줄 빌드 (`JAVA_HOME`에 JDK 17 또는 21 설정):

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

`local.properties`와 빌드 결과물은 Git에 포함하지 않습니다.
기기 시간에 따른 잠금은 체험용이며 실제 서비스의 보안 경계가 아닙니다.
구현 현황과 후속 작업은 [IMPLEMENTATION.md](IMPLEMENTATION.md)를 참고하세요.
