# 综合素质评价系统 (2.0)

学生综合素质评价 / 综测考评系统。基于角色（管理员 / 教师 / 学生）的奖项申报、审核、评分与批次管理平台。

- 后端：Spring Boot 2.7 + MyBatis-Plus + Spring Security (JWT) + MySQL 8
- 前端：React 19 + Vite + TypeScript + Ant Design 6 + Zustand
- 后端端口：`8081`，前端开发端口：`5173`

---

## 目录

- [功能概览](#功能概览)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [环境要求](#环境要求)
- [本地开发](#本地开发)
- [默认账号](#默认账号)
- [配置说明](#配置说明)
- [生产构建](#生产构建)
- [宝塔面板部署](#宝塔面板部署)
- [常见问题](#常见问题)

---

## 功能概览

| 角色 | 主要功能 |
| --- | --- |
| **学生** | 个人看板、奖项申报（含附件上传）、申报记录查看与撤回、消息通知 |
| **教师** | 审核队列、申报详情审核（通过 / 驳回 / 退回修改）、批量审核 |
| **管理员** | 用户管理、奖项库 / 奖项类别维护、评价批次管理（定时开放、目标班级）、操作日志、系统配置、公告通知 |

系统能力：JWT 鉴权与刷新、强制修改初始密码、账号状态/登录失败锁定、自动评分计算、操作日志审计、Excel 导出。

## 技术栈

**后端**

- Spring Boot `2.7.18` / Java `11`
- MyBatis-Plus `3.5.5`（逻辑删除、UUID 主键）
- Spring Security + JWT (`jjwt 0.11.5`)
- MySQL `8.x`（`mysql-connector-j`）
- Apache POI `5.2.5`（Excel 导出）
- Lombok

**前端**

- React `19` + React Router `7`
- Vite `8` + TypeScript
- Ant Design `6` + `@ant-design/icons`
- Zustand（状态管理）+ Axios（请求）+ dayjs

## 项目结构

```
JXJ2/
├── backend/                      # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/eval/jxj/
│       │   ├── controller/       # REST 接口
│       │   ├── service/          # 业务逻辑（评分计算、奖项、鉴权）
│       │   ├── entity/ mapper/   # 数据模型 + MyBatis-Plus Mapper
│       │   ├── security/         # JWT 过滤器、登录用户、强制改密
│       │   ├── config/           # Security / CORS / MVC / MyBatis 配置
│       │   └── common/           # 统一返回、全局异常
│       └── resources/
│           └── application.yml   # 数据库、JWT、上传等配置
├── frontend/                     # React 前端
│   └── src/
│       ├── api/                  # 接口封装（client.ts 含 baseURL）
│       ├── pages/                # 按角色划分页面 admin/teacher/student/...
│       ├── routes/ layouts/ store/ components/ hooks/ utils/ types/
├── sql/                          # 数据库脚本
│   ├── schema.sql                # 完整建库脚本（含初始账号）
│   └── *.sql                     # 增量迁移脚本（按日期）
└── README.md
```

## 环境要求

| 软件 | 版本 |
| --- | --- |
| JDK | 11+ |
| Maven | 3.8+ |
| Node.js | 20+ |
| MySQL | 8.0+ |

## 本地开发

### 1. 初始化数据库

`schema.sql` 已包含 `CREATE DATABASE IF NOT EXISTS eval_system`（字符集 `utf8mb4`）及初始账号，直接导入即可：

```bash
mysql -uroot -p eval_system < sql/schema.sql
# 若数据库尚未创建，schema.sql 内部已带 CREATE DATABASE，可直接：
# mysql -uroot -p < sql/schema.sql
```

如有增量脚本（`sql/2026-*.sql` 等），按日期顺序依次导入。

### 2. 启动后端

修改 [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml) 中的数据库账号密码后：

```bash
cd backend
mvn spring-boot:run
# 或先打包再运行
mvn clean package -DskipTests
java -jar target/jxj-1.0.0.jar
```

后端启动于 `http://localhost:8081`。

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端开发服务器 `http://localhost:5173`，通过 `src/api/client.ts` 调用后端 `http://localhost:8081`。

## 默认账号

`schema.sql` 内置初始账号（密码均为 bcrypt 存储）：

| 角色 | 账号 | 初始密码 |
| --- | --- | --- |
| 管理员 | `admin` | `admin123` |

> 教师 / 学生账号由 `schema.sql` 中的示例数据或管理员后台创建。首次登录如触发"强制修改密码"，请按提示修改。

## 配置说明

后端配置位于 [application.yml](backend/src/main/resources/application.yml)：

| 配置项 | 说明 |
| --- | --- |
| `server.port` | 后端端口，默认 `8081` |
| `spring.datasource.*` | MySQL 连接地址、账号、密码 |
| `jwt.secret` | JWT 签名密钥（**生产环境务必修改**） |
| `jwt.expiration` | 访问令牌有效期（毫秒，默认 2 小时） |
| `jwt.refresh-expiration` | 刷新令牌有效期（默认 7 天） |
| `upload.path` | 附件上传目录，默认 `./uploads` |
| `spring.servlet.multipart` | 单文件 10MB / 单请求 50MB 上限 |

> **前端接口地址**：[frontend/src/api/client.ts](frontend/src/api/client.ts) 中 `baseURL` 默认硬编码为 `http://localhost:8081`。
> 部署到服务器时建议改为**相对路径**（见下方宝塔部署），由 Nginx 反向代理转发，避免跨域与地址写死问题。

## 生产构建

**后端打包**（生成可执行 jar）：

```bash
cd backend
mvn clean package -DskipTests
# 产物：backend/target/jxj-1.0.0.jar
```

**前端打包**（生成静态文件）：

```bash
cd frontend
npm install
npm run build
# 产物：frontend/dist/
```

---

## 宝塔面板部署

以下假设服务器为 Linux + 已安装宝塔面板（aaPanel/宝塔）。整体方案：**Nginx 托管前端静态文件 + 反向代理 `/api` 到后端 jar + MySQL 提供数据库**。

### 0. 安装宝塔环境软件

在宝塔【软件商店】安装：

- **MySQL 8.0**
- **Nginx**（建议 1.20+）
- **Java 项目一键部署**（或在【软件商店 → 运行环境】安装 **JDK 11/17**）
- （可选）**Maven**、**Node.js 版本管理器**（也可在本地构建后上传产物）

### 1. 创建数据库

宝塔【数据库】→ 添加数据库：

- 数据库名：`eval_system`，字符集 `utf8mb4`
- 记录生成的用户名与密码

导入表结构：在该数据库的【导入】中上传并执行 `sql/schema.sql`，再依次执行增量脚本。

### 2. 部署后端 jar

1. 本地执行 `mvn clean package -DskipTests`，得到 `backend/target/jxj-1.0.0.jar`。
2. 将 jar 上传到服务器，例如 `/www/wwwroot/jxj/jxj-1.0.0.jar`。
3. **配置数据库连接**：推荐用启动参数覆盖，无需改动 jar 内的 `application.yml`：

   ```bash
   java -jar /www/wwwroot/jxj/jxj-1.0.0.jar \
     --server.port=8081 \
     --spring.datasource.url="jdbc:mysql://127.0.0.1:3306/eval_system?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8&allowPublicKeyRetrieval=true" \
     --spring.datasource.username=eval_system \
     --spring.datasource.password=你的数据库密码 \
     --jwt.secret=请替换为你自己的足够长的密钥 \
     --upload.path=/www/wwwroot/jxj/uploads
   ```

4. **设为常驻服务**：用宝塔【Java 项目】管理器添加项目（指定 jar 路径、端口 8081、上面的启动参数），或用【软件商店 → Supervisor 管理器】守护进程，保证开机自启与异常重启。

### 3. 配置前端接口地址（重要）

由于 `client.ts` 中 `baseURL` 写死为 `http://localhost:8081`，部署前需改为相对路径，让请求走当前域名再由 Nginx 代理：

将 [frontend/src/api/client.ts](frontend/src/api/client.ts) 中

```ts
const client = axios.create({
  baseURL: 'http://localhost:8081',
  timeout: 30000,
});
```

改为：

```ts
const client = axios.create({
  baseURL: '/',   // 走同源，由 Nginx 反向代理到后端
  timeout: 30000,
});
```

然后执行 `npm run build`，把生成的 `frontend/dist/` 上传到服务器，例如 `/www/wwwroot/jxj/dist`。

### 4. 创建站点并配置 Nginx

宝塔【网站】→ 添加站点（绑定你的域名，PHP 选纯静态）。站点根目录指向前端产物目录 `/www/wwwroot/jxj/dist`。

在站点【设置 → 配置文件】中加入反向代理与单页应用回退：

```nginx
server {
    listen 80;
    server_name your-domain.com;
    root /www/wwwroot/jxj/dist;
    index index.html;

    # 前端单页应用：找不到文件时回退到 index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 后端接口反向代理
    location /api/ {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 附件等上传文件访问（如需直接通过 Nginx 访问上传目录，可按需开启）
    # location /uploads/ {
    #     alias /www/wwwroot/jxj/uploads/;
    # }
}
```

保存后重载 Nginx。所有接口请求 `/api/...` 将被转发到本机 8081 端口的后端。

### 5. 放行端口与 HTTPS

- 数据库 3306、后端 8081 **仅本机访问即可**，无需对公网放行（更安全）。
- 仅放行 80 / 443（在宝塔【安全】及云服务商安全组中配置）。
- 在站点【SSL】中申请/部署证书，开启「强制 HTTPS」。

### 6. 验证

浏览器访问 `https://your-domain.com`，用 `admin / admin123` 登录，登录后立即修改默认密码。

---

## 常见问题

- **登录后接口 401 / 跨域**：确认前端 `baseURL` 已改为相对路径且 Nginx 正确代理 `/api/`；JWT 过期会自动跳转登录页。
- **附件上传失败**：检查 `upload.path` 目录是否存在且后端进程有写权限；注意 Nginx `client_max_body_size` 需 ≥ 50MB。
- **数据库连接失败**：核对 `application.yml` 或启动参数中的地址/账号/密码，时区参数 `serverTimezone=Asia/Shanghai`。
- **中文乱码**：数据库与连接字符集需统一为 `utf8mb4`。
- **后端进程退出**：使用宝塔 Java 项目管理器 / Supervisor 守护，避免 SSH 断开后进程被终止。

---

> 生产环境请务必修改：数据库密码、`jwt.secret`、默认管理员密码。
