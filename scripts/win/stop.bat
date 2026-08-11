@echo off
chcp 65001 >nul
echo ========================================
echo  博物馆购票系统 - 停止服务
echo ========================================
echo.

docker compose down

echo.
echo 服务已停止
pause
