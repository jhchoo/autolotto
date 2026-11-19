# 🎰 AutoLotto - 원본으로 가서 사용하세요.

동행복권 로또 6/45 자동 구매 & 당첨 확인 앱

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-blue.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203%20Expressive-green.svg)](https://developer.android.com/jetpack/compose)
[![Platform](https://img.shields.io/badge/Platform-Android%2012+-green.svg)](https://developer.android.com)


<p align="center">
  <a href="https://autolotto.umicorp.kr">
    <img src="docs/screenshots/landing.png" width="100%" />
  </a>
</p>

## ✨ 주요 기능

- **자동 구매** — 설정한 요일/시간에 자동으로 로또 구매 (최대 5게임)
- **즉시 구매** — 번호 탭에서 저장한 게임을 바로 결제, 이미 구매한 회차는 자동 게임 추가 구매 (주간 5게임 한도는 서버가 검증)
- **수동 · 반자동 · 자동 · 스마트 번호** — 게임 슬롯(A~E)별로 수동(6개), 반자동(1~5개 지정 후 나머지 랜덤), 완전 자동, 스마트 모드 선택
- **스마트 번호 추천 & 통계 필터** — 최근 100회 당첨 번호 빈도 분석(균형/다출/미출 전략)과 6대 통계 필터(총합 범위 기본 115~175, 연속 번호, 동일 끝수, 저고 비율, 홀짝 비율, 전멸 구간 1~2개) 적용. 사용자 필터 설정은 암호화 저장되어 **예약 자동구매 및 즉시 구매에도 그대로 반영**
- **당첨 확인 & 과거 이력 조회** — 매주 토요일 21:00 자동 당첨 확인 & 푸시 알림, 내장 당첨 데이터 + 최신 회차 동기화 기반 과거 당첨 번호 조회
- **구매 기록** — 최근 3개월 자동 조회 + '더 보기'로 최대 1년, 게임별 번호·등수·당첨금 한눈에 확인
- **점검/오프라인 대응** — 동행복권 서버 상태 확인을 통한 오프라인 모드 자동 전환 및 번호 설정·당첨 이력 조회 지원
- **보안 저장** — 계정 정보 및 설정은 Android Keystore(EncryptedSharedPreferences) 암호화 저장
- **다국어** — 한국어·영어·일본어, 설정에서 언어 선택 (기본: 시스템 언어)
- **모던 디자인** — Material 3 Expressive 'Lucky Gloss', 네이티브 Jetpack Compose · 스프링 인터랙션

## 📱 스크린샷

<p align="center">
  <img src="docs/screenshots/home.jpg" width="200" />
  <img src="docs/screenshots/numbers.jpg" width="200" />
  <img src="docs/screenshots/history.jpg" width="200" />
  <img src="docs/screenshots/settings.jpg" width="200" />
</p>

## 🔧 빌드 방법

### 요구 사항

- Android SDK 36 (compileSdk / targetSdk 36), minSdk 31 (Android 12+)
- JDK 17
- Android Studio 또는 Gradle

### 빌드 & 설치

```bash
# 디버그 빌드 + 유닛 테스트
./gradlew :app:assembleDebug :app:testDebugUnitTest

# 릴리스 APK (서명: 루트 key.properties 필요)
./gradlew :app:assembleRelease
```

빌드된 APK: `app/build/outputs/apk/release/app-release.apk`
릴리스 서명은 루트 `key.properties`(gitignore)가 있을 때만 활성화됩니다.

## 📂 프로젝트 구조

```
app/src/main/kotlin/com/umicorp/autolotto/
├── MainActivity.kt              # 진입점 (Compose setContent)
├── AppContainer.kt              # 앱 스코프 컴포지션 루트 (상태·서비스·슬롯 동적 평가)
├── dhlottery/                   # 동행복권 역공학 세션
│   ├── AuthService.kt           # RSA 로그인 (도메인별 쿠키)
│   ├── PurchaseService.kt       # 로또 구매 (execBuy.do)
│   ├── ResultService.kt         # 당첨번호 조회
│   ├── HistoryService.kt        # 구매 내역
│   ├── ServerHealthChecker.kt   # 서버 상태·점검 여부 확인
│   ├── DhlotterySession.kt      # OkHttp 세션 (수동 리다이렉트)
│   ├── DomainCookieStore.kt     # 도메인별 쿠키 저장소 (www↔ol 세션 공유)
│   ├── ApiConstants.kt          # API 경로 상수 (테스트용 베이스 URL 분리)
│   └── RsaCrypto.kt             # RSA PKCS1
├── data/
│   ├── SecureStore.kt           # EncryptedSharedPreferences (계정·슬롯·스마트 필터 영속 저장)
│   ├── LottoDatabase.kt         # SQLite 당첨 이력 DB
│   ├── LottoHistoryStore.kt     # 당첨 이력 캐시·동기화 저장소
│   ├── LottoHistoryCsvHelper.kt # 내장 CSV 시드 파서 (assets/winning_numbers.csv)
│   ├── Purchase.kt              # 구매 내역 모델
│   ├── PurchaseStore.kt         # 로컬 구매 내역 병합·캐시
│   └── WinningResult.kt         # 당첨 결과 모델
├── generator/                   # 스마트 번호 추천 · 통계 분석 엔진
│   ├── LottoFrequencyAnalyzer.kt # 최근 100회 출현 빈도 분석 (Hot/Cold/Normal)
│   └── SmartNumberGenerator.kt  # 전략별 번호 생성 · 6대 통계 필터 검증 · SmartFilterSettings
├── scheduler/                   # AlarmManager 자가연쇄 + WorkManager
│   ├── AlarmScheduler.kt        # 알람 등록 (자동구매 1001 / 결과확인 1002)
│   ├── AlarmTimes.kt            # 다음 알람 시각 순수 계산 (단위테스트 대상)
│   ├── AutoPurchaseWorker.kt    # 백그라운드 자동 구매
│   ├── CheckResultWorker.kt     # 백그라운드 당첨 확인
│   ├── SchedulerReceivers.kt    # 부팅·앱 업데이트 시 알람 복원
│   ├── ExactAlarmPermission.kt  # 정확 알람 권한 확인·설정 이동
│   └── Notifications.kt         # 알림 (구매/당첨/잔액)
├── util/
│   └── Gates.kt                 # 1회성 실행/초기화 게이트 유틸
└── ui/                          # Jetpack Compose · Material 3 Expressive
    ├── App.kt                   # 하단 pill 네비 + 4탭 페이저
    ├── SplashScreen.kt          # 스플래시 (자동 로그인)
    ├── screen/                  # Home · Number · History · Settings · Components
    ├── vm/                      # 화면별 ViewModel (상태 홀더)
    ├── theme/                   # Lucky Gloss 테마·색·모션
    └── util/                    # 볼 색상·포맷·런처 등 UI 헬퍼
```

## 🔐 보안

- 계정 정보는 **EncryptedSharedPreferences**(Android Keystore)로 암호화 저장 (Flutter판에서 자동 마이그레이션)
- 로그인 비밀번호는 동행복권 서버의 **RSA 공개키로 암호화** 후 전송
- 앱 코드에 로그 출력 없음, 릴리즈 빌드는 R8 축소·난독화 적용
- 모든 API 통신은 HTTPS

## ⚠️ 주의사항

- 이 앱은 **동행복권(dhlottery.co.kr) 계정**이 필요합니다
- 로또 구매에는 **예치금 충전**이 선행되어야 합니다
- 구매 가능 시간: 평일/일요일 06:00\~23:59, 토요일 06:00\~19:59
- 자동 구매가 정상 동작하려면 **배터리 최적화 제외** 설정을 권장합니다
- Google Play 도박 정책으로 인해 **스토어 배포 불가** → GitHub APK 직접 배포

## ⚖️ 면책 조항

동행복권의 공식 앱이 아니며, 사용에 따른 모든 책임은 사용자에게 있습니다. 동행복권 이용약관을 확인하고 본인의 판단 하에 사용하시기 바랍니다.

## 📄 라이선스

[MIT License](LICENSE) — 자유롭게 사용, 수정, 배포 가능합니다.

