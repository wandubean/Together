# LMS 정보 수집

Node.js 22 이상. 앱의 Canvas API 경로와 같은 방식으로 사용자, 수강 과목,
과제, 설명, 마감/잠금 시각, 원본 링크, 제출 상태와 과목별 공지를 수집한다.
과제의 필드는 Android `LmsAssignment`와 맞췄다. 앱으로 자동 입력하는 기능은 포함하지 않는다.

Together 폴더에서 실행:

```powershell
npm.cmd ci --prefix scripts
node scripts/lms-fetch.mjs --login
# 최초 로그인 이후에는 브라우저 없이 반복 수집
node scripts/lms-fetch.mjs
```

Chrome 로그인 창에서 학교 SSO 로그인을 직접 완료하면 자동으로 조회한다.
비밀번호를 스크립트에 입력하지 않으며,
별도 브라우저 세션을 사용하고 수집 후 닫는다.
로그인에 성공하면 세션 쿠키를 `.local/lms-session.json`에 저장하고,
다음 실행부터 Chrome 창 없이 HTTP 요청으로 재사용한다. 서버가 갱신한 쿠키도 반영한다.
세션 파일은 인증 정보이므로 공유하거나 커밋하지 않는다 (`.local/`은 Git 제외).
세션이 만료되면 다시 `--login`을 실행한다. `--session PATH`로 위치를 바꿀 수 있다.
브라우저가 설치되어 있어야 한다. 로그인은 최대 5분 대기한다.

자동화에서 최초 인증을 수행할 때는 `--login-stdin`으로 `username`, `password` 필드가
있는 JSON 한 줄을 표준입력에 전달할 수 있다. Chrome을 화면 없이 실행해 학교 SSO의
암호화 키가 준비된 뒤 인증하며 비밀번호를 파일이나 로그에 저장하지 않는다.
명령행 인수에 비밀번호를 넣지 않는다.

탐색기에서 `scripts/login.cmd`를 더블 클릭해도 실행할 수 있다.

이미 발급받은 API 토큰은 `LMS_TOKEN`, 기존 세션 쿠키는 `LMS_COOKIE` 환경변수로
전달할 수도 있다. 이 값은 저장소나 채팅에 올리지 않는다.

```powershell
node scripts/lms-fetch.mjs --out .local/lms-data.json
node --test scripts/lms-fetch.test.mjs
node scripts/lms-fetch.mjs --probe
```

`--probe`는 로그인 없이 실제 서버 응답을 확인한다. 인증 오류는 정상적으로 검출되지만
실제 수강 정보 수집 성공을 뜻하지 않는다.

기본 결과: `.local/lms-data.json` (Git 제외). 개인정보가 포함되므로 공유에 주의한다.
`courses`, `assignments`, `announcements`, `fetchedAt`, `errors`를 저장한다.
과제 조회 성공/실패는 기존 `fetchedCourseIds` / `failedCourseIds`,
공지 조회 성공/실패는 `fetchedAnnouncementCourseIds` / `failedAnnouncementCourseIds`로 구분한다.
오류에는 `resource` (`assignments` 또는 `announcements`)와 과목 ID가 포함된다.
공지에는 제목(`title`), HTML 본문(`message`), 게시일/수정일(`postedAt`, `updatedAt`),
원본 링크(`htmlUrl`), 작성자(`author`), 첨부파일 이름/링크(`attachments`)를 저장한다.
첨부파일 자체는 다운로드하지 않는다.
공지는 `courses/:id/discussion_topics?only_announcements=true`를 사용하여
최근 2주 기본 기간 제한 없이 접근 가능한 공지의 모든 페이지를 조회한다.
일부 과목이 실패해도 성공한 과목은 저장하고 종료 코드 2를 반환한다.
인증/전체 조회 실패는 종료 코드 1이며 기존 결과 파일은 덮어쓰지 않는다.
정상 전체 수집은 종료 코드 0. 실패한 과목의 기존 과제/공지를 삭제 판단에 사용하면 안 된다.
과제와 공지 조회는 독립적이므로 한쪽만 실패해도 다른 쪽 결과는 저장한다.
페이지 순환/100페이지 상한 초과도 실패 처리하여 잘린 목록을 성공으로 저장하지 않는다.

Canvas 문서:
- https://developerdocs.instructure.com/services/canvas/resources/assignments
- https://developerdocs.instructure.com/services/canvas/resources/discussion_topics
- https://developerdocs.instructure.com/services/canvas/basics/file.pagination
