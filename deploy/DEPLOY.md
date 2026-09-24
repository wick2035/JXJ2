# JXJ2 上传与部署

部署地址：`http://47.96.253.93:5858`。Nginx 在 `5858` 提供前端，并将 `/api/` 转发到仅监听本机 `8082` 的后端。

## 上传文件

将压缩包解压到 `/www/wwwroot/jxj`，目录结构应为：

```text
/www/wwwroot/jxj/
├── jxj-1.0.0.jar
├── dist/
│   ├── index.html
│   └── assets/
├── nginx-jxj-5858.conf
├── jxj.service
├── jxj.env.example
└── DEPLOY.md
```

前端构建已使用同源 `/api`，无需再修改接口地址。部署包内不含数据库密码和 JWT 密钥。

## 后端

服务器需要 Java 11 或更高版本，以及现有的 `eval_system` 数据库。按服务器的实际 Java 路径和运行用户调整 `jxj.service`，并确保该用户可以写入 uploads 目录。

```bash
mkdir -p /www/wwwroot/jxj/uploads /etc/jxj
cp /www/wwwroot/jxj/jxj.env.example /etc/jxj/jxj.env
chmod 600 /etc/jxj/jxj.env
```

编辑 `/etc/jxj/jxj.env`，填写 `DB_PASSWORD` 和足够长的随机 `JWT_SIGNING_KEY`。该文件不要放在网站根目录，也不要提交到 Git。之后安装服务：

```bash
cp /www/wwwroot/jxj/jxj.service /etc/systemd/system/jxj.service
systemctl daemon-reload
systemctl enable --now jxj
systemctl status jxj
```

如果使用宝塔 Java 项目管理器，可直接指定 jar 路径并设置 `jxj.env.example` 所列环境变量，无需安装 systemd 服务。

## 前端

将 `nginx-jxj-5858.conf` 作为 Nginx 站点配置，或将其中的 `server` 块加入宝塔的 Nginx 配置。检查后重载：

```bash
nginx -t
systemctl reload nginx
```

在服务器防火墙和云安全组开放 TCP `5858`。后端 `8082` 只监听 `127.0.0.1`，无需对公网开放。

## 检查

```bash
curl -I http://127.0.0.1:5858/
curl -I http://127.0.0.1:5858/api/categories
```

第二条请求可能返回鉴权错误，但不应返回前端的 `index.html`。然后访问 `http://47.96.253.93:5858` 并测试登录。
