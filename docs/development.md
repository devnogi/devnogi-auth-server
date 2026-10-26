# 개발과 검증

## 검증

Java 21을 설치하고 JAVA_HOME을 지정한다. Gradle은 저장소 wrapper를 사용한다.

```powershell
.\gradlew.bat verify --no-daemon
```

Linux/macOS/CI: `./gradlew verify --no-daemon`. check(테스트와 설정된 정적 검사) 및 bootJar를 실행한다. community/batch에는 spotlessCheck가 포함된다. 자동 포맷과 src 하위 REST Docs 재생성은 실행하지 않는다. 테스트 보고서는 build/reports/tests/test/index.html에 있다. 테스트 실패와 Java/의존성 다운로드 환경 오류를 구분해 기록한다.

## 로컬 Docker

MySQL(기본 호스트 3316)과 Kafka(9102)를 먼저 준비한다. 이 Compose는 DB와 Kafka를 만들지 않는다. Docker 빌드는 추적된 `application-sample.yml`을 이미지 안의 `application.yml`로 복사하고, 실행 시 환경 변수를 주입한다. 호스트의 실제 `application.yml`과 `.env` 파일은 이미지 빌드에서 제외한다. OAuth 로그인·메일·파일 업로드에는 실제 개발용 제공자 설정이 별도로 필요하다. 예제의 placeholder로 외부 기능이 성공한다고 보장하지 않는다.

서비스 폴더에서 아래 명령을 사용한다. 기존 `.env.local`이 있으면 덮어쓰지 말고 필요한 키를 비교한다. Docker 실행에는 호스트 `application.yml` 복사가 필요 없다. IDE에서 직접 실행할 때만 샘플을 복사하고 실제 파일은 Git에 올리지 않는다.

```powershell
Copy-Item .env.example .env.local
docker compose --env-file .env.local -f docker-compose-local.yml config --quiet
docker compose --env-file .env.local -f docker-compose-local.yml up --build auth-server
```

기본 포트: 8091. Compose의 env_file과 interpolation은 다르므로 --env-file을 생략하지 않는다. Linux에서는 host.docker.internal host-gateway 설정이 필요할 수 있다. 필요한 서비스만 지정해 Docker socket을 마운트하는 autoheal은 실행하지 않는다. config 통과는 기동·DB migration·외부 연동 성공의 증거가 아니다. 중단은 같은 파일과 --env-file로 stop을 실행한다. down -v는 데이터를 삭제하므로 별도 판단한다.

## CI와 배포

PR 필수 검사 이름은 verify와 secret-scan이다. 원격 브랜치 보호에서 두 검사와 사람 리뷰를 필수로 지정해야 강제된다. yaml 파일만 추가해도 보호가 활성화되는 것은 아니다. 이미지 게시/운영 배포는 workflow_dispatch로 분리했고 운영은 main/production, 개발은 dev/development로 제한한다. GitHub Environment에 승인자를 별도로 설정해야 실제 사람 승인 게이트가 생긴다. push/PR 생성만으로 배포하지 않는다.

닉네임 정책 변경의 MySQL 검증은 PR `verify` job에서 MySQL 8.0.42에 실제 Flyway 이력을 적용하는 `NicknameMigrationMySqlTest`와 인증·마이페이지 동시 갱신을 확인하는 `NicknameConcurrencyMySqlTest`를 순서대로 필수 실행한다. 로컬에서는 격리된 임시 컨테이너를 만드는 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-nickname-mysql.ps1 -Image mysql:8.0.42`로 같은 검사를 실행한다. 일반 `verify`는 MySQL 환경 변수가 없으면 해당 통합 테스트를 건너뛰므로, 일반 `verify`만으로 DB 제약이나 동시성을 검증했다고 주장하지 않는다.

실제 DB에 마이그레이션을 적용하기 전 `scripts/check-nickname-duplicates.sql`을 대상 auth 스키마에서 실행한다. 결과가 하나라도 나오면 자동 개명하지 않고 적용을 중단한다. 모든 Auth 노드는 JWT와 다른 전용 `NICKNAME_TICKET_SECRET`을 공유해야 하며, 누락·짧은 키·JWT 키 재사용 시 기동이 실패한다. 새 코드의 `NICKNAME_ENFORCE_ISSUED_CANDIDATES` 기본값은 `true`다. 구형 프런트엔드와 겹치는 별도 승인된 배포에서만 모든 노드에 `false`를 명시하고, 새 프런트엔드 전환 뒤 override를 제거한다.

각 서비스의 Compose는 개별 개발 환경이다. 전체 MSA가 자동 연결된다는 뜻은 아니다. Kafka는 bootstrap 주소뿐 아니라 브로커 advertised.listeners가 각 애플리케이션 컨테이너에서 접근 가능한 주소여야 한다. 호스트용 localhost 광고 주소를 컨테이너에 그대로 사용하지 않는다. 통합 환경에서는 공통 네트워크의 내부 리스너를 사용하거나 개발 호스트 주소로 광고하도록 별도 구성한다.

## 공동 로컬 인프라 연결

공동 루트 가이드로 devnogi-infra-prod의 mysql/kafka를 먼저 시작하면 memonogi-local 네트워크가 만들어진다. 기존 Compose 뒤에 -f docker-compose-shared.yml을 추가하면 애플리케이션이 공통 Kafka 내부 리스너(devnogi-local-kafka:29092)를 사용한다. auth는 공통 MySQL에도 연결한다. community/batch의 자체 DB 등은 유지되며 community의 별도 kafka는 선택하지 않는다. 명령: `docker compose --env-file .env.local -f docker-compose-local.yml -f docker-compose-shared.yml up --build auth-server`.

## 로컬 이메일 로그인

샘플은 `DB_SCHEMA=auth`를 사용한다. 기존 Flyway migration이 `auth.roles`를 참조하므로 공통 infra의 로컬 bootstrap으로 auth 스키마와 로컬 앱 사용자 권한을 먼저 준비한다. 이미 있는 volume에는 infra의 비파괴 bootstrap 절차를 사용하고 volume을 삭제하거나 기존 migration을 수정하지 않는다. 기존 `.env.local`의 `DB_SCHEMA=devnogi`도 확인한다.

auth와 gateway의 `.env.example`에는 동일한 공개 합성 Base64 키가 있다. 디코딩 후 32바이트 이상이어야 하며 이 키는 로컬 검증 전용이다. `JWT_ACCESS_TOKEN_VALIDITY=3600000`, `JWT_REFRESH_TOKEN_VALIDITY=86400000`은 milliseconds다. 쿠키 Max-Age는 각각 3600/86400 seconds로 변환한다. 환경 변수 생략 시 과거 fallback 숫자 3600/86400 milliseconds는 그대로이므로 로컬 실행에서 명시 값을 빠뜨리지 않는다.

| 환경 변수 | 생략 시 기본값 | HTTP localhost 샘플 |
| --- | --- | --- |
| `JWT_COOKIE_DOMAIN` | `.memonogi.com` | 빈 값: Domain 속성을 생략 |
| `JWT_COOKIE_SECURE` | `true` | `false` |
| `JWT_COOKIE_SAME_SITE` | `None` | `Lax` |

쿠키 생성·갱신·삭제는 같은 scope를 사용하고 이름, HttpOnly, Path=/는 유지한다. React의 logout 오류 경로도 같은 설정을 사용한다. 로컬 override를 공유/운영 환경에 적용하지 않는다. JWT는 응답 JSON이나 브라우저 JS 저장소로 옮기지 않는다.

합성 계정은 기존 `POST /api/auth/signup`에 이미지 없이 등록한다. 첫 로그인 검증 전에 `account_locks`를 수동 생성하지 않는다. 이메일 로그인 → `/api/user/info` → refresh → 인증된 logout을 확인하고, BFF 경로/응답 필드는 [HTTP 계약](../contracts/openapi.yaml)을 따른다. 비로그인 API는 JSON/401을 반환하고 OAuth 브라우저 경로는 리다이렉트한다. 실제 OAuth, SMTP, S3 자격 증명은 이메일 로그인 검증에 필요하지 않다.

기존 JWT 형식에는 고유 토큰 ID가 없으므로 같은 초에 같은 사용자에게 발급한 토큰 값이 같을 수 있다. 동시/연속 로그인과 refresh 저장 중복 문제는 별도 정책 검토 대상이며 이번 변경은 JWT claims나 잠금 정책을 바꾸지 않는다. 실패 시 HTTP 상태와 오류 코드만 기록하고 비밀번호·Cookie 헤더·전체 JWT를 로그에 남기지 않는다.
