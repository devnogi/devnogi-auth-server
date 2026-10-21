# 서비스 계약

## 이메일 로그인 HTTP 계약

원본은 [openapi.yaml](openapi.yaml)이다. 제공자는 auth, 소비자는 gateway(`/das` prefix)를 경유하는 React BFF다. 이번 계약은 `POST /api/auth/login`, `GET /api/user/info`, `POST /api/auth/refresh`, `POST /api/auth/logout` 네 경로만 다룬다. BFF의 내 정보 경로는 `/api/auth/me`다.

기존 `{email,password}` 요청과 사용자 DTO·CommonResponse 구조는 유지한다. 보호된 `/api/**`의 미인증 응답은 OAuth HTML/302 대신 JSON/401이며 OAuth 브라우저 진입점은 그대로 리다이렉트한다. 정상 로그인·갱신은 HttpOnly 쿠키 두 개를 각각 Set-Cookie로 반환한다. refresh 요청에는 refresh 쿠키만 필요하며 만료 access 쿠키를 gateway에 함께 전달하지 않는다. 로그인된 사용자의 logout은 서버 refresh 토큰을 폐기하고 같은 범위의 쿠키를 만료시킨다. 익명 logout은 쿠키만 삭제하므로 서버 폐기 성공과 구분한다.

`EmailLoginContractTest`가 실제 컨트롤러, SecurityConfig, 인증 필터, CookieUtil의 응답을 OpenAPI의 required·type·nullable·enum·reference에 대조한다. 전체 OpenAPI validator나 실제 DB 통합 테스트를 대신하지 않는다. `FreshAccountLoginJpaTest`는 정상 가입 후 lock 사전 삽입 없이 실제 JPA 저장·flush와 두 차례 로그인을 확인한다. 로컬 MySQL/Flyway와 브라우저 결과는 공동 루트 실행 기록을 함께 확인한다. 이 테스트들은 표준 `verify`/PR CI에서 실행한다.

필드 제거·이름/타입 변경·새 필수 입력은 소비자와 함께 기획·승인한다. 쿠키 설정을 생략하면 운영 기본값을 유지하고, HTTP localhost override는 auth와 React에서 같이 설정한다. JWT 시간 단위·claims·기존 DB migration은 변경하지 않는다.

## 이벤트 계약

제공자: devnogi-auth-server. 소비자: devnogi-community-server. Kafka topic: `USER_INFO_UPDATE_EVENT`. 스키마 원본은 이 저장소의 `user-info-update.schema.json`이다. 예제는 합성 데이터다.

필드의 필수 여부와 의미는 schema에 있다. DTO 선언과 Kafka serializer의 실제 JSON을 계약 테스트에서 확인한다. 소비자 저장소에는 동일한 합성 fixture와 역직렬화 테스트를 둔다. Schema/fixture 변경 PR에는 소비자 PR과 검증 결과를 함께 링크한다. 서비스 CI는 다른 저장소 최신 파일을 임의로 다운로드하지 않는다.

필드 삭제·이름/타입 변경·필수 필드 추가는 호환성 변경이다. 제공자 변경 전 소비자 수용 테스트를 실행하고 구버전/신버전 병행, 소비자 선배포, 복구 순서를 승인된 기획서에 기록한다. 선택 필드 추가도 실제 소비자 unknown-field 동작을 확인한 뒤 허용한다. 기존 wire 형식을 바꾸지 않는 1차 baseline이며 전체 의미·운영 통합을 보장하는 완전한 JSON Schema validator는 아니다.

## 현재 확인된 제공자/소비자 불일치

일반 가입 AuthConverter.fromUserSignUpRequestToUser 및 소셜 가입 fromOauthUserDTOToUser는 serverName을 설정하지 않는다. 저장 후 UserInfoUpdateEvent.of(user)가 발행하므로 인증 전 가입 이벤트의 serverName은 null이다. profileImageUrl도 이미지 없는 가입에서 null이다. 원본 schema와 예제는 이 실제 wire 형식을 기록하며, 일반/소셜 변환 경로를 사용하는 회귀 테스트가 두 nullable 필드를 확인한다. required는 필드 존재를 뜻하며 null 허용 여부는 type으로 따로 정의한다.

community의 UserSummaryConsumerDTO.serverName에는 @NotBlank가 있고 UserSummary.serverName DB 매핑은 nullable=false다. 따라서 역직렬화 성공만으로 가입 이벤트의 소비·저장 성공을 보장할 수 없다. 소비자 계약 테스트는 null payload가 현재 검증 제약에 걸리는 사실도 명시적으로 기록한다. 이 기존 제품 불일치는 이번 설정에서 수정하지 않았다. 이벤트 발행 시점, 미인증 사용자 모델 및 DB 호환성을 별도 기획·승인하고 제공자/소비자 통합 테스트로 해결해야 한다.
