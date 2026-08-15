# Linux 基础命令速查

> Day 60 | 2026-08-15 | Phase 3 第 9 周

---

## 1. 文件与目录

```bash
ls -la              # 列出所有文件（含隐藏）
cd /opt             # 切换目录
pwd                 # 当前路径
mkdir -p a/b/c      # 递归创建目录
cp -r src dst       # 复制目录
mv old new          # 移动/重命名
rm -rf dir          # 删除目录（危险！）
cat file            # 查看文件
tail -f log         # 实时查看日志
head -20 file       # 查看前 20 行
grep "ERROR" log    # 搜索文本
find / -name "*.jar" # 查找文件
```

## 2. 权限管理

```bash
chmod +x deploy.sh       # 添加执行权限
chmod 755 app.jar        # rwxr-xr-x
chown -R app:app /opt/app  # 修改属主
ls -l                    # 查看权限
```

| 权限 | 数字 | 含义 |
|------|------|------|
| rwx | 7 | 读写执行 |
| rw- | 6 | 读写 |
| r-x | 5 | 读执行 |
| r-- | 4 | 只读 |

## 3. 进程与服务

```bash
ps aux | grep java       # 查找 Java 进程
kill -9 PID              # 强制杀死进程
systemctl status app     # 查看服务状态
systemctl start app      # 启动
systemctl stop app       # 停止
systemctl restart app    # 重启
systemctl enable app     # 开机自启
journalctl -u app -f     # 实时查看服务日志
```

## 4. 网络与端口

```bash
ss -tlnp                 # 查看监听端口
netstat -tlnp            # 同上（旧版）
curl http://localhost:8080/health  # HTTP 请求
ping 8.8.8.8             # 网络连通性
lsof -i :8080            # 查看占用 8080 的进程
```

## 5. 磁盘与内存

```bash
df -h                    # 磁盘使用
du -sh /opt/app          # 目录大小
free -h                  # 内存使用
top                      # 实时资源监控
```

## 6. 压缩与传输

```bash
tar -czf app.tar.gz dir  # 压缩
tar -xzf app.tar.gz      # 解压
scp app.jar user@server:/opt/  # 远程复制
rsync -avz ./ user@server:/opt/app/  # 同步
```

## 7. 实战练习清单

> 在 Linux 服务器上逐条执行并理解结果：

1. `cd /opt && mkdir industrial-ai-hub && cd $_`
2. `echo $?` — 查看上一条命令退出码
3. `ps aux | grep sshd` — 管道 + 过滤
4. `cat /etc/os-release` — 查看系统版本
5. `whoami && id` — 当前用户
6. `chmod +x *.sh` — 批量加执行权限
7. `systemctl list-units --type=service | grep running`
8. `tail -f /var/log/syslog` — 实时日志（Ctrl+C 退出）
