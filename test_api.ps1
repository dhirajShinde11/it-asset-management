# 0. Authenticate as Admin
$loginBody = @{
    email = "dhiraj@bmconsulting.co"
    password = "12345"
} | ConvertTo-Json
$loginRes = Invoke-RestMethod -Uri "http://localhost:8085/api/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$headers = @{ 
    "Content-Type" = "application/json"
    "Authorization" = "Bearer $($loginRes.token)"
}

# 1. Dashboard summary
$summary = Invoke-RestMethod -Uri "http://localhost:8085/api/dashboard/summary" -Method Get -Headers $headers
Write-Host "Initial Total Assets: $($summary.totalAssets)"
Write-Host "Total Portfolio Value: `$$($summary.totalAssetValue)"

# 2. Register New Asset
$randomId = Get-Random -Minimum 1000 -Maximum 9999
$assetBody = @{
    assetTag = "AST-TEST-$randomId"
    name = "MacBook Pro 16 M3 Max"
    category = "LAPTOP"
    manufacturer = "Apple"
    model = "MacBook Pro 16"
    serialNumber = "C02XYZ$randomId"
    purchaseCost = 3499.00
    status = "AVAILABLE"
    condition = "NEW"
    location = "HQ - IT Storage Room 3"
    specifications = "36GB RAM, 1TB SSD, Space Black"
} | ConvertTo-Json

$created = Invoke-RestMethod -Uri "http://localhost:8085/api/assets" -Method Post -Body $assetBody -Headers $headers
Write-Host "Created Asset ID: $($created.id), Tag: $($created.assetTag), Status: $($created.status)"

# 3. Assign Asset
$assignBody = @{
    employeeName = "Alex Mercer"
    employeeEmail = "alex.mercer@company.com"
    department = "Engineering"
    assignedDate = "2026-10-06"
} | ConvertTo-Json

$assigned = Invoke-RestMethod -Uri "http://localhost:8085/api/assets/$($created.id)/assign" -Method Patch -Body $assignBody -Headers $headers
Write-Host "Assigned Asset: Status=$($assigned.status), Employee=$($assigned.assignedEmployeeName)"

# 4. Log Maintenance
$maintBody = @{
    assetId = $created.id
    title = "RAM and SSD diagnostics"
    description = "Hardware inspection and thermal paste replacement"
    serviceProvider = "AppleCare Enterprise"
    cost = 150.00
    serviceDate = "2026-10-06"
    status = "IN_PROGRESS"
} | ConvertTo-Json

$maint = Invoke-RestMethod -Uri "http://localhost:8085/api/assets/$($created.id)/maintenance" -Method Post -Body $maintBody -Headers $headers
Write-Host "Maintenance Logged ID: $($maint.id), Type=$($maint.maintenanceType), Cost=`$$($maint.cost)"

# 5. Unassign Asset
$unassigned = Invoke-RestMethod -Uri "http://localhost:8085/api/assets/$($created.id)/unassign" -Method Patch -Headers $headers
Write-Host "Unassigned Asset: Status=$($unassigned.status)"

# 6. Delete Test Asset
Invoke-RestMethod -Uri "http://localhost:8085/api/assets/$($created.id)" -Method Delete -Headers $headers
Write-Host "Cleaned up test asset $($created.id)"
