# 公网部署教程（适合第一次部署）

## 1. 准备服务器

购买一台 Linux 云服务器（Ubuntu 22.04/24.04 均可），安全组至少开放 22 和 80；准备域名时再开放 443。

## 2. 安装 Docker

按 Docker 官方文档安装 Docker Engine 与 Compose Plugin。安装后确认：

```bash
docker --version
docker compose version
```

## 3. 上传代码

把整个 `AIProject` 上传到服务器，例如 `/opt/englishmate`。

```bash
cd /opt/englishmate
cp .env.example .env
nano .env
```

填写：

- `DEEPSEEK_API_KEY`：你自己的 Key
- `MYSQL_ROOT_PASSWORD`：强密码
- `JWT_SECRET`：至少 32 字符随机字符串
- `CORS_ALLOWED_ORIGIN`：有域名后填写 `https://你的域名`

## 4. 一键启动

```bash
docker compose up -d --build
```

查看：

```bash
docker compose ps
docker compose logs -f backend
```

浏览器打开 `http://服务器公网IP`。

## 5. 域名与 HTTPS

把域名 A 记录指向服务器公网 IP。生产环境推荐在最外层使用 Caddy 或宿主机 Nginx + Let's Encrypt 证书，将 443 反向代理到本项目的 80 端口。

## 6. 更新版本

```bash
docker compose down
docker compose up -d --build
```

数据保存在 `mysql_data` 与 `redis_data` Docker volume 中，正常 rebuild 不会清空数据库。
