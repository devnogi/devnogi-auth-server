# 🎮 마비노기 경매장 거래 내역 조회 및 통계 서비스 

마비노기 경매장 거래 내역을 수집하고 분석하여 사용자에게 시세 정보와 커뮤니티 기능을 제공하는 웹 애플리케이션입니다.

### 📌 주요 기능

- **데이터 수집**: 1시간 간격으로 게임 경매장 거래 내역을 Open API를 통해 수집
- **데이터 분석**: 수집된 데이터를 기반으로 시세 변동, 거래량 등의 통계 정보 제공
- **커뮤니티**: 사용자 간의 소통을 위한 게시판 및 댓글 기능
- **아이템 시세 견적**: 사용자가 아이템의 시세를 조회하고 견적을 받을 수 있는 기능
- **관리자 페이지**: 사용자 관리, 게시물 관리, 데이터 모니터링 기능
- **기술 블로그**: 개발 과정, 리팩토링 내용, 기술적인 고민 등을 기록

<br>

### 🛠 기술 스택

- **Backend**: Spring Boot, JPA (Hibernate), Spring Security, Spring Batch, JWT
- **Test**: JUnit5, Mockito, K6
- **Database**: MySQL, Redis
- **DevOps**: Docker, Docker Compose, Flyway, GitHub Actions
- **Frontend**: Thymeleaf (or React/Vue)
- **Deployment**: AWS EC2, RDS, S3
- **Document**: Spring REST Docs, Postman
- **Cooperation**: Notion, Slack

<br>

### 📈 프로젝트 구조

- `api/`: Open API 연동 및 데이터 수집
- `batch/`: 배치 작업을 통한 데이터 수집 및 처리
- `community/`: 게시판 및 댓글 기능
- `estimate/`: 아이템 시세 견적 기능
- `admin/`: 관리자 페이지 기능
- `techblog/`: 기술 블로그 기능

<br>
