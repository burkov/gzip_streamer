# gzip_streamer
Test application which show problem in nginx gzip filter module


To compile & run use `./run.sh`:
```
usage: gzip_streamer.jar [nginx] [config]
nginx executable  = /usr/sbin/nginx
nginx config      = nginx.conf
rss poll interval = 3000 ms


nginx started, pid: 4645, workers: [4647] (output redirected to 'nginx.out.log', pidfile: 'nginx.pid')
now you should start client (e.g `curl -H 'Accept-Encoding: gzip' -v http://localhost:8080/stream 1>/dev/null`)
total rss:    2.55 kB   +2.48 kB
total rss:    5.03 kB    +528  b
total rss:    5.56 kB    +792  b
total rss:    6.35 kB    +528  b
total rss:    6.88 kB    +792  b
total rss:    7.67 kB    +528  b
total rss:    8.20 kB    +792  b
total rss:    8.99 kB    +528  b
<...>
```

In other terminal session connect client:
```
$ curl -H 'Accept-Encoding: gzip' -v http://localhost:8080/stream 1>/dev/null
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
  0     0    0     0    0     0      0      0 --:--:-- --:--:-- --:--:--     0*   Trying ::1...
* connect to ::1 port 8080 failed: Connection refused
*   Trying 127.0.0.1...
* Connected to localhost (127.0.0.1) port 8080 (#0)
> GET /stream HTTP/1.1
> Host: localhost:8080
> User-Agent: curl/7.47.0
> Accept: */*
> Accept-Encoding: gzip
>
< HTTP/1.1 200 OK
< Server: nginx/1.10.0 (Ubuntu)
< Date: Mon, 08 Aug 2016 23:46:29 GMT
< Content-Type: application/octet-stream
< Transfer-Encoding: chunked
< Connection: keep-alive
< Content-Encoding: gzip
<...>
```