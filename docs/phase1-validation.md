# 1차 설정 검증 기록

2026-10-21, Windows PowerShell, Amazon Corretto 21.0.12, 저장소 Gradle wrapper. 로컬 브랜치: codex/ai-workspace-phase1. commit/push/배포는 수행하지 않았다.

| 검사 | 결과 |
| --- | --- |
| .\gradlew.bat verify --no-daemon | 통과, 35s |
| JUnit XML 합계 | 15개, 실패 0, 건너뜀 0 |
| node scripts/secret-scan.mjs | finding 0 (현재 트리의 기본 검사, 전체 이력 검사 아님) |
| Docker Compose config --no-env-resolution --quiet | 합성 .env.example 사용, 구문 검사 통과 |
| git diff --check | 통과 |

기존 테스트 fixture에서 KafkaProducerService mock과 로그인 응답에 필요한 User.role을 보완했다. 테스트용 H2/가짜 OAuth/AWS 설정으로 Spring context가 외부 운영 자격 증명 없이 올라온다. 제품 Java 동작은 변경하지 않았다.

Docker 데몬 기동·이미지 빌드·전체 MSA 연동·운영 배포·실제 PR/원격 필수 검사 차단은 이 기록의 검증 범위 밖이다. --no-env-resolution은 서비스 env_file 로드를 생략하므로 실제 실행 전 .env.local 준비와 config 재검증이 필요하다.

독립 리뷰 P2 수정: 일반/소셜 가입의 실제 serverName=null 및 profileImageUrl=null을 schema/fixture와 생성 경로 회귀 테스트로 확인했다. 소비자 @NotBlank/DB non-null 불일치는 contracts/README.md에 미해결 제품 위험으로 기록했다.

## Docker 패키징 후속 검증

Dockerfile은 `clean build` 대신 `clean bootJar`로 실행용 JAR만 만든다. `build`의 plain JAR와 실행용 JAR가 wildcard COPY에서 충돌하는 문제를 방지한다. 테스트는 별도 표준 `verify`로 실행한다.

- JDK 21 `gradlew.bat clean bootJar --no-daemon`: 22초, 통과. `build/libs/das-0.0.1-SNAPSHOT.jar` 하나만 생성됐고 plain JAR는 없었다.
- 이어서 `gradlew.bat verify --no-daemon`: 34초, 통과. 새 JUnit XML 기준 15개 테스트, 실패·오류·건너뜀 0.
- `.dockerignore`와 추적된 샘플의 `application.yml` 교체 동작은 합성 파일을 사용하는 scratch Docker 빌드로 확인했다. 호스트의 실제 설정은 이미지에 포함하지 않는다.
- 전체 auth 이미지 빌드는 베이스 이미지 다운로드 중 중단했으므로 성공으로 기록하지 않는다. 실제 앱 기동과 외부 연동은 여전히 미검증이다.
