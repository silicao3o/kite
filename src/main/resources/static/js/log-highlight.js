// ANSI escape code 제거 — 터미널 색상 코드를 브라우저에서 표시하면 깨져 보이므로 제거
function stripAnsi(text) {
    return text.replace(/\x1b\[[0-9;]*m/g, '');
}

// 로그 텍스트에 색상 강조 적용 — ANSI 제거 후 HTML span으로 감싸기
function highlightLog(text) {
    // ANSI 코드 제거
    text = stripAnsi(text);
    // HTML 특수문자 이스케이프 (<script> 등 실행 방지)
    text = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    // 타임스탬프 강조 (2026-04-01T21:23:00 또는 2026-04-01 21:23:00 형식)
    text = text.replace(/(\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}[\.\d+]*[\+\dZ:]*)/g,
        '<span class="log-time">$1</span>');
    // 로그 레벨 강조
    text = text.replace(/\bERROR\b/g, '<span class="log-level-error">ERROR</span>');
    text = text.replace(/\bWARN\b/g, '<span class="log-level-warn">WARN</span>');
    text = text.replace(/\bINFO\b/g, '<span class="log-level-info">INFO</span>');
    text = text.replace(/\bDEBUG\b/g, '<span class="log-level-debug">DEBUG</span>');
    // error: 키워드 강조
    text = text.replace(/\berror:/gi, '<span class="log-level-error">error:</span>');
    // 대괄호 강조 [스레드명], [앱이름] 등
    text = text.replace(/\[/g, '<span class="log-bracket">[</span>');
    text = text.replace(/\]/g, '<span class="log-bracket">]</span>');
    // 부정적 키워드 강조 — 빨간색
    text = text.replace(/\b(Exception|Failed|Failure|Timeout|refused|rejected|denied|fatal|FATAL|critical)\b/gi, '<span class="log-level-error">$1</span>');
    text = text.replace(/(실패|에러|오류|예외)/g, '<span class="log-level-error">$1</span>');
    // HTTP 메서드 강조
    text = text.replace(/\b(GET|POST|PUT|DELETE|PATCH)\b/g, '<span class="log-bracket">$1</span>');
    // ===== 큐비 키워드 — 여기에 추가 =====
    // text = text.replace(/키워드/g, '<span class="log-level-error">키워드</span>');
    return text;
}
