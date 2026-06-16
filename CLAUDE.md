# WlsReminderApp — 프로젝트 컨텍스트

> 이 파일은 Claude Code가 프로젝트를 빠르게 이해하도록 돕는 인수인계 문서입니다.
> 실제 코드가 항상 우선입니다. 아래 설명과 파일이 다르면 파일 내용을 신뢰하세요.

---

## 1. 프로젝트 개요

개인용 **반복 리마인더 앱** (약 복용 등 일정 알림). 앱스토어 배포가 아니라
APK를 직접 전달해 지인에게 설치하는 용도. 네트워크는 업데이트 확인 외에는 불필요.

- 매일(선택한 요일에) 지정 시간에 알림
- 상단바에 "아직 완료하지 않은 일정입니다" 형태로 상시 표시
- "완료" 버튼을 누르면 그 알림만 사라짐
- 알림 하나당 상태 메시지 1개(가장 가까운 시간 1개)만 표시
- 여러 개의 리마인더 등록 가능

---

## 2. 개발 환경

- **언어:** Java (Kotlin 아님)
- **패키지명:** `com.example.wlsreminderapp`
- **빌드 스크립트:** `build.gradle.kts` (Kotlin DSL)
- **앱 표시 이름:** `Wls리마인더` (`strings.xml`의 `app_name`)
- **IDE:** Android Studio 2026.1.1 (Quail)
- **SDK:** compileSdk 34 / targetSdk 34 / minSdk 26
- **viewBinding:** 사용 (`ActivityMainBinding`, `ItemReminderBinding` 등)
- **DB:** Room (annotationProcessor, LiveData)
- **테스트 OS:** 삼성 갤럭시 (Android 14+ 추정)

---

## 3. 파일 구조

```
app/src/main/java/com/example/wlsreminderapp/
├── Reminder.java                 # Room Entity: id, name, description, times, days, isEnabled
├── ReminderDao.java              # @Dao: getAll(LiveData), getAllOnce, insert(long), update, delete
├── ReminderDatabase.java         # @Database, version 3, Migration 1→2, 2→3 포함
├── AlarmScheduler.java           # AlarmManager 스케줄링 (요일 기반), parseDays 유틸
├── ReminderReceiver.java         # BroadcastReceiver: 알람 수신 → 재예약 + 알림 표시
├── ReminderBootReceiver.java     # 재부팅 시 알람 복원
├── ReminderPersistentService.java# Foreground Service (알림 끈질기게 유지) — 아래 TODO 참고
├── ReminderAdapter.java          # RecyclerView ListAdapter (편집/삭제/토글 콜백)
├── MainActivity.java             # 목록 화면 + 권한 요청 + 업데이트 체크
├── AddReminderActivity.java      # 추가/편집 화면 (요일 토글 + 시간 다중 추가)
├── VersionChecker.java           # GitHub version.json 으로 업데이트 확인
└── AppUpdater.java               # DownloadManager로 APK 다운로드 후 설치 화면 띄움

app/src/main/res/layout/
├── activity_main.xml             # RecyclerView + FAB
├── activity_add_reminder.xml     # 입력폼 + 요일 ToggleButton 7개 + TimePicker + 시간 칩
└── item_reminder.xml             # 리스트 아이템 (이름/설명/시간/요일/스위치/편집/삭제)

app/src/main/res/xml/
└── file_paths.xml                # FileProvider 경로 (APK 설치용)

app/src/main/AndroidManifest.xml  # 권한, Activity/Receiver/Service, FileProvider 선언
```

---

## 4. 핵심 동작 메모

### 데이터 모델
- `times`: `"08:00,20:00,22:00"` 형태의 문자열로 여러 시간 저장. `getTimeList()`로 `List<int[]>` 변환.
- `days`: `"2,3,4,5,6,7,1"` (Calendar.DAY_OF_WEEK 값, **1=일, 2=월 … 7=토**). 비어있으면 매일로 간주.
- `isEnabled`: 스위치 on/off.

### 알람 ID 규칙
- 각 시간 슬롯의 alarmId = `reminderId * 1000 + (시간 인덱스)`
- 알림 ID(notifId) = `alarmId / 1000` = `reminderId` → **리마인더 1개당 알림 1개**
- `cancelAll(reminderId)`은 인덱스 0~9까지 취소 (시간은 최대 10개)

### 알림 소리/무음 규칙 (의도)
- **알람 정시**에 울릴 때만 소리/진동 있음
- **초기 저장 직후 즉시 표시** 또는 **스와이프 후 재표시**는 무음

### 스와이프 방지
- `setOngoing(true)` + `setDeleteIntent(...)`로 밀면 다시 뜨도록 구현
- (Android 13 이하: 스와이프 자체 불가 / 14+: 밀면 즉시 재표시)

### DB 마이그레이션
- `ReminderDatabase`에 `MIGRATION_1_2`, `MIGRATION_2_3` 존재 → 업데이트 시 데이터 보존
- ⚠️ **스키마(컬럼/테이블)를 바꾸면 반드시 version을 올리고 새 Migration을 추가**할 것.
  `fallbackToDestructiveMigration()`은 사용하지 말 것 (데이터 전체 삭제됨).

### 업데이트 흐름
1. `VersionChecker`가 GitHub의 `version.json`을 읽어 `versionName`과 비교
2. 새 버전이면 다이얼로그 → `AppUpdater.start()`로 구글드라이브 APK 다운로드 → 설치
- `version.json` 형식: `{ "version": "1.1", "url": "https://drive.google.com/uc?export=download&id=FILE_ID" }`
- `VersionChecker.VERSION_URL`에 GitHub Raw URL이 들어가 있어야 함

---

## 5. 알려진 이슈 & 개선 예정 (TODO)

> 우선순위 순. 아직 적용 전이면 이 순서로 진행 권장.

### 🔴 (1) Foreground Service + 여러 알람 동시 표시 충돌 — 중요
- 현재 리마인더 알림을 포그라운드 알림으로 합쳐 둠. 서비스는 알림을 하나만 가질 수 있어서:
    - 여러 리마인더가 동시에 떠 있을 때 하나를 "완료"하면 다른 알림이 사라지는 버그
    - `ACTION_DONE`이 `notifId`를 무시하고 `stopForeground`+`stopSelf`를 호출함
- **권장 수정:** Foreground Service를 제거하고, 알림을 각각 독립적으로
  `NotificationManager.notify()`로 표시. DONE/RESHOW는 `ReminderReceiver`에서 처리.
  소리/무음은 **채널 2개**(IMPORTANCE_HIGH / IMPORTANCE_LOW)로 분리.
  알람 자체는 AlarmManager가 울리므로 상시 서비스는 불필요.

### 🔴 (2) 삼성 배터리 최적화로 알람 멈춤 — 중요
- 삼성의 앱 절전 기능이 앱을 재워서 알람이 며칠 뒤 멈춤
- **수정:** `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 권한 + `MainActivity`에서
  `isIgnoringBatteryOptimizations()` 확인 후 설정 유도. 추가로 사용자에게
  "배터리 > 백그라운드 사용 제한 > 절전 앱에서 제외" 수동 안내.

### 🟡 (3) 알림 탭해도 앱이 안 열림
- 알림에 `setContentIntent(MainActivity로 가는 PendingIntent)` 추가 필요.

### 🟡 (4) 비활성 리마인더를 편집하면 다시 켜짐
- `AddReminderActivity.saveEdit()`이 `new Reminder(..., true)`로 항상 활성화.
- 편집 진입 시 원래 `isEnabled` 값을 보존하도록 수정.

### 🟡 (5) 업데이트 버전 비교가 단순 문자열 일치
- `!latest.equals(current)` → 다운그레이드/`1.2` vs `1.10` 오판 가능.
- 숫자로 파싱해 **최신이 더 높을 때만** 알림.

### 🟡 (6) 저장 즉시 알림이 요일을 무시
- 평일만 설정해도 주말에 만들면 즉시 알림이 뜸.
- "오늘이 선택된 요일일 때만 즉시 표시"로 보완.

### 🟢 (7) 백업/복원 — 데이터 유실 대비
- 데이터가 폰에만 존재. 리마인더를 JSON으로 내보내기/불러오기 기능.

### 🟢 (8) 기타 기능
- 스누즈("10분 후 다시"), 완료 기록/스트릭, 커스텀 앱 아이콘.

---

## 6. 작업 규칙

- **빌드 스크립트는 `build.gradle.kts` (Kotlin DSL)** 문법을 따를 것. `.gradle`(Groovy) 아님.
- **새 코드는 Java로** 작성 (Kotlin 변환하지 말 것).
- 포맷팅/뷰 접근은 **viewBinding** 사용.
- DB 변경 시 **version 올리기 + Migration 추가** (절대 destructive migration 금지).
- 백그라운드 작업은 기존 패턴대로 `Executors.newSingleThreadExecutor()` 사용,
  UI 갱신은 `runOnUiThread`.

### APK 빌드
- Android Studio: `Build > Build Bundle(s) / APK(s) > Build APK(s)`
- 결과물: `app/build/outputs/apk/debug/app-debug.apk`

### 배포 (업데이트)
1. `build.gradle.kts`의 `versionCode` +1, `versionName` 올리기
2. APK 빌드
3. 구글드라이브에 새 APK 업로드 → FILE_ID 확인
4. GitHub `version.json`의 `version` / `url` 수정
5. APK는 디스코드/드라이브로 공유 (카카오톡은 APK 차단)
- ⚠️ 설치 시 기존 앱을 **삭제하지 말고 덮어쓰기**해야 데이터 유지됨.