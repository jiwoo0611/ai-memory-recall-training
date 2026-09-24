# 백엔드 로그아웃

`POST /api/auth/logout`에 `Authorization: Bearer <accessToken>` 헤더를 전달합니다.
요청 본문은 필요하지 않습니다. 성공은 **204 No Content**, 응답 본문은 없습니다.
현재 사용자 존재 여부 확인 후 해당 토큰만 차단합니다. 다른 기기의 토큰이나 다른 사용자의 토큰은 유지됩니다.
토큰 없이 요청하거나 잘못된/만료된/이미 로그아웃한 토큰을 사용하면 **401 UNAUTHORIZED**입니다.

로그아웃 후 같은 토큰으로 모든 보호 API에 접근하면 기존 인증 오류 메시지가 반환됩니다.
JWT 검증기에 차단 여부 검사를 추가했으며, 기존 서명/issuer/audience/만료 검사는 유지합니다.
JWT의 jti와 만료시간을 revoked_tokens 테이블에 저장합니다. 토큰 원문은 저장하지 않습니다. 인증 요청마다 DB에서 차단 여부를 확인하므로 동일 DB를 사용하는 서버 재시작 후에도 차단됩니다. jti가 없는 기존 토큰은 legacy: 접두사와 서명 대상의 SHA-256 해시로 식별합니다.

## 적용 범위와 제한

- V2__create_revoked_tokens.sql로 전용 테이블만 추가합니다. 기존 6개 Entity/Repository 관계와 V1은 변경하지 않습니다. 기존 인증 응답 형식도 유지합니다.
- 로그아웃 응답 이전에 이미 인증을 통과해 실행 중인 요청을 취소하지는 않습니다.
- 기본 30분 만료와 만료 후 재로그인 정책은 유지합니다. 자동 갱신과 프론트엔드는 구현하지 않습니다.
- 현재 실행 중인 서버와 실제 MySQL은 변경하지 않습니다. 새 코드 반영은 별도 배포 시 필요합니다.

## PowerShell 요청 예시

새 코드가 서버에 반영된 후 기존 로그인 응답의 토큰 변수로 실행합니다.

```powershell
$headers = @{ Authorization = "Bearer $accessToken" }
Invoke-WebRequest -UseBasicParsing -Method Post `
  -Uri 'http://127.0.0.1:8080/api/auth/logout' -Headers $headers |
  Select-Object StatusCode

# 아래 요청은 401로 실패해야 합니다.
try {
  Invoke-WebRequest -UseBasicParsing -Method Get `
    -Uri 'http://127.0.0.1:8080/api/users/me' -Headers $headers
} catch {
  if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode }
  else { throw }
}
```

## 검증

AuthIntegrationTest에 로그인 → 본인 조회 → 로그아웃 → 동일 토큰 401,
전체 마이페이지 차단, 다른 토큰 유지/재로그인, 미인증·만료 토큰 거부,
jti 없는 기존 토큰 로그아웃 테스트를 추가했습니다.
격리된 H2 검증 보고서: `build/persistent-logout-verification/reports/tests/test/index.html`.

## 테이블과 정리

- revoked_tokens.jti: VARCHAR(255), PRIMARY KEY. 신규 토큰은 UUID jti를 그대로 저장.
- expires_at: TIMESTAMP(6), NOT NULL. 만료시간 인덱스 idx_revoked_tokens_expires.
- 기존 업무 테이블과 FK 관계를 만들지 않습니다.
- RevokedTokenRepository는 기존 Spring JDBC로 두 컬럼의 저장/조회/삭제만 수행합니다. 새 라이브러리는 없습니다.
- 로그아웃 트랜잭션에서 만료된 기록 삭제와 현재 토큰 저장을 함께 처리합니다. DB 저장 실패 시 성공 응답을 반환하지 않습니다.
- removeExpired()를 별도로 호출할 수도 있습니다. 주기 스케줄러는 추가하지 않았으며 로그아웃 요청이 없으면 만료된 행이 남을 수 있습니다.
- 만료된 행을 지워도 기존 JWT 만료 검증이 계속 차단합니다.
- 이전 버전 서버의 메모리에만 있던 차단 기록을 소급 복구하지는 않습니다. 이 버전에서 성공한 로그아웃부터 영속화됩니다.

PersistentLogoutIntegrationTest는 테스트 전체를 감싸는 트랜잭션 없이 로그아웃을 커밋하고,
새 Repository/Store 객체로 DB 기록을 다시 조회합니다. 실제 운영 서버를 재시작하는 테스트는 하지 않습니다.
중복 저장과 만료된 기록만 삭제하는 동작도 검증합니다.