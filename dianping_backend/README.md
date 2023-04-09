# 代码使用说明
项目代码说明
- main: 主分支，包含完整版代码

  
## 1.下载
克隆完整项目
```git
https://github.com/ZhidanDeng/dianping_project.git
```
建议切换分支，重新开发
```git
git checkout init
```

## 2.可能的问题
项目启动后，控制台会一直报错:
```
NOGROUP No such key 'stream.orders' or consumer group 'g1' in XREADGROUP with GROUP option
```
代码会尝试访问Redis，连接Redis的Stream。请先在Redis运行一下命令：
```text
XGROUP CREATE stream.orders g1 $ MKSTREAM
```