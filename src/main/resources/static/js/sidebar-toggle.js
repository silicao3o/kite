(function() {
    var layout = document.querySelector('.layout');
    if (!layout) return;

    var hamburger = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>';
    var kite = '<svg width="20" height="20" viewBox="0 0 32 32" fill="none"><path d="M16 3 L28 16 L16 24 L4 16 Z" fill="white" fill-opacity="0.95"/><path d="M16 24 L13 30 M16 24 L19 30" stroke="white" stroke-width="1.8" stroke-linecap="round" stroke-opacity="0.6"/><circle cx="16" cy="16" r="2.5" fill="#326ce5"/><path d="M4 16 L28 16" stroke="#326ce5" stroke-width="1" stroke-opacity="0.4"/><path d="M16 3 L16 24" stroke="#326ce5" stroke-width="1" stroke-opacity="0.4"/></svg>';

    var btn = document.createElement('button');
    btn.className = 'sidebar-toggle';
    btn.title = '사이드바 토글';
    btn.innerHTML = hamburger;
    btn.onclick = function() {
        layout.classList.toggle('sidebar-collapsed');
        btn.innerHTML = layout.classList.contains('sidebar-collapsed') ? kite : hamburger;
        localStorage.setItem('sidebarCollapsed', layout.classList.contains('sidebar-collapsed'));
    };
    layout.prepend(btn);

    if (localStorage.getItem('sidebarCollapsed') === 'true') {
        layout.classList.add('sidebar-collapsed');
        btn.innerHTML = kite;
    }
})();
