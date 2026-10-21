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

각 서비스의 Compose는 개별 개발 환경이다. 전체 MSA가 자동 연결된다는 뜻은 아니다. Kafka는 bootstrap 주소뿐 아니라 브로커 advertised.listeners가 각 애플리케이션 컨테이너에서 접근 가능한 주소여야 한다. 호스트용 localhost 광고 주소를 컨테이너에 그대로 사용하지 않는다. 통합 환경에서는 공통 네트워크의 내부 리스너를 사용하거나 개발 호스트 주소로 광고하도록 별도 구성한다.

## 공동 로컬 인프라 연결

공동 루트 가이드로 devnogi-infra-prod의 mysql/kafka를 먼저 시작하면 memonogi-local 네트워크가 만들어진다. 기존 Compose 뒤에 -f docker-compose-shared.yml을 추가하면 애플리케이션이 공통 Kafka 내부 리스너(devnogi-local-kafka:29092)를 사용한다. auth는 공통 MySQL에도 연결한다. community/batch의 자체 DB 등은 유지되며 community의 별도 kafka는 선택하지 않는다. 명령: `docker compose --env-file .env.local -f docker-compose-local.yml -f docker-compose-shared.yml up --build auth-server`.
