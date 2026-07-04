# Bubble AI Docker Compose 部署

这套部署不包含 Nginx，公网入口由同一台服务器上的 Higress 网关统一转发。Compose 只负责启动后端微服务和依赖：

- `bubble-ai-user`：绑定 `10.0.0.2:8124`，接口前缀 `/api/user/**`
- `bubble-ai-app`：绑定 `10.0.0.2:8125`，接口前缀 `/api/app/**`、`/api/chatHistory/**`、`/api/static/**`、`/api/output_covers/**`
- MySQL、Redis、Nacos：仅作为后端依赖使用

## 1. Higress 路由

在 Higress 中配置路由：

```text
/api/user/**          -> http://10.0.0.2:8124
/api/app/**           -> http://10.0.0.2:8125
/api/chatHistory/**   -> http://10.0.0.2:8125
/api/static/**        -> http://10.0.0.2:8125
/api/output_covers/** -> http://10.0.0.2:8125
```

后端服务本身已经配置了 `server.servlet.context-path=/api`，所以 Higress 路由转发时保留原始路径即可，不要把 `/api` 前缀剥掉。

因为你的服务器内网 IP 是 `10.0.0.2`，`.env` 保持 `BACKEND_BIND_HOST=10.0.0.2` 即可。这样 `8124/8125` 绑定在内网地址上，公网流量仍然通过 Higress 进入。

## 2. 服务器准备

Ubuntu/Debian 安装 Docker：

```bash
curl -fsSL https://get.docker.com | bash
docker compose version
```

安全组/防火墙建议：

- 对公网开放 Higress 使用的 `80/443`
- 后端 `8124/8125` 只绑定 `10.0.0.2`，不要在公网安全组放行
- 不要公网开放 `3306`、`6379`、`8848`

## 3. 配置环境变量

进入项目根目录：

```bash
cp deploy/.env.example .env
vim .env
```

必须修改：

- `MYSQL_PASSWORD`
- `MYSQL_ROOT_PASSWORD`
- `REDIS_PASSWORD`
- `KNIFE4J_BASIC_PASSWORD`
- `AI_CHAT_API_KEY`
- `AI_STREAMING_API_KEY`
- `AI_REASONING_API_KEY`
- `AI_ROUTING_API_KEY`

确认 `PUBLIC_BASE_URL` 是 Higress 对外访问地址，例如：

```text
PUBLIC_BASE_URL=http://49.235.165.209
```

## 4. 初始化数据库

当前仓库没有 SQL 初始化脚本。首次启动前，把本地数据库结构或数据导出到：

```text
deploy/mysql/init/01-schema.sql
```

示例：

```bash
mysqldump -h 127.0.0.1 -uroot -p bubble_ai_backend > deploy/mysql/init/01-schema.sql
```

`deploy/mysql/init/*.sql` 只会在 MySQL volume 首次创建时自动执行。已经启动过再补 SQL，需要手动导入：

```bash
docker compose --env-file .env -f deploy/docker-compose.yml exec -T mysql \
  sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' < deploy/mysql/init/01-schema.sql
```

## 5. 构建并启动

```bash
docker compose --env-file .env -f deploy/docker-compose.yml up -d --build
```

查看状态和日志：

```bash
docker compose --env-file .env -f deploy/docker-compose.yml ps
docker compose --env-file .env -f deploy/docker-compose.yml logs -f bubble-ai-user
docker compose --env-file .env -f deploy/docker-compose.yml logs -f bubble-ai-app
```

本机端口检查：

```bash
curl http://10.0.0.2:8124/api/user/get/login
curl "http://10.0.0.2:8125/api/app/get/vo?id=1"
```

通过 Higress 检查：

```bash
curl http://49.235.165.209/api/user/get/login
```

未登录返回业务错误也正常，重点是请求能到达对应服务。

## 6. 常用命令

更新代码后重新构建：

```bash
docker compose --env-file .env -f deploy/docker-compose.yml up -d --build
```

重启：

```bash
docker compose --env-file .env -f deploy/docker-compose.yml restart
```

停止：

```bash
docker compose --env-file .env -f deploy/docker-compose.yml down
```

停止并删除数据卷：

```bash
docker compose --env-file .env -f deploy/docker-compose.yml down -v
```

`down -v` 会删除 MySQL、Redis、Nacos 和 app 生成文件数据，谨慎使用。

## 7. Nacos 控制台访问

Nacos 只绑定服务器本机。需要临时查看时，用 SSH 隧道：

```bash
ssh -L 8848:127.0.0.1:8848 root@49.235.165.209
```

然后本机浏览器打开：

```text
http://127.0.0.1:8848/nacos
```

## 8. 重要提醒

原来的配置文件里出现过明文生产密钥，建议尽快到对应平台轮换这些 key。现在部署配置改成从 `.env` 读取，`.env` 已被 `.gitignore` 和 `.dockerignore` 排除，不要提交到仓库。
