# 🎮 Devnogi Auth Server (DAS)

마비노기 경매장 거래 내역 조회 및 통계 서비스, **Devnogi**의 **인증 및 인가(Authentication & Authorization) 서버**입니다.

## 1\. 개요 (Overview)

본 프로젝트는 [마비노기 경매장 거래 내역 조회 및 통계 서비스](https://www.google.com/search?q=https://github.com/example/devnogi-project) (가상)의
일부분으로, 마이크로서비스 아키텍처(MSA)에서 **사용자 인증 및 인가**를 전담하는 백엔드 서버입니다.

주요 역할은 다음과 같습니다:

* 표준 이메일 회원가입 및 로그인 처리
* OAuth 2.0을 이용한 소셜 로그인 (Google, Kakao, Naver) 연동
* 인증 완료 후, 서비스 전반에서 사용될 JWT(Access Token, Refresh Token) 발급
* 사용자 정보(프로필, 닉네임 등) 관리 및 회원 탈퇴
* Kafka를 통해 사용자 정보 변경 사항(예: 닉네임 변경)을 다른 서비스(커뮤니티, 알림 등)에 전파(Eventual Consistency)

## 2\. 주요 기능 (Key Features)

본 인증 서버의 핵심 기능입니다.

* **🔐 표준 인증 (Standard Auth)**

    * 이메일, 비밀번호, 닉네임 기반의 회원가입
    * 이메일 및 닉네임 중복 확인 API 제공
    * Bcrypt를 사용한 비밀번호 암호화
    * 로그인 성공 시 **JWT (Access/Refresh Token)** 를 `HttpOnly` 쿠키로 발급

* **✨ 소셜 로그인 (Social Login)**

    * **OAuth 2.0** 클라이언트를 통한 소셜 로그인 연동
    * 지원 프로바이더: **Google, Kakao, Naver**
    * 최초 소셜 로그인 시, 추가 정보(닉네임, 프로필 이미지)를 입력받아 회원가입 완료

* **👤 사용자 관리 (User Management)**

    * 인증된 사용자의 프로필 정보(닉네임, 프로필 이미지) 조회 및 수정
    * 회원 탈퇴 (계정 `INACTIVE` 상태 변경)
    * 프로필 이미지 업로드 시 **AWS S3** 사용

* **🛡️ 인가 및 보안 (Authorization & Security)**

    * Spring Security 기반의 인증/인가 파이프라인 구축
    * `UserAuthenticationFilter`를 통해 매 요청 시 쿠키의 JWT를 검증하여 사용자 인증
    * `SUPER_ADMIN`, `ADMIN`, `USER` 역할(Role) 기반의 API 접근 제어
    * `@ActiveUserRequired` AOP 어노테이션을 통해 비활성화/탈퇴 유저의 API 접근 차단

* **📨 비동기 이벤트 발행 (Async Event Publishing)**

    * 사용자 정보(닉네임, 프로필) 변경 시, **Kafka**를 통해 `USER_INFO_UPDATE_EVENT` 메시지 발행
    * (이를 통해 다른 마이크로서비스가 사용자 정보의 변경 사항을 구독하여 동기화할 수 있습니다.)

## 3\. 기술 스택 (Tech Stack)

* **Backend**: Kotlin, Spring Boot 3.5.0, Spring Security, Spring Data JPA (Hibernate)
* **Authentication**: JWT, OAuth2 Client (Google, Naver, Kakao)
* **Database**: MySQL, Flyway (DB 마이그레이션), Redis
* **Async**: Kafka (이벤트 발행)
* **Storage**: AWS S3 (프로필 이미지 저장)
* **API Docs**: SpringDoc (Swagger UI)
* **DevOps**: Docker, Docker Compose, Gradle
* **Testing**: JUnit5, Mockito

## 4\. DB 스키마 (Database Schema)

`src/main/resources/db/migration`의 Flyway 스크립트를 기반으로 한 주요 테이블입니다.

* `users`: 핵심 사용자 정보 (이메일, 비밀번호, 닉네임, 프로필 URL, 상태, role\_id)
* `roles`: 역할 정의 (예: SUPER\_ADMIN, ADMIN, USER)
* `oauth_users`: `users` 테이블과 소셜 로그인 정보(provider, provider\_user\_id)를 매핑
* `refresh_tokens`: JWT 리프레시 토큰 저장 (로그아웃 및 토큰 탈취 대응)
* `account_locks`: 로그인 실패 횟수 및 계정 잠금 관리
* `password_history`: 비밀번호 변경 이력 (재사용 방지)
* `login_history`: 로그인 성공/실패 이력

## 5\. 프로젝트 구조 (Package Structure)

```
src/main/java/until/the/eternity/das
├── auth          # 1. 표준 인증 (회원가입, 로그인) 컨트롤러, 서비스, DTO
├── oauth         # 2. 소셜 로그인 (OAuth2) 서비스, 핸들러, DTO
├── user          # 3. 사용자 정보 관리 (프로필 조회/수정, 탈퇴)
├── role          # 4. 역할(Role) 관련 엔티티, 리포지토리
├── token         # 5. JWT 유틸, 토큰(Refresh Token 등) 엔티티, 서비스
├── login         # 6. 로그인 관련 엔티티 (로그인 이력, 계정 잠금 등)
├── common        # 7. 공통 모듈
│   ├── application (S3, Kafka 등 공용 서비스)
│   ├── aop         (AOP 관련 로직)
│   ├── config      (SecurityConfig 등 설정)
│   ├── exception   (전역 예외 처리)
│   ├── filter      (JWT 인증 필터)
│   ├── response    (공통 응답 포맷)
│   └── util        (JWT, Cookie 유틸)
└── demo          # 8. 데모 및 테스트용 컨트롤러
```

## 6\. 시작하기 (Getting Started)

### 1\. 설정 파일 복사

프로젝트 루트에서 제공되는 샘플 설정 파일을 복사하여 실제 설정 파일을 생성합니다.

```bash
# 1. .env.sample 파일을 .env 로 복사
cp .env.sample .env

# 2. application-sample.yml 파일을 application.yml 로 복사
cp src/main/resources/application-sample.yml src/main/resources/application.yml
```

### 2\. 설정 파일 수정

생성된 `.env` 파일과 `application.yml` 파일에 실제 환경에 맞는 값(DB, JWT, OAuth, AWS, Kafka 등)을 입력합니다.

* `.env`: DB 접속 정보, AWS 키, OAuth 클라이언트 ID/Secret 등 민감 정보
* `application.yml`: 서버 포트, 프로필 설정 등

### 3\. 프로젝트 실행

**A. Docker Compose 사용 (권장)**

(프로젝트 루트에 `docker-compose.yml`이 구성되어 있다고 가정합니다.)

```bash
docker-compose up -d --build
```

**B. 로컬에서 직접 실행**

```bash
./gradlew bootRun
```

### 4\. API 문서 확인

서버가 실행된 후, 브라우저에서 아래 주소로 접속하여 API 문서를 확인할 수 있습니다.

* [http://localhost:8080/swagger-ui.html](https://www.google.com/search?q=http://localhost:8080/swagger-ui.html) (포트는
  `application.yml` 설정에 따라 다를 수 있습니다.)

## 7\. 주요 API (Key Endpoints)

* `POST /api/auth/signup`: 표준 (이메일) 회원가입
* `POST /api/auth/admin/signup`: (관리자용) 관리자 계정 생성
* `POST /api/auth/login`: 표준 (이메일) 로그인
* `GET /api/auth/check-email`: 이메일 중복 확인
* `GET /api/auth/check-nickname`: 닉네임 중복 확인
* `POST /api/auth/signup/social`: 소셜 로그인 최초 가입 (추가 정보 입력)
* `GET /login/oauth2/code/{provider}`: (Redirect) 소셜 로그인 (provider: `google`, `kakao`, `naver`)
* `GET /api/user/info`: 내 정보 조회
* `PUT /api/user/info`: 내 정보 수정 (닉네임, 프로필 이미지)
* `PATCH /api/user/withdraw`: 회원 탈퇴