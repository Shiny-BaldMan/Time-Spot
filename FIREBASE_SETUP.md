# Firebase 로그인 설정

프로젝트: `timespot-d05dd` / Android 패키지: `com.example.timecaptule`

## 콘솔에서 한 번 설정

1. Authentication → 로그인 방법 → 이메일/비밀번호 활성화.
2. Firestore Database → 규칙 → 이 저장소의 `firestore.rules` 내용을 붙여넣고 게시.
   이 파일을 Git에 추가하는 것만으로 서버 규칙이 바뀌지는 않습니다.
   기존 데이터에 다른 기능용 규칙이 있다면 해당 규칙도 검토 후 함께 유지하세요.
3. 앱을 실행해 회원가입. 닉네임은 영문·숫자·밑줄 2~20자, 비밀번호는 8자 이상.

## 저장되는 데이터

- Authentication: `{nickname}@login.timespot.example` / Firebase가 비밀번호 관리.
- Firestore `users/{uid}`: `nickname`, `createdAt`(서버 시각).
- Firestore `nicknames/{nickname}`: `uid`. 닉네임 정확 일치 중복 확인용.
- 계정 생성 후 두 Firestore 문서는 트랜잭션으로 저장합니다.
- Firestore 저장 실패 시 계정은 Authentication에 남을 수 있습니다. 규칙·네트워크를
  확인한 뒤 같은 계정으로 로그인하면 사용자 정보 생성을 재시도합니다.

닉네임은 소문자로 정규화하며 변경하지 않습니다. 내부 이메일은 실제 메일함이
아니므로 이메일 인증·비밀번호 복구를 제공하지 않습니다. 기존 `123 / 123` 더미
로그인은 제거했습니다. 테스트할 계정도 회원가입으로 직접 생성해야 합니다.

## 확인 순서

1. 새 닉네임으로 중복 확인 → 회원가입 → 메인 화면 이동.
2. Firebase 콘솔에서 Authentication 계정 및 두 Firestore 문서 생성 확인.
3. 앱 강제 종료 후 재실행 → 자동 로그인 확인.
4. 친구 · 계정 → 로그아웃 → 잘못된 비밀번호로 오류 확인 → 올바른 비밀번호로 로그인.
5. 같은 닉네임으로 재가입하면 중복 오류, 다른 계정의 사용자 문서는 조회 거부.

캡슐은 아직 계정별 로컬 저장입니다. 친구·캡슐의 Firestore 저장과 사진 업로드는
이번 로그인 구현에 포함되지 않습니다. 이전 체험 데이터는 자동 이전하지 않습니다.

CLI를 사용할 경우 Firebase 프로젝트 관리자 인증 후 다음 명령으로 규칙만 배포할 수 있습니다.

```shell
firebase deploy --only firestore:rules --project timespot-d05dd
```
