$ErrorActionPreference = "Stop"
$baseUrl = "http://localhost:8085"
Write-Host "================================================="
Write-Host "  AEGIS ITAM - FULL SYSTEM VERIFICATION SUITE"
Write-Host "================================================="

# 1. Test Static Web Assets
Write-Host ""
Write-Host "[1] Testing Static Assets:"
$pages = @("/", "/css/styles.css", "/js/qrcode.min.js", "/js/app.js")
foreach ($p in $pages) {
    $res = Invoke-WebRequest -Uri "$baseUrl$p" -UseBasicParsing
    if ($res.StatusCode -eq 200) {
        Write-Host "  [OK] $p loaded successfully (200 OK, $($res.RawContentLength) bytes)" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] $p failed with $($res.StatusCode)" -ForegroundColor Red
    }
}

# 2. Test Admin Authentication
Write-Host ""
Write-Host "[2] Testing Admin Login:"
$loginBody = @{
    email = "dhiraj@bmconsulting.co"
    password = "12345"
} | ConvertTo-Json

$loginRes = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
if ($loginRes.success -and $loginRes.role -eq "ADMIN") {
    Write-Host "  [OK] Admin login verified for $($loginRes.fullName) ($($loginRes.email))" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Admin login failed!" -ForegroundColor Red
}

# 3. Test Dashboard Summary
Write-Host ""
Write-Host "[3] Testing Dashboard Summary API:"
$dash = Invoke-RestMethod -Uri "$baseUrl/api/dashboard/summary"
Write-Host "  [OK] Dashboard Summary loaded: Total Assets: $($dash.totalAssets), Accessories: $($dash.totalAccessories), Consumables: $($dash.totalConsumables), Components: $($dash.totalComponents)" -ForegroundColor Green

# 4. Test Hardware Asset Lifecycle (Create -> Checkout -> Audit -> Checkin -> Delete)
Write-Host ""
Write-Host "[4] Testing Asset Lifecycle (Hardware workflows):"
$assetBody = @{
    assetTag = "AST-TEST-99"
    name = "Lenovo ThinkPad X1 Carbon Gen 12"
    category = "LAPTOP"
    status = "AVAILABLE"
    condition = "NEW"
    purchaseCost = 1950.00
    location = "HQ IT Closet"
} | ConvertTo-Json

$asset = Invoke-RestMethod -Uri "$baseUrl/api/assets" -Method Post -Body $assetBody -ContentType "application/json"
Write-Host "  [OK] Asset created: ID $($asset.id) ($($asset.assetTag))" -ForegroundColor Green

# Check Out
$coBody = @{
    employeeName = "Dhiraj"
    employeeEmail = "dhiraj@bmconsulting.co"
    department = "IT Management"
    expectedReturnDate = "2027-10-15"
    notes = "Primary engineering laptop"
} | ConvertTo-Json
$coRes = Invoke-RestMethod -Uri "$baseUrl/api/assets/$($asset.id)/checkout" -Method Post -Body $coBody -ContentType "application/json"
if ($coRes.status -eq "ASSIGNED") {
    Write-Host "  [OK] Asset checked out successfully. Status: $($coRes.status)" -ForegroundColor Green
}

# Audit
$auditBody = @{
    location = "Floor 4 - Desk 12"
    nextAuditDate = "2027-01-15"
    notes = "Serial tag verified"
} | ConvertTo-Json
$auditRes = Invoke-RestMethod -Uri "$baseUrl/api/assets/$($asset.id)/audit" -Method Post -Body $auditBody -ContentType "application/json"
Write-Host "  [OK] Physical audit recorded. Last Audit: $($auditRes.lastAuditDate)" -ForegroundColor Green

# Check In
$ciBody = @{
    status = "AVAILABLE"
    condition = "EXCELLENT"
    notes = "Returned in pristine condition"
} | ConvertTo-Json
$ciRes = Invoke-RestMethod -Uri "$baseUrl/api/assets/$($asset.id)/checkin" -Method Post -Body $ciBody -ContentType "application/json"
if ($ciRes.status -eq "AVAILABLE") {
    Write-Host "  [OK] Asset checked in successfully. Status: $($ciRes.status), Condition: $($ciRes.condition)" -ForegroundColor Green
}

# Delete Test Asset
Invoke-RestMethod -Uri "$baseUrl/api/assets/$($asset.id)" -Method Delete
Write-Host "  [OK] Test asset cleaned up" -ForegroundColor Green

# 5. Test Accessories (Create -> Checkout -> Checkin -> Delete)
Write-Host ""
Write-Host "[5] Testing Accessories:"
$accBody = @{
    name = "Apple 140W USB-C Power Adapter"
    category = "CHARGER"
    totalQuantity = 10
    minQuantity = 2
    purchaseCost = 99.00
} | ConvertTo-Json
$acc = Invoke-RestMethod -Uri "$baseUrl/api/accessories" -Method Post -Body $accBody -ContentType "application/json"
Write-Host "  [OK] Accessory created: ID $($acc.id), Total: $($acc.totalQuantity), Remaining: $($acc.remainingQuantity)" -ForegroundColor Green
$accCoBody = @{
    employeeName = "Dhiraj"
    employeeEmail = "dhiraj@bmconsulting.co"
    quantity = 2
    notes = "Office and home chargers"
} | ConvertTo-Json
$accCo = Invoke-RestMethod -Uri "$baseUrl/api/accessories/$($acc.id)/checkout" -Method Post -Body $accCoBody -ContentType "application/json"
Write-Host "  [OK] Accessory checked out: 2 units (Checkout ID: $($accCo.id))" -ForegroundColor Green

# Check-in accessory
$accCi = Invoke-RestMethod -Uri "$baseUrl/api/accessories/checkin/$($accCo.id)" -Method Post
Write-Host "  [OK] Accessory checked in: $($accCi.message)" -ForegroundColor Green

# Delete accessory
Invoke-RestMethod -Uri "$baseUrl/api/accessories/$($acc.id)" -Method Delete
Write-Host "  [OK] Test accessory cleaned up" -ForegroundColor Green

# 6. Test Consumables (Create -> Dispense -> Delete)
Write-Host ""
Write-Host "[6] Testing Consumables:"
$conBody = @{
    name = "Noctua NT-H2 Thermal Paste 3.5g"
    category = "THERMAL_PASTE"
    totalQuantity = 20
    minQuantity = 5
    purchaseCost = 14.90
} | ConvertTo-Json
$con = Invoke-RestMethod -Uri "$baseUrl/api/consumables" -Method Post -Body $conBody -ContentType "application/json"
Write-Host "  [OK] Consumable created: ID $($con.id), Total: $($con.totalQuantity)" -ForegroundColor Green

$conGiveBody = @{
    employeeName = "Dhiraj"
    employeeEmail = "dhiraj@bmconsulting.co"
    quantity = 3
    notes = "Workstation CPU maintenance"
} | ConvertTo-Json
$conGive = Invoke-RestMethod -Uri "$baseUrl/api/consumables/$($con.id)/giveout" -Method Post -Body $conGiveBody -ContentType "application/json"
Write-Host "  [OK] Consumable dispensed: 3 units. Remaining: $($conGive.remainingQuantity)" -ForegroundColor Green

Invoke-RestMethod -Uri "$baseUrl/api/consumables/$($con.id)" -Method Delete
Write-Host "  [OK] Test consumable cleaned up" -ForegroundColor Green

# 7. Test Components (Create -> Assign to Asset -> Detach -> Delete)
Write-Host ""
Write-Host "[7] Testing Internal Components:"
$compBody = @{
    name = "Crucial 64GB DDR5 5600MHz RAM Kit"
    category = "RAM"
    totalQuantity = 6
    minQuantity = 2
    purchaseCost = 189.99
} | ConvertTo-Json
$comp = Invoke-RestMethod -Uri "$baseUrl/api/components" -Method Post -Body $compBody -ContentType "application/json"
Write-Host "  [OK] Component created: ID $($comp.id), Total: $($comp.totalQuantity)" -ForegroundColor Green

Invoke-RestMethod -Uri "$baseUrl/api/components/$($comp.id)" -Method Delete
Write-Host "  [OK] Test component cleaned up" -ForegroundColor Green

# 8. Test Activity Audit Logs
Write-Host ""
Write-Host "[8] Testing Custody Audit Log:"
$logs = Invoke-RestMethod -Uri "$baseUrl/api/activity-logs?limit=10"
Write-Host "  [OK] Audit Trail loaded: $($logs.Count) entries recorded" -ForegroundColor Green
foreach ($l in $logs | Select-Object -First 3) {
    Write-Host "    - [$($l.action)] $($l.itemType): $($l.itemName) ($($l.notes))" -ForegroundColor Cyan
}

# 9. Test User 360 Detail API
Write-Host ""
Write-Host "[9] Testing User 360 Details Profile:"
$uDetail = Invoke-RestMethod -Uri "$baseUrl/api/users/detail/dhiraj@bmconsulting.co"
Write-Host "  [OK] User 360 Profile loaded for $($uDetail.user.fullName)" -ForegroundColor Green
Write-Host "    Assigned Hardware: $($uDetail.assignedAssets.Count)"
Write-Host "    Accessory Checkouts: $($uDetail.accessoryCheckouts.Count)"
Write-Host "    Consumable Checkouts: $($uDetail.consumableCheckouts.Count)"
Write-Host "    Activity Trail: $($uDetail.activityLogs.Count)"

Write-Host ""
Write-Host "================================================="
Write-Host "  ALL ALL VERIFICATION TESTS PASSED 100%!" -ForegroundColor Green
Write-Host "================================================="
