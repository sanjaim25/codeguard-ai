# Push CodeGuard AI to a new GitHub repository
# Prerequisites: Create an empty GitHub repo (no README), then run this script
# Usage: .\push-to-github.ps1 -RepoUrl https://github.com/YOUR_USERNAME/codeguard-ai.git
param(
    [Parameter(Mandatory=$true)]
    [string]$RepoUrl
)

$ErrorActionPreference = 'Stop'

Write-Host "Adding remote origin: $RepoUrl" -ForegroundColor Cyan
git remote add origin $RepoUrl

Write-Host "Renaming branch to main..." -ForegroundColor Cyan
git branch -M main

Write-Host "Pushing to GitHub..." -ForegroundColor Cyan
git push -u origin main
git push origin --tags

$actionsUrl = $RepoUrl -replace '\.git$', '/actions'
Write-Host ""
Write-Host "Done! Next steps:" -ForegroundColor Green
Write-Host "  1. CI will auto-run at: $actionsUrl"
Write-Host "  2. Create GitHub App per docs/GITHUB_SETUP.md"
Write-Host "  3. Set repo secrets: GITHUB_APP_ID, GITHUB_CLIENT_ID, GITHUB_CLIENT_SECRET, etc."
Write-Host "  4. Set AI secret: AI_API_KEY (optional, for live AI reviews)"
