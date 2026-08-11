@echo off
chcp 65001 >nul
echo ========================================
echo  博物馆购票系统 - 启动中间件
echo  (MySQL / Redis / RabbitMQ)
echo ========================================
echo.

REM 检查 Docker 是否安装
docker --version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未检测到 Docker，请先安装 Docker Desktop for Windows
    echo 下载地址: https://www.docker.com/products/docker-desktop
    pause
    exit /b 1
)

REM 检查 Docker 是否运行
docker info >nul 2>&1
if errorlevel 1 (
    echo [错误] Docker 未运行，请先启动 Docker Desktop
    pause
    exit /b 1
)

REM 检查 docker compose 是否可用
docker compose version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未检测到 docker compose，请确保 Docker Desktop 已正确安装
    pause
    exit /b 1
)

echo [信息] Docker 环境检查通过
echo.

REM 检查 miaosha.sql 是否存在
if not exist "miaosha.sql" (
    echo [警告] 未找到 miaosha.sql 文件
    echo 请确保数据库初始化脚本存在，否则 MySQL 将无法初始化
    echo.
    set /p continue="是否继续启动？(y/n): "
    if /i not "%continue%"=="y" (
        echo 已取消启动
        pause
        exit /b 0
    )
)

echo [信息] 开始启动中间件服务...
echo.

REM 启动中间件
echo [步骤 1/2] 启动 MySQL / Redis / RabbitMQ...
docker compose up -d mysql redis rabbitmq

if errorlevel 1 (
    echo [错误] 服务启动失败
    docker compose logs
    pause
    exit /b 1
)

REM 等待服务就绪
echo [步骤 2/2] 等待服务就绪（约 30 秒）...
timeout /t 30 /nobreak >nul

echo.
echo ========================================
echo  中间件启动完成！
echo ========================================
echo.
echo 服务端口:
echo   - MySQL:        localhost:3306 (root/root)
echo   - Redis:        localhost:6379
echo   - RabbitMQ:     localhost:5672 (guest/guest)
echo   - RabbitMQ 管理台: http://localhost:15672
echo.
echo 下一步: 在 IDEA 中启动 MiaoshaWebApplication
echo.
echo 停止服务: stop.bat
echo 查看日志: logs.bat
echo.

echo 按任意键查看服务状态...
pause >nul

docker compose ps

echo.
pause
