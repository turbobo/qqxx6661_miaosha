@echo off
chcp 65001 >nul
echo ========================================
echo  博物馆购票系统 - 日志查看
echo ========================================
echo.
echo 选择要查看的服务:
echo   1. MySQL 数据库
echo   2. Redis 缓存
echo   3. RabbitMQ 消息队列
echo   4. App 应用服务（全栈模式）
echo   5. 所有服务
echo.

set /p choice="请输入选项 (1-5): "

if "%choice%"=="1" docker compose logs -f mysql
if "%choice%"=="2" docker compose logs -f redis
if "%choice%"=="3" docker compose logs -f rabbitmq
if "%choice%"=="4" docker compose logs -f app
if "%choice%"=="5" docker compose logs -f

echo.
pause
