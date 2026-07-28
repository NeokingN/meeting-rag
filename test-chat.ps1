$body = @{
    question = "Q3产品会定了哪些排期和责任人？"
    userId = "user-001"
    department = "产品部"
} | ConvertTo-Json

$response = Invoke-RestMethod -Uri 'http://localhost:8081/api/v1/rag/chat' `
    -Method Post `
    -ContentType 'application/json; charset=utf-8' `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($body))

$response | ConvertTo-Json -Depth 10
