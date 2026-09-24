# 실제 로컬 MySQL 검증

대상: localhost:3306 / memory_recall / root, MySQL 9.2.
기존 utf8mb4 / utf8mb4_unicode_ci 설정을 그대로 사용합니다. 다른 DB와 사용자 계정은 변경하지 않습니다.

## 비밀번호를 파일에 저장하지 않고 실행

프로젝트 폴더의 PowerShell에서:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Start-LocalMySqlVerification.ps1
```

이 명령의 ExecutionPolicy는 실행한 PowerShell 프로세스에만 적용되며 시스템 정책을 변경하지 않습니다.
root 비밀번호를 숨김 입력합니다. DB 비밀번호는 생성하거나 변경하지 않습니다.
JWT 키가 환경변수에 없으면 검증 실행용 난수 키를 메모리에서 생성합니다. 키를 출력하거나 파일에 저장하지 않습니다.
사용자 환경변수/레지스트리에 비밀값을 저장하지도 않습니다.

이 도구는 MySQL 접속을 먼저 확인하고, 기존 애플리케이션을 실제 MySQL로 실행합니다.
Flyway가 V1을 적용하고 JPA가 테이블 구조를 검증한 뒤 실제 HTTP 요청으로 회원 기능을 검증합니다.
DB 조회는 memory_recall 및 해당 스키마의 information_schema 메타데이터에 한정됩니다.
새 테스트 계정은 매 실행마다 하나 생성하며 삭제하지 않습니다. 반복 실행하면 테스트 계정이 추가됩니다.
테스트 계정의 비밀번호/JWT/DB 비밀번호/JWT 키/비밀번호 해시는 보고서에 넣지 않습니다.

결과 파일: `build/local-mysql-verification.json` (Git 제외).
성공 시 `status: PASSED`, 각 `checks`는 true입니다. 실패 시 통과한 항목과 실패 종류를 기록합니다.
도구를 아직 실행하지 않았다면 실제 DB 검증이 완료된 것이 아닙니다.
서버는 검증 이후 `http://127.0.0.1:8080`에서 실행을 유지합니다. 종료는 Ctrl+C입니다.
기존 서버가 8080을 사용 중이면 해당 서버를 먼저 정상 종료하세요. 도구는 기존 프로세스를 강제로 종료하지 않습니다.

## 평소 서버 실행 명령

일반 실행은 매번 테스트 계정을 만들지 않습니다. 같은 PowerShell 창에서 아래를 실행합니다.

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/memory_recall?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
$env:DB_USERNAME = 'root'
$securePassword = Read-Host 'MySQL root password' -AsSecureString
$credential = New-Object System.Management.Automation.PSCredential('root', $securePassword)
$env:DB_PASSWORD = $credential.GetNetworkCredential().Password

# 첫 로컬 실행용 키. 재생성하면 이전 JWT는 무효가 됩니다.
$keyBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($keyBytes)
$rng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($keyBytes)
[Array]::Clear($keyBytes, 0, $keyBytes.Length)
$env:DEBUG = 'false'
$env:TRACE = 'false'

.\gradlew.bat --gradle-user-home .gradle-home --no-daemon --offline bootRun
```

필수 환경변수는 DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET입니다.
URL의 시간대 옵션은 기존 UTC 저장 규칙을 유지하기 위한 것이며 대상 DB는 memory_recall입니다.
`.env.example`은 안내용이므로 자동으로 적용되지 않습니다.
JWT 키를 계속 재사용하려면 자신의 안전한 비밀 관리 환경에서 설정하세요. Git 추적 파일에 넣지 마세요.

## Workbench 확인

Schemas에서 memory_recall을 우클릭하여 Refresh All을 실행하고 Tables를 펼칩니다.
아래 쿼리는 모두 조회 전용입니다.

```sql
SHOW TABLES FROM memory_recall;

SELECT version, description, script, success, installed_on
FROM memory_recall.flyway_schema_history;

-- 테스트 계정만 확인. 비밀번호 해시 자체를 출력하지 않고 저장 형식만 확인합니다.
SELECT id, email, name, created_at, updated_at,
       CHAR_LENGTH(password_hash) AS hash_length,
       password_hash LIKE '$2%' AS bcrypt_format
FROM memory_recall.users
WHERE email LIKE 'mysql-check-%@example.com';

SHOW CREATE TABLE memory_recall.users;
SHOW INDEX FROM memory_recall.users;

SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'memory_recall' AND REFERENCED_TABLE_NAME IS NOT NULL;
```

정상 결과는 업무 테이블 6개와 flyway_schema_history, V1의 success=1,
테스트 계정의 hash_length=60 / bcrypt_format=1입니다.
실제 비밀번호 일치 여부는 도구가 BCrypt.matches로 따로 검사합니다.

## 호환성 확인 기준

현재 버전은 Spring Boot 3.5.16 / Connector-J 9.7.0 / Flyway core 및 mysql 11.7.2입니다.
[Connector/J 공식 개발 가이드](https://downloads.mysql.com/docs/connector-j-en.pdf)의 9.7 지원 범위는 MySQL 8.0 이상입니다.
[Flyway 11.7.2 MySQL 구현](https://github.com/flyway/flyway/blob/flyway-11.7.2/flyway-database/flyway-mysql/src/main/java/org/flywaydb/database/mysql/MySQLDatabase.java)은
8.1보다 새로운 서버에 업그레이드 권고 경고를 표시합니다. 경고와 실제 실행 실패는 구분해야 합니다.
따라서 현재 버전 그대로 로컬 실측을 우선하며, 문서만으로 MySQL 9.2가 이 Flyway 버전의 공식 검증 대상이라고 단정하지 않습니다.
마이그레이션과 API 테스트 결과를 확인하기 전에는 실제 호환성 검증 완료라고 판단하지 않습니다.

`build.gradle`의 verifyLocalMySql은 명시적으로 호출할 때만 동작하며 기본 build/test에서는 실행되지 않습니다.
검증 Java 코드는 src/test에 있어 실제 배포 JAR에는 포함되지 않습니다.
기존 Entity, V1 마이그레이션, 회원 API 구현은 이 검증 도구 추가로 변경하지 않았습니다.
