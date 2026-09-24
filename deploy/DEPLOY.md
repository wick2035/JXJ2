# JXJ2 宝塔项目配置

## 前端

前端静态文件已放在 `/www/wwwroot/JXJ2/frontend/dist`。这是 React/Vite 构建产物，宝塔中应选择 **HTML项目**，将网站根目录设为该 `dist` 目录，监听端口设为 `5858`。无需为静态文件选择可执行文件或填写执行命令。

在站点的 Nginx 配置中，将 `/api/` 转发到 `http://127.0.0.1:8082`，并让前端路由回退到 `index.html`。本目录的 `nginx-jxj-5858.conf` 提供了可参考的完整 `server` 块。保存配置后运行 `nginx -t`，再重载 Nginx。

服务器防火墙和云安全组需要放行 TCP `5858`。站点启用后检查：

```bash
curl -I http://127.0.0.1:5858/
curl -I http://127.0.0.1:5858/api/categories
```

第二条请求未登录时可能返回 401，这是后端的鉴权响应。

## 后端

当前后端 jar 位于 `/www/wwwroot/JXJ2/backend/jxj-1.0.0.jar`，由宝塔 Java 项目 `spring_jxj-1` 管理，端口为 `8082`。数据库连接和 JWT 密钥应在宝塔的 **Java项目** 设置中配置。宝塔重启项目时会重写 `/var/tmp/springboot/vhost/env/jxj-1.env`，不要只手动编辑该文件。

当前 jar 可使用的环境变量：`SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD`、`JWT_SECRET`、`UPLOAD_PATH`。数据库密码和 JWT 密钥不要放进前端文件或 Git。`jxj.service` 是独立 systemd 部署的备选模板，当前服务器无需安装。
