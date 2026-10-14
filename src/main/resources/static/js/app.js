// =====================================================================
// Aegis IT Asset Management — Full Frontend Logic
// v2.1 — Dedicated Full-Screen Login, Direct Mail Routing, Employee Hub
// =====================================================================

// -------------------------------------------------------
// APP STATE
// -------------------------------------------------------
const App = {
    session: null,          // { id, email, fullName, role, dept, jobTitle }
    isAdminView: true,      // Admin can toggle to Employee portal view
    cachedAssets: [],
    cachedUsers: [],
    cachedEmails: [],
    cachedAccessories: [],
    cachedConsumables: [],
    cachedComponents: [],
    cachedLicenses: [],
    cachedActivity: [],
    sortField: null,
    sortAsc: true,
    searchTimer: null,
    lastCreatedUser: null,  // Holds data for success modal
    networkInfo: null,      // Server LAN IP & URL for mobile QR codes
    currentLabelAsset: null,// Current asset in label modal
    scannedAsset: null      // Current asset viewed via QR mobile scan
};

// -------------------------------------------------------
// INIT
// -------------------------------------------------------
document.addEventListener('DOMContentLoaded', async () => {
    // Early fetch server LAN network info so QR codes immediately have phone-reachable IP
    fetchNetworkInfo();

    // Check URL query parameters (e.g. from mobile QR scan or welcome email link)
    const params = new URLSearchParams(window.location.search);
    const scanTagParam = params.get('scanTag') || (window.location.hash.startsWith('#scanTag=') ? window.location.hash.substring(9) : null);
    const emailParam = params.get('email');
    const logoutParam = params.get('logout');

    if (logoutParam) {
        localStorage.removeItem('itam_session');
    }

    // If accessed via QR Code scan on mobile / camera, open Equipment Passport
    if (scanTagParam) {
        showAuthPage(null); // Keep login page as backdrop, protecting the admin console
        openMobileAssetPassport(scanTagParam);
        return;
    }

    // If an email param was provided in the URL (user clicked link from their onboarding email)
    if (emailParam) {
        // Clear any prior session so they aren't stuck in someone else's login
        localStorage.removeItem('itam_session');
        showAuthPage(emailParam);
        return;
    }

    // Check existing stored session
    const saved = localStorage.getItem('itam_session');
    if (saved) {
        try {
            App.session = JSON.parse(saved);
            activateAppLayout();
        } catch {
            showAuthPage(null);
        }
    } else {
        if (!scanTagParam) {
            showAuthPage(null);
        }
    }

    // Global listener: Click outside closes user dropdown
    document.addEventListener('click', (e) => {
        const dropdown = document.getElementById('user-dropdown-menu');
        const capsule = document.getElementById('user-capsule');
        if (dropdown && capsule && !capsule.contains(e.target)) {
            dropdown.style.display = 'none';
        }
    });

    // Global listener: Keyboard shortcut for Spotlight Search (Ctrl+K, Cmd+K, or '/')
    document.addEventListener('keydown', (e) => {
        if ((e.ctrlKey || e.metaKey) && (e.key === 'k' || e.key === 'K')) {
            e.preventDefault();
            openSpotlightSearch();
        } else if (e.key === '/' && document.activeElement && document.activeElement.tagName !== 'INPUT' && document.activeElement.tagName !== 'TEXTAREA') {
            e.preventDefault();
            openSpotlightSearch();
        } else if (e.key === 'Escape') {
            closeSpotlightSearch();
        }
    });
});

function showAuthPage(emailToFill) {
    App.session = null;
    const authView = document.getElementById('auth-view');
    const appLayout = document.getElementById('app-layout');

    if (authView) authView.style.display = 'flex';
    if (appLayout) appLayout.style.display = 'none';

    const emailInput = document.getElementById('auth-email');
    const pwdInput = document.getElementById('auth-password');
    const errBox = document.getElementById('auth-error-msg');
    const subTitle = document.getElementById('auth-welcome-sub');

    if (errBox) errBox.style.display = 'none';

    if (emailToFill && emailInput) {
        emailInput.value = emailToFill;
        if (pwdInput) {
            pwdInput.value = '';
            setTimeout(() => pwdInput.focus(), 100);
        }
        if (subTitle) {
            subTitle.innerHTML = `<span style="color:#38bdf8;font-weight:600;">Welcome!</span> Enter your temporary password from your email:`;
        }
    } else if (emailInput) {
        emailInput.value = '';
        if (pwdInput) pwdInput.value = '';
        if (subTitle) {
            subTitle.textContent = 'Sign in to view your assigned hardware & equipment';
        }
    }
}

function fillAuthForm(email, password) {
    const emailInput = document.getElementById('auth-email');
    const pwdInput = document.getElementById('auth-password');
    const errBox = document.getElementById('auth-error-msg');
    if (emailInput) emailInput.value = email;
    if (pwdInput) pwdInput.value = password;
    if (errBox) errBox.style.display = 'none';
}

async function submitAuthPageLogin(event) {
    event.preventDefault();
    const btn = document.getElementById('btn-auth-submit');
    const email = document.getElementById('auth-email').value.trim();
    const password = document.getElementById('auth-password').value;
    const errEl = document.getElementById('auth-error-msg');

    btn.disabled = true;
    btn.textContent = 'Verifying credentials...';
    errEl.style.display = 'none';

    try {
        const res = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password })
        });
        const data = await res.json();

        if (!res.ok || !data.success) {
            errEl.textContent = data.message || 'Invalid organization email or password. Please check your credentials.';
            errEl.style.display = 'block';
            return;
        }

        // Persist session
        App.session = {
            id: data.id,
            email: data.email,
            fullName: data.fullName,
            role: data.role,
            department: data.department,
            jobTitle: data.jobTitle
        };
        localStorage.setItem('itam_session', JSON.stringify(App.session));

        // Clean query parameter from URL
        window.history.replaceState({}, document.title, window.location.pathname);

        // Activate layout
        activateAppLayout();
        showToast(`Welcome back, ${data.fullName}!`, 'success');

    } catch (err) {
        errEl.textContent = 'Connection error. Please ensure backend is running.';
        errEl.style.display = 'block';
    } finally {
        btn.disabled = false;
        btn.textContent = 'Sign In to Portal →';
    }
}

async function activateAppLayout() {
    const authView = document.getElementById('auth-view');
    const appLayout = document.getElementById('app-layout');

    if (authView) authView.style.display = 'none';
    if (appLayout) appLayout.style.display = 'flex';

    renderSessionCapsule();

    if (App.session.role === 'EMPLOYEE') {
        // Show ONLY employee navigation and hub
        document.getElementById('admin-nav-menu').style.display = 'none';
        document.getElementById('employee-nav-menu').style.display = 'block';
        document.getElementById('sidebar-role-indicator').textContent = 'Employee Self-Service Hub';

        const adminMetrics = document.getElementById('admin-metrics-grid');
        if (adminMetrics) adminMetrics.style.display = 'none';

        const btnAsset = document.getElementById('btn-create-asset');
        const btnCsv = document.getElementById('btn-export-csv');
        if (btnAsset) btnAsset.style.display = 'none';
        if (btnCsv) btnCsv.style.display = 'none';

        switchTab('employee-portal');
        await loadEmployeePortal(App.session.email);
    } else {
        // ADMIN: Show full command center
        document.getElementById('admin-nav-menu').style.display = 'block';
        document.getElementById('employee-nav-menu').style.display = 'none';
        document.getElementById('sidebar-role-indicator').textContent = 'Admin Command Hub';

        showAdminLayout();
        switchTab('assets');
        await refreshAll();
    }
}

async function refreshAll() {
    await Promise.all([
        loadDashboardSummary(),
        loadAssets(),
        loadAccessories(),
        loadConsumables(),
        loadComponents(),
        loadLicenses(),
        loadMaintenanceLogs(),
        loadActivityLogs()
    ]);
    if (App.session && App.session.role === 'ADMIN') {
        loadUsers();
        loadEmails();
    }
}

// -------------------------------------------------------
// SESSION CAPSULE UI
// -------------------------------------------------------
function renderSessionCapsule() {
    const s = App.session;
    if (!s) return;

    const initials = s.fullName ? s.fullName.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase() : 'ME';
    const isAdmin = s.role === 'ADMIN';

    const set = (id, val) => { const el = document.getElementById(id); if (el) el.textContent = val; };
    set('capsule-avatar', initials);
    set('capsule-name', s.fullName);
    set('capsule-dept', s.department || '');
    set('dd-user-name', s.fullName);
    set('dd-user-email', s.email);

    const roleEl = document.getElementById('capsule-role');
    if (roleEl) {
        roleEl.textContent = isAdmin ? 'ADMIN' : 'EMPLOYEE';
        roleEl.className = `role-pill ${isAdmin ? 'pill-admin' : 'pill-employee'}`;
    }

    const switchBtn = document.getElementById('dd-switch-view-btn');
    if (switchBtn) {
        // Only admins have the toggle switch option
        switchBtn.style.display = isAdmin ? 'flex' : 'none';
        set('dd-switch-label', 'Switch to Employee View');
    }
}

function toggleUserDropdown() {
    const dd = document.getElementById('user-dropdown-menu');
    if (dd) dd.style.display = dd.style.display === 'none' ? 'block' : 'none';
}

function showAdminLayout() {
    const mg = document.getElementById('admin-metrics-grid');
    if (mg) mg.style.display = 'grid';

    const btnAsset = document.getElementById('btn-create-asset');
    const btnCsv = document.getElementById('btn-export-csv');
    const btnUser = document.getElementById('btn-add-user-top');
    if (btnAsset) btnAsset.style.display = '';
    if (btnCsv) btnCsv.style.display = '';
    if (btnUser) btnUser.style.display = 'none';
}

function logoutUser() {
    localStorage.removeItem('itam_session');
    App.session = null;
    const dd = document.getElementById('user-dropdown-menu');
    if (dd) dd.style.display = 'none';

    window.history.replaceState({}, document.title, window.location.pathname);
    showAuthPage(null);
    showToast('Signed out of IT Portal', 'info');
}

function toggleRoleView() {
    const dd = document.getElementById('user-dropdown-menu');
    if (dd) dd.style.display = 'none';
    if (App.session && App.session.role === 'ADMIN') {
        switchTab('employee-portal');
        loadEmployeePortal(App.session.email);
        const lbl = document.getElementById('dd-switch-label');
        if (lbl) lbl.textContent = 'Back to Admin Panel';
    } else {
        switchTab('assets');
    }
}

function scrollToEmpSection(sectionId) {
    document.getElementById(sectionId)?.scrollIntoView({ behavior: 'smooth' });
}

// -------------------------------------------------------
// MODAL LOGIN (Optional secondary fallback)
// -------------------------------------------------------
function openLoginModal() {
    logoutUser();
}

function fillLoginForm(email, password) {
    fillAuthForm(email, password);
}

function submitLogin(event) {
    submitAuthPageLogin(event);
}

// -------------------------------------------------------
// NAVIGATION & MOBILE RESPONSIVE DRAWER
// -------------------------------------------------------
function toggleMobileMenu() {
    const navWrap = document.getElementById('sidebar-nav-wrap');
    if (navWrap) {
        navWrap.classList.toggle('open');
    }
}

function switchTab(tabKey) {
    // If logged in as an EMPLOYEE, strictly lock navigation to employee-portal
    if (App.session && App.session.role === 'EMPLOYEE') {
        tabKey = 'employee-portal';
    }

    // Auto-close mobile drawer on tab selection
    const navWrap = document.getElementById('sidebar-nav-wrap');
    if (navWrap && navWrap.classList.contains('open')) {
        navWrap.classList.remove('open');
    }

    const allTabs = ['assets', 'accessories', 'consumables', 'components', 'licenses', 'users', 'activity', 'emails', 'maintenance', 'analytics', 'employee-portal'];
    allTabs.forEach(key => {
        const btn = document.getElementById(`nav-${key}-btn`);
        const pane = document.getElementById(`pane-${key}`);
        const isActive = (key === tabKey);
        if (btn) btn.classList.toggle('active', isActive);
        if (pane) {
            pane.style.display = isActive ? 'block' : 'none';
            pane.classList.toggle('active', isActive);
        }
    });

    // Also handle employee sub-buttons
    const empBtn = document.getElementById('nav-emp-dashboard-btn');
    if (empBtn) empBtn.classList.toggle('active', tabKey === 'employee-portal');

    const heading = document.getElementById('page-main-heading');
    const sub = document.getElementById('page-sub-heading');
    const btnAsset = document.getElementById('btn-create-asset');
    const btnCsv = document.getElementById('btn-export-csv');
    const btnUser = document.getElementById('btn-add-user-top');

    const isAdmin = App.session && App.session.role === 'ADMIN';
    if (btnAsset) btnAsset.style.display = (isAdmin && tabKey === 'assets') ? '' : 'none';
    if (btnCsv) btnCsv.style.display = (isAdmin && tabKey === 'assets') ? '' : 'none';
    if (btnUser) btnUser.style.display = (isAdmin && tabKey === 'users') ? '' : 'none';

    const headings = {
        'assets': ['IT Asset Portfolio', 'Track, audit, provision, and maintain organization hardware'],
        'accessories': ['Accessories Inventory', 'Track keyboards, chargers, docking stations, and active employee checkouts'],
        'consumables': ['Consumables Dispensing', 'Track and dispense printer toner, thermal paste, cables, and safety supplies'],
        'components': ['Internal Components', 'Track RAM, NVMe SSDs, GPUs, and parts installed into hardware hosts'],
        'licenses': ['Software Licenses & Subscriptions', 'SaaS subscriptions, seat utilization, and renewal tracking'],
        'users': ['Users & Workforce Onboarding', 'Add employees, dispatch welcome emails, and manage access'],
        'activity': ['Custody & Activity Audit Trail', 'Immutable audit log of all check-outs, returns, physical audits, and part installations'],
        'emails': ['Email Outbox & Notification Audit', 'Live audit log of all dispatched onboarding and assignment notifications'],
        'maintenance': ['Hardware Maintenance & Service Records', 'Repair audits, vendor RMAs, and diagnostic logs'],
        'analytics': ['Asset Analytics & Department Allocation', 'Portfolio distribution by category and department'],
        'employee-portal': ['My IT Equipment Hub', 'Your assigned organization hardware & equipment details']
    };

    if (headings[tabKey]) {
        heading.innerText = headings[tabKey][0];
        if (sub) sub.innerText = headings[tabKey][1];
    }

    if (tabKey === 'accessories') loadAccessories();
    if (tabKey === 'consumables') loadConsumables();
    if (tabKey === 'components') loadComponents();
    if (tabKey === 'activity') loadActivityLogs();
    if (tabKey === 'analytics') renderAnalyticsBreakdowns();
    if (tabKey === 'emails') loadEmails();
    if (tabKey === 'users') loadUsers();
}

// -------------------------------------------------------
// DASHBOARD METRICS
// -------------------------------------------------------
async function loadDashboardSummary() {
    try {
        const res = await fetch('/api/dashboard/summary');
        if (!res.ok) return;
        const data = await res.json();

        document.getElementById('val-total-assets').innerText = data.totalAssets;
        document.getElementById('val-assigned-assets').innerText = data.assignedAssets;
        document.getElementById('val-available-assets').innerText = data.availableAssets;
        document.getElementById('val-maintenance-assets').innerText = data.maintenanceAssets;

        const valEl = document.getElementById('val-asset-value');
        valEl.innerText = data.totalAssetValue ? '$' + Number(data.totalAssetValue).toLocaleString('en-US', { minimumFractionDigits: 2 }) : '$0.00';

        document.getElementById('val-expiring-count').innerText = data.expiringWarrantiesCount;
        document.getElementById('nav-count-assets').innerText = data.totalAssets;

        // Equipment Inventory Counters
        const setNav = (id, val) => { const el = document.getElementById(id); if (el && val !== undefined) el.innerText = val; };
        setNav('nav-count-accessories', data.totalAccessories);
        setNav('nav-count-consumables', data.totalConsumables);
        setNav('nav-count-components', data.totalComponents);
        if (data.recentActivityLogs) setNav('nav-count-activity', data.recentActivityLogs.length);

        window.lastDashboardSummary = data;
    } catch (err) {
        console.error('Dashboard summary error:', err);
    }
}

// -------------------------------------------------------
// ASSETS TABLE & ADVANCED CONTROLS
// -------------------------------------------------------
async function loadAssets() {
    const query = document.getElementById('asset-search-input')?.value.trim() || '';
    const category = document.getElementById('filter-category')?.value || '';
    const status = document.getElementById('filter-status')?.value || '';
    const department = document.getElementById('filter-dept')?.value || '';
    const condition = document.getElementById('filter-condition')?.value || '';

    const params = new URLSearchParams();
    if (query) params.append('query', query);
    if (category) params.append('category', category);
    if (status) params.append('status', status);
    if (department) params.append('department', department);

    try {
        const res = await fetch(`/api/assets?${params.toString()}`);
        if (!res.ok) return;
        let assets = await res.json();
        if (condition) {
            assets = assets.filter(a => (a.condition || '').toUpperCase() === condition.toUpperCase());
        }
        App.cachedAssets = assets;
        applySortToCachedAssets();
        renderAssetsTable(App.cachedAssets);
    } catch (err) {
        console.error('Error loading assets:', err);
    }
}

function debounceSearch() {
    clearTimeout(App.searchTimer);
    App.searchTimer = setTimeout(() => loadAssets(), 280);
}

function resetFilters() {
    if (document.getElementById('asset-search-input')) document.getElementById('asset-search-input').value = '';
    if (document.getElementById('filter-category')) document.getElementById('filter-category').value = '';
    if (document.getElementById('filter-status')) document.getElementById('filter-status').value = '';
    if (document.getElementById('filter-dept')) document.getElementById('filter-dept').value = '';
    if (document.getElementById('filter-condition')) document.getElementById('filter-condition').value = '';
    loadAssets();
}

function renderAssetsTable(assets) {
    const tbody = document.getElementById('assets-table-body');
    if (!tbody) return;

    if (!assets || assets.length === 0) {
        tbody.innerHTML = '<tr><td colspan="11" style="text-align:center; padding: 40px; color: var(--text-muted);">No assets match your search/filters. Try resetting filters or add a new asset.</td></tr>';
        updateSelectedAssetsBar();
        return;
    }

    tbody.innerHTML = assets.map(a => {
        const statusClass = `pill-${a.status.toLowerCase().replace(/_/g, '-')}`;
        const formattedStatus = a.status.replace(/_/g, ' ');

        let warrantyHtml = '<span class="text-muted">–</span>';
        if (a.warrantyExpiryDate) {
            const diffDays = Math.ceil((new Date(a.warrantyExpiryDate) - new Date()) / 86400000);
            if (diffDays < 0) warrantyHtml = `<span class="warranty-badge warranty-warn">Expired</span>`;
            else if (diffDays <= 60) warrantyHtml = `<span class="warranty-badge warranty-warn">Exp ${diffDays}d</span>`;
            else warrantyHtml = `<span class="warranty-badge warranty-ok">Active</span>`;
        }

        let empHtml = '<span class="text-muted">–</span>';
        if (a.assignedEmployeeName) {
            const initials = a.assignedEmployeeName.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase();
            empHtml = `<div class="employee-cell"><div class="avatar-initial">${initials}</div><div><div style="font-weight:500">${escapeHtml(a.assignedEmployeeName)}</div></div></div>`;
        }

        const conditionBadge = `<span class="cond-badge cond-${(a.condition||'').toLowerCase()}">${a.condition || '–'}</span>`;
        const costStr = a.purchaseCost != null ? '$' + Number(a.purchaseCost).toLocaleString('en-US', { minimumFractionDigits: 2 }) : '–';

        return `<tr>
            <td style="text-align: center;">
                <input type="checkbox" class="asset-row-select" value="${a.id}" onchange="updateSelectedAssetsBar()" title="Select Asset ${escapeHtml(a.assetTag)}">
            </td>
            <td><span class="asset-tag-badge">${escapeHtml(a.assetTag)}</span></td>
            <td>
                <span class="asset-name-title">${escapeHtml(a.name)}</span>
                <span class="asset-model-sub">${escapeHtml(a.manufacturer || '')} ${escapeHtml(a.model || '')}</span>
            </td>
            <td><span class="category-tag">${a.category}</span></td>
            <td><span class="pill ${statusClass}"><span class="pill-dot"></span>${formattedStatus}</span></td>
            <td>${conditionBadge}</td>
            <td>${empHtml}</td>
            <td><span style="font-size:0.8rem;color:var(--text-secondary)">${escapeHtml(a.assignedDepartment || '–')}</span></td>
            <td><span style="font-size:0.8rem;color:var(--text-secondary)">${escapeHtml(a.location || '–')}</span></td>
            <td><span style="font-family:var(--font-mono);font-size:0.82rem">${costStr}</span></td>
            <td style="text-align:right">
                <div class="action-btn-group">
                    <button class="btn-table-action" onclick="openAssetLabelModal(${a.id})" title="Print QR Label Sticker">🏷️ QR</button>
                    ${a.status === 'ASSIGNED'
                        ? `<button class="btn-table-action" onclick="openCheckinAssetModal(${a.id})" title="Check In Hardware" style="color:var(--status-green);border-color:rgba(16,185,129,0.3)">Check-In</button>`
                        : `<button class="btn-table-action" onclick="openCheckoutAssetModal(${a.id})" title="Check Out Hardware" style="color:var(--status-blue);border-color:rgba(56,189,248,0.3)">Check-Out</button>`
                    }
                    <button class="btn-table-action" onclick="openAuditAssetModal(${a.id})" title="Physical Custody Audit">Audit</button>
                    <button class="btn-table-action" onclick="openEditAssetModal(${a.id})" title="Edit Details">Edit</button>
                    <button class="btn-table-action" onclick="cloneAsset(${a.id})" title="Clone Asset (Duplicate Specs)">Clone</button>
                    <button class="btn-table-action" onclick="openMaintenanceModal(${a.id})" title="Service & Maintenance">Service</button>
                    <button class="btn-table-action danger" onclick="deleteAsset(${a.id})" title="Delete">&times;</button>
                </div>
            </td>
        </tr>`;
    }).join('');

    updateSelectedAssetsBar();
}

function sortAssets(field) {
    if (App.sortField === field) {
        App.sortAsc = !App.sortAsc;
    } else {
        App.sortField = field;
        App.sortAsc = true;
    }
    const sortKeys = ['assetTag', 'name', 'status', 'assignedEmployeeName', 'purchaseCost'];
    sortKeys.forEach(f => {
        const el = document.getElementById(`sort-${f}`);
        if (el) {
            if (f === App.sortField) {
                el.textContent = App.sortAsc ? ' ▲' : ' ▼';
            } else {
                el.textContent = '';
            }
        }
    });
    applySortToCachedAssets();
    renderAssetsTable(App.cachedAssets);
}

function applySortToCachedAssets() {
    if (!App.sortField || !App.cachedAssets || App.cachedAssets.length === 0) return;
    const field = App.sortField;
    const asc = App.sortAsc;
    App.cachedAssets.sort((a, b) => {
        let valA = a[field] ?? '';
        let valB = b[field] ?? '';
        if (field === 'purchaseCost') {
            const numA = parseFloat(valA) || 0;
            const numB = parseFloat(valB) || 0;
            return asc ? numA - numB : numB - numA;
        }
        if (typeof valA === 'string') valA = valA.toLowerCase();
        if (typeof valB === 'string') valB = valB.toLowerCase();
        if (valA < valB) return asc ? -1 : 1;
        if (valA > valB) return asc ? 1 : -1;
        return 0;
    });
}

function toggleSelectAllAssets(masterCheckbox) {
    const isChecked = !!masterCheckbox?.checked;
    document.querySelectorAll('.asset-row-select').forEach(cb => {
        cb.checked = isChecked;
    });
    updateSelectedAssetsBar();
}

function updateSelectedAssetsBar() {
    const allBoxes = document.querySelectorAll('.asset-row-select');
    const checkedBoxes = document.querySelectorAll('.asset-row-select:checked');
    const bar = document.getElementById('assets-bulk-bar');
    const countBadge = document.getElementById('bulk-selected-count');
    const master = document.getElementById('assets-select-all');

    const count = checkedBoxes.length;
    if (bar) {
        if (count > 0) {
            bar.classList.add('visible');
            if (countBadge) countBadge.textContent = `${count} Selected`;
        } else {
            bar.classList.remove('visible');
        }
    }

    if (master && allBoxes.length > 0) {
        master.checked = (count === allBoxes.length);
        master.indeterminate = (count > 0 && count < allBoxes.length);
    } else if (master) {
        master.checked = false;
        master.indeterminate = false;
    }
}

function deselectAllAssets() {
    const master = document.getElementById('assets-select-all');
    if (master) {
        master.checked = false;
        master.indeterminate = false;
    }
    document.querySelectorAll('.asset-row-select').forEach(cb => { cb.checked = false; });
    updateSelectedAssetsBar();
}

function getSelectedAssetIds() {
    return Array.from(document.querySelectorAll('.asset-row-select:checked')).map(cb => Number(cb.value));
}

async function bulkCheckinSelectedAssets() {
    const ids = getSelectedAssetIds();
    if (ids.length === 0) {
        showToast('No assets selected.', 'warning');
        return;
    }
    if (!confirm(`Are you sure you want to Check In ${ids.length} selected asset(s) back to available inventory?`)) {
        return;
    }
    try {
        const res = await fetch('/api/assets/bulk-checkin', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(ids)
        });
        const data = await res.json();
        showToast(`Successfully checked in ${data.checkedInCount || ids.length} asset(s)!`, 'success');
        deselectAllAssets();
        await loadAssets();
        if (typeof loadDashboardSummary === 'function') loadDashboardSummary();
        if (typeof loadActivityLogs === 'function') loadActivityLogs();
    } catch (err) {
        console.error('Bulk checkin error:', err);
        showToast('Failed to check in selected assets: ' + err.message, 'error');
    }
}

async function bulkChangeStatusSelectedAssets(newStatus) {
    if (!newStatus) return;
    const ids = getSelectedAssetIds();
    if (ids.length === 0) {
        showToast('No assets selected.', 'warning');
        const sel = document.getElementById('bulk-status-select');
        if (sel) sel.value = '';
        return;
    }
    const readable = newStatus.replace(/_/g, ' ');
    if (!confirm(`Update status of ${ids.length} selected asset(s) to "${readable}"?`)) {
        const sel = document.getElementById('bulk-status-select');
        if (sel) sel.value = '';
        return;
    }
    try {
        const res = await fetch('/api/assets/bulk-status', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ ids: ids, status: newStatus })
        });
        const data = await res.json();
        showToast(`Updated status for ${data.updatedCount || ids.length} asset(s) to ${readable}!`, 'success');
        const sel = document.getElementById('bulk-status-select');
        if (sel) sel.value = '';
        deselectAllAssets();
        await loadAssets();
        if (typeof loadDashboardSummary === 'function') loadDashboardSummary();
        if (typeof loadActivityLogs === 'function') loadActivityLogs();
    } catch (err) {
        console.error('Bulk status change error:', err);
        showToast('Failed to update status: ' + err.message, 'error');
        const sel = document.getElementById('bulk-status-select');
        if (sel) sel.value = '';
    }
}

async function bulkDeleteSelectedAssets() {
    const ids = getSelectedAssetIds();
    if (ids.length === 0) {
        showToast('No assets selected.', 'warning');
        return;
    }
    if (!confirm(`⚠️ DANGER: Are you sure you want to permanently DELETE ${ids.length} selected asset(s)?\nThis action cannot be undone.`)) {
        return;
    }
    try {
        const res = await fetch('/api/assets/bulk-delete', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(ids)
        });
        const data = await res.json();
        showToast(`Deleted ${data.deletedCount || ids.length} asset(s).`, 'success');
        deselectAllAssets();
        await loadAssets();
        if (typeof loadDashboardSummary === 'function') loadDashboardSummary();
        if (typeof loadActivityLogs === 'function') loadActivityLogs();
    } catch (err) {
        console.error('Bulk delete error:', err);
        showToast('Failed to delete assets: ' + err.message, 'error');
    }
}

function bulkExportSelectedAssets() {
    const ids = getSelectedAssetIds();
    if (ids.length === 0) {
        showToast('No assets selected to export.', 'warning');
        return;
    }
    const selectedAssets = (App.cachedAssets || []).filter(a => ids.includes(a.id));
    if (selectedAssets.length === 0) {
        showToast('No asset data found to export.', 'warning');
        return;
    }

    const headers = ['Asset Tag', 'Name', 'Category', 'Manufacturer', 'Model', 'Serial Number', 'Status', 'Condition', 'Assigned Employee', 'Assigned Email', 'Department', 'Location', 'Purchase Cost', 'Purchase Date', 'Warranty Expiry', 'Notes'];
    const rows = selectedAssets.map(a => [
        `"${(a.assetTag || '').replace(/"/g, '""')}"`,
        `"${(a.name || '').replace(/"/g, '""')}"`,
        `"${(a.category || '').replace(/"/g, '""')}"`,
        `"${(a.manufacturer || '').replace(/"/g, '""')}"`,
        `"${(a.model || '').replace(/"/g, '""')}"`,
        `"${(a.serialNumber || '').replace(/"/g, '""')}"`,
        `"${(a.status || '').replace(/"/g, '""')}"`,
        `"${(a.condition || '').replace(/"/g, '""')}"`,
        `"${(a.assignedEmployeeName || '').replace(/"/g, '""')}"`,
        `"${(a.assignedEmployeeEmail || '').replace(/"/g, '""')}"`,
        `"${(a.assignedDepartment || '').replace(/"/g, '""')}"`,
        `"${(a.location || '').replace(/"/g, '""')}"`,
        `"${a.purchaseCost != null ? a.purchaseCost : ''}"`,
        `"${(a.purchaseDate || '')}"`,
        `"${(a.warrantyExpiryDate || '')}"`,
        `"${(a.notes || '').replace(/"/g, '""')}"`
    ]);

    const csvContent = 'data:text/csv;charset=utf-8,' + [headers.join(','), ...rows.map(r => r.join(','))].join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `selected_it_assets_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showToast(`Exported ${selectedAssets.length} asset(s) to CSV!`, 'success');
}

function cloneAsset(id) {
    const src = (App.cachedAssets || []).find(a => a.id === id);
    if (!src) return;
    openAssetModal();
    const title = document.getElementById('asset-modal-title');
    if (title) title.textContent = `Clone Asset Template (from ${src.assetTag})`;

    document.getElementById('asset-id').value = '';
    const tagInput = document.getElementById('asset-tag');
    if (tagInput) {
        tagInput.value = '';
        tagInput.placeholder = 'e.g. AST-' + Math.floor(1000 + Math.random() * 9000);
    }
    const serialInput = document.getElementById('asset-serial');
    if (serialInput) {
        serialInput.value = '';
        serialInput.placeholder = 'Enter new device serial number';
    }

    if (document.getElementById('asset-name')) document.getElementById('asset-name').value = src.name || '';
    if (document.getElementById('asset-category')) document.getElementById('asset-category').value = src.category || 'LAPTOP';
    if (document.getElementById('asset-manufacturer')) document.getElementById('asset-manufacturer').value = src.manufacturer || '';
    if (document.getElementById('asset-model')) document.getElementById('asset-model').value = src.model || '';
    if (document.getElementById('asset-status')) document.getElementById('asset-status').value = 'AVAILABLE';
    if (document.getElementById('asset-condition')) document.getElementById('asset-condition').value = src.condition || 'NEW';
    if (document.getElementById('asset-cost')) document.getElementById('asset-cost').value = src.purchaseCost != null ? src.purchaseCost : '';
    if (document.getElementById('asset-location')) document.getElementById('asset-location').value = src.location || '';
    if (document.getElementById('asset-specs')) document.getElementById('asset-specs').value = src.specifications || '';
    if (document.getElementById('asset-notes')) document.getElementById('asset-notes').value = `Cloned from ${src.assetTag}. ` + (src.notes || '');

    showToast(`Cloned specifications from ${src.assetTag}. Provide a new Tag & Serial.`, 'info');
}

// -------------------------------------------------------
// ASSET CRUD
// -------------------------------------------------------
function openAssetModal() {
    document.getElementById('asset-modal-title').textContent = 'Register New Asset';
    document.getElementById('asset-form').reset();
    document.getElementById('asset-id').value = '';
    document.getElementById('asset-modal').style.display = 'flex';
}

function openEditAssetModal(assetId) {
    const asset = App.cachedAssets.find(a => a.id === assetId);
    if (!asset) return;

    document.getElementById('asset-modal-title').textContent = `Edit Asset (${asset.assetTag})`;
    document.getElementById('asset-id').value = asset.id;
    document.getElementById('asset-tag').value = asset.assetTag;
    document.getElementById('asset-name').value = asset.name;
    document.getElementById('asset-category').value = asset.category;
    document.getElementById('asset-manufacturer').value = asset.manufacturer || '';
    document.getElementById('asset-model').value = asset.model || '';
    document.getElementById('asset-serial').value = asset.serialNumber || '';
    document.getElementById('asset-status').value = asset.status;
    document.getElementById('asset-condition').value = asset.condition;
    document.getElementById('asset-cost').value = asset.purchaseCost || '';
    document.getElementById('asset-purchase-date').value = asset.purchaseDate || '';
    document.getElementById('asset-warranty-date').value = asset.warrantyExpiryDate || '';
    document.getElementById('asset-location').value = asset.location || '';
    document.getElementById('asset-specs').value = asset.specifications || '';
    document.getElementById('asset-notes').value = asset.notes || '';
    document.getElementById('asset-modal').style.display = 'flex';
}

async function submitAsset(event) {
    event.preventDefault();
    const id = document.getElementById('asset-id').value;
    const payload = {
        assetTag: document.getElementById('asset-tag').value.trim(),
        name: document.getElementById('asset-name').value.trim(),
        category: document.getElementById('asset-category').value,
        manufacturer: document.getElementById('asset-manufacturer').value.trim() || null,
        model: document.getElementById('asset-model').value.trim() || null,
        serialNumber: document.getElementById('asset-serial').value.trim() || null,
        status: document.getElementById('asset-status').value,
        condition: document.getElementById('asset-condition').value,
        purchaseCost: document.getElementById('asset-cost').value ? parseFloat(document.getElementById('asset-cost').value) : null,
        purchaseDate: document.getElementById('asset-purchase-date').value || null,
        warrantyExpiryDate: document.getElementById('asset-warranty-date').value || null,
        location: document.getElementById('asset-location').value.trim() || null,
        specifications: document.getElementById('asset-specs').value.trim() || null,
        notes: document.getElementById('asset-notes').value.trim() || null
    };

    const isEdit = Boolean(id);
    try {
        const res = await fetch(isEdit ? `/api/assets/${id}` : '/api/assets', {
            method: isEdit ? 'PUT' : 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (!res.ok) {
            const e = await res.json().catch(() => ({}));
            throw new Error(e.message || 'Error saving asset');
        }
        closeModal('asset-modal');
        showToast(isEdit ? 'Asset updated' : 'Asset created', 'success');
        await refreshAll();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function deleteAsset(id) {
    const asset = App.cachedAssets.find(a => a.id === id);
    if (!confirm(`Delete ${asset?.assetTag || 'this asset'}? This cannot be undone.`)) return;
    try {
        await fetch(`/api/assets/${id}`, { method: 'DELETE' });
        showToast('Asset deleted', 'success');
        await refreshAll();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// -------------------------------------------------------
// ASSIGN / UNASSIGN
// -------------------------------------------------------
async function openAssignModal(assetId, preselectedUser) {
    document.getElementById('assign-form').reset();
    document.getElementById('assign-asset-id').value = assetId || '';
    document.getElementById('assign-date').valueAsDate = new Date();

    const pickerGroup = document.getElementById('assign-asset-picker-group');
    const assetPicker = document.getElementById('assign-asset-picker');

    if (!assetId) {
        if (pickerGroup) pickerGroup.style.display = 'block';
        if (assetPicker) {
            assetPicker.required = true;
            assetPicker.innerHTML = '<option value="">-- Choose Available Equipment or Accessory --</option>';
            const available = (App.cachedAssets || []).filter(a => a.status === 'AVAILABLE');
            if (available.length === 0) {
                assetPicker.innerHTML = '<option value="">No hardware/accessories currently available in stock</option>';
            } else {
                available.forEach(a => {
                    const opt = document.createElement('option');
                    opt.value = a.id;
                    opt.textContent = `${a.assetTag} — ${a.name} (${a.category}) [${a.condition || 'Good'}]`;
                    assetPicker.appendChild(opt);
                });
            }
        }
    } else {
        if (pickerGroup) pickerGroup.style.display = 'none';
        if (assetPicker) assetPicker.required = false;
    }

    // Populate user dropdown
    const sel = document.getElementById('assign-user-select');
    sel.innerHTML = '<option value="">-- Choose from Registered Users --</option>';
    const users = App.cachedUsers.length ? App.cachedUsers : await loadUsersRaw();
    users.forEach(u => {
        const opt = document.createElement('option');
        opt.value = JSON.stringify({ email: u.email, fullName: u.fullName, department: u.department });
        opt.textContent = `${u.fullName} (${u.email}) [${u.role}]`;
        sel.appendChild(opt);
    });

    if (preselectedUser) {
        document.getElementById('assign-emp-name').value = preselectedUser.fullName || '';
        document.getElementById('assign-emp-email').value = preselectedUser.email || '';
        document.getElementById('assign-emp-dept').value = preselectedUser.department || '';
        for (let i = 0; i < sel.options.length; i++) {
            if (sel.options[i].text.includes(preselectedUser.email)) {
                sel.selectedIndex = i;
                break;
            }
        }
    }

    document.getElementById('assign-modal').style.display = 'flex';
}

function onSelectAssetForAssignment() {
    const picker = document.getElementById('assign-asset-picker');
    if (picker) {
        document.getElementById('assign-asset-id').value = picker.value;
    }
}

function onSelectUserForAssignment() {
    const val = document.getElementById('assign-user-select').value;
    if (!val) return;
    const user = JSON.parse(val);
    document.getElementById('assign-emp-name').value = user.fullName;
    document.getElementById('assign-emp-email').value = user.email;
    document.getElementById('assign-emp-dept').value = user.department || '';
}

async function submitAssignAsset(event) {
    event.preventDefault();
    const id = document.getElementById('assign-asset-id').value;
    if (!id) {
        showToast('Please select a hardware asset or accessory to assign', 'error');
        return;
    }
    const payload = {
        employeeName: document.getElementById('assign-emp-name').value.trim(),
        employeeEmail: document.getElementById('assign-emp-email').value.trim(),
        department: document.getElementById('assign-emp-dept').value.trim(),
        assignedDate: document.getElementById('assign-date').value || null
    };
    try {
        const res = await fetch(`/api/assets/${id}/assign`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (!res.ok) throw new Error('Failed to assign');
        closeModal('assign-modal');
        showToast('Asset assigned & email notification dispatched ✓', 'success');
        await refreshAll();
        if (currentDetailUserId) {
            await openUserDetailsModal(currentDetailUserId);
        }
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function unassignAsset(id) {
    if (!confirm('Unassign and return to available stock?')) return;
    try {
        await fetch(`/api/assets/${id}/unassign`, { method: 'PATCH' });
        showToast('Asset returned to inventory', 'success');
        await refreshAll();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// -------------------------------------------------------
// MAINTENANCE LOGS
// -------------------------------------------------------
function openMaintenanceModal(assetId) {
    document.getElementById('maintenance-form').reset();
    document.getElementById('maint-asset-id').value = assetId;
    document.getElementById('maint-date').valueAsDate = new Date();
    document.getElementById('maintenance-modal').style.display = 'flex';
}

async function submitMaintenance(event) {
    event.preventDefault();
    const assetId = document.getElementById('maint-asset-id').value;
    const payload = {
        assetId: parseInt(assetId),
        title: document.getElementById('maint-title').value.trim(),
        serviceProvider: document.getElementById('maint-provider').value.trim() || null,
        cost: document.getElementById('maint-cost').value ? parseFloat(document.getElementById('maint-cost').value) : null,
        serviceDate: document.getElementById('maint-date').value || null,
        status: document.getElementById('maint-status').value,
        description: document.getElementById('maint-desc').value.trim() || null
    };
    try {
        const res = await fetch(`/api/assets/${assetId}/maintenance`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (!res.ok) throw new Error('Failed to save maintenance record');
        closeModal('maintenance-modal');
        showToast('Maintenance record saved', 'success');
        await refreshAll();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function loadMaintenanceLogs() {
    try {
        const res = await fetch('/api/maintenance');
        if (!res.ok) return;
        const logs = await res.json();
        const tbody = document.getElementById('maintenance-table-body');
        if (!tbody) return;

        if (logs.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;padding:40px;color:var(--text-muted)">No maintenance records yet.</td></tr>';
            return;
        }
        tbody.innerHTML = logs.map(m => `
            <tr>
                <td><span style="font-family:var(--font-mono);font-size:0.8rem">${m.serviceDate || 'N/A'}</span></td>
                <td><span class="asset-tag-badge">${escapeHtml(m.assetTag || m.asset?.assetTag || 'Asset')}</span></td>
                <td><strong>${escapeHtml(m.title)}</strong>
                    ${m.description ? `<div style="font-size:0.75rem;color:var(--text-muted);margin-top:3px">${escapeHtml(m.description.substring(0,80))}${m.description.length > 80 ? '…' : ''}</div>` : ''}
                </td>
                <td>${escapeHtml(m.serviceProvider || 'Internal IT')}</td>
                <td><span style="font-family:var(--font-mono)">${m.cost != null ? '$' + Number(m.cost).toFixed(2) : '–'}</span></td>
                <td><span class="pill pill-${(m.status||'').toLowerCase().replace(/_/g,'-')}"><span class="pill-dot"></span>${m.status}</span></td>
                <td style="text-align:right">
                    <button class="btn-table-action danger" onclick="deleteMaintenance(${m.id})" title="Delete">&times;</button>
                </td>
            </tr>`).join('');
    } catch (err) {
        console.error('Error loading maintenance:', err);
    }
}

async function deleteMaintenance(id) {
    if (!confirm('Delete this maintenance record?')) return;
    try {
        await fetch(`/api/maintenance/${id}`, { method: 'DELETE' });
        showToast('Maintenance record removed', 'success');
        loadMaintenanceLogs();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// -------------------------------------------------------
// SOFTWARE LICENSES
// -------------------------------------------------------
async function loadLicenses() {
    try {
        const res = await fetch('/api/licenses');
        if (!res.ok) return;
        const licenses = await res.json();
        App.cachedLicenses = licenses;
        const navBadge = document.getElementById('nav-count-licenses');
        if (navBadge) navBadge.innerText = licenses.length;
        filterLicensesList();
    } catch (err) {
        console.error('Error loading licenses:', err);
    }
}

function filterLicensesList() {
    const query = (document.getElementById('license-search-input')?.value || '').toLowerCase().trim();
    const typeFilter = document.getElementById('filter-license-type')?.value || '';

    let filtered = App.cachedLicenses || [];
    if (query) {
        filtered = filtered.filter(l =>
            (l.softwareName && l.softwareName.toLowerCase().includes(query)) ||
            (l.vendor && l.vendor.toLowerCase().includes(query)) ||
            (l.licenseKey && l.licenseKey.toLowerCase().includes(query)) ||
            (l.department && l.department.toLowerCase().includes(query))
        );
    }
    if (typeFilter) {
        filtered = filtered.filter(l => l.licenseType === typeFilter);
    }
    renderLicenseCards('licenses-grid', filtered);
}

function resetLicenseFilters() {
    if (document.getElementById('license-search-input')) document.getElementById('license-search-input').value = '';
    if (document.getElementById('filter-license-type')) document.getElementById('filter-license-type').value = '';
    filterLicensesList();
}

function renderLicenseCards(containerId, licenses) {
    const container = document.getElementById(containerId);
    if (!container) return;

    if (!licenses || licenses.length === 0) {
        container.innerHTML = '<div class="empty-state" style="grid-column:1/-1"><h3>No Software Licenses</h3><p>Add a license to begin tracking seats and subscriptions.</p></div>';
        return;
    }

    container.innerHTML = licenses.map(l => {
        const used = l.usedSeats || 0;
        const total = l.totalSeats || 1;
        const pct = Math.min(100, Math.round((used / total) * 100));
        const barColor = pct > 90 ? '#f43f5e' : pct > 75 ? '#f59e0b' : '#10b981';
        const costStr = l.annualCost ? '$' + Number(l.annualCost).toLocaleString('en-US') + '/yr' : '–';

        return `<div class="license-card">
            <div class="license-header">
                <div>
                    <div class="license-title">${escapeHtml(l.softwareName)}</div>
                    <div class="license-vendor">${escapeHtml(l.vendor || '')} · ${l.licenseType}</div>
                </div>
                <span class="category-tag">${escapeHtml(l.department || 'All')}</span>
            </div>
            <div style="margin: 12px 0 8px">
                <div style="display:flex;justify-content:space-between;font-size:0.8rem;margin-bottom:6px">
                    <span class="text-secondary">Seat Utilization</span>
                    <strong>${used} / ${total} <span style="color:var(--text-muted)">(${pct}%)</span></strong>
                </div>
                <div class="seat-progress-bar">
                    <div class="seat-progress-fill" style="width:${pct}%;background:${barColor}"></div>
                </div>
            </div>
            <div class="license-meta-row">
                <div>Cost: <strong style="color:#34d399">${costStr}</strong></div>
                <div>Expires: <strong>${l.expiryDate || 'Perpetual'}</strong></div>
            </div>
        </div>`;
    }).join('');
}

function openLicenseModal() {
    document.getElementById('license-form').reset();
    document.getElementById('license-modal').style.display = 'flex';
}

async function submitLicense(event) {
    event.preventDefault();
    const payload = {
        softwareName: document.getElementById('lic-name').value.trim(),
        vendor: document.getElementById('lic-vendor').value.trim() || null,
        licenseType: document.getElementById('lic-type').value,
        totalSeats: parseInt(document.getElementById('lic-seats').value),
        usedSeats: parseInt(document.getElementById('lic-used').value || 0),
        annualCost: document.getElementById('lic-cost').value ? parseFloat(document.getElementById('lic-cost').value) : null,
        expiryDate: document.getElementById('lic-expiry').value || null,
        department: document.getElementById('lic-dept').value.trim() || null,
        licenseKey: document.getElementById('lic-key').value.trim() || null
    };
    try {
        const res = await fetch('/api/licenses', {
            method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
        });
        if (!res.ok) throw new Error('Failed to add license');
        closeModal('license-modal');
        showToast('License added', 'success');
        await loadLicenses();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// -------------------------------------------------------
// USER MANAGEMENT (Admin)
// -------------------------------------------------------
async function loadUsersRaw() {
    try {
        const res = await fetch('/api/users');
        if (!res.ok) return [];
        const users = await res.json();
        App.cachedUsers = users;
        return users;
    } catch { return []; }
}

async function loadUsers() {
    const users = await loadUsersRaw();
    renderUsersTable(users);
    updateUserStats(users);

    const badge = document.getElementById('nav-count-users');
    if (badge) badge.textContent = users.length;
}

function updateUserStats(users) {
    const total = users.length;
    const employees = users.filter(u => u.role === 'EMPLOYEE').length;
    const withAssets = users.filter(u => u.assignedAssetsCount > 0).length;
    const admins = users.filter(u => u.role === 'ADMIN').length;

    const set = (id, val) => { const el = document.getElementById(id); if (el) el.textContent = val; };
    set('stat-total-users', total);
    set('stat-active-employees', employees);
    set('stat-hardware-holders', withAssets);
    set('stat-admin-count', admins);
}

function renderUsersTable(users) {
    const tbody = document.getElementById('users-table-body');
    if (!tbody) return;

    if (!users || users.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;padding:40px;color:var(--text-muted)">No users registered yet. Add your first team member above.</td></tr>';
        return;
    }

    const query = (document.getElementById('user-search-input')?.value || '').toLowerCase();
    const roleFilter = document.getElementById('filter-user-role')?.value || '';
    const deptFilter = document.getElementById('filter-user-dept')?.value || '';

    const filtered = users.filter(u => {
        const matchQ = !query || u.fullName.toLowerCase().includes(query) || u.email.toLowerCase().includes(query) || (u.jobTitle || '').toLowerCase().includes(query);
        const matchR = !roleFilter || u.role === roleFilter;
        const matchD = !deptFilter || (u.department || '').toLowerCase() === deptFilter.toLowerCase();
        return matchQ && matchR && matchD;
    });

    tbody.innerHTML = filtered.map(u => {
        const initials = u.fullName.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase();
        const isAdmin = u.role === 'ADMIN';
        const assignedHtml = u.assignedAssetsCount > 0
            ? `<span class="nav-badge" style="font-size:0.72rem;background:rgba(56,189,248,0.15);color:var(--status-blue);border:1px solid rgba(56,189,248,0.3)">${u.assignedAssetsCount} items</span>`
            : '<span class="text-muted">None</span>';

        return `<tr class="clickable-user-row" onclick="openUserDetailsModal(${u.id})" title="Click to view full user profile & equipment">
            <td>
                <div class="employee-cell">
                    <div class="avatar-initial avatar-${isAdmin ? 'admin' : 'emp'}">${initials}</div>
                    <div>
                        <div style="font-weight:600;display:flex;align-items:center;gap:6px">
                            <span>${escapeHtml(u.fullName)}</span>
                            <span class="user-click-hint" style="font-size:0.7rem;color:var(--status-blue);font-weight:400">View 360° &rarr;</span>
                        </div>
                        <div style="font-size:0.75rem;color:var(--text-muted)">${escapeHtml(u.jobTitle || '–')}</div>
                    </div>
                </div>
            </td>
            <td><code style="font-size:0.8rem;color:var(--status-blue)">${escapeHtml(u.email)}</code></td>
            <td>
                <div style="font-weight:500">${escapeHtml(u.department || '–')}</div>
                ${u.phoneNumber ? `<div style="font-size:0.75rem;color:var(--text-muted)">${escapeHtml(u.phoneNumber)}</div>` : ''}
            </td>
            <td><span class="role-pill ${isAdmin ? 'pill-admin' : 'pill-employee'}">${u.role}</span></td>
            <td>${assignedHtml}
                ${u.assignedAssetTags && u.assignedAssetTags.length > 0
                    ? `<div style="font-size:0.72rem;color:var(--text-muted);margin-top:3px">${u.assignedAssetTags.map(t => `<span class="asset-tag-badge" style="font-size:0.68rem;padding:2px 6px;margin:1px">${escapeHtml(t.split('(')[0].trim())}</span>`).join('')}</div>`
                    : ''
                }
            </td>
            <td><span class="pill pill-${(u.status||'active').toLowerCase()}"><span class="pill-dot"></span>${u.status || 'ACTIVE'}</span></td>
            <td style="text-align:right" onclick="event.stopPropagation()">
                <div class="action-btn-group">
                    <button class="btn-table-action" onclick="openUserDetailsModal(${u.id})" style="background:rgba(56,189,248,0.12);color:var(--status-blue);border-color:rgba(56,189,248,0.3);font-weight:600" title="View details, hardware & accessories">View Details</button>
                    <button class="btn-table-action" onclick="resendWelcomeEmail(${u.id})" title="Resend onboarding email">Resend Mail</button>
                    <button class="btn-table-action danger" onclick="deleteUser(${u.id})" title="Remove user">&times;</button>
                </div>
            </td>
        </tr>`;
    }).join('');
}

function filterUsersList() {
    renderUsersTable(App.cachedUsers);
}

function resetUserFilters() {
    if (document.getElementById('user-search-input')) document.getElementById('user-search-input').value = '';
    if (document.getElementById('filter-user-role')) document.getElementById('filter-user-role').value = '';
    if (document.getElementById('filter-user-dept')) document.getElementById('filter-user-dept').value = '';
    renderUsersTable(App.cachedUsers);
}

function openAddUserModal() {
    document.getElementById('add-user-form').reset();
    document.getElementById('add-user-modal').style.display = 'flex';
}

function generateRandomPassField() {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ0123456789';
    let code = '';
    for (let i = 0; i < 4; i++) code += chars[Math.floor(Math.random() * chars.length)];
    document.getElementById('user-init-password').value = `Aegis#${code}`;
}

async function submitAddUser(event) {
    event.preventDefault();
    const btn = document.getElementById('btn-submit-add-user');
    btn.disabled = true;
    btn.textContent = 'Creating...';

    const payload = {
        fullName: document.getElementById('user-full-name').value.trim(),
        email: document.getElementById('user-email').value.trim(),
        department: document.getElementById('user-dept').value,
        jobTitle: document.getElementById('user-title').value.trim() || null,
        phoneNumber: document.getElementById('user-phone').value.trim() || null,
        role: document.getElementById('user-role').value,
        initialPassword: document.getElementById('user-init-password').value.trim() || null,
        sendWelcomeEmail: document.getElementById('user-send-email').checked
    };

    try {
        const res = await fetch('/api/users', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await res.json();

        if (!res.ok) {
            throw new Error(data.message || 'Failed to create user');
        }

        App.lastCreatedUser = data;
        closeModal('add-user-modal');

        // Show success modal with credentials
        document.getElementById('succ-email').textContent = data.email;
        document.getElementById('succ-password').textContent = data.temporaryPassword || '(auto-generated)';
        document.getElementById('succ-link').textContent = `${window.location.protocol}//${window.location.host}/`;
        document.getElementById('user-success-modal').style.display = 'flex';

        await loadUsers();
        await loadEmails();
        loadDashboardSummary();

    } catch (err) {
        showToast(err.message, 'error');
    } finally {
        btn.disabled = false;
        btn.innerHTML = 'Create User &amp; Dispatch Mail &rarr;';
    }
}

async function deleteUser(id) {
    const user = App.cachedUsers.find(u => u.id === id);
    if (!confirm(`Remove ${user?.fullName || 'this user'} from the system? Their assigned assets will be released.`)) return;
    try {
        await fetch(`/api/users/${id}`, { method: 'DELETE' });
        showToast('User removed and assets released', 'success');
        await loadUsers();
        await refreshAll();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function resendWelcomeEmail(id) {
    try {
        const res = await fetch(`/api/users/${id}/resend-welcome`, { method: 'POST' });
        if (!res.ok) throw new Error('Failed to resend');
        showToast('Welcome email re-dispatched with new password ✓', 'success');
        await loadUsers();
        await loadEmails();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

function copyText(elementId) {
    const el = document.getElementById(elementId);
    navigator.clipboard.writeText(el.textContent).then(() => showToast('Copied to clipboard!', 'success'));
}

function viewSentEmailForUser() {
    closeModal('user-success-modal');
    switchTab('emails');
}

// -------------------------------------------------------
// EMAIL OUTBOX & SMTP MANAGEMENT
// -------------------------------------------------------
async function loadEmails() {
    try {
        const res = await fetch('/api/emails');
        if (!res.ok) return;
        const emails = await res.json();
        App.cachedEmails = emails;

        const badge = document.getElementById('nav-count-emails');
        if (badge) badge.textContent = emails.length;

        filterEmailsList();
        await checkSmtpStatus();
    } catch (err) {
        console.error('Error loading emails:', err);
    }
}

function filterEmailsList() {
    const query = (document.getElementById('email-search-input')?.value || '').toLowerCase().trim();
    const statusFilter = document.getElementById('filter-email-status')?.value || '';

    let filtered = App.cachedEmails || [];
    if (query) {
        filtered = filtered.filter(e =>
            (e.recipientEmail && e.recipientEmail.toLowerCase().includes(query)) ||
            (e.recipientName && e.recipientName.toLowerCase().includes(query)) ||
            (e.subject && e.subject.toLowerCase().includes(query)) ||
            (e.emailType && e.emailType.toLowerCase().includes(query))
        );
    }
    if (statusFilter) {
        if (statusFilter === 'DELIVERED_TO_OUTBOX') {
            filtered = filtered.filter(e => e.status === 'DELIVERED_TO_OUTBOX' || e.status === 'DELIVERED_TO_INBOX');
        } else if (statusFilter === 'SMTP_SENT') {
            filtered = filtered.filter(e => e.status && e.status.includes('SMTP'));
        } else if (statusFilter === 'PENDING') {
            filtered = filtered.filter(e => e.status === 'PENDING');
        }
    }
    renderEmailsTable(filtered);
}

function resetEmailFilters() {
    if (document.getElementById('email-search-input')) document.getElementById('email-search-input').value = '';
    if (document.getElementById('filter-email-status')) document.getElementById('filter-email-status').value = '';
    renderEmailsTable(App.cachedEmails || []);
}

async function checkSmtpStatus() {
    const bannerText = document.getElementById('smtp-banner-text');
    if (!bannerText) return;
    try {
        const res = await fetch('/api/smtp/settings');
        if (!res.ok) return;
        const s = await res.json();
        if (s.enabled) {
            bannerText.innerHTML = `
                <span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:#10b981;box-shadow:0 0 8px #10b981;"></span>
                <span style="color:#f8fafc;font-weight:500;">
                    Live SMTP Delivery Active &bull; <strong style="color:#38bdf8">${escapeHtml(s.host)}:${s.port}</strong> &bull; From: <code>${escapeHtml(s.fromEmail || s.username)}</code>
                </span>`;
        } else {
            bannerText.innerHTML = `
                <span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:#f59e0b;"></span>
                <span style="color:#cbd5e1;">
                    Live SMTP Delivery is currently <strong>Inactive</strong> (Emails are stored locally in this Outbox). Click <em>Configure Live SMTP</em> to connect Gmail, Outlook, or corporate mail servers.
                </span>`;
        }
    } catch (e) {
        console.warn('Could not check SMTP status:', e);
    }
}

function renderEmailsTable(emails) {
    const tbody = document.getElementById('emails-table-body');
    if (!tbody) return;

    if (!emails || emails.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:40px;color:var(--text-muted)">No emails dispatched yet.</td></tr>';
        return;
    }

    tbody.innerHTML = emails.map(e => {
        const typeBadge = e.emailType === 'WELCOME_ONBOARDING'
            ? '<span class="role-pill pill-admin" style="font-size:0.7rem">ONBOARDING</span>'
            : '<span class="role-pill pill-employee" style="font-size:0.7rem">ASSET ASSIGN</span>';
        const sentDate = e.sentAt ? new Date(e.sentAt).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' }) : '–';

        let statusBadge = '';
        if (e.status === 'DELIVERED_TO_INBOX') {
            statusBadge = '<span class="pill pill-available"><span class="pill-dot"></span>INBOX DELIVERED</span>';
        } else if (e.status && e.status.includes('SMTP')) {
            statusBadge = `<span class="pill pill-damaged" title="${escapeHtml(e.status)}"><span class="pill-dot"></span>SMTP ERROR</span>`;
        } else {
            statusBadge = '<span class="pill pill-reserved"><span class="pill-dot"></span>OUTBOX CAPTURED</span>';
        }

        return `<tr>
            <td>
                <div style="font-weight:500">${escapeHtml(e.recipientName)}</div>
                <code style="font-size:0.75rem;color:var(--status-blue)">${escapeHtml(e.recipientEmail)}</code>
            </td>
            <td>${typeBadge}</td>
            <td style="font-size:0.82rem;max-width:240px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${escapeHtml(e.subject)}</td>
            <td><span style="font-size:0.8rem;color:var(--text-secondary)">${sentDate}</span></td>
            <td>${statusBadge}</td>
            <td style="text-align:right">
                <button class="btn-table-action" onclick="previewEmail(${e.id})">Preview HTML</button>
            </td>
        </tr>`;
    }).join('');
}

async function previewEmail(id) {
    try {
        const res = await fetch(`/api/emails/${id}`);
        if (!res.ok) return;
        const email = await res.json();

        document.getElementById('view-email-subject').textContent = email.subject || 'Email Preview';
        document.getElementById('view-email-meta').textContent = `To: ${email.recipientEmail} • Sent: ${email.sentAt ? new Date(email.sentAt).toLocaleString() : 'Unknown'}`;

        const previewDiv = document.getElementById('email-preview-body');
        if (email.content) {
            previewDiv.innerHTML = '';
            const iframe = document.createElement('iframe');
            iframe.style.width = '100%';
            iframe.style.height = '480px';
            iframe.style.border = 'none';
            iframe.style.borderRadius = '8px';
            iframe.style.background = '#0f172a';
            previewDiv.appendChild(iframe);
            iframe.srcdoc = email.content;
        } else {
            previewDiv.innerHTML = '<p class="text-muted" style="padding:20px;text-align:center">No HTML content available.</p>';
        }

        document.getElementById('view-email-modal').style.display = 'flex';
    } catch (err) {
        showToast('Could not load email preview', 'error');
    }
}

// -------------------------------------------------------
// REAL SMTP SETTINGS & TESTING
// -------------------------------------------------------
async function openSmtpModal() {
    const modal = document.getElementById('smtp-modal');
    if (!modal) return;
    try {
        const res = await fetch('/api/smtp/settings');
        if (res.ok) {
            const s = await res.json();
            document.getElementById('smtp-enabled').checked = !!s.enabled;
            document.getElementById('smtp-host').value = s.host || 'smtp.gmail.com';
            document.getElementById('smtp-port').value = s.port || 587;
            document.getElementById('smtp-username').value = s.username || '';
            document.getElementById('smtp-password').value = s.password || '';
            document.getElementById('smtp-from-email').value = s.fromEmail || '';
            document.getElementById('smtp-from-name').value = s.fromName || 'Aegis IT Asset Management';
            document.getElementById('smtp-starttls').checked = s.startTls !== false;
            document.getElementById('smtp-ssl').checked = !!s.ssl;
        }
    } catch (e) {
        console.error('Failed to load SMTP settings:', e);
    }
    const testStatus = document.getElementById('smtp-test-status');
    if (testStatus) testStatus.style.display = 'none';
    modal.style.display = 'flex';
}

function applySmtpPreset(provider) {
    const hostEl = document.getElementById('smtp-host');
    const portEl = document.getElementById('smtp-port');
    const starttlsEl = document.getElementById('smtp-starttls');
    const sslEl = document.getElementById('smtp-ssl');

    if (provider === 'gmail') {
        hostEl.value = 'smtp.gmail.com';
        portEl.value = '587';
        starttlsEl.checked = true;
        sslEl.checked = false;
        showToast('Applied Google Workspace / Gmail presets (Port 587 TLS)', 'info');
    } else if (provider === 'office365') {
        hostEl.value = 'smtp.office365.com';
        portEl.value = '587';
        starttlsEl.checked = true;
        sslEl.checked = false;
        showToast('Applied Microsoft 365 / Outlook presets (Port 587 TLS)', 'info');
    } else if (provider === 'brevo') {
        hostEl.value = 'smtp-relay.brevo.com';
        portEl.value = '587';
        starttlsEl.checked = true;
        sslEl.checked = false;
        showToast('Applied Brevo SMTP presets', 'info');
    } else {
        hostEl.value = '';
        portEl.value = '587';
        starttlsEl.checked = true;
        sslEl.checked = false;
    }
}

async function submitSmtpSettings(e) {
    e.preventDefault();
    const btn = document.getElementById('btn-save-smtp');
    btn.disabled = true;
    btn.textContent = 'Saving...';

    const payload = {
        enabled: document.getElementById('smtp-enabled').checked,
        host: document.getElementById('smtp-host').value.trim(),
        port: parseInt(document.getElementById('smtp-port').value, 10) || 587,
        username: document.getElementById('smtp-username').value.trim(),
        password: document.getElementById('smtp-password').value,
        fromEmail: document.getElementById('smtp-from-email').value.trim(),
        fromName: document.getElementById('smtp-from-name').value.trim(),
        startTls: document.getElementById('smtp-starttls').checked,
        ssl: document.getElementById('smtp-ssl').checked,
        auth: true
    };

    try {
        const res = await fetch('/api/smtp/settings', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.message || 'Failed to save SMTP settings');

        showToast('SMTP settings saved successfully!', 'success');
        closeModal('smtp-modal');
        await checkSmtpStatus();
    } catch (err) {
        showToast(err.message, 'error');
    } finally {
        btn.disabled = false;
        btn.textContent = 'Save Configuration';
    }
}

async function runSmtpTest() {
    const recipientInput = document.getElementById('smtp-test-recipient');
    const recipient = recipientInput.value.trim();
    const statusDiv = document.getElementById('smtp-test-status');
    const btn = document.getElementById('btn-test-smtp');

    if (!recipient) {
        showToast('Please enter a recipient email address for testing', 'error');
        recipientInput.focus();
        return;
    }

    btn.disabled = true;
    btn.textContent = 'Sending Test...';
    statusDiv.style.display = 'block';
    statusDiv.style.color = '#38bdf8';
    statusDiv.textContent = `Connecting to SMTP server and transmitting test email to ${recipient}...`;

    try {
        const res = await fetch('/api/smtp/test', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ recipientEmail: recipient })
        });
        const data = await res.json();

        if (res.ok && data.success) {
            statusDiv.style.color = '#10b981';
            statusDiv.innerHTML = `&check; <strong>Success:</strong> ${escapeHtml(data.message)}. Check ${escapeHtml(recipient)} inbox!`;
            showToast('Test email delivered successfully!', 'success');
            await loadEmails();
        } else {
            statusDiv.style.color = '#ef4444';
            statusDiv.innerHTML = `&#10008; <strong>Failed:</strong> ${escapeHtml(data.message || 'SMTP connection failed')}`;
            showToast('SMTP test failed - verify credentials', 'error');
        }
    } catch (err) {
        statusDiv.style.color = '#ef4444';
        statusDiv.innerHTML = `&#10008; <strong>Error:</strong> ${escapeHtml(err.message)}`;
        showToast('SMTP test error: ' + err.message, 'error');
    } finally {
        btn.disabled = false;
        btn.textContent = 'Send Test Email';
    }
}

// -------------------------------------------------------
// EMPLOYEE PORTAL
// -------------------------------------------------------
async function loadEmployeePortal(email) {
    if (!email) return;
    try {
        const res = await fetch(`/api/my/overview?email=${encodeURIComponent(email)}`);
        if (!res.ok) return;
        const data = await res.json();
        renderEmployeePortal(data, email);
    } catch (err) {
        console.error('Error loading employee portal:', err);
    }
}

function renderEmployeePortal(data, email) {
    const profile = data.profile || {};
    const assets = data.assignedAssets || [];
    const tickets = data.serviceHistory || [];

    // Hero card
    const heroName = profile.fullName || 'Employee';
    const heroInitials = heroName.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase();
    document.getElementById('emp-hero-avatar').textContent = heroInitials;
    document.getElementById('emp-hero-name').textContent = heroName;
    document.getElementById('emp-hero-title').textContent = `${profile.jobTitle || 'Team Member'} · ${profile.department || 'Organization'}`;
    document.getElementById('emp-hero-email').textContent = profile.email || email;
    document.getElementById('emp-hero-dept').textContent = `Department: ${profile.department || 'General'}`;

    // KPI metrics
    const setEl = (id, val) => { const el = document.getElementById(id); if (el) el.textContent = val; };
    setEl('emp-val-assets-count', assets.length);
    setEl('emp-val-status', assets.length > 0 ? 'Assigned & Active' : 'No Hardware Allocated');
    setEl('emp-val-tickets-count', tickets.length);
    setEl('nav-emp-count-assets', assets.length);

    // Assigned Assets cards
    const assetsContainer = document.getElementById('emp-assets-container');
    if (assetsContainer) {
        if (assets.length === 0) {
            assetsContainer.innerHTML = `
                <div class="empty-state" style="grid-column: 1 / -1; padding: 48px 24px; text-align: center; background: rgba(30, 41, 59, 0.4); border: 1px dashed rgba(255,255,255,0.12); border-radius: 12px;">
                    <div style="font-size: 32px; margin-bottom: 12px;">💻</div>
                    <h3 style="margin-bottom: 8px; color: var(--text-primary); font-size: 16px;">No Hardware Currently Assigned</h3>
                    <p style="color: var(--text-muted); max-width: 440px; margin: 0 auto; font-size: 13px; line-height: 1.6;">
                        Your IT Administrator has not allocated any equipment to your profile yet. Once an asset is assigned, its specifications, asset tag, and service actions will appear here.
                    </p>
                </div>`;
        } else {
            assetsContainer.innerHTML = assets.map(a => {
                const condBadge = `<span class="cond-badge cond-${(a.condition || '').toLowerCase()}">${a.condition || '–'}</span>`;
                return `<div class="emp-asset-card">
                    <div class="emp-asset-header">
                        <span class="asset-tag-badge">${escapeHtml(a.assetTag)}</span>
                        ${condBadge}
                    </div>
                    <div class="emp-asset-name">${escapeHtml(a.name)}</div>
                    <div class="emp-asset-model">${escapeHtml(a.manufacturer || '')} ${escapeHtml(a.model || '')}</div>
                    ${a.specifications ? `<div class="emp-asset-spec">${escapeHtml(a.specifications.substring(0, 90))}${a.specifications.length > 90 ? '…' : ''}</div>` : ''}
                    <div class="emp-asset-footer">
                        <span class="text-muted" style="font-size:0.75rem">S/N: ${escapeHtml(a.serialNumber || 'N/A')}</span>
                        <button class="btn-table-action" onclick="openEmpServiceModal(${a.id}, '${escapeHtml(a.assetTag)}', '${escapeHtml(a.name)}')" style="font-size:0.72rem">
                            🔧 Report Issue
                        </button>
                    </div>
                </div>`;
            }).join('');
        }
    }

    // Service history tickets
    const ticketsTbody = document.getElementById('emp-tickets-tbody');
    if (ticketsTbody) {
        if (tickets.length === 0) {
            ticketsTbody.innerHTML = '<tr><td colspan="5" style="text-align:center;padding:24px;color:var(--text-muted)">No service history on your equipment.</td></tr>';
        } else {
            ticketsTbody.innerHTML = tickets.map(t => {
                const assetTag = t.asset?.assetTag || t.assetTag || '–';
                return `<tr>
                    <td style="font-family:var(--font-mono);font-size:0.78rem">${t.serviceDate || '–'}</td>
                    <td><span class="asset-tag-badge" style="font-size:0.7rem">${escapeHtml(assetTag)}</span></td>
                    <td>${escapeHtml(t.title)}</td>
                    <td>${escapeHtml(t.serviceProvider || 'Internal IT')}</td>
                    <td><span class="pill pill-${(t.status || '').toLowerCase().replace(/_/g, '-')}"><span class="pill-dot"></span>${t.status}</span></td>
                </tr>`;
            }).join('');
        }
    }
}

function openEmpServiceModal(assetId, assetTag, assetName) {
    document.getElementById('emp-service-form').reset();
    document.getElementById('emp-srv-asset-id').value = assetId;
    document.getElementById('emp-srv-asset-tag-display').textContent = `${assetTag} — ${assetName}`;
    document.getElementById('emp-service-modal').style.display = 'flex';
}

async function submitEmpServiceRequest(event) {
    event.preventDefault();
    const email = App.session.email;
    const assetId = document.getElementById('emp-srv-asset-id').value;
    const payload = {
        assetId: parseInt(assetId),
        title: document.getElementById('emp-srv-title').value.trim(),
        issueDescription: document.getElementById('emp-srv-desc').value.trim() || null,
        urgency: document.getElementById('emp-srv-urgency').value,
        contactPhone: document.getElementById('emp-srv-phone').value.trim() || null
    };
    try {
        const res = await fetch(`/api/my/service-request?email=${encodeURIComponent(email)}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (!res.ok) throw new Error('Failed to submit service request');
        closeModal('emp-service-modal');
        showToast('Service request submitted to IT Operations ✓', 'success');
        await loadEmployeePortal(email);
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// -------------------------------------------------------
// PROFILE MODAL
// -------------------------------------------------------
function openProfileModal() {
    document.getElementById('user-dropdown-menu').style.display = 'none';
    const s = App.session;
    const initials = s.fullName.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase();
    document.getElementById('profile-modal-body').innerHTML = `
        <div style="display:flex;align-items:center;gap:16px;margin-bottom:20px">
            <div class="avatar-initial" style="width:56px;height:56px;font-size:1.2rem;flex-shrink:0">${initials}</div>
            <div>
                <div style="font-size:1.1rem;font-weight:700">${escapeHtml(s.fullName)}</div>
                <div style="color:var(--text-secondary);font-size:0.85rem">${escapeHtml(s.jobTitle || 'Team Member')}</div>
                <div style="margin-top:4px"><span class="role-pill ${s.role === 'ADMIN' ? 'pill-admin' : 'pill-employee'}">${s.role}</span></div>
            </div>
        </div>
        <div style="display:grid;gap:10px">
            <div class="profile-field"><span class="profile-label">Organization Email</span><code style="color:var(--status-blue)">${escapeHtml(s.email)}</code></div>
            <div class="profile-field"><span class="profile-label">Department</span><span>${escapeHtml(s.department || '–')}</span></div>
            <div class="profile-field"><span class="profile-label">Account Status</span><span class="pill pill-available" style="font-size:0.72rem"><span class="pill-dot"></span>ACTIVE</span></div>
        </div>
        <div class="alert-box alert-info" style="margin-top:16px;font-size:0.82rem">
            Use <strong>Change Password</strong> from the user menu to update your credentials after first login.
        </div>
    `;
    document.getElementById('profile-modal').style.display = 'flex';
}

// -------------------------------------------------------
// CHANGE PASSWORD MODAL
// -------------------------------------------------------
function openChangePasswordModal() {
    document.getElementById('user-dropdown-menu').style.display = 'none';
    document.getElementById('change-pwd-form').reset();
    document.getElementById('pwd-error-msg').style.display = 'none';
    document.getElementById('change-pwd-modal').style.display = 'flex';
}

async function submitChangePassword(event) {
    event.preventDefault();
    const errEl = document.getElementById('pwd-error-msg');
    errEl.style.display = 'none';

    const current = document.getElementById('pwd-current').value;
    const newPwd = document.getElementById('pwd-new').value;
    const confirm = document.getElementById('pwd-confirm').value;

    if (newPwd !== confirm) {
        errEl.textContent = 'New passwords do not match.';
        errEl.style.display = 'block';
        return;
    }

    const queryParam = App.session && App.session.id
        ? `userId=${App.session.id}`
        : `email=${encodeURIComponent((App.session && App.session.email) || '')}`;

    if (!App.session || (!App.session.id && !App.session.email)) {
        errEl.textContent = 'Session information missing. Please sign in again.';
        errEl.style.display = 'block';
        return;
    }

    try {
        const res = await fetch(`/api/auth/change-password?${queryParam}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ currentPassword: current, newPassword: newPwd })
        });
        const data = await res.json();
        if (!res.ok || !data.success) {
            errEl.textContent = data.message || 'Password change failed.';
            errEl.style.display = 'block';
            return;
        }
        closeModal('change-pwd-modal');
        showToast('Password changed successfully ✓', 'success');
    } catch (err) {
        errEl.textContent = 'Connection error. Please try again.';
        errEl.style.display = 'block';
    }
}

// -------------------------------------------------------
// ANALYTICS
// -------------------------------------------------------
function renderAnalyticsBreakdowns() {
    const summary = window.lastDashboardSummary;
    if (!summary) return;

    const catContainer = document.getElementById('analytics-categories');
    const deptContainer = document.getElementById('analytics-departments');

    if (catContainer && summary.assetsByCategory) {
        const total = Object.values(summary.assetsByCategory).reduce((a, b) => a + b, 0) || 1;
        catContainer.innerHTML = Object.entries(summary.assetsByCategory).sort(([,a],[,b]) => b - a).map(([cat, count]) => {
            const pct = Math.round((count / total) * 100);
            return `<div class="breakdown-row">
                <span class="breakdown-item-name">${cat.replace(/_/g, ' ')}</span>
                <div class="breakdown-bar-wrap">
                    <div class="breakdown-bar-fill" style="width:${pct}%"></div>
                </div>
                <span class="breakdown-item-count">${count}</span>
            </div>`;
        }).join('');
    }

    if (deptContainer && summary.assetsByDepartment) {
        const total = Object.values(summary.assetsByDepartment).reduce((a, b) => a + b, 0) || 1;
        deptContainer.innerHTML = Object.entries(summary.assetsByDepartment).sort(([,a],[,b]) => b - a).map(([dept, count]) => {
            const pct = Math.round((count / total) * 100);
            return `<div class="breakdown-row">
                <span class="breakdown-item-name">${dept}</span>
                <div class="breakdown-bar-wrap">
                    <div class="breakdown-bar-fill" style="width:${pct}%;background:var(--primary-indigo)"></div>
                </div>
                <span class="breakdown-item-count">${count}</span>
            </div>`;
        }).join('');
    }
}

// -------------------------------------------------------
// CSV EXPORT
// -------------------------------------------------------
function exportAssetsCsv() {
    if (!App.cachedAssets || App.cachedAssets.length === 0) {
        showToast('No assets to export', 'error');
        return;
    }
    const headers = ['Asset Tag', 'Name', 'Category', 'Manufacturer', 'Model', 'Serial', 'Status', 'Condition', 'Cost', 'Assigned To', 'Email', 'Department', 'Location', 'Warranty'];
    const rows = App.cachedAssets.map(a => [
        `"${a.assetTag||''}"`, `"${a.name||''}"`, `"${a.category||''}"`, `"${a.manufacturer||''}"`,
        `"${a.model||''}"`, `"${a.serialNumber||''}"`, `"${a.status||''}"`, `"${a.condition||''}"`,
        `"${a.purchaseCost||''}"`, `"${a.assignedEmployeeName||''}"`, `"${a.assignedEmployeeEmail||''}"`,
        `"${a.assignedDepartment||''}"`, `"${a.location||''}"`, `"${a.warrantyExpiryDate||''}"`
    ]);
    const csv = 'data:text/csv;charset=utf-8,' + [headers.join(','), ...rows.map(r => r.join(','))].join('\n');
    const link = document.createElement('a');
    link.setAttribute('href', encodeURI(csv));
    link.setAttribute('download', `itam_assets_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showToast('Assets exported to CSV ✓', 'success');
}

// -------------------------------------------------------
// UTILS
// -------------------------------------------------------
function closeModal(id) {
    const el = document.getElementById(id);
    if (el) el.style.display = 'none';
}

function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.innerHTML = message;
    container.appendChild(toast);
    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(8px)';
        setTimeout(() => toast.remove(), 300);
    }, 3800);
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#039;');
}

// -------------------------------------------------------
// USER 360 DETAILS MODAL & EQUIPMENT BREAKDOWN
// -------------------------------------------------------
let currentDetailUserId = null;
let currentDetailUserData = null;

async function openUserDetailsModal(userId) {
    currentDetailUserId = userId;
    const modal = document.getElementById('user-details-modal');
    if (!modal) return;

    const body = document.getElementById('user-details-body');
    body.innerHTML = `
        <div style="text-align:center;padding:50px 20px;color:var(--text-muted)">
            <div class="pulse-indicator" style="display:inline-block;margin-bottom:14px;width:12px;height:12px"></div>
            <div style="font-size:0.95rem;font-weight:500;color:#f8fafc">Loading complete profile & equipment...</div>
        </div>`;
    modal.style.display = 'flex';

    try {
        const res = await fetch(`/api/users/${userId}/details`);
        if (!res.ok) throw new Error('Could not fetch user details');
        const data = await res.json();
        currentDetailUserData = data;

        const u = data.user;
        const initials = u.fullName.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase();
        const isAdmin = u.role === 'ADMIN';

        // Update modal header
        const avatarEl = document.getElementById('ud-avatar');
        avatarEl.textContent = initials;
        avatarEl.className = `avatar-initial avatar-${isAdmin ? 'admin' : 'emp'}`;
        document.getElementById('ud-name').textContent = u.fullName;

        const rolePill = document.getElementById('ud-role-pill');
        rolePill.textContent = u.role;
        rolePill.className = `role-pill ${isAdmin ? 'pill-admin' : 'pill-employee'}`;

        const statusPill = document.getElementById('ud-status-pill');
        statusPill.innerHTML = `<span class="pill-dot"></span>${u.status || 'ACTIVE'}`;
        statusPill.className = `pill pill-${(u.status || 'active').toLowerCase()}`;

        document.getElementById('ud-title-dept').textContent =
            `${u.jobTitle || 'Team Member'} • ${u.department || 'General'}`;

        renderUserDetailsContent(data);
    } catch (err) {
        body.innerHTML = `<div class="alert-box alert-error" style="margin:20px">${escapeHtml(err.message)}</div>`;
    }
}

function renderUserDetailsContent(data) {
    const body = document.getElementById('user-details-body');
    if (!body) return;

    const u = data.user;
    const assets = data.assignedAssets || [];
    const tickets = data.serviceHistory || [];
    const emails = data.emailHistory || [];
    const accCheckouts = data.accessoryCheckouts || [];
    const conCheckouts = data.consumableCheckouts || [];
    const activities = data.activityLogs || [];
    const totalVal = data.totalAssetValue ? Number(data.totalAssetValue) : 0;

    // Separate primary hardware from accessories/peripherals
    const hardwareCategories = ['LAPTOP', 'DESKTOP', 'SERVER', 'MOBILE_DEVICE'];
    const primaryHardware = assets.filter(a => hardwareCategories.includes(a.category));
    const accessories = assets.filter(a => !hardwareCategories.includes(a.category));

    const createdStr = u.createdAt ? new Date(u.createdAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }) : '–';
    const lastLoginStr = u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' }) : 'Never logged in';

    let html = `
    <!-- Top Stat KPI Counters -->
    <div class="ud-stat-grid" style="padding: 0 20px;">
        <div class="ud-stat-card">
            <span class="ud-stat-label">Primary Hardware</span>
            <span class="ud-stat-val text-blue">${primaryHardware.length} <span style="font-size:0.75rem;font-weight:400;color:var(--text-muted)">units</span></span>
        </div>
        <div class="ud-stat-card">
            <span class="ud-stat-label">Accessories & Displays</span>
            <span class="ud-stat-val text-purple">${accessories.length + accCheckouts.length} <span style="font-size:0.75rem;font-weight:400;color:var(--text-muted)">items</span></span>
        </div>
        <div class="ud-stat-card">
            <span class="ud-stat-label">Custody Asset Value</span>
            <span class="ud-stat-val text-emerald">$${totalVal.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
        </div>
        <div class="ud-stat-card">
            <span class="ud-stat-label">Activity & Service</span>
            <span class="ud-stat-val text-amber">${tickets.length + activities.length} <span style="font-size:0.75rem;font-weight:400;color:var(--text-muted)">records</span></span>
        </div>
    </div>

    <!-- User Profile Information Card -->
    <div style="padding: 0 20px;">
        <div class="ud-profile-grid">
            <div class="ud-profile-item">
                <span class="ud-profile-label">Organization Email</span>
                <span class="ud-profile-val" style="display:flex;align-items:center;gap:6px">
                    <code style="color:var(--status-blue);font-size:0.84rem">${escapeHtml(u.email)}</code>
                    <button class="btn-table-action" style="padding:1px 5px;font-size:0.7rem" onclick="copyValue('${escapeHtml(u.email)}', 'Email')" title="Copy email">📋</button>
                </span>
            </div>
            <div class="ud-profile-item">
                <span class="ud-profile-label">Department / Unit</span>
                <span class="ud-profile-val">${escapeHtml(u.department || '–')}</span>
            </div>
            <div class="ud-profile-item">
                <span class="ud-profile-label">Official Job Title</span>
                <span class="ud-profile-val">${escapeHtml(u.jobTitle || '–')}</span>
            </div>
            <div class="ud-profile-item">
                <span class="ud-profile-label">Direct Phone</span>
                <span class="ud-profile-val">${escapeHtml(u.phoneNumber || '–')}</span>
            </div>
            <div class="ud-profile-item">
                <span class="ud-profile-label">Member Since</span>
                <span class="ud-profile-val">${createdStr}</span>
            </div>
            <div class="ud-profile-item">
                <span class="ud-profile-label">Last Portal Activity</span>
                <span class="ud-profile-val" style="font-size:0.8rem;color:var(--text-secondary)">${lastLoginStr}</span>
            </div>
            ${u.temporaryPassword ? `
            <div class="ud-profile-item" style="grid-column: 1 / -1; background: rgba(245, 158, 11, 0.08); border: 1px dashed rgba(245, 158, 11, 0.35); padding: 10px 14px; border-radius: 8px;">
                <span class="ud-profile-label" style="color:#fbbf24;font-weight:600">Initial / Temporary Access Password:</span>
                <div style="display:flex;align-items:center;gap:10px;margin-top:4px">
                    <code style="font-size:1rem;color:#fef08a;font-weight:700;letter-spacing:0.04em">${escapeHtml(u.temporaryPassword)}</code>
                    <button class="btn-table-action" style="padding:2px 8px;font-size:0.75rem;background:rgba(245, 158, 11, 0.2);color:#fef08a;border-color:rgba(245, 158, 11, 0.4)" onclick="copyValue('${escapeHtml(u.temporaryPassword)}', 'Password')">Copy Password</button>
                    <span style="font-size:0.72rem;color:var(--text-muted)">User can log in to Employee Portal using this temporary password.</span>
                </div>
            </div>` : ''}
        </div>
    </div>

    <!-- Primary Assigned Hardware (Laptops, Desktops, Servers, Mobiles) -->
    <div style="padding: 0 20px;">
        <div class="ud-section-header">
            <span class="ud-section-title">
                💻 Primary Assigned Hardware Devices (${primaryHardware.length})
            </span>
            <button class="btn-table-action" onclick="openAssignFromUserModal()" style="font-size:0.75rem;padding:3px 10px;color:var(--status-blue);border-color:rgba(56,189,248,0.3)">
                + Assign Hardware
            </button>
        </div>
        ${primaryHardware.length > 0 
            ? primaryHardware.map(a => renderAssetDetailCard(a, u.id, false)).join('')
            : `<div class="ud-empty-box">
                No primary hardware devices assigned to this user.
                <div style="margin-top:8px"><button class="btn btn-secondary btn-sm" onclick="openAssignFromUserModal()">+ Assign Laptop / Desktop</button></div>
               </div>`
        }
    </div>

    <!-- Checked Out Accessories -->
    <div style="padding: 0 20px; margin-top: 14px;">
        <div class="ud-section-header">
            <span class="ud-section-title">
                🔌 Checked Out Accessories &amp; Peripherals (${accCheckouts.length})
            </span>
        </div>
        ${accCheckouts.length > 0 ? `
            <div class="table-container" style="max-height: 200px; overflow-y: auto;">
                <table class="data-table" style="font-size: 0.8rem;">
                    <thead>
                        <tr>
                            <th>Accessory</th>
                            <th>Quantity</th>
                            <th>Checkout Date</th>
                            <th>Expected Return</th>
                            <th>Notes</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${accCheckouts.map(ac => {
                            const dateStr = ac.checkoutDate ? new Date(ac.checkoutDate).toLocaleDateString() : '–';
                            const retStr = ac.expectedReturnDate ? new Date(ac.expectedReturnDate).toLocaleDateString() : '–';
                            return `<tr>
                                <td><strong>${escapeHtml(ac.accessoryName || 'Accessory')}</strong></td>
                                <td><span style="font-weight:700;color:var(--status-blue)">${ac.quantity}</span></td>
                                <td>${dateStr}</td>
                                <td>${retStr}</td>
                                <td><span style="font-size:0.75rem;color:var(--text-muted)">${escapeHtml(ac.notes || '–')}</span></td>
                            </tr>`;
                        }).join('')}
                    </tbody>
                </table>
            </div>
        ` : `<div class="ud-empty-box">No accessory units checked out to this employee.</div>`}
    </div>

    <!-- Dispensed Consumables -->
    <div style="padding: 0 20px; margin-top: 14px;">
        <div class="ud-section-header">
            <span class="ud-section-title">
                📦 Dispensed Consumables &amp; Supplies (${conCheckouts.length})
            </span>
        </div>
        ${conCheckouts.length > 0 ? `
            <div class="table-container" style="max-height: 200px; overflow-y: auto;">
                <table class="data-table" style="font-size: 0.8rem;">
                    <thead>
                        <tr>
                            <th>Consumable Item</th>
                            <th>Quantity</th>
                            <th>Dispensed Date</th>
                            <th>Dispensed By</th>
                            <th>Notes</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${conCheckouts.map(cc => {
                            const dateStr = cc.checkoutDate ? new Date(cc.checkoutDate).toLocaleDateString() : '–';
                            return `<tr>
                                <td><strong>${escapeHtml(cc.consumableName || 'Item')}</strong></td>
                                <td><span style="font-weight:700;color:var(--status-amber)">${cc.quantity}</span></td>
                                <td>${dateStr}</td>
                                <td>${escapeHtml(cc.dispensedBy || 'IT Admin')}</td>
                                <td><span style="font-size:0.75rem;color:var(--text-muted)">${escapeHtml(cc.notes || '–')}</span></td>
                            </tr>`;
                        }).join('')}
                    </tbody>
                </table>
            </div>
        ` : `<div class="ud-empty-box">No consumable supplies dispensed to this employee.</div>`}
    </div>

    <!-- Equipment Maintenance Records -->
    <div style="padding: 0 20px; margin-top: 14px;">
        <div class="ud-section-header">
            <span class="ud-section-title">
                🛠️ Equipment Service &amp; Repair History (${tickets.length})
            </span>
        </div>
        ${tickets.length > 0 ? `
            <div class="table-container" style="max-height: 200px; overflow-y: auto; margin-bottom: 12px;">
                <table class="data-table" style="font-size: 0.8rem;">
                    <thead>
                        <tr>
                            <th>Date</th>
                            <th>Asset</th>
                            <th>Issue / Service</th>
                            <th>Technician</th>
                            <th>Cost</th>
                            <th>Status</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${tickets.map(t => {
                            const dateStr = t.serviceDate ? new Date(t.serviceDate).toLocaleDateString() : '–';
                            const costStr = t.cost ? '$' + Number(t.cost).toFixed(2) : '–';
                            return `<tr>
                                <td>${dateStr}</td>
                                <td><strong>${escapeHtml(t.assetTag || '–')}</strong></td>
                                <td>${escapeHtml(t.issueDescription || t.serviceTitle || 'Maintenance')}</td>
                                <td>${escapeHtml(t.serviceProvider || '–')}</td>
                                <td style="color:#34d399">${costStr}</td>
                                <td><span class="pill pill-${(t.status||'completed').toLowerCase()}"><span class="pill-dot"></span>${t.status||'COMPLETED'}</span></td>
                            </tr>`;
                        }).join('')}
                    </tbody>
                </table>
            </div>
        ` : `<div class="ud-empty-box">No maintenance, RMA, or repair records logged for this user's gear.</div>`}
    </div>

    <!-- Custody & Activity Timeline -->
    <div style="padding: 0 20px; margin-top: 14px;">
        <div class="ud-section-header">
            <span class="ud-section-title">
                📜 Custody &amp; Activity Timeline (${activities.length})
            </span>
        </div>
        ${activities.length > 0 ? `
            <div class="table-container" style="max-height: 220px; overflow-y: auto;">
                <table class="data-table" style="font-size: 0.8rem;">
                    <thead>
                        <tr>
                            <th>Timestamp</th>
                            <th>Action</th>
                            <th>Item Type</th>
                            <th>Item</th>
                            <th>Performed By</th>
                            <th>Notes</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${activities.map(al => {
                            const dateStr = al.timestamp ? new Date(al.timestamp).toLocaleString('en-US', { dateStyle: 'short', timeStyle: 'short' }) : '–';
                            const actClass = `act-${(al.action || 'update').toLowerCase()}`;
                            return `<tr>
                                <td>${dateStr}</td>
                                <td><span class="act-badge ${actClass}" style="font-size:0.65rem">${al.action}</span></td>
                                <td><span class="category-tag" style="font-size:0.65rem">${al.itemType}</span></td>
                                <td><strong>${escapeHtml(al.itemName || '–')}</strong></td>
                                <td>${escapeHtml(al.performedBy || 'System')}</td>
                                <td><span style="font-size:0.75rem;color:var(--text-muted)">${escapeHtml(al.notes || '–')}</span></td>
                            </tr>`;
                        }).join('')}
                    </tbody>
                </table>
            </div>
        ` : `<div class="ud-empty-box">No activity logs recorded for this user yet.</div>`}
    </div>

    <!-- Email Dispatch & Onboarding Notifications Log -->
    <div style="padding: 0 20px; margin-top: 14px;">
        <div class="ud-section-header">
            <span class="ud-section-title">
                ✉️ Email Delivery &amp; Onboarding Audit Log (${emails.length})
            </span>
        </div>
        ${emails.length > 0 ? `
            <div class="table-container" style="max-height: 200px; overflow-y: auto;">
                <table class="data-table" style="font-size: 0.8rem;">
                    <thead>
                        <tr>
                            <th>Dispatched At</th>
                            <th>Type</th>
                            <th>Subject</th>
                            <th>Status</th>
                            <th style="text-align:right">Action</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${emails.map(e => {
                            const sentDate = e.sentAt ? new Date(e.sentAt).toLocaleString('en-US', { dateStyle: 'short', timeStyle: 'short' }) : '–';
                            const isDelivered = e.status === 'DELIVERED_TO_INBOX';
                            const statusPill = isDelivered
                                ? '<span class="pill pill-available"><span class="pill-dot"></span>INBOX DELIVERED</span>'
                                : `<span class="pill pill-reserved"><span class="pill-dot"></span>${escapeHtml(e.status || 'OUTBOX')}</span>`;
                            return `<tr>
                                <td style="color:var(--text-muted)">${sentDate}</td>
                                <td><span class="role-pill pill-${e.emailType === 'WELCOME_ONBOARDING' ? 'admin' : 'employee'}" style="font-size:0.68rem">${e.emailType === 'WELCOME_ONBOARDING' ? 'ONBOARDING' : 'ASSET'}</span></td>
                                <td style="max-width:200px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${escapeHtml(e.subject)}</td>
                                <td>${statusPill}</td>
                                <td style="text-align:right">
                                    <button class="btn-table-action" style="font-size:0.72rem;padding:2px 8px" onclick="previewEmail(${e.id})">Preview HTML</button>
                                </td>
                            </tr>`;
                        }).join('')}
                    </tbody>
                </table>
            </div>
        ` : `<div class="ud-empty-box">No emails logged yet for this recipient.</div>`}
    </div>
    `;

    body.innerHTML = html;
}

function renderAssetDetailCard(asset, userId, isAccessory) {
    const costStr = asset.purchaseCost ? '$' + Number(asset.purchaseCost).toLocaleString('en-US', { minimumFractionDigits: 2 }) : '–';
    const assignedStr = asset.assignedDate ? new Date(asset.assignedDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }) : '–';
    const icon = isAccessory ? '🔌' : '💻';

    return `
    <div class="ud-item-card">
        <div class="ud-item-left">
            <div class="ud-item-icon">${icon}</div>
            <div>
                <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
                    <span class="asset-tag-badge" style="font-size:0.75rem">${escapeHtml(asset.assetTag)}</span>
                    <strong style="color:#f8fafc;font-size:0.92rem">${escapeHtml(asset.name)}</strong>
                    <span class="category-tag" style="font-size:0.7rem">${asset.category}</span>
                    <span class="pill pill-${(asset.condition || 'good').toLowerCase()}" style="font-size:0.7rem"><span class="pill-dot"></span>${asset.condition || 'GOOD'}</span>
                </div>
                <div style="font-size:0.78rem;color:var(--text-secondary);margin-top:4px">
                    ${asset.manufacturer ? `${escapeHtml(asset.manufacturer)} ` : ''}${asset.model ? `• Model: ${escapeHtml(asset.model)} ` : ''}
                    ${asset.serialNumber ? `• Serial: <code>${escapeHtml(asset.serialNumber)}</code>` : ''}
                </div>
                ${asset.specifications ? `<div style="font-size:0.75rem;color:var(--text-muted);margin-top:3px">⚙️ ${escapeHtml(asset.specifications)}</div>` : ''}
                <div style="font-size:0.74rem;color:var(--text-muted);margin-top:4px">
                    Assigned: <strong style="color:#cbd5e1">${assignedStr}</strong> &bull; Purchase Value: <strong style="color:#34d399">${costStr}</strong>
                    ${asset.location ? ` &bull; Loc: ${escapeHtml(asset.location)}` : ''}
                </div>
            </div>
        </div>
        <div style="display:flex;gap:8px;align-items:center;flex-shrink:0;margin-left:14px">
            <button class="btn-table-action" onclick="openMaintenanceModal(${asset.id})" style="font-size:0.75rem" title="Log repair or diagnostic pass">Service</button>
            <button class="btn-table-action danger" onclick="unassignAssetFromUserModal(${asset.id}, ${userId})" style="font-size:0.75rem" title="Return this device to unassigned stock">Unassign</button>
        </div>
    </div>`;
}

async function unassignAssetFromUserModal(assetId, userId) {
    if (!confirm('Are you sure you want to unassign this equipment from this user and return it to stock?')) return;
    try {
        const res = await fetch(`/api/assets/${assetId}/unassign`, { method: 'PATCH' });
        if (!res.ok) throw new Error('Failed to unassign asset');
        showToast('Equipment returned to inventory stock ✓', 'success');
        await refreshAll();
        if (userId) {
            await openUserDetailsModal(userId);
        }
    } catch (err) {
        showToast(err.message, 'error');
    }
}

function openAssignFromUserModal() {
    if (!currentDetailUserData || !currentDetailUserData.user) return;
    const u = currentDetailUserData.user;
    openAssignModal(null, { fullName: u.fullName, email: u.email, department: u.department });
}

async function resendWelcomeEmailFromModal() {
    if (!currentDetailUserId) return;
    const btn = document.getElementById('ud-btn-resend');
    if (btn) {
        btn.disabled = true;
        btn.textContent = 'Sending...';
    }
    try {
        const res = await fetch(`/api/users/${currentDetailUserId}/resend-welcome`, { method: 'POST' });
        if (!res.ok) throw new Error('Failed to resend welcome email');
        const updatedUser = await res.json();
        showToast(`Credentials email sent to ${updatedUser.email} ✓`, 'success');
        await refreshAll();
        await openUserDetailsModal(currentDetailUserId);
    } catch (err) {
        showToast(err.message, 'error');
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = '✉️ Resend Credentials Email';
        }
    }
}

function copyValue(text, label = 'Value') {
    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(() => {
            showToast(`${label} copied to clipboard!`, 'info');
        }).catch(() => {
            prompt(`Copy ${label}:`, text);
        });
    } else {
        prompt(`Copy ${label}:`, text);
    }
}

// ==========================================================================
// MODULE: NETWORK DISCOVERY & PRINTABLE ASSET LABEL WITH SMART QR
// ==========================================================================
async function fetchNetworkInfo() {
    try {
        const res = await fetch('/api/system/network-info');
        if (res.ok) {
            App.networkInfo = await res.json();
            console.log('[ITAM] Server network discovery:', App.networkInfo);

            // Update Topbar LAN Sharing Pill with live IP or Hostname
            const pillLabel = document.getElementById('lan-share-label');
            if (pillLabel && App.networkInfo.lanUrl) {
                const displayHost = App.networkInfo.lanIp || App.networkInfo.hostName || 'Connected';
                pillLabel.innerHTML = `LAN: <strong>${escapeHtml(displayHost)}:${App.networkInfo.serverPort || 8085}</strong>`;
                const pill = document.getElementById('lan-share-pill');
                if (pill) {
                    pill.title = `Live address: ${App.networkInfo.lanUrl} (Click to copy and share with anyone on your Wi-Fi)`;
                }
            }
        }
    } catch (e) {
        console.warn('[ITAM] Network info fetch error:', e);
    }
}

function copyLanUrl() {
    const url = (App.networkInfo && (App.networkInfo.lanUrl || App.networkInfo.hostnameUrl)) || window.location.origin;
    if (navigator.clipboard) {
        navigator.clipboard.writeText(url).then(() => {
            showToast(`Copied LAN URL: ${url} ✓ (Share with anyone on this Wi-Fi)`, 'success');
        }).catch(() => {
            prompt('Copy this link for users on your network:', url);
        });
    } else {
        prompt('Copy this link for users on your network:', url);
    }
}

function openNetworkShareModal() {
    if (!App.networkInfo) {
        fetchNetworkInfo().then(() => populateAndShowNetworkShareModal());
    } else {
        populateAndShowNetworkShareModal();
    }
}

function populateAndShowNetworkShareModal() {
    const port = (App.networkInfo && App.networkInfo.serverPort) || 8085;
    const hostname = (App.networkInfo && App.networkInfo.hostName) || window.location.hostname || 'DESKTOP-GA6UVF3';
    const lanIp = (App.networkInfo && App.networkInfo.lanIp) || window.location.hostname;
    
    const hostnameUrl = (App.networkInfo && App.networkInfo.hostnameUrl) || `http://${hostname}:${port}`;
    const mdnsUrl = (App.networkInfo && App.networkInfo.mdnsUrl) || `http://${hostname.toLowerCase()}.local:${port}`;
    const lanUrl = (App.networkInfo && App.networkInfo.lanUrl) || `http://${lanIp}:${port}`;

    const hostInput = document.getElementById('net-share-hostname-url');
    if (hostInput) hostInput.value = hostnameUrl;

    const mdnsInput = document.getElementById('net-share-mdns-url');
    if (mdnsInput) mdnsInput.value = mdnsUrl;

    const lanInput = document.getElementById('net-share-lan-url');
    if (lanInput) lanInput.value = lanUrl;

    const statusEl = document.getElementById('net-share-status');
    if (statusEl) {
        statusEl.textContent = `Online: Port ${port} (Auto-Detected: ${lanIp})`;
    }

    // Render QR Code in modal pointing to the live network URL
    const qrContainer = document.getElementById('net-share-qr');
    if (qrContainer) {
        qrContainer.innerHTML = '';
        if (typeof QRCode !== 'undefined') {
            new QRCode(qrContainer, {
                text: lanUrl,
                width: 120,
                height: 120,
                colorDark: '#0f172a',
                colorLight: '#ffffff',
                correctLevel: QRCode.CorrectLevel.M
            });
        }
    }

    const modal = document.getElementById('network-share-modal');
    if (modal) modal.style.display = 'flex';
}

function closeNetworkShareModal() {
    const modal = document.getElementById('network-share-modal');
    if (modal) modal.style.display = 'none';
}

// Auto-detect IP changes whenever tab regains focus or periodically
window.addEventListener('focus', () => {
    fetchNetworkInfo();
});
setInterval(fetchNetworkInfo, 45000);

function getSelectedQrBaseUrl() {
    const select = document.getElementById('qr-host-preset-select');
    if (select && select.value) {
        return select.value;
    }
    // Smart dynamic fallback:
    if (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') {
        if (App.networkInfo && App.networkInfo.lanUrl) {
            return App.networkInfo.lanUrl;
        }
    }
    return window.location.origin;
}

function openAssetLabelModal(assetId) {
    const asset = App.cachedAssets.find(a => a.id === assetId);
    if (!asset) return;
    App.currentLabelAsset = asset;

    document.getElementById('lbl-tag').textContent = asset.assetTag;
    document.getElementById('lbl-name').textContent = asset.name;
    document.getElementById('lbl-category').textContent = (asset.category || 'ASSET').replace(/_/g, ' ');
    document.getElementById('lbl-serial').textContent = asset.serialNumber || 'N/A';
    document.getElementById('lbl-model').textContent = `${asset.manufacturer || ''} ${asset.model || ''}`.trim() || 'N/A';

    // Populate network preset selector
    populateQrHostPresetOptions();

    // Render QR Code with phone-accessible link
    renderQrForCurrentLabelAsset();

    // Render Clean SVG Barcode
    renderBarcodeSvg(asset.assetTag);

    document.getElementById('asset-label-modal').style.display = 'flex';
}

function populateQrHostPresetOptions() {
    const select = document.getElementById('qr-host-preset-select');
    if (!select) return;

    const port = (App.networkInfo && App.networkInfo.serverPort) || window.location.port || '8085';
    const detectedLanIp = (App.networkInfo && App.networkInfo.lanIp) || window.location.hostname;
    const lanUrl = (App.networkInfo && App.networkInfo.lanUrl) || `http://${detectedLanIp}:${port}`;
    const browserOrigin = window.location.origin;

    const options = [
        {
            value: lanUrl,
            label: `📱 Current Wi-Fi / LAN (Auto-Detected): ${lanUrl}`
        }
    ];

    if (App.networkInfo && App.networkInfo.hostnameUrl && App.networkInfo.hostnameUrl !== lanUrl) {
        options.push({
            value: App.networkInfo.hostnameUrl,
            label: `💻 Computer Name (Localhost Name): ${App.networkInfo.hostnameUrl}`
        });
    }

    if (App.networkInfo && App.networkInfo.mdnsUrl && App.networkInfo.mdnsUrl !== lanUrl) {
        options.push({
            value: App.networkInfo.mdnsUrl,
            label: `🍏 Local Hostname (mDNS): ${App.networkInfo.mdnsUrl}`
        });
    }

    if (browserOrigin !== lanUrl) {
        options.push({
            value: browserOrigin,
            label: `🖥️ Desktop Browser (Local): ${browserOrigin}`
        });
    }

    if (App.networkInfo && Array.isArray(App.networkInfo.availableIps)) {
        App.networkInfo.availableIps.forEach(ip => {
            const url = `http://${ip}:${port}`;
            if (url !== lanUrl && url !== browserOrigin) {
                options.push({
                    value: url,
                    label: `🌐 Interface IP: ${url}`
                });
            }
        });
    }

    select.innerHTML = options.map((opt, idx) => `
        <option value="${escapeHtml(opt.value)}" ${idx === 0 ? 'selected' : ''}>
            ${escapeHtml(opt.label)}
        </option>
    `).join('');
}

function onQrHostPresetChanged() {
    renderQrForCurrentLabelAsset();
}

function renderQrForCurrentLabelAsset() {
    if (!App.currentLabelAsset) return;
    const asset = App.currentLabelAsset;
    const baseUrl = getSelectedQrBaseUrl();
    const qrText = `${baseUrl}/?scanTag=${encodeURIComponent(asset.assetTag)}`;

    const urlInput = document.getElementById('qr-target-url-input');
    if (urlInput) urlInput.value = qrText;

    const qrContainer = document.getElementById('lbl-qr-code');
    if (!qrContainer) return;
    qrContainer.innerHTML = '';

    try {
        if (typeof QRCode !== 'undefined') {
            new QRCode(qrContainer, {
                text: qrText,
                width: 130,
                height: 130,
                colorDark: '#0f172a',
                colorLight: '#ffffff',
                correctLevel: QRCode.CorrectLevel.H
            });
        } else {
            qrContainer.innerHTML = `<div style="font-size:11px;padding:20px;border:1px dashed #94a3b8">QR: ${asset.assetTag}</div>`;
        }
    } catch (e) {
        console.error('QR Code render error:', e);
        qrContainer.innerHTML = `<div style="font-size:11px;padding:20px;border:1px dashed #94a3b8">QR: ${asset.assetTag}</div>`;
    }
}

function copyQrLink() {
    const urlInput = document.getElementById('qr-target-url-input');
    if (!urlInput) return;
    const url = urlInput.value;
    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(url).then(() => {
            showToast('Mobile scan URL copied to clipboard!', 'info');
        }).catch(() => {
            prompt('Copy scan URL:', url);
        });
    } else {
        prompt('Copy scan URL:', url);
    }
}

function testQrLinkInTab() {
    const urlInput = document.getElementById('qr-target-url-input');
    if (urlInput && urlInput.value) {
        window.open(urlInput.value, '_blank');
    }
}

// ==========================================================================
// MODULE: MOBILE EQUIPMENT PASSPORT & QUICK PHYSICAL AUDIT
// ==========================================================================
function formatDate(dateVal) {
    if (!dateVal) return 'N/A';
    try {
        const d = new Date(dateVal);
        if (isNaN(d.getTime())) return String(dateVal);
        return d.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });
    } catch {
        return String(dateVal);
    }
}

async function openMobileAssetPassport(tag) {
    try {
        const res = await fetch(`/api/assets/tag/${encodeURIComponent(tag.trim())}`);
        if (!res.ok) {
            showToast(`Asset with tag "${tag}" not found in inventory.`, 'error');
            if (!App.session) showAuthPage(null);
            return;
        }
        const asset = await res.json();
        App.scannedAsset = asset;

        const setText = (id, val) => {
            const el = document.getElementById(id);
            if (el) el.textContent = val ?? '';
        };

        setText('scan-passport-category', (asset.category || 'ASSET').replace(/_/g, ' '));
        
        const statusEl = document.getElementById('scan-passport-status');
        if (statusEl) {
            const st = asset.status || 'AVAILABLE';
            statusEl.textContent = st.replace(/_/g, ' ');
            if (st === 'AVAILABLE' || st === 'READY_TO_DEPLOY') {
                statusEl.className = 'badge badge-success';
            } else if (st === 'ASSIGNED' || st === 'DEPLOYED') {
                statusEl.className = 'badge badge-info';
            } else if (st === 'IN_REPAIR' || st === 'MAINTENANCE') {
                statusEl.className = 'badge badge-warning';
            } else {
                statusEl.className = 'badge badge-secondary';
            }
        }

        setText('scan-passport-name', asset.name || 'Unnamed Asset');
        setText('scan-passport-tag', asset.assetTag || tag);
        setText('scan-passport-serial', asset.serialNumber || 'N/A');
        setText('scan-passport-user', asset.assignedEmployeeName ? `${asset.assignedEmployeeName} (${asset.assignedEmployeeEmail || ''})` : 'Unassigned (In Stockroom)');
        setText('scan-passport-dept', asset.assignedDepartment || asset.department || 'IT Stockroom');
        setText('scan-passport-location', asset.location || 'Main Office');
        setText('scan-passport-condition', asset.condition || 'GOOD');

        setText('scan-passport-model', `${asset.manufacturer || ''} ${asset.model || ''}`.trim() || 'N/A');
        setText('scan-passport-cpu', asset.processor || asset.cpu || 'N/A');
        setText('scan-passport-ram', asset.ram || asset.memory || 'N/A');
        setText('scan-passport-storage', asset.storage || 'N/A');

        setText('scan-passport-last-audit', asset.lastAuditDate ? formatDate(asset.lastAuditDate) : 'Not yet audited');
        setText('scan-passport-next-audit', asset.nextAuditDate ? formatDate(asset.nextAuditDate) : 'In 6 months');

        // Reset verify button state
        const verifyBtn = document.getElementById('btn-quick-verify-audit');
        if (verifyBtn) {
            verifyBtn.disabled = false;
            verifyBtn.innerHTML = `
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 11 12 14 22 4"></polyline><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"></path></svg>
                Verify Physical Audit
            `;
            verifyBtn.className = 'btn btn-primary';
            verifyBtn.style.background = '';
            verifyBtn.style.borderColor = '';
        }

        const modal = document.getElementById('mobile-asset-scan-modal');
        if (modal) {
            modal.style.display = 'flex';
        }
    } catch (err) {
        console.error('Error fetching asset passport for tag:', tag, err);
        showToast(`Could not load passport: ${err.message || 'Check network connection'}`, 'error');
        if (!App.session) showAuthPage(null);
    }
}

async function submitMobileQuickAudit() {
    if (!App.scannedAsset) return;
    const btn = document.getElementById('btn-quick-verify-audit');
    if (btn) {
        btn.disabled = true;
        btn.textContent = 'Verifying physical presence...';
    }

    try {
        const res = await fetch(`/api/assets/${App.scannedAsset.id}/audit?notes=Mobile%20QR%20Scan%20Physical%20Verification`, {
            method: 'POST'
        });
        if (res.ok) {
            const updated = await res.json();
            App.scannedAsset = updated;
            
            const lastAuditEl = document.getElementById('scan-passport-last-audit');
            if (lastAuditEl) lastAuditEl.textContent = 'Verified Just Now (Today)';
            
            const nextAuditEl = document.getElementById('scan-passport-next-audit');
            if (nextAuditEl) nextAuditEl.textContent = updated.nextAuditDate ? formatDate(updated.nextAuditDate) : 'In 6 months';

            if (btn) {
                btn.innerHTML = `
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#ffffff" stroke-width="2.5"><polyline points="20 6 9 17 4 12"></polyline></svg>
                    ✓ Physical Audit Verified!
                `;
                btn.className = 'btn btn-success';
                btn.style.background = '#10b981';
                btn.style.borderColor = '#10b981';
            }
            showToast(`Asset ${updated.assetTag} physical audit successfully recorded!`, 'success');
            
            // If main dashboard is running, refresh tables
            if (App.session && App.session.role === 'ADMIN') {
                loadAssets();
                loadActivityLogs();
            }
        } else {
            showToast('Audit recording failed. Please try again.', 'error');
            if (btn) {
                btn.disabled = false;
                btn.textContent = 'Verify Physical Audit';
            }
        }
    } catch (e) {
        console.error('Audit submit error:', e);
        showToast('Network error while verifying audit.', 'error');
        if (btn) {
            btn.disabled = false;
            btn.textContent = 'Verify Physical Audit';
        }
    }
}

function closeMobilePassportModal() {
    closeModal('mobile-asset-scan-modal');
    window.history.replaceState({}, document.title, window.location.pathname);
    if (!App.session) {
        showAuthPage(null);
    }
}

function goToAuthLoginPage() {
    closeModal('mobile-asset-scan-modal');
    window.history.replaceState({}, document.title, window.location.pathname);
    // Explicitly clear session to prevent unauthorized access from scanned equipment
    localStorage.removeItem('itam_session');
    App.session = null;
    showAuthPage(null);
}

function goToFullDashboard() {
    goToAuthLoginPage();
}

function renderBarcodeSvg(tag) {
    const container = document.getElementById('lbl-barcode-container');
    if (!container) return;

    let seed = 0;
    for (let i = 0; i < tag.length; i++) seed += tag.charCodeAt(i);
    
    const widths = [1, 2, 3, 4, 1, 3, 2, 1, 2, 4, 2, 1, 3, 1, 2, 3];
    const bars = [];
    let x = 10;
    for (let i = 0; i < 48; i++) {
        const w = widths[(seed + i * 3) % widths.length];
        const isSpace = (i % 2 === 1);
        if (!isSpace) {
            bars.push(`<rect x="${x}" y="0" width="${w}" height="32" fill="#0f172a" />`);
        }
        x += w + 1;
    }

    container.innerHTML = `
        <svg width="${x + 10}" height="36" viewBox="0 0 ${x + 10} 36" xmlns="http://www.w3.org/2000/svg">
            ${bars.join('')}
        </svg>
    `;
}

function printAssetLabel() {
    window.print();
}

// ==========================================================================
// MODULE: HARDWARE ASSET CHECK-OUT / CHECK-IN / AUDIT
// ==========================================================================
function openCheckoutAssetModal(assetId) {
    const asset = App.cachedAssets.find(a => a.id === assetId);
    if (!asset) return;

    document.getElementById('checkout-asset-id').value = asset.id;
    document.getElementById('checkout-asset-banner').innerHTML = `
        <strong>${escapeHtml(asset.assetTag)}</strong> &bull; ${escapeHtml(asset.name)} 
        <span class="category-tag" style="margin-left:8px">${asset.category}</span>
    `;

    const select = document.getElementById('checkout-user-select');
    select.innerHTML = '<option value="">-- Choose Employee --</option>' + 
        App.cachedUsers.map(u => `<option value="${escapeHtml(u.email)}" data-name="${escapeHtml(u.fullName)}" data-dept="${escapeHtml(u.department || '')}">${escapeHtml(u.fullName)} (${escapeHtml(u.email)})</option>`).join('');

    const nextYear = new Date();
    nextYear.setFullYear(nextYear.getFullYear() + 1);
    document.getElementById('checkout-return-date').value = nextYear.toISOString().split('T')[0];
    document.getElementById('checkout-notes').value = '';

    document.getElementById('checkout-asset-modal').style.display = 'flex';
}

function onSelectUserForCheckout() {
    const sel = document.getElementById('checkout-user-select');
    const opt = sel.options[sel.selectedIndex];
    if (!opt || !opt.value) return;

    document.getElementById('checkout-emp-name').value = opt.getAttribute('data-name') || '';
    document.getElementById('checkout-emp-email').value = opt.value;
    document.getElementById('checkout-emp-dept').value = opt.getAttribute('data-dept') || '';
}

async function submitCheckoutAsset(event) {
    event.preventDefault();
    const assetId = document.getElementById('checkout-asset-id').value;
    const payload = {
        employeeName: document.getElementById('checkout-emp-name').value.trim(),
        employeeEmail: document.getElementById('checkout-emp-email').value.trim(),
        department: document.getElementById('checkout-emp-dept').value.trim(),
        expectedReturnDate: document.getElementById('checkout-return-date').value || null,
        notes: document.getElementById('checkout-notes').value.trim() || null
    };

    try {
        const res = await fetch(`/api/assets/${assetId}/checkout`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.message || 'Failed to check out asset');

        closeModal('checkout-asset-modal');
        showToast(`Asset successfully checked out to ${payload.employeeName} ✓`, 'success');
        await refreshAll();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

function openCheckinAssetModal(assetId) {
    const asset = App.cachedAssets.find(a => a.id === assetId);
    if (!asset) return;

    document.getElementById('checkin-asset-id').value = asset.id;
    document.getElementById('checkin-asset-banner').innerHTML = `
        <strong>${escapeHtml(asset.assetTag)}</strong> &bull; ${escapeHtml(asset.name)} 
        <div style="font-size:12px;color:var(--text-secondary);margin-top:4px">Currently assigned to: <strong>${escapeHtml(asset.assignedEmployeeName || 'Unknown')}</strong> (${escapeHtml(asset.assignedEmployeeEmail || '–')})</div>
    `;

    document.getElementById('checkin-status').value = 'AVAILABLE';
    document.getElementById('checkin-condition').value = asset.condition || 'GOOD';
    document.getElementById('checkin-notes').value = '';

    document.getElementById('checkin-asset-modal').style.display = 'flex';
}

async function submitCheckinAsset(event) {
    event.preventDefault();
    const assetId = document.getElementById('checkin-asset-id').value;
    const payload = {
        status: document.getElementById('checkin-status').value,
        condition: document.getElementById('checkin-condition').value,
        notes: document.getElementById('checkin-notes').value.trim() || null
    };

    try {
        const res = await fetch(`/api/assets/${assetId}/checkin`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.message || 'Failed to check in asset');

        closeModal('checkin-asset-modal');
        showToast('Asset returned to inventory & graded ✓', 'success');
        await refreshAll();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

function openAuditAssetModal(assetId) {
    const asset = App.cachedAssets.find(a => a.id === assetId);
    if (!asset) return;

    document.getElementById('audit-asset-id').value = asset.id;
    document.getElementById('audit-asset-banner').innerHTML = `
        <strong>${escapeHtml(asset.assetTag)}</strong> &bull; ${escapeHtml(asset.name)}
        <div style="font-size:12px;color:var(--text-secondary);margin-top:4px">
            Location: <strong>${escapeHtml(asset.location || 'Not set')}</strong> &bull; 
            Assigned: <strong>${escapeHtml(asset.assignedEmployeeName || 'In Stock')}</strong>
        </div>
    `;

    document.getElementById('audit-location').value = asset.location || '';
    const nextAudit = new Date();
    nextAudit.setDate(nextAudit.getDate() + 90);
    document.getElementById('audit-next-date').value = nextAudit.toISOString().split('T')[0];
    document.getElementById('audit-notes').value = '';

    document.getElementById('audit-asset-modal').style.display = 'flex';
}

async function submitAuditAsset(event) {
    event.preventDefault();
    const assetId = document.getElementById('audit-asset-id').value;
    const payload = {
        location: document.getElementById('audit-location').value.trim() || null,
        nextAuditDate: document.getElementById('audit-next-date').value || null,
        notes: document.getElementById('audit-notes').value.trim() || null
    };

    try {
        const res = await fetch(`/api/assets/${assetId}/audit`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.message || 'Failed to log physical audit');

        closeModal('audit-asset-modal');
        showToast('Physical asset audit verified & recorded ✓', 'success');
        await refreshAll();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// ==========================================================================
// MODULE: ACCESSORIES MANAGEMENT (QUANTITY TRACKING)
// ==========================================================================
async function loadAccessories() {
    try {
        const res = await fetch('/api/accessories');
        if (!res.ok) return;
        const list = await res.json();
        App.cachedAccessories = list;

        const badge = document.getElementById('nav-count-accessories');
        if (badge) badge.innerText = list.length;

        renderAccessoriesTable(list);
    } catch (err) {
        console.error('Error loading accessories:', err);
    }
}

function filterAccessoriesList() {
    const q = (document.getElementById('accessory-search-input')?.value || '').toLowerCase();
    const cat = document.getElementById('filter-accessory-category')?.value || '';

    const filtered = App.cachedAccessories.filter(a => {
        const matchQ = !q || a.name.toLowerCase().includes(q) || (a.modelNumber || '').toLowerCase().includes(q) || (a.manufacturer || '').toLowerCase().includes(q);
        const matchCat = !cat || a.category === cat;
        return matchQ && matchCat;
    });

    renderAccessoriesTable(filtered);
}

function resetAccessoryFilters() {
    document.getElementById('accessory-search-input').value = '';
    document.getElementById('filter-accessory-category').value = '';
    renderAccessoriesTable(App.cachedAccessories);
}

function renderAccessoriesTable(list) {
    const tbody = document.getElementById('accessories-table-body');
    if (!tbody) return;

    if (!list || list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="11" style="text-align:center;padding:40px;color:var(--text-muted)">No accessories found. Click "+ New Accessory" to create one.</td></tr>';
        return;
    }

    tbody.innerHTML = list.map(a => {
        const total = a.totalQuantity || 0;
        const out = a.checkedOutQuantity || 0;
        const avail = a.availableQuantity || 0;
        const pct = total > 0 ? Math.round((avail / total) * 100) : 0;
        const costStr = a.purchaseCost ? '$' + Number(a.purchaseCost).toLocaleString('en-US', { minimumFractionDigits: 2 }) : '–';

        let fillClass = 'stock-fill-ok';
        if (avail === 0) fillClass = 'stock-fill-danger';
        else if (pct <= 30) fillClass = 'stock-fill-warn';

        const isLow = a.minQuantity && avail <= a.minQuantity;
        const healthBadge = isLow
            ? `<span class="badge-low-stock">⚠️ Low (${avail} left)</span>`
            : `<span class="badge-stock-ok">In Stock</span>`;

        return `<tr>
            <td>
                <span class="asset-name-title">${escapeHtml(a.name)}</span>
                <span class="asset-model-sub">${escapeHtml(a.manufacturer || '')}</span>
            </td>
            <td><span class="category-tag">${a.category}</span></td>
            <td><code>${escapeHtml(a.modelNumber || '–')}</code></td>
            <td>${escapeHtml(a.manufacturer || '–')}</td>
            <td><strong>${total}</strong></td>
            <td><span style="color:var(--status-amber);font-weight:600">${out}</span></td>
            <td><strong style="color:var(--status-blue)">${avail}</strong></td>
            <td>
                <div class="stock-bar-wrap">
                    <div class="stock-track"><div class="stock-fill ${fillClass}" style="width:${pct}%"></div></div>
                    ${healthBadge}
                </div>
            </td>
            <td><span style="font-family:var(--font-mono);font-size:0.82rem">${costStr}</span></td>
            <td><span style="font-size:0.8rem;color:var(--text-secondary)">${escapeHtml(a.location || '–')}</span></td>
            <td style="text-align:right">
                <div class="action-btn-group">
                    <button class="btn-table-action" onclick="openAccessoryCheckoutModal(${a.id})" ${avail <= 0 ? 'disabled style="opacity:0.5"' : ''} title="Check Out to User" style="color:var(--status-blue);border-color:rgba(56,189,248,0.3)">Check-Out</button>
                    <button class="btn-table-action" onclick="openAccessoryCheckoutsModal(${a.id})" title="View Holders & Return">Checkouts (${out})</button>
                    <button class="btn-table-action" onclick="openEditAccessoryModal(${a.id})" title="Edit">Edit</button>
                    <button class="btn-table-action danger" onclick="deleteAccessory(${a.id})" title="Delete">&times;</button>
                </div>
            </td>
        </tr>`;
    }).join('');
}

function openAccessoryModal() {
    document.getElementById('acc-modal-title').textContent = 'Register New Accessory';
    document.getElementById('accessory-form').reset();
    document.getElementById('acc-id').value = '';
    document.getElementById('accessory-modal').style.display = 'flex';
}

function openEditAccessoryModal(id) {
    const a = App.cachedAccessories.find(x => x.id === id);
    if (!a) return;

    document.getElementById('acc-modal-title').textContent = `Edit Accessory (${a.name})`;
    document.getElementById('acc-id').value = a.id;
    document.getElementById('acc-name').value = a.name;
    document.getElementById('acc-category').value = a.category;
    document.getElementById('acc-model').value = a.modelNumber || '';
    document.getElementById('acc-manufacturer').value = a.manufacturer || '';
    document.getElementById('acc-total-qty').value = a.totalQuantity;
    document.getElementById('acc-min-qty').value = a.minQuantity || 0;
    document.getElementById('acc-cost').value = a.purchaseCost || '';
    document.getElementById('acc-purchase-date').value = a.purchaseDate || '';
    document.getElementById('acc-location').value = a.location || '';
    document.getElementById('acc-notes').value = a.notes || '';

    document.getElementById('accessory-modal').style.display = 'flex';
}

async function submitAccessory(event) {
    event.preventDefault();
    const id = document.getElementById('acc-id').value;
    const payload = {
        name: document.getElementById('acc-name').value.trim(),
        category: document.getElementById('acc-category').value,
        modelNumber: document.getElementById('acc-model').value.trim() || null,
        manufacturer: document.getElementById('acc-manufacturer').value.trim() || null,
        totalQuantity: parseInt(document.getElementById('acc-total-qty').value),
        minQuantity: parseInt(document.getElementById('acc-min-qty').value) || 0,
        purchaseCost: document.getElementById('acc-cost').value ? parseFloat(document.getElementById('acc-cost').value) : null,
        purchaseDate: document.getElementById('acc-purchase-date').value || null,
        location: document.getElementById('acc-location').value.trim() || null,
        notes: document.getElementById('acc-notes').value.trim() || null
    };

    try {
        const url = id ? `/api/accessories/${id}` : '/api/accessories';
        const method = id ? 'PUT' : 'POST';
        const res = await fetch(url, {
            method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.message || 'Failed to save accessory');

        closeModal('accessory-modal');
        showToast(`Accessory ${id ? 'updated' : 'created'} successfully ✓`, 'success');
        await loadAccessories();
        loadDashboardSummary();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

function openAccessoryCheckoutModal(id) {
    const a = App.cachedAccessories.find(x => x.id === id);
    if (!a) return;

    document.getElementById('acc-checkout-id').value = a.id;
    document.getElementById('acc-checkout-banner').innerHTML = `
        <strong>${escapeHtml(a.name)}</strong> &bull; Available Stock: <strong style="color:var(--status-blue)">${a.availableQuantity}</strong> of ${a.totalQuantity}
    `;

    document.getElementById('acc-checkout-qty').max = a.availableQuantity;
    document.getElementById('acc-checkout-qty').value = 1;

    const select = document.getElementById('acc-checkout-user-select');
    select.innerHTML = '<option value="">-- Choose Employee --</option>' + 
        App.cachedUsers.map(u => `<option value="${escapeHtml(u.email)}" data-name="${escapeHtml(u.fullName)}">${escapeHtml(u.fullName)} (${escapeHtml(u.email)})</option>`).join('');

    const returnDate = new Date();
    returnDate.setMonth(returnDate.getMonth() + 6);
    document.getElementById('acc-checkout-return-date').value = returnDate.toISOString().split('T')[0];
    document.getElementById('acc-checkout-notes').value = '';

    document.getElementById('accessory-checkout-modal').style.display = 'flex';
}

function onSelectUserForAccCheckout() {
    const sel = document.getElementById('acc-checkout-user-select');
    const opt = sel.options[sel.selectedIndex];
    if (!opt || !opt.value) return;

    document.getElementById('acc-checkout-emp-name').value = opt.getAttribute('data-name') || '';
    document.getElementById('acc-checkout-emp-email').value = opt.value;
}

async function submitAccessoryCheckout(event) {
    event.preventDefault();
    const id = document.getElementById('acc-checkout-id').value;
    const payload = {
        employeeName: document.getElementById('acc-checkout-emp-name').value.trim(),
        employeeEmail: document.getElementById('acc-checkout-emp-email').value.trim(),
        quantity: parseInt(document.getElementById('acc-checkout-qty').value),
        expectedReturnDate: document.getElementById('acc-checkout-return-date').value || null,
        notes: document.getElementById('acc-checkout-notes').value.trim() || null
    };

    try {
        const res = await fetch(`/api/accessories/${id}/checkout`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.message || 'Failed to check out accessory');

        closeModal('accessory-checkout-modal');
        showToast('Accessory checked out successfully ✓', 'success');
        await loadAccessories();
        loadDashboardSummary();
        loadActivityLogs();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function openAccessoryCheckoutsModal(id) {
    const a = App.cachedAccessories.find(x => x.id === id);
    if (!a) return;

    document.getElementById('acc-view-title').textContent = `Checkouts: ${a.name}`;
    document.getElementById('acc-view-sub').textContent = `Total checked out: ${a.checkedOutQuantity} units`;

    const tbody = document.getElementById('acc-checkouts-table-body');
    tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;padding:20px;color:var(--text-muted)">Loading checkouts...</td></tr>';
    document.getElementById('accessory-checkouts-modal').style.display = 'flex';

    try {
        const res = await fetch(`/api/accessories/${id}/checkouts`);
        const list = await res.json();

        if (!list || list.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;padding:30px;color:var(--text-muted)">No active checkouts for this accessory.</td></tr>';
            return;
        }

        tbody.innerHTML = list.map(c => {
            const checkDate = c.checkoutDate ? new Date(c.checkoutDate).toLocaleDateString() : '–';
            const retDate = c.expectedReturnDate ? new Date(c.expectedReturnDate).toLocaleDateString() : '–';
            return `<tr>
                <td><strong>${escapeHtml(c.employeeName)}</strong></td>
                <td><code>${escapeHtml(c.employeeEmail)}</code></td>
                <td><span style="font-weight:700;color:var(--status-amber)">${c.quantity}</span></td>
                <td>${checkDate}</td>
                <td>${retDate}</td>
                <td><span style="font-size:0.8rem;color:var(--text-muted)">${escapeHtml(c.notes || '–')}</span></td>
                <td style="text-align:right">
                    <button class="btn-table-action" onclick="checkinAccessory(${c.id}, ${id})" style="color:var(--status-green);border-color:rgba(16,185,129,0.3)">Check-In</button>
                </td>
            </tr>`;
        }).join('');
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="7" class="text-rose">${escapeHtml(err.message)}</td></tr>`;
    }
}

async function checkinAccessory(checkoutId, accessoryId) {
    try {
        const res = await fetch(`/api/accessories/checkin/${checkoutId}`, { method: 'POST' });
        const data = await res.json();
        if (!res.ok) throw new Error(data.message || 'Failed to check in');

        showToast('Accessory returned to inventory stock ✓', 'success');
        await loadAccessories();
        await openAccessoryCheckoutsModal(accessoryId);
        loadDashboardSummary();
        loadActivityLogs();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function deleteAccessory(id) {
    if (!confirm('Are you sure you want to delete this accessory?')) return;
    try {
        await fetch(`/api/accessories/${id}`, { method: 'DELETE' });
        showToast('Accessory deleted', 'success');
        await loadAccessories();
        loadDashboardSummary();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// ==========================================================================
// MODULE: CONSUMABLES MANAGEMENT (ONE-WAY DISPENSING)
// ==========================================================================
async function loadConsumables() {
    try {
        const res = await fetch('/api/consumables');
        if (!res.ok) return;
        const list = await res.json();
        App.cachedConsumables = list;

        const badge = document.getElementById('nav-count-consumables');
        if (badge) badge.innerText = list.length;

        renderConsumablesTable(list);
    } catch (err) {
        console.error('Error loading consumables:', err);
    }
}

function filterConsumablesList() {
    const q = (document.getElementById('consumable-search-input')?.value || '').toLowerCase();
    const cat = document.getElementById('filter-consumable-category')?.value || '';

    const filtered = App.cachedConsumables.filter(c => {
        const matchQ = !q || c.name.toLowerCase().includes(q) || (c.itemNumber || '').toLowerCase().includes(q) || (c.manufacturer || '').toLowerCase().includes(q);
        const matchCat = !cat || c.category === cat;
        return matchQ && matchCat;
    });

    renderConsumablesTable(filtered);
}

function resetConsumableFilters() {
    document.getElementById('consumable-search-input').value = '';
    document.getElementById('filter-consumable-category').value = '';
    renderConsumablesTable(App.cachedConsumables);
}

function renderConsumablesTable(list) {
    const tbody = document.getElementById('consumables-table-body');
    if (!tbody) return;

    if (!list || list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="11" style="text-align:center;padding:40px;color:var(--text-muted)">No consumables found. Click "+ New Consumable" to create one.</td></tr>';
        return;
    }

    tbody.innerHTML = list.map(c => {
        const total = c.totalQuantity || 0;
        const out = c.dispensedQuantity || 0;
        const remaining = c.remainingQuantity || 0;
        const pct = total > 0 ? Math.round((remaining / total) * 100) : 0;
        const costStr = c.purchaseCost ? '$' + Number(c.purchaseCost).toLocaleString('en-US', { minimumFractionDigits: 2 }) : '–';

        let fillClass = 'stock-fill-ok';
        if (remaining === 0) fillClass = 'stock-fill-danger';
        else if (pct <= 30) fillClass = 'stock-fill-warn';

        const isLow = c.minQuantity && remaining <= c.minQuantity;
        const healthBadge = isLow
            ? `<span class="badge-low-stock">⚠️ Low Stock (${remaining} left)</span>`
            : `<span class="badge-stock-ok">Sufficient</span>`;

        return `<tr>
            <td>
                <span class="asset-name-title">${escapeHtml(c.name)}</span>
                <span class="asset-model-sub">${escapeHtml(c.manufacturer || '')}</span>
            </td>
            <td><span class="category-tag">${c.category}</span></td>
            <td><code>${escapeHtml(c.itemNumber || '–')}</code></td>
            <td>${escapeHtml(c.manufacturer || '–')}</td>
            <td><strong>${total}</strong></td>
            <td><span style="color:var(--status-amber);font-weight:600">${out}</span></td>
            <td><strong style="color:var(--status-blue)">${remaining}</strong></td>
            <td>
                <div class="stock-bar-wrap">
                    <div class="stock-track"><div class="stock-fill ${fillClass}" style="width:${pct}%"></div></div>
                    ${healthBadge}
                </div>
            </td>
            <td><span style="font-family:var(--font-mono);font-size:0.82rem">${costStr}</span></td>
            <td><span style="font-size:0.8rem;color:var(--text-secondary)">${escapeHtml(c.location || '–')}</span></td>
            <td style="text-align:right">
                <div class="action-btn-group">
                    <button class="btn-table-action" onclick="openConsumableGiveoutModal(${c.id})" ${remaining <= 0 ? 'disabled style="opacity:0.5"' : ''} title="Dispense to Employee" style="color:var(--status-amber);border-color:rgba(245,158,11,0.3)">Dispense</button>
                    <button class="btn-table-action" onclick="openConsumableCheckoutsModal(${c.id})" title="Dispensing Audit Trail">History (${out})</button>
                    <button class="btn-table-action" onclick="openEditConsumableModal(${c.id})" title="Edit">Edit</button>
                    <button class="btn-table-action danger" onclick="deleteConsumable(${c.id})" title="Delete">&times;</button>
                </div>
            </td>
        </tr>`;
    }).join('');
}

function openConsumableModal() {
    document.getElementById('con-modal-title').textContent = 'Register New Consumable';
    document.getElementById('consumable-form').reset();
    document.getElementById('con-id').value = '';
    document.getElementById('consumable-modal').style.display = 'flex';
}

function openEditConsumableModal(id) {
    const c = App.cachedConsumables.find(x => x.id === id);
    if (!c) return;

    document.getElementById('con-modal-title').textContent = `Edit Consumable (${c.name})`;
    document.getElementById('con-id').value = c.id;
    document.getElementById('con-name').value = c.name;
    document.getElementById('con-category').value = c.category;
    document.getElementById('con-item-no').value = c.itemNumber || '';
    document.getElementById('con-manufacturer').value = c.manufacturer || '';
    document.getElementById('con-total-qty').value = c.totalQuantity;
    document.getElementById('con-min-qty').value = c.minQuantity || 0;
    document.getElementById('con-cost').value = c.purchaseCost || '';
    document.getElementById('con-purchase-date').value = c.purchaseDate || '';
    document.getElementById('con-location').value = c.location || '';
    document.getElementById('con-notes').value = c.notes || '';

    document.getElementById('consumable-modal').style.display = 'flex';
}

async function submitConsumable(event) {
    event.preventDefault();
    const id = document.getElementById('con-id').value;
    const payload = {
        name: document.getElementById('con-name').value.trim(),
        category: document.getElementById('con-category').value,
        itemNumber: document.getElementById('con-item-no').value.trim() || null,
        manufacturer: document.getElementById('con-manufacturer').value.trim() || null,
        totalQuantity: parseInt(document.getElementById('con-total-qty').value),
        minQuantity: parseInt(document.getElementById('con-min-qty').value) || 0,
        purchaseCost: document.getElementById('con-cost').value ? parseFloat(document.getElementById('con-cost').value) : null,
        purchaseDate: document.getElementById('con-purchase-date').value || null,
        location: document.getElementById('con-location').value.trim() || null,
        notes: document.getElementById('con-notes').value.trim() || null
    };

    try {
        const url = id ? `/api/consumables/${id}` : '/api/consumables';
        const method = id ? 'PUT' : 'POST';
        const res = await fetch(url, {
            method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.message || 'Failed to save consumable');

        closeModal('consumable-modal');
        showToast(`Consumable ${id ? 'updated' : 'created'} successfully ✓`, 'success');
        await loadConsumables();
        loadDashboardSummary();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

function openConsumableGiveoutModal(id) {
    const c = App.cachedConsumables.find(x => x.id === id);
    if (!c) return;

    document.getElementById('con-giveout-id').value = c.id;
    document.getElementById('con-giveout-banner').innerHTML = `
        <strong>${escapeHtml(c.name)}</strong> &bull; Remaining Stock: <strong style="color:var(--status-blue)">${c.remainingQuantity}</strong> of ${c.totalQuantity}
    `;

    document.getElementById('con-giveout-qty').max = c.remainingQuantity;
    document.getElementById('con-giveout-qty').value = 1;

    const select = document.getElementById('con-giveout-user-select');
    select.innerHTML = '<option value="">-- Choose Employee --</option>' + 
        App.cachedUsers.map(u => `<option value="${escapeHtml(u.email)}" data-name="${escapeHtml(u.fullName)}">${escapeHtml(u.fullName)} (${escapeHtml(u.email)})</option>`).join('');

    document.getElementById('con-giveout-notes').value = '';
    document.getElementById('consumable-giveout-modal').style.display = 'flex';
}

function onSelectUserForConGiveout() {
    const sel = document.getElementById('con-giveout-user-select');
    const opt = sel.options[sel.selectedIndex];
    if (!opt || !opt.value) return;

    document.getElementById('con-giveout-emp-name').value = opt.getAttribute('data-name') || '';
    document.getElementById('con-giveout-emp-email').value = opt.value;
}

async function submitConsumableGiveout(event) {
    event.preventDefault();
    const id = document.getElementById('con-giveout-id').value;
    const payload = {
        employeeName: document.getElementById('con-giveout-emp-name').value.trim(),
        employeeEmail: document.getElementById('con-giveout-emp-email').value.trim(),
        quantity: parseInt(document.getElementById('con-giveout-qty').value),
        notes: document.getElementById('con-giveout-notes').value.trim() || null
    };

    try {
        const res = await fetch(`/api/consumables/${id}/giveout`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.message || 'Failed to dispense consumable');

        closeModal('consumable-giveout-modal');
        showToast('Consumable permanently dispensed ✓', 'success');
        await loadConsumables();
        loadDashboardSummary();
        loadActivityLogs();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function openConsumableCheckoutsModal(id) {
    const c = App.cachedConsumables.find(x => x.id === id);
    if (!c) return;

    document.getElementById('con-view-title').textContent = `Dispense Log: ${c.name}`;
    document.getElementById('con-view-sub').textContent = `Total dispensed: ${c.dispensedQuantity} units`;

    const tbody = document.getElementById('con-checkouts-table-body');
    tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:20px;color:var(--text-muted)">Loading history...</td></tr>';
    document.getElementById('consumable-checkouts-modal').style.display = 'flex';

    try {
        const res = await fetch(`/api/consumables/${id}/checkouts`);
        const list = await res.json();

        if (!list || list.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:30px;color:var(--text-muted)">No items dispensed yet.</td></tr>';
            return;
        }

        tbody.innerHTML = list.map(item => {
            const dateStr = item.checkoutDate ? new Date(item.checkoutDate).toLocaleDateString() : '–';
            return `<tr>
                <td><strong>${escapeHtml(item.employeeName)}</strong></td>
                <td><code>${escapeHtml(item.employeeEmail)}</code></td>
                <td><span style="font-weight:700;color:var(--status-amber)">${item.quantity}</span></td>
                <td>${dateStr}</td>
                <td><span style="font-size:0.8rem;color:var(--text-secondary)">${escapeHtml(item.dispensedBy || 'IT Admin')}</span></td>
                <td><span style="font-size:0.8rem;color:var(--text-muted)">${escapeHtml(item.notes || '–')}</span></td>
            </tr>`;
        }).join('');
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="6" class="text-rose">${escapeHtml(err.message)}</td></tr>`;
    }
}

async function deleteConsumable(id) {
    if (!confirm('Are you sure you want to delete this consumable item?')) return;
    try {
        await fetch(`/api/consumables/${id}`, { method: 'DELETE' });
        showToast('Consumable deleted', 'success');
        await loadConsumables();
        loadDashboardSummary();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// ==========================================================================
// MODULE: COMPONENTS MANAGEMENT (PARTS INSTALLED INTO ASSETS)
// ==========================================================================
async function loadComponents() {
    try {
        const res = await fetch('/api/components');
        if (!res.ok) return;
        const list = await res.json();
        App.cachedComponents = list;

        const badge = document.getElementById('nav-count-components');
        if (badge) badge.innerText = list.length;

        renderComponentsTable(list);
    } catch (err) {
        console.error('Error loading components:', err);
    }
}

function filterComponentsList() {
    const q = (document.getElementById('component-search-input')?.value || '').toLowerCase();
    const cat = document.getElementById('filter-component-category')?.value || '';

    const filtered = App.cachedComponents.filter(c => {
        const matchQ = !q || c.name.toLowerCase().includes(q) || (c.serialNumber || '').toLowerCase().includes(q) || (c.specifications || '').toLowerCase().includes(q);
        const matchCat = !cat || c.category === cat;
        return matchQ && matchCat;
    });

    renderComponentsTable(filtered);
}

function resetComponentFilters() {
    document.getElementById('component-search-input').value = '';
    document.getElementById('filter-component-category').value = '';
    renderComponentsTable(App.cachedComponents);
}

function renderComponentsTable(list) {
    const tbody = document.getElementById('components-table-body');
    if (!tbody) return;

    if (!list || list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="11" style="text-align:center;padding:40px;color:var(--text-muted)">No components found. Click "+ New Component" to create one.</td></tr>';
        return;
    }

    tbody.innerHTML = list.map(c => {
        const total = c.totalQuantity || 0;
        const assigned = c.assignedQuantity || 0;
        const avail = c.availableQuantity || 0;
        const pct = total > 0 ? Math.round((avail / total) * 100) : 0;
        const costStr = c.purchaseCost ? '$' + Number(c.purchaseCost).toLocaleString('en-US', { minimumFractionDigits: 2 }) : '–';

        let fillClass = 'stock-fill-ok';
        if (avail === 0) fillClass = 'stock-fill-danger';
        else if (pct <= 30) fillClass = 'stock-fill-warn';

        const isLow = c.minQuantity && avail <= c.minQuantity;
        const healthBadge = isLow
            ? `<span class="badge-low-stock">⚠️ Low Stock (${avail} left)</span>`
            : `<span class="badge-stock-ok">In Stock</span>`;

        return `<tr>
            <td>
                <span class="asset-name-title">${escapeHtml(c.name)}</span>
            </td>
            <td><span class="category-tag">${c.category}</span></td>
            <td><code>${escapeHtml(c.serialNumber || '–')}</code></td>
            <td><span style="font-size:0.8rem;color:var(--text-secondary)">${escapeHtml(c.specifications || '–')}</span></td>
            <td><strong>${total}</strong></td>
            <td><span style="color:var(--status-purple);font-weight:600">${assigned}</span></td>
            <td><strong style="color:var(--status-blue)">${avail}</strong></td>
            <td>
                <div class="stock-bar-wrap">
                    <div class="stock-track"><div class="stock-fill ${fillClass}" style="width:${pct}%"></div></div>
                    ${healthBadge}
                </div>
            </td>
            <td><span style="font-family:var(--font-mono);font-size:0.82rem">${costStr}</span></td>
            <td><span style="font-size:0.8rem;color:var(--text-secondary)">${escapeHtml(c.location || '–')}</span></td>
            <td style="text-align:right">
                <div class="action-btn-group">
                    <button class="btn-table-action" onclick="openComponentAssignModal(${c.id})" ${avail <= 0 ? 'disabled style="opacity:0.5"' : ''} title="Install into Hardware Asset" style="color:var(--status-purple);border-color:rgba(168,85,247,0.3)">Install</button>
                    <button class="btn-table-action" onclick="openComponentAssignmentsModal(${c.id})" title="Installed Asset Hosts">Hosts (${assigned})</button>
                    <button class="btn-table-action" onclick="openEditComponentModal(${c.id})" title="Edit">Edit</button>
                    <button class="btn-table-action danger" onclick="deleteComponent(${c.id})" title="Delete">&times;</button>
                </div>
            </td>
        </tr>`;
    }).join('');
}

function openComponentModal() {
    document.getElementById('comp-modal-title').textContent = 'Register New Component';
    document.getElementById('component-form').reset();
    document.getElementById('comp-id').value = '';
    document.getElementById('component-modal').style.display = 'flex';
}

function openEditComponentModal(id) {
    const c = App.cachedComponents.find(x => x.id === id);
    if (!c) return;

    document.getElementById('comp-modal-title').textContent = `Edit Component (${c.name})`;
    document.getElementById('comp-id').value = c.id;
    document.getElementById('comp-name').value = c.name;
    document.getElementById('comp-category').value = c.category;
    document.getElementById('comp-serial').value = c.serialNumber || '';
    document.getElementById('comp-total-qty').value = c.totalQuantity;
    document.getElementById('comp-min-qty').value = c.minQuantity || 0;
    document.getElementById('comp-cost').value = c.purchaseCost || '';
    document.getElementById('comp-purchase-date').value = c.purchaseDate || '';
    document.getElementById('comp-location').value = c.location || '';
    document.getElementById('comp-specs').value = c.specifications || '';
    document.getElementById('comp-notes').value = c.notes || '';

    document.getElementById('component-modal').style.display = 'flex';
}

async function submitComponent(event) {
    event.preventDefault();
    const id = document.getElementById('comp-id').value;
    const payload = {
        name: document.getElementById('comp-name').value.trim(),
        category: document.getElementById('comp-category').value,
        serialNumber: document.getElementById('comp-serial').value.trim() || null,
        totalQuantity: parseInt(document.getElementById('comp-total-qty').value),
        minQuantity: parseInt(document.getElementById('comp-min-qty').value) || 0,
        purchaseCost: document.getElementById('comp-cost').value ? parseFloat(document.getElementById('comp-cost').value) : null,
        purchaseDate: document.getElementById('comp-purchase-date').value || null,
        location: document.getElementById('comp-location').value.trim() || null,
        specifications: document.getElementById('comp-specs').value.trim() || null,
        notes: document.getElementById('comp-notes').value.trim() || null
    };

    try {
        const url = id ? `/api/components/${id}` : '/api/components';
        const method = id ? 'PUT' : 'POST';
        const res = await fetch(url, {
            method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.message || 'Failed to save component');

        closeModal('component-modal');
        showToast(`Component ${id ? 'updated' : 'created'} successfully ✓`, 'success');
        await loadComponents();
        loadDashboardSummary();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

function openComponentAssignModal(id) {
    const c = App.cachedComponents.find(x => x.id === id);
    if (!c) return;

    document.getElementById('comp-assign-id').value = c.id;
    document.getElementById('comp-assign-banner').innerHTML = `
        <strong>${escapeHtml(c.name)}</strong> &bull; Available Stock: <strong style="color:var(--status-blue)">${c.availableQuantity}</strong> of ${c.totalQuantity}
    `;

    document.getElementById('comp-assign-qty').max = c.availableQuantity;
    document.getElementById('comp-assign-qty').value = 1;

    const select = document.getElementById('comp-assign-asset-select');
    select.innerHTML = '<option value="">-- Choose Target Hardware Asset --</option>' + 
        App.cachedAssets.map(a => `<option value="${a.id}">[${escapeHtml(a.assetTag)}] ${escapeHtml(a.name)} (${a.category})</option>`).join('');

    document.getElementById('comp-assign-notes').value = '';
    document.getElementById('component-assign-modal').style.display = 'flex';
}

async function submitComponentAssign(event) {
    event.preventDefault();
    const id = document.getElementById('comp-assign-id').value;
    const assetId = document.getElementById('comp-assign-asset-select').value;
    if (!assetId) {
        showToast('Please select a target hardware asset', 'error');
        return;
    }

    const payload = {
        assetId: parseInt(assetId),
        quantity: parseInt(document.getElementById('comp-assign-qty').value),
        notes: document.getElementById('comp-assign-notes').value.trim() || null
    };

    try {
        const res = await fetch(`/api/components/${id}/assign`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.message || 'Failed to install component');

        closeModal('component-assign-modal');
        showToast('Component successfully installed into asset ✓', 'success');
        await loadComponents();
        loadDashboardSummary();
        loadActivityLogs();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function openComponentAssignmentsModal(id) {
    const c = App.cachedComponents.find(x => x.id === id);
    if (!c) return;

    document.getElementById('comp-view-title').textContent = `Installed Hosts: ${c.name}`;
    document.getElementById('comp-view-sub').textContent = `Total installed units: ${c.assignedQuantity}`;

    const tbody = document.getElementById('comp-assignments-table-body');
    tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:20px;color:var(--text-muted)">Loading installations...</td></tr>';
    document.getElementById('component-assignments-modal').style.display = 'flex';

    try {
        const res = await fetch(`/api/components/${id}/assignments`);
        const list = await res.json();

        if (!list || list.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:30px;color:var(--text-muted)">Not installed in any assets currently.</td></tr>';
            return;
        }

        tbody.innerHTML = list.map(item => {
            const dateStr = item.assignedDate ? new Date(item.assignedDate).toLocaleDateString() : '–';
            return `<tr>
                <td><span class="asset-tag-badge">${escapeHtml(item.assetTag)}</span></td>
                <td><strong>${escapeHtml(item.assetName)}</strong></td>
                <td><span style="font-weight:700;color:var(--status-purple)">${item.quantity}</span></td>
                <td>${dateStr}</td>
                <td><span style="font-size:0.8rem;color:var(--text-muted)">${escapeHtml(item.notes || '–')}</span></td>
                <td style="text-align:right">
                    <button class="btn-table-action danger" onclick="removeComponentFromAsset(${item.id}, ${id})">Detach</button>
                </td>
            </tr>`;
        }).join('');
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="6" class="text-rose">${escapeHtml(err.message)}</td></tr>`;
    }
}

async function removeComponentFromAsset(assignmentId, compId) {
    if (!confirm('Detach component from this asset and return to available stock?')) return;
    try {
        const res = await fetch(`/api/components/assignments/${assignmentId}`, { method: 'DELETE' });
        const data = await res.json();
        if (!res.ok) throw new Error(data.message || 'Failed to detach');

        showToast('Component detached and returned to stock ✓', 'success');
        await loadComponents();
        await openComponentAssignmentsModal(compId);
        loadDashboardSummary();
        loadActivityLogs();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function deleteComponent(id) {
    if (!confirm('Are you sure you want to delete this component?')) return;
    try {
        await fetch(`/api/components/${id}`, { method: 'DELETE' });
        showToast('Component deleted', 'success');
        await loadComponents();
        loadDashboardSummary();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// ==========================================================================
// MODULE: CUSTODY & ACTIVITY AUDIT TRAIL
// ==========================================================================
async function loadActivityLogs() {
    try {
        const res = await fetch('/api/activity-logs?limit=100');
        if (!res.ok) return;
        const logs = await res.json();
        App.cachedActivity = logs;

        const badge = document.getElementById('nav-count-activity');
        if (badge) badge.innerText = logs.length;

        renderActivityTable(logs);
    } catch (err) {
        console.error('Error loading activity logs:', err);
    }
}

function filterActivityList() {
    const q = (document.getElementById('activity-search-input')?.value || '').toLowerCase();
    const act = document.getElementById('filter-activity-action')?.value || '';
    const typ = document.getElementById('filter-activity-type')?.value || '';

    const filtered = App.cachedActivity.filter(l => {
        const matchQ = !q || (l.itemName || '').toLowerCase().includes(q) || (l.itemTag || '').toLowerCase().includes(q) || (l.targetUserName || '').toLowerCase().includes(q) || (l.notes || '').toLowerCase().includes(q);
        const matchAct = !act || l.action === act;
        const matchTyp = !typ || l.itemType === typ;
        return matchQ && matchAct && matchTyp;
    });

    renderActivityTable(filtered);
}

function resetActivityFilters() {
    document.getElementById('activity-search-input').value = '';
    document.getElementById('filter-activity-action').value = '';
    document.getElementById('filter-activity-type').value = '';
    renderActivityTable(App.cachedActivity);
}

function renderActivityTable(logs) {
    const tbody = document.getElementById('activity-table-body');
    if (!tbody) return;

    if (!logs || logs.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;padding:40px;color:var(--text-muted)">No activity logged yet. System transactions will appear here.</td></tr>';
        return;
    }

    tbody.innerHTML = logs.map(l => {
        const dateStr = l.timestamp ? new Date(l.timestamp).toLocaleString('en-US', { dateStyle: 'short', timeStyle: 'short' }) : '–';
        const actClass = `act-${(l.action || 'update').toLowerCase()}`;
        const itemPill = `<span class="category-tag" style="font-size:0.7rem">${l.itemType}</span>`;

        let targetHtml = '<span class="text-muted">–</span>';
        if (l.targetUserName) {
            targetHtml = `<div><strong>${escapeHtml(l.targetUserName)}</strong><div style="font-size:0.72rem;color:var(--status-blue)">${escapeHtml(l.targetUserEmail || '')}</div></div>`;
        }

        return `<tr>
            <td><span style="font-size:0.8rem;color:var(--text-muted)">${dateStr}</span></td>
            <td><span class="act-badge ${actClass}">${l.action}</span></td>
            <td>${itemPill}</td>
            <td>
                <strong>${escapeHtml(l.itemName || '–')}</strong>
                ${l.itemTag ? `<span class="asset-tag-badge" style="font-size:0.68rem;margin-left:6px">${escapeHtml(l.itemTag)}</span>` : ''}
            </td>
            <td>${targetHtml}</td>
            <td><span style="font-size:0.8rem;color:var(--text-secondary)">${escapeHtml(l.performedBy || 'System')}</span></td>
            <td><span style="font-size:0.8rem;color:var(--text-secondary)">${escapeHtml(l.notes || '–')}</span></td>
        </tr>`;
    }).join('');
}

// ==============================================================
// BULK DATA IMPORT & SPREADSHEET SYNC
// ==============================================================
App.bulkState = {
    type: 'assets',
    fileName: '',
    rawCsv: '',
    parsedRows: [],
    detectedCount: 0,
    dragInitialized: false
};

const BULK_TEMPLATES = {
    assets: {
        filename: 'itam_assets_template.csv',
        title: 'Need standard hardware asset format?',
        desc: 'Download template with tags, serials, status, cost, condition, and employee assignees.',
        csv: 'Asset Tag,Name,Category,Manufacturer,Model,Serial,Status,Condition,Cost,Assigned To,Email,Department,Location,Warranty\n' +
             'LT-2024-001,MacBook Pro 16",LAPTOP,Apple,M3 Pro 36GB,C02G12345678,ASSIGNED,EXCELLENT,2499.00,Sarah Jenkins,sarah.j@company.com,Engineering,San Francisco HQ,2027-01-15\n' +
             'DT-2024-010,Precision 3660 Tower,DESKTOP,Dell,i9-13900K 64GB,DL99887766,AVAILABLE,GOOD,1850.00,,,IT Storage Room B,Austin Campus,2026-06-30\n' +
             'SRV-2024-002,PowerEdge R750,SERVER,Dell,2x Xeon Silver 4314,SRV44332211,AVAILABLE,EXCELLENT,4200.00,,,Data Center Rack 04,Chicago Hub,2028-12-01\n' +
             'MON-2024-055,UltraSharp 27" 4K,MONITOR,Dell,U2723QE,CN00112233,ASSIGNED,EXCELLENT,580.00,Marcus Brody,marcus.b@company.com,Product Design,San Francisco HQ,2026-08-15\n' +
             'NET-2024-008,Catalyst 9300 48-Port,NETWORKING,Cisco,C9300-48P-A,FOC22334455,AVAILABLE,GOOD,3100.00,,,MDF Room 1,San Francisco HQ,2027-04-10\n'
    },
    users: {
        filename: 'itam_users_template.csv',
        title: 'Need employee onboarding format?',
        desc: 'Download template with full names, organization emails, departments, job titles, and roles.',
        csv: 'Full Name,Email,Department,Job Title,Phone,Role\n' +
             'David Miller,david.m@company.com,Engineering,Senior Software Engineer,+1 555-0101,EMPLOYEE\n' +
             'Rachel Green,rachel.g@company.com,Marketing,Brand Specialist,+1 555-0102,EMPLOYEE\n' +
             'Thomas Wayne,thomas.w@company.com,IT Operations,Systems Administrator,+1 555-0103,ADMIN\n'
    },
    accessories: {
        filename: 'itam_accessories_template.csv',
        title: 'Need accessories stock format?',
        desc: 'Download template with accessory names, categories, quantities, purchase costs, and locations.',
        csv: 'Name,Category,Manufacturer,Model,Quantity,Cost,Location,Notes\n' +
             'Magic Keyboard with Touch ID,Keyboards,Apple,A2449,15,199.00,IT Storage Room A,US Layout Wireless\n' +
             'MX Master 3S Wireless Mouse,Mice,Logitech,910-006557,25,99.99,IT Storage Room A,Ergonomic Bluetooth\n' +
             'CalDigit TS4 Thunderbolt 4 Dock,Docks,CalDigit,TS4-US,10,399.95,IT Storage Room B,230W Power Delivery\n'
    },
    consumables: {
        filename: 'itam_consumables_template.csv',
        title: 'Need consumables stock format?',
        desc: 'Download template with consumable items, item numbers, quantities, costs, and storage bins.',
        csv: 'Name,Category,Manufacturer,Item Number,Quantity,Cost,Location,Notes\n' +
             'USB-C to USB-C Cable 2M,Cables,Anker,A8856,50,15.99,Bin C-12,100W Power Delivery Braided\n' +
             'Cat6 Ethernet Patch Cable 3ft,Cables,Monoprice,11218,100,3.50,Bin C-04,Blue jacket molded boots\n' +
             'HP 58A Black LaserJet Toner,Toner & Ink,HP,CF258A,8,114.99,Cabinet 2,High yield cartridge\n'
    }
};

function openBulkImportModal(initialType = 'assets') {
    App.bulkState.type = initialType;
    App.bulkState.fileName = '';
    App.bulkState.rawCsv = '';
    App.bulkState.parsedRows = [];
    App.bulkState.detectedCount = 0;

    const fileInput = document.getElementById('bulk-file-input');
    if (fileInput) fileInput.value = '';

    const textarea = document.getElementById('bulk-csv-textarea');
    if (textarea) textarea.value = '';

    const pasteWrap = document.getElementById('bulk-text-paste-wrap');
    if (pasteWrap) pasteWrap.style.display = 'none';

    const dropzoneTitle = document.getElementById('bulk-dropzone-title');
    if (dropzoneTitle) dropzoneTitle.textContent = 'Click to choose a CSV file or drag & drop it here';

    const dropzoneSub = document.getElementById('bulk-dropzone-sub');
    if (dropzoneSub) dropzoneSub.textContent = 'Supports UTF-8 CSV exports from Excel, Google Sheets, or Snipe-IT';

    const previewWrap = document.getElementById('bulk-preview-wrap');
    if (previewWrap) previewWrap.style.display = 'none';

    const resultBanner = document.getElementById('bulk-result-banner');
    if (resultBanner) {
        resultBanner.style.display = 'none';
        resultBanner.innerHTML = '';
    }

    const footerSummary = document.getElementById('bulk-footer-summary');
    if (footerSummary) footerSummary.textContent = 'No file selected';

    const execBtn = document.getElementById('btn-execute-bulk');
    if (execBtn) {
        execBtn.disabled = true;
        execBtn.textContent = 'Import Records →';
    }

    setBulkImportType(initialType);

    // Initialize drag-drop listeners once
    initBulkDropzone();

    const modal = document.getElementById('bulk-import-modal');
    if (modal) modal.style.display = 'flex';
}

function initBulkDropzone() {
    if (App.bulkState.dragInitialized) return;
    const dropzone = document.getElementById('bulk-dropzone');
    if (!dropzone) return;

    ['dragenter', 'dragover'].forEach(eventName => {
        dropzone.addEventListener(eventName, (e) => {
            e.preventDefault();
            e.stopPropagation();
            dropzone.classList.add('dragover');
        }, false);
    });

    ['dragleave', 'drop'].forEach(eventName => {
        dropzone.addEventListener(eventName, (e) => {
            e.preventDefault();
            e.stopPropagation();
            dropzone.classList.remove('dragover');
        }, false);
    });

    dropzone.addEventListener('drop', (e) => {
        const dt = e.dataTransfer;
        const files = dt.files;
        if (files && files.length > 0) {
            handleBulkDroppedFile(files[0]);
        }
    }, false);

    App.bulkState.dragInitialized = true;
}

function setBulkImportType(type) {
    App.bulkState.type = type;

    // Toggle active tab buttons
    ['assets', 'users', 'accessories', 'consumables'].forEach(t => {
        const btn = document.getElementById(`bulk-type-${t}`);
        if (btn) {
            if (t === type) btn.classList.add('active');
            else btn.classList.remove('active');
        }
    });

    // Update template banner
    const meta = BULK_TEMPLATES[type] || BULK_TEMPLATES.assets;
    const titleEl = document.getElementById('bulk-template-title');
    if (titleEl) titleEl.textContent = meta.title;
    const descEl = document.getElementById('bulk-template-desc');
    if (descEl) descEl.textContent = meta.desc;

    // Show/hide auto-create users checkbox (relevant for assets)
    const autoUserWrap = document.getElementById('bulk-opt-users-wrap');
    if (autoUserWrap) {
        autoUserWrap.style.display = (type === 'assets') ? 'flex' : 'none';
    }

    // Show/hide welcome email checkbox (relevant for users and assets)
    const emailWrap = document.getElementById('bulk-opt-email-wrap');
    if (emailWrap) {
        emailWrap.style.display = (type === 'users' || type === 'assets') ? 'flex' : 'none';
    }

    // If data already loaded, re-process preview for current type
    if (App.bulkState.rawCsv) {
        processCsvData(App.bulkState.rawCsv);
    }
}

function downloadCurrentBulkTemplate() {
    const meta = BULK_TEMPLATES[App.bulkState.type] || BULK_TEMPLATES.assets;
    const blob = new Blob([meta.csv], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);
    link.setAttribute('href', url);
    link.setAttribute('download', meta.filename);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showToast(`Downloaded ${meta.filename} ✓`, 'success');
}

function handleBulkFileSelect(event) {
    const file = event.target.files && event.target.files[0];
    if (file) handleBulkDroppedFile(file);
}

function handleBulkDroppedFile(file) {
    if (!file) return;
    App.bulkState.fileName = file.name;

    const titleEl = document.getElementById('bulk-dropzone-title');
    if (titleEl) titleEl.textContent = `📄 Selected: ${file.name} (${(file.size / 1024).toFixed(1)} KB)`;
    const subEl = document.getElementById('bulk-dropzone-sub');
    if (subEl) subEl.textContent = 'Click to change file or drag another CSV';

    const reader = new FileReader();
    reader.onload = (e) => {
        App.bulkState.rawCsv = e.target.result;
        processCsvData(App.bulkState.rawCsv);
    };
    reader.readAsText(file, 'UTF-8');
}

function toggleBulkTextPaste() {
    const wrap = document.getElementById('bulk-text-paste-wrap');
    if (wrap) {
        wrap.style.display = (wrap.style.display === 'none' || !wrap.style.display) ? 'block' : 'none';
        if (wrap.style.display === 'block') {
            document.getElementById('bulk-csv-textarea')?.focus();
        }
    }
}

function handleBulkTextInput(text) {
    App.bulkState.rawCsv = text;
    App.bulkState.fileName = 'Pasted CSV Text';
    const titleEl = document.getElementById('bulk-dropzone-title');
    if (titleEl) titleEl.textContent = '📝 Using raw pasted CSV text';
    processCsvData(text);
}

// RFC-4180 CSV parser handling quotes, escaped quotes, and multiline cells
function parseCsv(text) {
    if (!text) return [];
    if (text.charCodeAt(0) === 0xFEFF) text = text.slice(1); // Strip UTF-8 BOM
    let rows = [];
    let currentRow = [''];
    let inQuotes = false;

    for (let i = 0; i < text.length; i++) {
        const c = text[i];
        const next = text[i + 1];

        if (c === '"') {
            if (inQuotes && next === '"') {
                currentRow[currentRow.length - 1] += '"';
                i++;
            } else {
                inQuotes = !inQuotes;
            }
        } else if (c === ',' && !inQuotes) {
            currentRow.push('');
        } else if ((c === '\r' || c === '\n') && !inQuotes) {
            if (c === '\r' && next === '\n') i++;
            rows.push(currentRow.map(f => f.trim()));
            currentRow = [''];
        } else {
            currentRow[currentRow.length - 1] += c;
        }
    }
    if (currentRow.length > 1 || (currentRow.length === 1 && currentRow[0] !== '')) {
        rows.push(currentRow.map(f => f.trim()));
    }
    return rows.filter(r => r.length > 0 && r.some(cell => cell !== ''));
}

function normalizeBulkHeader(h) {
    if (!h) return '';
    return h.toLowerCase().replace(/[^a-z0-9]/g, '');
}

function getColValue(cols, headerMap, ...aliases) {
    for (const a of aliases) {
        const idx = headerMap[a];
        if (idx !== undefined && idx < cols.length) {
            const val = cols[idx];
            if (val !== undefined && val !== null && val.trim() !== '') return val.trim();
        }
    }
    return null;
}

function processCsvData(csvText) {
    const table = parseCsv(csvText);
    const previewWrap = document.getElementById('bulk-preview-wrap');
    const resultBanner = document.getElementById('bulk-result-banner');
    const execBtn = document.getElementById('btn-execute-bulk');
    const footerSummary = document.getElementById('bulk-footer-summary');

    if (!table || table.length < 2) {
        if (previewWrap) previewWrap.style.display = 'none';
        if (resultBanner) {
            resultBanner.style.display = 'block';
            resultBanner.className = 'alert-box alert-error';
            resultBanner.innerHTML = '⚠️ CSV must contain at least 1 header line and 1 data row.';
        }
        if (execBtn) execBtn.disabled = true;
        if (footerSummary) footerSummary.textContent = 'Invalid or empty CSV';
        return;
    }

    if (resultBanner) resultBanner.style.display = 'none';

    const headers = table[0];
    const headerMap = {};
    headers.forEach((h, i) => {
        headerMap[normalizeBulkHeader(h)] = i;
    });

    const type = App.bulkState.type;
    const parsedRows = [];

    for (let r = 1; r < table.length; r++) {
        const cols = table[r];
        if (!cols || cols.length === 0 || cols.every(c => !c)) continue;

        if (type === 'assets') {
            const tag = getColValue(cols, headerMap, 'assettag', 'tag', 'asset_tag');
            const name = getColValue(cols, headerMap, 'name', 'assetname', 'asset_name');
            if (!tag && !name) continue;

            parsedRows.push({
                assetTag: tag || `AST-${1000 + r}`,
                name: name || `Hardware Asset ${tag || r}`,
                category: getColValue(cols, headerMap, 'category', 'type') || 'LAPTOP',
                manufacturer: getColValue(cols, headerMap, 'manufacturer', 'make', 'brand') || '',
                model: getColValue(cols, headerMap, 'model', 'modelnumber') || '',
                serialNumber: getColValue(cols, headerMap, 'serial', 'serialnumber', 'serial_number', 'serial#') || '',
                status: getColValue(cols, headerMap, 'status', 'state') || 'AVAILABLE',
                condition: getColValue(cols, headerMap, 'condition') || 'GOOD',
                purchaseCost: getColValue(cols, headerMap, 'cost', 'purchasecost', 'price') || '',
                purchaseDate: getColValue(cols, headerMap, 'purchasedate', 'purchase_date') || '',
                warrantyExpiryDate: getColValue(cols, headerMap, 'warranty', 'warrantyexpiry', 'warrantyexpirydate') || '',
                location: getColValue(cols, headerMap, 'location', 'site') || '',
                assignedEmployeeName: getColValue(cols, headerMap, 'assignedto', 'assignee', 'employee', 'assigned_to', 'employeename') || '',
                assignedEmployeeEmail: getColValue(cols, headerMap, 'email', 'assignedemail', 'employeeemail', 'assigned_email') || '',
                assignedDepartment: getColValue(cols, headerMap, 'department', 'dept') || '',
                specifications: getColValue(cols, headerMap, 'specifications', 'specs') || '',
                notes: getColValue(cols, headerMap, 'notes', 'comments') || ''
            });
        } else if (type === 'users') {
            const email = getColValue(cols, headerMap, 'email', 'emailaddress', 'user_email');
            const fullName = getColValue(cols, headerMap, 'fullname', 'name', 'full_name', 'employee_name');
            if (!email && !fullName) continue;

            parsedRows.push({
                fullName: fullName || (email ? email.split('@')[0] : 'Employee'),
                email: email || '',
                department: getColValue(cols, headerMap, 'department', 'dept') || '',
                jobTitle: getColValue(cols, headerMap, 'jobtitle', 'title', 'role_title', 'position') || '',
                phoneNumber: getColValue(cols, headerMap, 'phone', 'phonenumber', 'telephone') || '',
                role: getColValue(cols, headerMap, 'role', 'userrole') || 'EMPLOYEE',
                initialPassword: getColValue(cols, headerMap, 'password', 'initialpassword') || ''
            });
        } else if (type === 'accessories') {
            const name = getColValue(cols, headerMap, 'name', 'accessoryname');
            if (!name) continue;
            parsedRows.push({
                name: name,
                category: getColValue(cols, headerMap, 'category') || 'Keyboards',
                manufacturer: getColValue(cols, headerMap, 'manufacturer', 'make') || '',
                modelNumber: getColValue(cols, headerMap, 'model', 'modelnumber') || '',
                totalQuantity: parseInt(getColValue(cols, headerMap, 'quantity', 'qty', 'totalquantity') || '10', 10),
                purchaseCost: getColValue(cols, headerMap, 'cost', 'purchasecost', 'price') || '',
                location: getColValue(cols, headerMap, 'location') || '',
                notes: getColValue(cols, headerMap, 'notes') || ''
            });
        } else if (type === 'consumables') {
            const name = getColValue(cols, headerMap, 'name', 'consumablename');
            if (!name) continue;
            parsedRows.push({
                name: name,
                category: getColValue(cols, headerMap, 'category') || 'Cables',
                manufacturer: getColValue(cols, headerMap, 'manufacturer', 'make') || '',
                itemNumber: getColValue(cols, headerMap, 'itemnumber', 'partnumber', 'sku') || '',
                totalQuantity: parseInt(getColValue(cols, headerMap, 'quantity', 'qty', 'totalquantity') || '25', 10),
                purchaseCost: getColValue(cols, headerMap, 'cost', 'purchasecost', 'price') || '',
                location: getColValue(cols, headerMap, 'location') || '',
                notes: getColValue(cols, headerMap, 'notes') || ''
            });
        }
    }

    App.bulkState.parsedRows = parsedRows;
    App.bulkState.detectedCount = parsedRows.length;

    if (parsedRows.length === 0) {
        if (previewWrap) previewWrap.style.display = 'none';
        if (resultBanner) {
            resultBanner.style.display = 'block';
            resultBanner.className = 'alert-box alert-error';
            resultBanner.innerHTML = '⚠️ No valid records could be parsed. Check column headers against sample template.';
        }
        if (execBtn) execBtn.disabled = true;
        if (footerSummary) footerSummary.textContent = '0 valid rows detected';
        return;
    }

    // Render Preview Table
    renderBulkPreview(type, parsedRows);

    if (previewWrap) previewWrap.style.display = 'block';
    if (footerSummary) footerSummary.textContent = `✓ ${parsedRows.length} valid records ready to import`;
    if (execBtn) {
        execBtn.disabled = false;
        execBtn.textContent = `Import ${parsedRows.length} Records →`;
    }
}

function renderBulkPreview(type, rows) {
    const thead = document.getElementById('bulk-preview-thead');
    const tbody = document.getElementById('bulk-preview-tbody');
    const countLabel = document.getElementById('bulk-preview-count-label');
    const statusBadge = document.getElementById('bulk-preview-status-badge');

    if (countLabel) countLabel.textContent = `Previewing ${Math.min(6, rows.length)} of ${rows.length} Records`;
    if (statusBadge) statusBadge.textContent = `✓ ${rows.length} Ready`;

    const sample = rows.slice(0, 6);

    if (type === 'assets') {
        thead.innerHTML = `<tr>
            <th>Tag</th>
            <th>Asset Name</th>
            <th>Category</th>
            <th>Model / Make</th>
            <th>Serial</th>
            <th>Status</th>
            <th>Cost</th>
            <th>Assignee</th>
        </tr>`;
        tbody.innerHTML = sample.map(r => `<tr>
            <td><strong style="color:var(--status-blue)">${escapeHtml(r.assetTag)}</strong></td>
            <td>${escapeHtml(r.name)}</td>
            <td><span class="category-tag">${escapeHtml(r.category)}</span></td>
            <td>${escapeHtml((r.manufacturer + ' ' + r.model).trim() || '–')}</td>
            <td style="font-family:var(--font-mono);font-size:11px">${escapeHtml(r.serialNumber || '–')}</td>
            <td><span class="badge ${r.status.toLowerCase().includes('assign') ? 'badge-assigned' : 'badge-available'}">${escapeHtml(r.status)}</span></td>
            <td>${r.purchaseCost ? '$' + escapeHtml(r.purchaseCost) : '–'}</td>
            <td>${escapeHtml(r.assignedEmployeeName || r.assignedEmployeeEmail || '–')}</td>
        </tr>`).join('');
    } else if (type === 'users') {
        thead.innerHTML = `<tr>
            <th>Full Name</th>
            <th>Email</th>
            <th>Department</th>
            <th>Job Title</th>
            <th>Phone</th>
            <th>Role</th>
        </tr>`;
        tbody.innerHTML = sample.map(r => `<tr>
            <td><strong>${escapeHtml(r.fullName)}</strong></td>
            <td style="color:var(--status-blue)">${escapeHtml(r.email)}</td>
            <td>${escapeHtml(r.department || '–')}</td>
            <td>${escapeHtml(r.jobTitle || '–')}</td>
            <td>${escapeHtml(r.phoneNumber || '–')}</td>
            <td><span class="badge ${r.role === 'ADMIN' ? 'badge-danger' : 'badge-primary'}">${escapeHtml(r.role)}</span></td>
        </tr>`).join('');
    } else if (type === 'accessories') {
        thead.innerHTML = `<tr>
            <th>Accessory Name</th>
            <th>Category</th>
            <th>Make / Model</th>
            <th>Quantity</th>
            <th>Cost</th>
            <th>Location</th>
        </tr>`;
        tbody.innerHTML = sample.map(r => `<tr>
            <td><strong>${escapeHtml(r.name)}</strong></td>
            <td><span class="category-tag">${escapeHtml(r.category)}</span></td>
            <td>${escapeHtml((r.manufacturer + ' ' + r.modelNumber).trim() || '–')}</td>
            <td><strong>${r.totalQuantity}</strong></td>
            <td>${r.purchaseCost ? '$' + escapeHtml(r.purchaseCost) : '–'}</td>
            <td>${escapeHtml(r.location || '–')}</td>
        </tr>`).join('');
    } else if (type === 'consumables') {
        thead.innerHTML = `<tr>
            <th>Consumable Item</th>
            <th>Category</th>
            <th>Item Number</th>
            <th>Quantity</th>
            <th>Cost</th>
            <th>Location</th>
        </tr>`;
        tbody.innerHTML = sample.map(r => `<tr>
            <td><strong>${escapeHtml(r.name)}</strong></td>
            <td><span class="category-tag">${escapeHtml(r.category)}</span></td>
            <td style="font-family:var(--font-mono);font-size:11px">${escapeHtml(r.itemNumber || '–')}</td>
            <td><strong>${r.totalQuantity}</strong></td>
            <td>${r.purchaseCost ? '$' + escapeHtml(r.purchaseCost) : '–'}</td>
            <td>${escapeHtml(r.location || '–')}</td>
        </tr>`).join('');
    }
}

async function executeBulkImport() {
    const rows = App.bulkState.parsedRows;
    if (!rows || rows.length === 0) {
        showToast('No records ready to import', 'error');
        return;
    }

    const type = App.bulkState.type;
    const updateExisting = document.getElementById('bulk-opt-update')?.checked || false;
    const autoCreateUsers = document.getElementById('bulk-opt-autouser')?.checked || false;
    const sendWelcomeEmail = document.getElementById('bulk-opt-sendemail')?.checked !== false;
    const execBtn = document.getElementById('btn-execute-bulk');
    const resultBanner = document.getElementById('bulk-result-banner');

    if (execBtn) {
        execBtn.disabled = true;
        execBtn.innerHTML = '⏳ Importing Data...';
    }

    try {
        let endpoint = '/api/bulk/assets';
        let payload = {};

        if (type === 'assets') {
            endpoint = '/api/bulk/assets';
            payload = { assets: rows, updateExisting, autoCreateUsers, sendWelcomeEmail };
        } else if (type === 'users') {
            endpoint = '/api/bulk/users';
            payload = { users: rows, updateExisting, sendWelcomeEmail };
        } else if (type === 'accessories') {
            endpoint = '/api/bulk/accessories';
            payload = { accessories: rows, updateExisting };
        } else if (type === 'consumables') {
            endpoint = '/api/bulk/consumables';
            payload = { consumables: rows, updateExisting };
        }

        const res = await fetch(endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (!res.ok) {
            const errData = await res.json().catch(() => ({ message: res.statusText }));
            throw new Error(errData.message || 'Import failed with server error ' + res.status);
        }

        const data = await res.json();

        // Render summary banner
        if (resultBanner) {
            resultBanner.style.display = 'block';
            resultBanner.className = 'alert-box ' + (data.success ? 'alert-success' : 'alert-warning');
            resultBanner.innerHTML = `
                <div style="font-weight:700;font-size:14px;margin-bottom:8px">
                    ${data.success ? '🎉 Bulk Import Completed Successfully!' : '⚠️ Bulk Import Completed with Notices'}
                </div>
                <div style="font-size:13px;margin-bottom:12px;color:rgba(255,255,255,0.9)">${escapeHtml(data.message || '')}</div>
                <div class="bulk-stats-grid">
                    <div class="bulk-stat-pill" style="background:rgba(255,255,255,0.05)">
                        <span class="num">${data.totalRows}</span>
                        <span class="label">Total Processed</span>
                    </div>
                    <div class="bulk-stat-pill" style="background:var(--status-green-bg);border-color:var(--status-green)">
                        <span class="num" style="color:var(--status-green)">${data.createdCount}</span>
                        <span class="label">Created New</span>
                    </div>
                    <div class="bulk-stat-pill" style="background:var(--status-blue-bg);border-color:var(--status-blue)">
                        <span class="num" style="color:var(--status-blue)">${data.updatedCount}</span>
                        <span class="label">Updated</span>
                    </div>
                    <div class="bulk-stat-pill" style="background:var(--status-amber-bg);border-color:var(--status-amber)">
                        <span class="num" style="color:var(--status-amber)">${data.skippedCount}</span>
                        <span class="label">Skipped</span>
                    </div>
                </div>
                ${(data.warnings && data.warnings.length > 0) ? `
                    <div style="margin-top:10px;font-size:12px;color:var(--status-amber);max-height:80px;overflow-y:auto;">
                        ${data.warnings.map(w => `<div>• ${escapeHtml(w)}</div>`).join('')}
                    </div>` : ''}
            `;
        }

        showToast(`Bulk Import: ${data.createdCount} created, ${data.updatedCount} updated ✓`, 'success');

        if (execBtn) {
            execBtn.textContent = 'Import Completed ✓';
            execBtn.disabled = true;
        }

        // Refresh system data in background
        if (type === 'assets') {
            if (typeof loadAssets === 'function') loadAssets();
            if (typeof loadDashboardSummary === 'function') loadDashboardSummary();
            if (typeof loadActivityLogs === 'function') loadActivityLogs();
        } else if (type === 'users') {
            if (typeof loadUsers === 'function') loadUsers();
            if (typeof loadDashboardSummary === 'function') loadDashboardSummary();
        } else if (type === 'accessories') {
            if (typeof loadAccessories === 'function') loadAccessories();
        } else if (type === 'consumables') {
            if (typeof loadConsumables === 'function') loadConsumables();
        }

    } catch (err) {
        console.error('Bulk import error:', err);
        if (resultBanner) {
            resultBanner.style.display = 'block';
            resultBanner.className = 'alert-box alert-error';
            resultBanner.innerHTML = `<strong>Error during import:</strong> ${escapeHtml(err.message)}`;
        }
        showToast('Bulk import failed: ' + err.message, 'error');
        if (execBtn) {
            execBtn.disabled = false;
            execBtn.textContent = 'Try Import Again →';
        }
    }
}

// -------------------------------------------------------
// GLOBAL SPOTLIGHT SEARCH (Ctrl + K / Cmd + K / /)
// -------------------------------------------------------
function openSpotlightSearch() {
    const modal = document.getElementById('spotlight-search-modal');
    const input = document.getElementById('spotlight-input');
    const results = document.getElementById('spotlight-results');
    if (!modal) return;
    modal.style.display = 'flex';
    if (input) {
        input.value = '';
        setTimeout(() => input.focus(), 60);
    }
    if (results) {
        results.innerHTML = `
            <div style="padding: 28px 16px; text-align: center; color: var(--text-muted); font-size: 13px;">
                <div style="font-size: 24px; margin-bottom: 8px;">🔍</div>
                Type an asset tag, serial, model, employee name, email, software license, or accessory...
            </div>`;
    }
}

function closeSpotlightSearch() {
    const modal = document.getElementById('spotlight-search-modal');
    if (modal) modal.style.display = 'none';
}

function closeSpotlightOnBackdrop(e) {
    if (e.target.id === 'spotlight-search-modal') {
        closeSpotlightSearch();
    }
}

let spotlightTimer = null;
function handleSpotlightInput(val) {
    clearTimeout(spotlightTimer);
    const query = (val || '').trim();
    const results = document.getElementById('spotlight-results');
    if (!results) return;

    if (!query) {
        results.innerHTML = `
            <div style="padding: 28px 16px; text-align: center; color: var(--text-muted); font-size: 13px;">
                <div style="font-size: 24px; margin-bottom: 8px;">🔍</div>
                Type an asset tag, serial, model, employee name, email, software license, or accessory...
            </div>`;
        return;
    }

    spotlightTimer = setTimeout(async () => {
        try {
            results.innerHTML = `<div style="padding: 20px; text-align: center; color: var(--text-muted); font-size: 13px;">Searching all IT records...</div>`;
            const res = await fetch(`/api/system/search?q=${encodeURIComponent(query)}`);
            if (!res.ok) throw new Error('Search failed');
            const data = await res.json();
            renderSpotlightResults(data, query);
        } catch (err) {
            results.innerHTML = `<div style="padding: 16px; text-align: center; color: var(--status-red); font-size: 13px;">Search error: ${escapeHtml(err.message)}</div>`;
        }
    }, 180);
}

function renderSpotlightResults(data, query) {
    const results = document.getElementById('spotlight-results');
    if (!results) return;

    const assets = data.assets || [];
    const users = data.users || [];
    const licenses = data.licenses || [];
    const accessories = data.accessories || [];

    const total = assets.length + users.length + licenses.length + accessories.length;
    if (total === 0) {
        results.innerHTML = `
            <div style="padding: 32px 16px; text-align: center; color: var(--text-muted); font-size: 13px;">
                No IT records found matching "<strong style="color:var(--text-primary)">${escapeHtml(query)}</strong>".
            </div>`;
        return;
    }

    let html = '';

    if (assets.length > 0) {
        html += `<div class="spotlight-section-title">Hardware Assets (${assets.length})</div>`;
        assets.forEach(a => {
            const statusClass = `pill-${a.status.toLowerCase().replace(/_/g, '-')}`;
            html += `
                <div class="spotlight-item" onclick="jumpToAssetFromSpotlight('${escapeHtml(a.assetTag)}')">
                    <div class="spotlight-item-icon">💻</div>
                    <div class="spotlight-item-body">
                        <div class="spotlight-item-title">${escapeHtml(a.name)} <span style="font-size:11px;color:var(--text-muted);font-weight:400">(${escapeHtml(a.assetTag)})</span></div>
                        <div class="spotlight-item-sub">
                            ${escapeHtml(a.manufacturer || '')} ${escapeHtml(a.model || '')} 
                            ${a.assignedEmployeeName ? `&bull; Assigned to <strong style="color:#cbd5e1">${escapeHtml(a.assignedEmployeeName)}</strong>` : '&bull; <span style="color:#10b981">Available</span>'}
                        </div>
                    </div>
                    <span class="pill ${statusClass}" style="font-size:11px;">${a.status.replace(/_/g, ' ')}</span>
                </div>`;
        });
    }

    if (users.length > 0) {
        html += `<div class="spotlight-section-title">Employees &amp; Staff (${users.length})</div>`;
        users.forEach(u => {
            html += `
                <div class="spotlight-item" onclick="jumpToUserFromSpotlight('${escapeHtml(u.email)}')">
                    <div class="spotlight-item-icon">👤</div>
                    <div class="spotlight-item-body">
                        <div class="spotlight-item-title">${escapeHtml(u.fullName)} <span style="font-size:11px;color:var(--text-muted);font-weight:400">(${escapeHtml(u.email)})</span></div>
                        <div class="spotlight-item-sub">${escapeHtml(u.department || 'No Dept')} &bull; ${escapeHtml(u.jobTitle || 'Employee')}</div>
                    </div>
                    <span class="role-pill pill-${u.role === 'ADMIN' ? 'admin' : 'employee'}" style="font-size:11px;">${u.role}</span>
                </div>`;
        });
    }

    if (licenses.length > 0) {
        html += `<div class="spotlight-section-title">Software Licenses (${licenses.length})</div>`;
        licenses.forEach(l => {
            html += `
                <div class="spotlight-item" onclick="jumpToTab('licenses')">
                    <div class="spotlight-item-icon">🔑</div>
                    <div class="spotlight-item-body">
                        <div class="spotlight-item-title">${escapeHtml(l.softwareName)} <span style="font-size:11px;color:var(--text-muted);font-weight:400">(${escapeHtml(l.vendor || '')})</span></div>
                        <div class="spotlight-item-sub">${escapeHtml(l.department || 'All')} &bull; Seats: ${l.usedSeats || 0} / ${l.totalSeats || 1} &bull; ${l.licenseType || 'Subscription'}</div>
                    </div>
                    <span class="category-tag" style="font-size:11px;">License</span>
                </div>`;
        });
    }

    if (accessories.length > 0) {
        html += `<div class="spotlight-section-title">Accessories (${accessories.length})</div>`;
        accessories.forEach(acc => {
            html += `
                <div class="spotlight-item" onclick="jumpToTab('accessories')">
                    <div class="spotlight-item-icon">🔌</div>
                    <div class="spotlight-item-body">
                        <div class="spotlight-item-title">${escapeHtml(acc.name)}</div>
                        <div class="spotlight-item-sub">${escapeHtml(acc.category || 'Accessory')} &bull; In Stock: ${acc.totalQuantity - acc.checkedOutQuantity} / ${acc.totalQuantity}</div>
                    </div>
                    <span class="cond-badge cond-good" style="font-size:11px;">Hardware</span>
                </div>`;
        });
    }

    results.innerHTML = html;
}

function jumpToAssetFromSpotlight(assetTag) {
    closeSpotlightSearch();
    switchTab('assets');
    const searchInput = document.getElementById('asset-search-input');
    if (searchInput) {
        searchInput.value = assetTag;
        loadAssets();
    }
}

function jumpToUserFromSpotlight(email) {
    closeSpotlightSearch();
    switchTab('users');
    const searchInput = document.getElementById('user-search-input');
    if (searchInput) {
        searchInput.value = email;
        filterUsersList();
    }
}

function jumpToTab(tabName) {
    closeSpotlightSearch();
    switchTab(tabName);
}



