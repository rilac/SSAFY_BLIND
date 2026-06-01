# DB 마이그레이션 — ✅ Flyway로 전환됨 (M-NEW-7)

> **이 디렉터리(`backend/db`)의 수동 DDL 방식은 폐기되었습니다.**
> 스키마는 이제 **Flyway**가 관리합니다 → 마이그레이션 위치: **`backend/src/main/resources/db/migration/`**
> (기존 수동 델타 스크립트 `2026-06-01_phase_c_d_schema_delta.sql`·`2026-06-02_refresh_tokens.sql`의 내용은
> `V1__baseline.sql`에 모두 흡수되어 삭제되었습니다.)

## 동작 방식

- **dev / prod 모두** `spring.flyway.enabled=true` + `spring.flyway.baseline-on-migrate=true`.
- 기동 시 Flyway가 `classpath:db/migration`의 `V*__*.sql`을 순서대로 적용한 뒤, Hibernate가 `ddl-auto: validate`로 스키마를 재검증한다(이중 안전망). **더 이상 배포 전 수동 DDL이 필요 없다.**
- 테스트(H2)는 베이스라인이 MySQL 전용이라 `spring.flyway.enabled=false` + `ddl-auto: create-drop` 유지.

| 상황 | Flyway 동작 |
|---|---|
| 빈 DB(신규 prod) | `V1__baseline.sql`부터 실행해 전체 스키마 생성 |
| 기존 DB(로컬 dev 등, V1 수준) | `baseline-on-migrate`가 V1을 "적용됨"으로만 기록(미실행) → 기존 스키마 보존. 이후 `V2__...`만 적용 |

## 스키마 변경 절차 (앞으로)

1. 엔티티 수정.
2. `backend/src/main/resources/db/migration/V2__설명.sql`(다음 번호) 추가 — 변경분 DDL만.
3. 끝. dev/prod 모두 기동 시 Flyway가 자동 적용하고 `validate`가 정합을 확인한다.

> 베이스라인 DDL은 손으로 쓰지 않고 **Hibernate가 생성**했다(=`validate`와 정확히 일치 보장).
> 스키마 전체를 다시 뽑아보려면 `GenerateBaselineTest`(@Disabled)를 수동 실행 → `build/generated-baseline.sql` 참고.

## ⚠️ 기존 prod가 V1보다 "뒤처진" 경우 (1회 전환)

이미 운영 중인 DB가 최신 스키마(예: `refresh_tokens`, `post_views`, `posts.reviewed`)를 **아직 갖고 있지 않다면**, `baseline-on-migrate`가 V1을 미실행으로 건너뛰므로 누락 테이블이 생기지 않는다. 전환 시 **둘 중 하나**:

- (권장) 빈 DB로 새로 시작 → Flyway가 V1 전체를 생성.
- 기존 데이터 유지가 필요하면, **baseline 직전에** 누락분만 수동 적용해 V1 수준으로 맞춘 뒤 배포(이때만 1회 수동). 이후로는 전부 Flyway가 담당.
