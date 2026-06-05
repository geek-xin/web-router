(function () {
    const elements = {
        modal: document.getElementById('modal'),
        viewModal: document.getElementById('viewModal'),
        routeLogModal: document.getElementById('routeLogModal'),
        confirmModal: document.getElementById('confirmModal'),
        routeForm: document.getElementById('routeForm'),
        jsonEditor: document.getElementById('jsonEditor'),
        btnAdd: document.getElementById('btnAdd'),
        btnBatchDelete: document.getElementById('btnBatchDelete'),
        routeSearch: document.getElementById('routeSearch'),
        routeCards: document.getElementById('routeCards'),
        modalTitle: document.getElementById('modalTitle'),
        isEdit: document.getElementById('isEdit'),
        oldRouteId: document.getElementById('oldRouteId'),
        name: document.getElementById('name'),
        pathPrefix: document.getElementById('pathPrefix'),
        pathPrefixInput: document.getElementById('pathPrefixInput'),
        pathPrefixList: document.getElementById('pathPrefixList'),
        btnAddPathPrefix: document.getElementById('btnAddPathPrefix'),
        targetUrl: document.getElementById('targetUrl'),
        localIp: document.getElementById('localIp'),
        localPort: document.getElementById('localPort'),
        enabled: document.getElementById('enabled'),
        viewTitle: document.getElementById('viewTitle'),
        btnToggleEdit: document.getElementById('btnToggleEdit'),
        btnCancel: document.getElementById('btnCancel'),
        btnCloseView: document.getElementById('btnCloseView'),
        btnCloseRouteLog: document.getElementById('btnCloseRouteLog'),
        btnCancelDelete: document.getElementById('btnCancelDelete'),
        btnConfirmDelete: document.getElementById('btnConfirmDelete'),
        deleteName: document.getElementById('deleteName'),
        deleteMessage: document.getElementById('deleteMessage'),
        routeLogTitle: document.getElementById('routeLogTitle'),
        routeLogName: document.getElementById('routeLogName'),
        routeLogTotalRequests: document.getElementById('routeLogTotalRequests'),
        routeLogTotalDuration: document.getElementById('routeLogTotalDuration'),
        routeLogDirectoryStats: document.getElementById('routeLogDirectoryStats'),
        routeLogRefreshRate: document.getElementById('routeLogRefreshRate'),
        routeLogLimit: document.getElementById('routeLogLimit'),
        routeLogPathSearch: document.getElementById('routeLogPathSearch'),
        routeLogRows: document.getElementById('routeLogRows')
    };

    let pendingDeleteIds = [];
    let viewFileName = '';
    let activeRouteLogName = '';
    let activeRouteLogEnabled = false;
    let routeLogSource = null;
    let routeLogPollTimer = null;
    let routeLogRefreshModeIndex = 0;
    const ROUTE_LOG_MAX_RECENT = 100;
    const ROUTE_LOG_DEFAULT_DISPLAY = 10;
    const ROUTE_PATH_STATS_LIMIT = 50;
    const routeLogRefreshModes = [
        { label: '实时刷新', intervalMs: 0 },
        { label: '1秒刷新', intervalMs: 1000 },
        { label: '3秒刷新', intervalMs: 3000 },
        { label: '5秒刷新', intervalMs: 5000 },
        { label: '暂停刷新', intervalMs: -1 }
    ];
    let routeLogState = {
        totalRequests: 0,
        totalDurationMs: 0,
        requestsByIp: {},
        pathStats: {},
        pathDurationStats: {},
        recentLogs: []
    };
    let routeFilterTimer = null;

    function showToast(msg, type = 'success') {
        const toast = document.createElement('div');
        toast.className = 'toast toast-' + type;
        toast.textContent = msg;
        document.body.appendChild(toast);
        setTimeout(() => toast.remove(), 2500);
    }

    function requireElements() {
        const missing = Object.entries(elements)
            .filter(([, element]) => !element)
            .map(([id]) => id);
        if (missing.length > 0) {
            const message = '页面脚本初始化失败，缺少元素: ' + missing.join(', ');
            console.error(message);
            showToast(message, 'error');
            return false;
        }
        return true;
    }

    async function fetchJson(url, options = {}) {
        const response = await fetch(url, options);
        let result;
        try {
            result = await response.json();
        } catch (error) {
            throw new Error('响应不是有效 JSON: HTTP ' + response.status);
        }
        if (!response.ok || !result.success) {
            throw new Error(result.message || ('请求失败: HTTP ' + response.status));
        }
        return result.data;
    }

    function isValidTargetUrl(targetUrl) {
        return /^(https?:\/\/)?[-a-zA-Z0-9.]+:\d{1,5}$/.test(targetUrl);
    }

    function comparableTargetUrl(targetUrl) {
        return (targetUrl || '').trim().replace(/^https?:\/\//, '');
    }

    function displayTargetUrl(targetUrl) {
        return comparableTargetUrl(targetUrl);
    }

    function comparableRouteName(name) {
        return (name || '').trim();
    }

    function hasRouteNameConflict(name, excludeRouteId) {
        const value = comparableRouteName(name);
        return Array.from(elements.routeCards.querySelectorAll('.route-card'))
            .some((card) => card.dataset.id !== excludeRouteId && comparableRouteName(card.dataset.name) === value);
    }

    function hasTargetUrlConflict(targetUrl, excludeRouteId) {
        const value = comparableTargetUrl(targetUrl);
        return Array.from(elements.routeCards.querySelectorAll('.route-card'))
            .some((card) => card.dataset.id !== excludeRouteId && comparableTargetUrl(card.dataset.target) === value);
    }

    function normalizedLocalIp(localIp) {
        return (localIp || '').trim() || '127.0.0.1';
    }

    function localBinding(localIp, localPort) {
        const port = (localPort || '').toString().trim();
        return port ? normalizedLocalIp(localIp) + ':' + port : '';
    }

    function isValidLocalIp(localIp) {
        const value = normalizedLocalIp(localIp);
        if (value === 'localhost') {
            return true;
        }
        const parts = value.split('.');
        if (parts.length !== 4) {
            return false;
        }
        return parts.every((part) => {
            if (!/^\d{1,3}$/.test(part)) {
                return false;
            }
            const value = Number(part);
            return value >= 0 && value <= 255;
        });
    }

    function isValidLocalPort(localPort) {
        if (!localPort) {
            return true;
        }
        const value = Number(localPort);
        return Number.isInteger(value) && value >= 1 && value <= 65535;
    }

    function hasLocalBindingConflict(localIp, localPort, excludeRouteId) {
        const value = localBinding(localIp, localPort);
        if (!value) {
            return false;
        }
        return Array.from(elements.routeCards.querySelectorAll('.route-card'))
            .some((card) => card.dataset.id !== excludeRouteId && card.dataset.localBinding === value);
    }

    function routeCard(routeId) {
        return elements.routeCards.querySelector('[data-id="' + CSS.escape(routeId) + '"]');
    }

    function routeDisplayName(routeId) {
        const card = routeCard(routeId);
        return card ? card.dataset.name : routeId;
    }

    function selectedRouteIds() {
        return Array.from(elements.routeCards.querySelectorAll('.route-select-checkbox:checked'))
            .map((checkbox) => checkbox.dataset.id)
            .filter(Boolean);
    }

    function updateBatchDeleteButton(revealSelection = true) {
        const count = selectedRouteIds().length;
        elements.btnBatchDelete.disabled = false;
        elements.btnBatchDelete.textContent = count === 0 ? '选择删除' : '删除选中(' + count + ')';
        if (count > 0 && revealSelection) {
            showSelectionControls();
        }
    }

    function setCardSelected(routeId, selected) {
        const card = routeCard(routeId);
        if (card) {
            card.classList.toggle('route-card-selected', selected);
        }
    }

    function openDeleteConfirm(routeIds) {
        pendingDeleteIds = routeIds;
        if (routeIds.length === 1) {
            elements.deleteMessage.innerHTML = '<span class="delete-confirm-title">确定要删除这个路由吗？</span><span class="delete-route-name" id="deleteName"></span><span class="delete-warning">删除后配置文件将被移除，此操作不可撤销。</span>';
            elements.deleteName = document.getElementById('deleteName');
            elements.deleteName.textContent = routeDisplayName(routeIds[0]);
        } else {
            elements.deleteMessage.innerHTML = '<span class="delete-confirm-title">确定要删除选中的路由吗？</span><span class="delete-count"><strong>' + routeIds.length + '</strong><small>个路由</small></span><span class="delete-warning">删除后配置文件将被移除，此操作不可撤销。</span>';
        }
        elements.confirmModal.classList.add('active');
    }

    function routePathPrefixes(routeId) {
        const card = routeCard(routeId);
        if (!card) {
            return [];
        }
        return Array.from(card.querySelectorAll('.route-card-prefixes code'))
            .map((element) => element.textContent.trim())
            .filter(Boolean);
    }

    function routePathPrefix(routeId) {
        return routePathPrefixes(routeId)[0] || '';
    }

    function routeEnabled(routeId) {
        const card = routeCard(routeId);
        if (!card) {
            return false;
        }
        const button = card.querySelector('.btn-toggle-status');
        return button ? button.dataset.enabled === 'true' : false;
    }

    function existingRouteValues(datasetKey) {
        return new Set(Array.from(elements.routeCards.querySelectorAll('.route-card'))
            .map((card) => card.dataset[datasetKey] || '')
            .filter(Boolean));
    }

    function existingPathPrefixes() {
        const values = new Set();
        elements.routeCards.querySelectorAll('.route-card .route-card-prefixes code').forEach((element) => {
            const prefix = element.textContent.trim();
            if (prefix) {
                values.add(prefix);
            }
        });
        return values;
    }

    function nextCopyValue(baseValue, existingValues) {
        let candidate = baseValue + '-copy';
        let index = 2;
        while (existingValues.has(candidate)) {
            candidate = baseValue + '-copy-' + index;
            index += 1;
        }
        return candidate;
    }

    function parsePathPrefixes(value) {
        return (value || '')
            .split(/\r?\n|,/)
            .map((prefix) => prefix.trim())
            .filter(Boolean);
    }

    function formatPathPrefixes(pathPrefixes) {
        return (pathPrefixes || []).join('\n');
    }

    function normalizePathPrefixValue(value) {
        const trimmed = (value || '').trim();
        if (!trimmed) {
            return '';
        }
        return trimmed.startsWith('/') ? trimmed : '/' + trimmed;
    }

    function uniquePathPrefixes(pathPrefixes) {
        const seen = new Set();
        return (pathPrefixes || [])
            .map(normalizePathPrefixValue)
            .filter(Boolean)
            .filter((prefix) => {
                if (seen.has(prefix)) {
                    return false;
                }
                seen.add(prefix);
                return true;
            });
    }

    function pathPrefixValues() {
        return uniquePathPrefixes(parsePathPrefixes(elements.pathPrefix.value));
    }

    function setPathPrefixes(pathPrefixes) {
        const normalized = uniquePathPrefixes(pathPrefixes);
        elements.pathPrefix.value = formatPathPrefixes(normalized);
        renderPathPrefixList(normalized);
    }

    function renderPathPrefixList(pathPrefixes = pathPrefixValues()) {
        elements.pathPrefixList.innerHTML = '';
        if (pathPrefixes.length === 0) {
            const empty = document.createElement('span');
            empty.className = 'path-prefix-empty';
            empty.textContent = '还没有路径，输入后点击添加';
            elements.pathPrefixList.appendChild(empty);
            return;
        }
        pathPrefixes.forEach((prefix) => {
            const chip = document.createElement('span');
            chip.className = 'path-prefix-chip';
            const label = document.createElement('span');
            label.className = 'path-prefix-chip-text';
            label.textContent = prefix;
            chip.appendChild(label);
            const button = document.createElement('button');
            button.type = 'button';
            button.textContent = '×';
            button.setAttribute('aria-label', '删除路径 ' + prefix);
            button.addEventListener('click', () => {
                setPathPrefixes(pathPrefixValues().filter((item) => item !== prefix));
            });
            chip.appendChild(button);
            elements.pathPrefixList.appendChild(chip);
        });
    }

    function addPathPrefixFromInput() {
        const prefix = normalizePathPrefixValue(elements.pathPrefixInput.value);
        if (!prefix) {
            showToast('请输入路径前缀', 'error');
            return;
        }
        if (!/^\/[-a-zA-Z0-9_/]*$/.test(prefix)) {
            showToast('路径前缀格式不正确，如 /api/users', 'error');
            return;
        }
        const current = pathPrefixValues();
        if (current.includes(prefix)) {
            showToast('路径前缀已存在', 'error');
            return;
        }
        setPathPrefixes([...current, prefix]);
        elements.pathPrefixInput.value = '';
        elements.pathPrefixInput.focus();
    }

    function firstPathPrefix(pathPrefixes) {
        return (pathPrefixes || [])[0] || '';
    }

    function baseRouteId(routeId) {
        return routeId.replace(/__\d+$/, '');
    }

    function matchedPathPrefix(path, pathPrefixes) {
        return (pathPrefixes || [])
            .filter((prefix) => path === prefix || path.startsWith(prefix + '/'))
            .sort((a, b) => b.length - a.length)[0] || firstPathPrefix(pathPrefixes);
    }

    function matchedPrefixLabel(path, routeId) {
        return matchedPathPrefix(path || '', routePathPrefixes(routeId)) || '/';
    }

    function fileNameFromPath(path) {
        return (path || '').split(/[\\/]/).filter(Boolean).pop() || path || '';
    }

    function showSelectionControls() {
        elements.routeCards.classList.add('selection-mode');
    }

    function hideSelectionControlsIfIdle() {
        if (selectedRouteIds().length === 0) {
            elements.routeCards.classList.remove('selection-mode');
        }
    }

    function filterRoutes() {
        const keyword = elements.routeSearch.value.trim().toLowerCase();
        const cards = Array.from(elements.routeCards.querySelectorAll('.route-card'));
        const firstRects = routeCardRects(cards);
        const visibility = new Map();

        if (routeFilterTimer) {
            clearTimeout(routeFilterTimer);
            routeFilterTimer = null;
        }

        cards.forEach((card) => {
            const prefixes = Array.from(card.querySelectorAll('.route-card-prefixes code'))
                .map((element) => element.textContent)
                .join(' ');
            const fileElement = card.querySelector('.file-path');
            const filePath = fileElement ? ((fileElement.title || '') + ' ' + fileElement.textContent) : '';
            const searchable = [card.dataset.name || '', card.dataset.target || '', card.dataset.localBinding || '', prefixes, filePath]
                .join(' ')
                .toLowerCase();
            const visible = !keyword || searchable.includes(keyword);
            visibility.set(card, visible);
            card.classList.remove('route-card-filtering-in');
            if (visible) {
                const wasHidden = card.hidden;
                card.hidden = false;
                card.classList.remove('route-card-filtering-out');
                if (wasHidden) {
                    card.classList.add('route-card-filtering-in');
                }
                return;
            }
            if (!card.hidden) {
                card.classList.add('route-card-filtering-out');
            }
        });

        routeFilterTimer = setTimeout(() => {
            cards.forEach((card) => {
                if (!visibility.get(card)) {
                    card.hidden = true;
                    card.classList.remove('route-card-filtering-out');
                }
            });
            animateRouteCardMoves(cards, firstRects);
            setTimeout(() => {
                cards.forEach((card) => {
                    card.classList.remove('route-card-filtering-in');
                });
            }, 260);
            routeFilterTimer = null;
        }, 260);
    }

    function routeCardRects(cards) {
        return new Map(cards
            .filter((card) => !card.hidden)
            .map((card) => [card, card.getBoundingClientRect()]));
    }

    function animateRouteCardMoves(cards, firstRects) {
        cards.filter((card) => !card.hidden && firstRects.has(card)).forEach((card) => {
            const first = firstRects.get(card);
            const last = card.getBoundingClientRect();
            const dx = first.left - last.left;
            const dy = first.top - last.top;
            if (Math.abs(dx) < 1 && Math.abs(dy) < 1) {
                return;
            }
            card.animate([
                { transform: 'translate(' + dx + 'px, ' + dy + 'px)' },
                { transform: 'translate(0, 0)' }
            ], {
                duration: 300,
                easing: 'cubic-bezier(0.22, 1, 0.36, 1)'
            });
        });
    }

    function normalizeConfigForEditor(config) {
        if (!config || typeof config !== 'object' || Array.isArray(config)) {
            return config;
        }
        const normalized = { ...config };
        const pathPrefixes = (!Array.isArray(normalized.pathPrefixes) || normalized.pathPrefixes.length === 0) && normalized.pathPrefix
            ? parsePathPrefixes(normalized.pathPrefix)
            : normalized.pathPrefixes;
        delete normalized.pathPrefix;
        delete normalized.pathPrefixes;
        if (Array.isArray(pathPrefixes)) {
            return { ...normalized, pathPrefixes };
        }
        return normalized;
    }

    function compactPathPrefixesInJson(json, config) {
        if (!config || !Array.isArray(config.pathPrefixes)) {
            return json;
        }
        const compactArray = config.pathPrefixes
            .reduce((lines, prefix, index) => {
                if (index % 6 === 0) {
                    lines.push([]);
                }
                lines[lines.length - 1].push(JSON.stringify(prefix));
                return lines;
            }, [])
            .map((line) => '    ' + line.join(', '))
            .join(',\n');
        const replacement = compactArray
            ? '  "pathPrefixes": [\n' + compactArray + '\n  ]'
            : '  "pathPrefixes": []';
        return json.replace(/  "pathPrefixes": \[\n(?:    .*\n)*?  \]/, replacement);
    }

    function formatConfigJson(content, hideLegacyPathPrefix = false) {
        try {
            const parsed = JSON.parse(content);
            const formatted = hideLegacyPathPrefix ? normalizeConfigForEditor(parsed) : parsed;
            return compactPathPrefixesInJson(JSON.stringify(formatted, null, 2), formatted);
        } catch (error) {
            return content;
        }
    }

    function prettifyJsonEditor() {
        const formatted = formatConfigJson(elements.jsonEditor.value, true);
        if (formatted !== elements.jsonEditor.value) {
            elements.jsonEditor.value = formatted;
        }
        return formatted;
    }

    function setRouteLogStatus(text, type) {
        // 状态不再在日志弹窗展示，保留函数避免刷新流程分支重复判断。
    }

    function formatLogTime(value) {
        if (!value) {
            return '-';
        }
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return '-';
        }
        const pad = (num) => String(num).padStart(2, '0');
        return date.getFullYear()
            + '-' + pad(date.getMonth() + 1)
            + '-' + pad(date.getDate())
            + ' ' + pad(date.getHours())
            + ':' + pad(date.getMinutes())
            + ':' + pad(date.getSeconds());
    }

    function cell(text, className) {
        const td = document.createElement('td');
        td.textContent = text;
        if (className) {
            td.className = className;
        }
        return td;
    }

    function renderRouteLogStats() {
        elements.routeLogTotalRequests.textContent = routeLogState.totalRequests.toString();
        elements.routeLogTotalDuration.textContent = formatDuration(routeLogState.totalDurationMs);
        renderPathStats();
    }

    function formatDuration(ms) {
        const value = Math.max(0, Number(ms) || 0);
        if (value >= 60_000) {
            return (value / 60_000).toFixed(1).replace(/\.0$/, '') + 'min';
        }
        if (value >= 1_000) {
            return (value / 1_000).toFixed(1).replace(/\.0$/, '') + 's';
        }
        return value + 'ms';
    }

    function renderPathStats() {
        const entries = Object.entries(routeLogState.pathStats || {})
            .sort((a, b) => b[1] - a[1]);
        elements.routeLogDirectoryStats.innerHTML = '';

        if (entries.length === 0) {
            const tr = document.createElement('tr');
            tr.dataset.empty = 'true';
            tr.appendChild(cell('暂无请求', 'empty-small'));
            tr.firstChild.colSpan = 3;
            elements.routeLogDirectoryStats.appendChild(tr);
            return;
        }

        entries.slice(0, ROUTE_PATH_STATS_LIMIT).forEach(([prefix, count]) => {
            const tr = document.createElement('tr');
            tr.appendChild(cell(prefix, 'path-cell'));
            tr.appendChild(cell(count.toString()));
            tr.appendChild(cell(formatDuration((routeLogState.pathDurationStats || {})[prefix] || 0)));
            elements.routeLogDirectoryStats.appendChild(tr);
        });
    }

    function normalizedLogPath(path) {
        const value = (path || '').trim();
        return value || '/';
    }

    function buildPathStats(logs) {
        const stats = {};
        (logs || []).forEach((entry) => {
            const path = normalizedLogPath(entry.path);
            stats[path] = (stats[path] || 0) + 1;
        });
        return stats;
    }

    function buildPathDurationStats(logs) {
        const stats = {};
        (logs || []).forEach((entry) => {
            const path = normalizedLogPath(entry.path);
            stats[path] = (stats[path] || 0) + Math.max(0, Number(entry.durationMs) || 0);
        });
        return stats;
    }

    function routeLogDisplayLimit() {
        const parsed = Number.parseInt(elements.routeLogLimit.value, 10);
        if (!Number.isFinite(parsed)) {
            return ROUTE_LOG_DEFAULT_DISPLAY;
        }
        return Math.min(ROUTE_LOG_MAX_RECENT, Math.max(1, parsed));
    }

    function normalizeRouteLogLimitInput() {
        const limit = routeLogDisplayLimit();
        elements.routeLogLimit.value = String(limit);
        return limit;
    }

    function routeLogMatchesSearch(entry) {
        const pathKeyword = (elements.routeLogPathSearch.value || '').trim().toLowerCase();
        return !pathKeyword || (entry.path || '').toLowerCase().includes(pathKeyword);
    }

    function appendRouteLog(entry, prepend) {
        if (!routeLogMatchesSearch(entry)) {
            return;
        }
        const empty = elements.routeLogRows.querySelector('[data-empty="true"]');
        if (empty) {
            empty.remove();
        }

        const tr = document.createElement('tr');
        tr.appendChild(cell(formatLogTime(entry.timestamp)));
        tr.appendChild(cell(entry.method || '-'));
        tr.appendChild(cell(entry.path || '-', 'path-cell'));
        tr.appendChild(cell((entry.status || 0).toString()));
        tr.appendChild(cell((entry.durationMs || 0) + 'ms'));

        if (prepend && elements.routeLogRows.firstChild) {
            elements.routeLogRows.insertBefore(tr, elements.routeLogRows.firstChild);
        } else {
            elements.routeLogRows.appendChild(tr);
        }

        while (elements.routeLogRows.children.length > routeLogDisplayLimit()) {
            elements.routeLogRows.removeChild(elements.routeLogRows.lastChild);
        }
    }

    function renderRouteLogs(logs) {
        elements.routeLogRows.innerHTML = '';
        const pathKeyword = (elements.routeLogPathSearch.value || '').trim().toLowerCase();
        const filteredLogs = pathKeyword
            ? (logs || []).filter((entry) => (entry.path || '').toLowerCase().includes(pathKeyword))
            : (logs || []);
        const visibleLogs = filteredLogs.slice(0, routeLogDisplayLimit());
        if (visibleLogs.length === 0) {
            const tr = document.createElement('tr');
            tr.dataset.empty = 'true';
            tr.appendChild(cell(pathKeyword ? '没有匹配的路径' : '暂无代理请求', 'empty-small'));
            tr.firstChild.colSpan = 5;
            elements.routeLogRows.appendChild(tr);
            return;
        }
        visibleLogs.forEach((entry) => appendRouteLog(entry, false));
    }

    function addRouteLog(entry) {
        if (!activeRouteLogName || baseRouteId(entry.routeId || '') !== activeRouteLogName) {
            return;
        }
        routeLogState.totalRequests += 1;
        routeLogState.totalDurationMs += Math.max(0, Number(entry.durationMs) || 0);
        const ip = entry.clientIp || '-';
        const path = normalizedLogPath(entry.path);
        const durationMs = Math.max(0, Number(entry.durationMs) || 0);
        routeLogState.requestsByIp[ip] = (routeLogState.requestsByIp[ip] || 0) + 1;
        routeLogState.pathStats[path] = (routeLogState.pathStats[path] || 0) + 1;
        routeLogState.pathDurationStats[path] = (routeLogState.pathDurationStats[path] || 0) + durationMs;
        routeLogState.recentLogs = [entry, ...(routeLogState.recentLogs || [])].slice(0, ROUTE_LOG_MAX_RECENT);
        renderRouteLogStats();
        appendRouteLog(entry, true);
    }

    function closeRouteLog() {
        stopRouteLogStream();
        stopRouteLogPolling();
        activeRouteLogName = '';
        activeRouteLogEnabled = false;
        elements.routeLogModal.classList.remove('active');
    }

    function stopRouteLogStream() {
        if (routeLogSource) {
            routeLogSource.close();
            routeLogSource = null;
        }
    }

    function stopRouteLogPolling() {
        if (routeLogPollTimer) {
            clearInterval(routeLogPollTimer);
            routeLogPollTimer = null;
        }
    }

    function updateRouteLogStatus() {
        setRouteLogStatus(activeRouteLogEnabled ? '启用' : '停用', activeRouteLogEnabled ? 'active' : 'error');
    }

    function updateRefreshRateButton() {
        const mode = routeLogRefreshModes[routeLogRefreshModeIndex];
        elements.routeLogRefreshRate.textContent = mode.label;
        elements.routeLogRefreshRate.dataset.mode = String(mode.intervalMs);
    }

    function applyRouteLogRefreshMode() {
        stopRouteLogStream();
        stopRouteLogPolling();
        updateRefreshRateButton();
        if (!activeRouteLogName) {
            return;
        }
        if (!activeRouteLogEnabled) {
            elements.routeLogRefreshRate.textContent = '已暂停';
            elements.routeLogRefreshRate.dataset.mode = 'disabled';
            return;
        }
        const mode = routeLogRefreshModes[routeLogRefreshModeIndex];
        if (mode.intervalMs === 0) {
            connectRouteLogStream(activeRouteLogName);
            return;
        }
        if (mode.intervalMs > 0) {
            routeLogPollTimer = setInterval(() => refreshRouteLogSnapshot(activeRouteLogName, false), mode.intervalMs);
        }
    }

    async function refreshRouteLogSnapshot(routeId, showError) {
        try {
            const snapshot = await fetchJson('/admin/api/proxy-logs/routes/' + encodeURIComponent(routeId));
            if (activeRouteLogName !== routeId) {
                return;
            }
            const logs = snapshot.recentLogs || [];
            routeLogState = {
                totalRequests: snapshot.totalRequests || 0,
                totalDurationMs: snapshot.totalDurationMs || 0,
                requestsByIp: snapshot.requestsByIp || {},
                pathStats: snapshot.pathStats || buildPathStats(logs),
                pathDurationStats: snapshot.pathDurationStats || buildPathDurationStats(logs),
                recentLogs: logs.slice(0, ROUTE_LOG_MAX_RECENT)
            };
            renderRouteLogStats();
            renderRouteLogs(routeLogState.recentLogs);
            updateRouteLogStatus();
        } catch (error) {
            if (showError) {
                setRouteLogStatus('加载失败', 'error');
                showToast('日志加载失败: ' + error.message, 'error');
            }
        }
    }

    function connectRouteLogStream(routeId) {
        if (!window.EventSource) {
            return;
        }
        routeLogSource = new EventSource('/admin/api/proxy-logs/routes/' + encodeURIComponent(routeId) + '/stream');
        routeLogSource.onopen = updateRouteLogStatus;
        routeLogSource.onerror = updateRouteLogStatus;
        routeLogSource.addEventListener('proxy-request', (event) => {
            try {
                addRouteLog(JSON.parse(event.data));
            } catch (error) {
                console.error('解析路由日志失败', error);
            }
        });
    }

    async function openRouteLog(name) {
        stopRouteLogStream();
        stopRouteLogPolling();
        activeRouteLogName = name;
        activeRouteLogEnabled = routeEnabled(name);
        routeLogState = {
            totalRequests: 0,
            totalDurationMs: 0,
            requestsByIp: {},
            pathStats: {},
            pathDurationStats: {},
            recentLogs: []
        };
        elements.routeLogTitle.textContent = '路由日志';
        elements.routeLogName.textContent = routeDisplayName(name);
        elements.routeLogName.title = routeDisplayName(name);
        elements.routeLogPathSearch.value = '';
        updateRouteLogStatus();
        renderRouteLogStats();
        renderRouteLogs([]);
        elements.routeLogModal.classList.add('active');

        await refreshRouteLogSnapshot(name, true);
        applyRouteLogRefreshMode();
    }

    if (!requireElements()) {
        return;
    }

    elements.btnAdd.addEventListener('click', () => {
        elements.modalTitle.textContent = '新增路由';
        elements.isEdit.value = 'false';
        elements.oldRouteId.value = '';
        elements.routeForm.reset();
        elements.enabled.value = 'false';
        elements.localIp.value = '127.0.0.1';
        elements.pathPrefixInput.value = '';
        setPathPrefixes([]);
        elements.modal.classList.add('active');
        elements.name.focus();
    });

    elements.routeSearch.addEventListener('input', filterRoutes);
    elements.btnBatchDelete.addEventListener('click', () => {
        const routeIds = selectedRouteIds();
        if (routeIds.length === 0) {
            showSelectionControls();
            showToast('请选择要删除的路由', 'error');
            return;
        }
        openDeleteConfirm(routeIds);
    });
    elements.routeLogRefreshRate.addEventListener('click', () => {
        routeLogRefreshModeIndex = (routeLogRefreshModeIndex + 1) % routeLogRefreshModes.length;
        applyRouteLogRefreshMode();
    });
    elements.routeLogLimit.addEventListener('input', () => {
        renderRouteLogs(routeLogState.recentLogs || []);
    });
    elements.routeLogLimit.addEventListener('change', () => {
        normalizeRouteLogLimitInput();
        renderRouteLogs(routeLogState.recentLogs || []);
    });
    elements.routeLogPathSearch.addEventListener('input', () => {
        renderRouteLogs(routeLogState.recentLogs || []);
    });
    elements.btnAddPathPrefix.addEventListener('click', addPathPrefixFromInput);
    elements.pathPrefixInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            addPathPrefixFromInput();
        }
    });

    elements.routeCards.addEventListener('click', (e) => {
        if (e.target.classList.contains('route-select-checkbox')) {
            setCardSelected(e.target.dataset.id, e.target.checked);
            updateBatchDeleteButton();
            hideSelectionControlsIfIdle();
            return;
        }
        if (e.target.closest('.route-card-select')) {
            showSelectionControls();
            return;
        }
        const title = e.target.closest('.route-card-title h3');
        if (title) {
            const card = e.target.closest('.route-card');
            const checkbox = card ? card.querySelector('.route-select-checkbox') : null;
            if (checkbox) {
                checkbox.checked = !checkbox.checked;
                setCardSelected(checkbox.dataset.id, checkbox.checked);
                updateBatchDeleteButton(false);
                hideSelectionControlsIfIdle();
            }
            return;
        }
        const btn = e.target.closest('button');
        if (!btn) {
            return;
        }
        const routeId = btn.dataset.id;
        if (!routeId) {
            return;
        }

        if (btn.classList.contains('btn-view')) {
            viewFile(routeId);
        } else if (btn.classList.contains('btn-logs')) {
            openRouteLog(routeId);
        } else if (btn.classList.contains('btn-copy')) {
            copyRoute(routeId);
        } else if (btn.classList.contains('btn-edit')) {
            editRoute(routeId);
        } else if (btn.classList.contains('btn-toggle-status')) {
            toggleRouteStatus(routeId, btn.dataset.enabled === 'true');
        }
    });

    async function viewFile(routeId) {
        try {
            viewFileName = routeId;
            const data = await fetchJson('/admin/api/routes/' + encodeURIComponent(routeId) + '/raw');
            elements.viewTitle.textContent = fileNameFromPath(data.fileName) || (routeId + '.json');
            elements.jsonEditor.value = formatConfigJson(data.content, true);
            elements.jsonEditor.readOnly = true;
            elements.btnToggleEdit.textContent = '编辑此文件';
            elements.viewModal.classList.add('active');
        } catch (error) {
            showToast('加载失败: ' + error.message, 'error');
        }
    }

    async function copyRoute(routeId) {
        try {
            const cfg = await fetchJson('/admin/api/routes/' + encodeURIComponent(routeId));
            elements.modalTitle.textContent = '拷贝路由';
            elements.isEdit.value = 'false';
            elements.oldRouteId.value = '';
            elements.routeForm.reset();
            elements.name.value = nextCopyValue((cfg.name || '').trim(), existingRouteValues('name'));
            elements.pathPrefixInput.value = '';
            setPathPrefixes(cfg.pathPrefixes || [cfg.pathPrefix].filter(Boolean));
            elements.targetUrl.value = displayTargetUrl(cfg.targetUrl);
            elements.localIp.value = cfg.localIp || '127.0.0.1';
            elements.localPort.value = '';
            elements.enabled.value = String(cfg.enabled === true);
            elements.modal.classList.add('active');
            elements.name.focus();
            elements.name.select();
        } catch (error) {
            showToast('拷贝失败: ' + error.message, 'error');
        }
    }

    async function editRoute(routeId) {
        try {
            const cfg = await fetchJson('/admin/api/routes/' + encodeURIComponent(routeId));
            elements.modalTitle.textContent = '编辑路由';
            elements.isEdit.value = 'true';
            elements.oldRouteId.value = cfg.id;
            elements.name.value = cfg.name;
            elements.pathPrefixInput.value = '';
            setPathPrefixes(cfg.pathPrefixes || [cfg.pathPrefix].filter(Boolean));
            elements.targetUrl.value = displayTargetUrl(cfg.targetUrl);
            elements.localIp.value = cfg.localIp || '127.0.0.1';
            elements.localPort.value = cfg.localPort || '';
            elements.enabled.value = cfg.enabled.toString();
            elements.modal.classList.add('active');
        } catch (error) {
            showToast('加载失败: ' + error.message, 'error');
        }
    }

    async function toggleRouteStatus(routeId, enabled) {
        try {
            const cfg = await fetchJson('/admin/api/routes/' + encodeURIComponent(routeId));
            const nextEnabled = !enabled;
            const pathPrefixes = cfg.pathPrefixes || [cfg.pathPrefix].filter(Boolean);
            const payload = {
                name: cfg.name,
                pathPrefixes: pathPrefixes,
                targetUrl: cfg.targetUrl,
                localIp: cfg.localIp,
                localPort: cfg.localPort,
                enabled: nextEnabled
            };
            await fetchJson('/admin/api/routes/' + encodeURIComponent(routeId), {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            if (!nextEnabled && activeRouteLogName === routeId) {
                activeRouteLogEnabled = false;
                routeLogRefreshModeIndex = routeLogRefreshModes.length - 1;
                applyRouteLogRefreshMode();
            }
            showToast(nextEnabled ? '已启用' : '已停用');
            setTimeout(() => location.reload(), 500);
        } catch (error) {
            showToast('操作失败: ' + error.message, 'error');
        }
    }

    elements.btnToggleEdit.addEventListener('click', async () => {
        if (elements.jsonEditor.readOnly) {
            elements.jsonEditor.readOnly = false;
            elements.btnToggleEdit.textContent = '保存修改';
            elements.viewTitle.textContent = '编辑 - ' + viewFileName + '.json';
            return;
        }

        try {
            const updated = JSON.parse(prettifyJsonEditor());
            const pathPrefixes = Array.isArray(updated.pathPrefixes)
                ? updated.pathPrefixes.map((prefix) => (prefix || '').trim()).filter(Boolean)
                : parsePathPrefixes(updated.pathPrefix || '');
            const payload = {
                name: (updated.name || '').trim(),
                pathPrefixes: pathPrefixes,
                targetUrl: updated.targetUrl,
                localIp: updated.localIp,
                localPort: updated.localPort,
                enabled: updated.enabled !== false
            };
            await fetchJson('/admin/api/routes/' + encodeURIComponent(viewFileName), {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            elements.viewModal.classList.remove('active');
            showToast('保存成功');
            setTimeout(() => location.reload(), 500);
        } catch (error) {
            const message = error instanceof SyntaxError ? 'JSON 格式错误' : error.message;
            showToast('保存失败: ' + message, 'error');
        }
    });

    elements.btnCancel.addEventListener('click', () => elements.modal.classList.remove('active'));
    elements.jsonEditor.addEventListener('blur', prettifyJsonEditor);
    elements.btnCloseView.addEventListener('click', () => elements.viewModal.classList.remove('active'));
    elements.btnCloseRouteLog.addEventListener('click', closeRouteLog);
    elements.btnCancelDelete.addEventListener('click', () => elements.confirmModal.classList.remove('active'));
    elements.modal.addEventListener('click', (e) => {
        if (e.target === elements.modal) {
            elements.modal.classList.remove('active');
        }
    });
    elements.viewModal.addEventListener('click', (e) => {
        if (e.target === elements.viewModal) {
            elements.viewModal.classList.remove('active');
        }
    });
    elements.routeLogModal.addEventListener('click', (e) => {
        if (e.target === elements.routeLogModal) {
            closeRouteLog();
        }
    });
    elements.confirmModal.addEventListener('click', (e) => {
        if (e.target === elements.confirmModal) {
            elements.confirmModal.classList.remove('active');
        }
    });

    elements.routeForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const isEdit = elements.isEdit.value === 'true';
        const oldRouteId = elements.oldRouteId.value;
        const pathPrefixes = pathPrefixValues();
        const payload = {
            name: elements.name.value.trim(),
            pathPrefixes: pathPrefixes,
            targetUrl: elements.targetUrl.value.trim(),
            localIp: normalizedLocalIp(elements.localIp.value),
            localPort: elements.localPort.value ? Number(elements.localPort.value) : null,
            enabled: elements.enabled.value === 'true'
        };

        if (!payload.name) {
            showToast('请输入路由名称', 'error');
            return;
        }
        if (hasRouteNameConflict(payload.name, isEdit ? oldRouteId : '')) {
            showToast('路由名称已存在，不能重复新增', 'error');
            return;
        }
        if (payload.pathPrefixes.length === 0) {
            showToast('请输入路径前缀并点击添加路径', 'error');
            return;
        }
        if (!payload.targetUrl) {
            showToast('请输入目标地址', 'error');
            return;
        }
        if (!isValidTargetUrl(payload.targetUrl)) {
            showToast('目标地址需包含端口，如 127.0.0.1:8080 或 api.example.com:8080', 'error');
            return;
        }
        if (hasTargetUrlConflict(payload.targetUrl, isEdit ? oldRouteId : '')) {
            showToast('目标地址已存在，不能重复新增', 'error');
            return;
        }
        if (!isValidLocalIp(payload.localIp)) {
            showToast('本地 IP 格式不正确，如 127.0.0.1', 'error');
            return;
        }
        if (!isValidLocalPort(payload.localPort)) {
            showToast('本地端口范围为 1-65535', 'error');
            return;
        }
        if (hasLocalBindingConflict(payload.localIp, payload.localPort, isEdit ? oldRouteId : '')) {
            showToast('本地 IP 和端口已存在，不能重复新增', 'error');
            return;
        }

        const url = isEdit
            ? '/admin/api/routes/' + encodeURIComponent(oldRouteId)
            : '/admin/api/routes';
        const method = isEdit ? 'PUT' : 'POST';

        try {
            await fetchJson(url, {
                method: method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            elements.modal.classList.remove('active');
            showToast('保存成功');
            setTimeout(() => location.reload(), 500);
        } catch (error) {
            showToast('请求失败: ' + error.message, 'error');
        }
    });

    window.deleteRoute = function (routeId) {
        openDeleteConfirm([routeId]);
    };

    elements.btnBatchDelete.addEventListener('mouseenter', showSelectionControls);
    elements.btnBatchDelete.addEventListener('focus', showSelectionControls);
    elements.btnBatchDelete.addEventListener('click', showSelectionControls);

    document.querySelectorAll('.file-path').forEach((element) => {
        if (!element.title) {
            element.title = element.textContent;
        }
        element.textContent = fileNameFromPath(element.textContent);
    });
    updateBatchDeleteButton();

    elements.btnConfirmDelete.addEventListener('click', async () => {
        try {
            for (const routeId of pendingDeleteIds) {
                await fetchJson('/admin/api/routes/' + encodeURIComponent(routeId), {
                    method: 'DELETE'
                });
            }
            elements.confirmModal.classList.remove('active');
            showToast(pendingDeleteIds.length > 1 ? '批量删除成功' : '删除成功');
            setTimeout(() => location.reload(), 500);
        } catch (error) {
            showToast('请求失败: ' + error.message, 'error');
        }
    });
})();
