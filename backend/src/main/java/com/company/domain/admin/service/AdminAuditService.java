package com.company.domain.admin.service;

import com.company.domain.admin.entity.AdminAuditAction;
import com.company.domain.admin.entity.AdminAuditLog;
import com.company.domain.admin.entity.AdminAuditTargetType;
import com.company.domain.admin.repository.AdminAuditLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 관리자 행위 감사 기록.
 *
 * <p>기록 방식은 인터셉터가 아니라 서비스 내 명시적 호출이다. 관리자 권한 글 수정·삭제가
 * {@code /api/posts/{id}}로 나가 {@code /api/admin/**} 인터셉터에 잡히지 않고, 잡더라도
 * "작성자 본인 삭제 vs 관리자 권한 삭제"를 인터셉터가 구분할 수 없기 때문이다.
 *
 * <p>{@code REQUIRES_NEW}인 이유: (1) 검색·조회는 readOnly 트랜잭션이라 같은 트랜잭션에서 INSERT하면
 * 커넥션 read-only 힌트에 걸릴 수 있고, (2) 업무 트랜잭션이 롤백돼도 "시도했다"는 사실은 남아야 한다.
 * 트레이드오프로 "기록은 있는데 실제 변경은 안 된" 경우가 이론상 생기지만, 감사에서는 과다 기록이 누락보다 낫다.
 *
 * <p>감사 기록 실패가 업무를 막아서는 안 되므로 예외를 삼키고 ERROR로만 남긴다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminAuditService {

    // detail은 varchar(500) — 검색어 등 가변 입력이 들어오므로 저장 전에 자른다.
    private static final int DETAIL_MAX = 500;

    private final AdminAuditLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long actorId, AdminAuditAction action,
                       AdminAuditTargetType targetType, Long targetId, String detail) {
        try {
            repository.save(AdminAuditLog.builder()
                    .actorId(actorId)
                    .action(action)
                    .targetType(targetType)
                    .targetId(targetId)
                    .detail(truncate(detail))
                    .ip(currentIp())
                    .build());
        } catch (Exception e) {
            // 감사 기록 실패로 차단/숨김 같은 모더레이션 자체가 막히면 더 나쁘다.
            log.error("[관리자 감사] 기록 실패 actor={} action={} target={}/{}", actorId, action, targetType, targetId, e);
        }
    }

    /** 대상이 없는 행위(검색, 인증)용 단축 오버로드. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long actorId, AdminAuditAction action, String detail) {
        record(actorId, action, AdminAuditTargetType.NONE, null, detail);
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= DETAIL_MAX ? s : s.substring(0, DETAIL_MAX);
    }

    /**
     * 요청 IP — 서비스 시그니처마다 HttpServletRequest를 끌고 다니지 않도록 여기서 해석한다.
     * 스케줄러 등 요청 컨텍스트가 없는 경로에서 호출되면 null.
     *
     * <p>getRemoteAddr()를 쓴다. X-Forwarded-For를 직접 파싱하면 클라이언트가 위조한 값이 감사 로그에
     * 그대로 남아 사고 조사 시 엉뚱한 IP를 가리키게 된다(server.forward-headers-strategy가 신뢰 프록시
     * 기준으로 이미 치환해준다).
     */
    private String currentIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
