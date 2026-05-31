# DB 수동 마이그레이션 (Flyway 미도입)

운영(prod) 프로파일은 `spring.jpa.hibernate.ddl-auto=validate`라 **엔티티가 요구하는
테이블/컬럼이 DB에 미리 있어야** 애플리케이션이 기동됩니다(없으면 검증 실패로 부팅 실패).
아직 Flyway/Liquibase를 도입하지 않았으므로(M-NEW-7 보류), 스키마 변경은 이 디렉터리의
SQL 스크립트를 **배포 전 수동으로** 적용합니다. dev 프로파일은 `ddl-auto=update`라 자동 반영됩니다.

## 적용 방법

해당 변경이 포함된 앱 버전을 배포하기 **전에**, 파일명 날짜 순서대로 운영 DB에 1회 적용:

```bash
mysql -h "$DB_HOST" -u "$DB_USERNAME" -p "$DB_NAME" < 2026-06-01_phase_c_d_schema_delta.sql
```

- 스크립트는 **멱등이 아닙니다**(MySQL은 `ADD COLUMN IF NOT EXISTS` 미지원). 이미 적용했다면 재실행하지 마세요.
- 적용 후 앱을 기동해 `validate`가 통과하는지 확인하세요.

## 스크립트 목록

| 파일 | 내용 | 출처 |
|------|------|------|
| `migration/2026-06-01_phase_c_d_schema_delta.sql` | `posts.reviewed` 컬럼 추가, `post_views` 테이블 생성 | Phase C(§1-1), Phase D(M-NEW-5) |

## 향후 — Flyway 전환(M-NEW-7)

실 MySQL에서 현재 스키마를 덤프해 V1 베이스라인을 만들고 검증할 수 있는 환경이 갖춰지면
Flyway로 이관 권장:

1. `flyway-core` + `flyway-mysql` 의존성 추가.
2. 운영 스키마 덤프 → `src/main/resources/db/migration/V1__baseline.sql`(엔티티와 정확히 일치해야 함).
3. 이후 변경은 `V2__...sql`로. 기존 운영 DB에는 `spring.flyway.baseline-on-migrate=true`.
4. 테스트는 H2(MySQL 전용 SQL과 불일치)이므로 `spring.flyway.enabled=false`로 분리.
