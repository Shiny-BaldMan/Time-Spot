# Firebase 계정, 친구, 캡슐 설정

프로젝트: `timespot-d05dd` / Android 패키지: `com.example.timecaptule`

## 콘솔 설정

1. Firebase Authentication에서 이메일/비밀번호 로그인을 활성화합니다.
2. 관리자 인증이 된 Firebase CLI에서 다음 명령을 실행해 Firestore 규칙과 친구 요청 인덱스를 배포합니다.

```shell
firebase deploy --only firestore:rules,firestore:indexes --project timespot-d05dd
```

## 데이터 구조

- Authentication 계정 이메일은 `{nickname}@login.timespot.example` 형식이며, Firebase Auth가 닉네임 중복을 막습니다.
- `users/{uid}`는 공개 PID(`pid`, Firebase Auth UID), 닉네임, 생성 시각만 보관합니다. 다른 사용자의 프로필은 PID를 알고 있을 때만 읽을 수 있고 전체 사용자 목록은 조회할 수 없습니다.
- `friendLookup/{nickname}`은 닉네임을 UID로 찾는 정확 일치 검색 인덱스입니다. 전체 목록은 조회할 수 없습니다. 닉네임 검색을 제공하려면 이처럼 별도의 검색 인덱스가 필요합니다.
- `friendRequests/{fromUid}__{toUid}`는 보낸 사람, 받은 사람, 상태를 보관합니다. 받은 사람이 수락하면 `friendships/{uidPair}`가 생성되고 양쪽 친구 목록에 나타납니다.
- `users/{uid}/sharedCapsules/{capsuleId}`는 공유받은 캡슐의 참조만 보관합니다. 실제 캡슐 내용은 별도 읽기 규칙으로 다시 확인합니다.
- `capsules/{capsuleId}`는 소유자, 내용, 열람 시각, 공유 대상 PID 목록을 보관합니다. 소유자는 언제든지 볼 수 있고, 공유 대상 친구는 친구 관계가 유지되고 `opensAt`이 지난 뒤에만 읽을 수 있습니다.
- 캡슐 하나는 최대 8명과 공유할 수 있습니다.
- 기존 `nicknames/{nickname}` 문서는 앱에서 더 이상 읽거나 쓰지 않습니다. 이 변경은 기존 문서를 자동 삭제하지 않습니다.

기존 계정의 `friendLookup` 항목은 새 앱으로 로그인할 때 `ensureProfile()`이 채웁니다. Firestore 배포 후 기존 사용자가 한 번 로그인해야 닉네임으로 검색할 수 있습니다.

닉네임은 소문자로 정규화하며 변경하지 않습니다. 내부 이메일은 실제 메일함이 아니므로 이메일 인증과 비밀번호 복구는 제공하지 않습니다. 비밀번호는 8자 이상이어야 합니다.

## 사용 방법

1. 회원가입 후 친구 · 계정 화면에서 닉네임으로 친구 요청을 보냅니다.
2. 상대방이 요청을 수락하면 양쪽 친구 목록에 표시됩니다.
3. 캡슐 작성 화면에서 공유할 친구를 선택합니다. 선택하지 않으면 나만 볼 수 있습니다.
4. 친구는 캡슐의 열람 시각이 지난 뒤 홈 화면에서 공유 캡슐을 볼 수 있습니다.

로그인 전 만든 체험 캡슐은 로컬 기기에 남으며 Firestore로 자동 이전되지 않습니다. 로그인한 계정의 새 캡슐은 Firestore에 저장됩니다. 닉네임 검색 인덱스에는 닉네임과 UID만 저장하고, 프로필 문서에도 민감한 정보를 넣지 마세요.

Firebase CLI 명령은 Firestore 배포 권한을 가진 개발자가 실행해야 합니다. 이 저장소의 규칙 파일만 변경해도 Firebase 콘솔의 실제 규칙은 바뀌지 않습니다.
