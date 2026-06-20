# API_SPEED_OPTIMIZATION.md
# FocusLock — Lightning Fast API & Network Configuration
# Applies to: NextDNS DoH requests + Meta Scan HTTP fetch

---

## 1. OkHttp Client — Single Shared Instance

Never create a new OkHttpClient per request. One shared singleton for the
entire app lifetime. Creating a new client every time wastes memory, kills
connection pooling, and adds 50–200ms cold start penalty per request.

```
Create ONE OkHttpClient instance in your dependency container or Application class.
Share it across NextDNS DoH calls AND meta scan fetch calls.
Never call OkHttpClient() inside a function that runs per URL check.
```

---

## 2. Connection Pool

OkHttp maintains a pool of open TCP connections. Default is 5 connections,
5 minutes idle keep-alive. Tune it for your use case:

```
maxIdleConnections = 10
keepAliveDuration  = 10 minutes
```

Why: Your app fires requests in bursts (user browsing session). A larger pool
means the second, third, fourth URL check in the same session reuses an already
open TCP connection — skipping the entire TCP handshake + TLS handshake cost
which is 100–400ms on a cold connection.

---

## 3. Timeouts — Set All Three

OkHttp has three independent timeouts. All three must be set. Missing one means
the OS default applies (sometimes infinite):

```
connectTimeout = 3 seconds   — time to open TCP connection
readTimeout    = 4 seconds   — time to receive response body
writeTimeout   = 3 seconds   — time to send request body
```

For NextDNS DoH specifically — DNS queries are tiny. Use tighter timeouts:
```
connectTimeout = 2 seconds
readTimeout    = 2 seconds
writeTimeout   = 1 second
```

For meta scan fetch — HTML pages can be larger. Use slightly relaxed timeouts:
```
connectTimeout = 3 seconds
readTimeout    = 4 seconds
writeTimeout   = 2 seconds
```

Do NOT use a single timeout value for both. Two separate OkHttpClient instances
built from the same base client via `.newBuilder()` is the right approach here.

---

## 4. HTTP/2 Multiplexing

OkHttp supports HTTP/2 automatically if the server supports it. NextDNS DoH
endpoint supports HTTP/2. Never disable it. HTTP/2 allows multiple requests over
a single TCP connection simultaneously — critical when checking multiple URLs
in rapid succession.

```
Do NOT call .protocols(listOf(Protocol.HTTP_1_1)) — this disables HTTP/2.
Leave protocols at default — OkHttp negotiates HTTP/2 automatically via ALPN.
```

---

## 5. DNS Cache for OkHttp

By default OkHttp does not cache DNS resolutions. Every request to
`dns.nextdns.io` re-resolves the hostname. Add an in-memory DNS cache:

```
Implement the Dns interface on your OkHttpClient.
Cache resolved IPs with a 60-second TTL.
On a cache hit, skip the system DNS lookup entirely.
For a hostname like dns.nextdns.io that never changes IP, this saves
20–80ms per request after the first resolution.
```

Implementation approach:
```
- Keep a ConcurrentHashMap<String, Pair<List<InetAddress>, Long>>
- Key = hostname, Value = (resolved addresses, expiry timestamp)
- On lookup: return cached addresses if not expired
- On miss or expiry: call system DNS, cache result, return
```

---

## 6. Response Caching (HTTP Cache)

OkHttp supports standard HTTP caching using Cache-Control headers. Set up a
disk cache so responses that are cacheable don't hit the network at all:

```
Cache directory: context.cacheDir / "okhttp_cache"
Cache size: 5 MB
```

For NextDNS DoH: DNS responses often include a TTL. Some DoH servers return
proper Cache-Control headers. When they do, OkHttp serves from cache.

For meta scan fetch: Most pages return `no-cache` or `no-store`. Don't rely on
this for meta scan — but don't disable it either since static assets might
still benefit.

---

## 7. GZip Compression

OkHttp adds `Accept-Encoding: gzip` automatically and decompresses responses.
Never disable this. A typical HTML page is 50–80% smaller when gzipped.
Smaller payload = less time reading from network.

```
Do NOT add a manual Accept-Encoding header — it disables OkHttp's
automatic gzip handling.
Do NOT call .addInterceptor that strips Accept-Encoding.
```

---

## 8. NextDNS DoH — Request Format

Use GET not POST for DoH queries. GET with the DNS query in the URL parameter
is faster because:
- No request body to serialize and send
- Response is more likely to be cached by HTTP cache

```
Endpoint: https://dns.nextdns.io/{profileId}?dns={base64url_encoded_dns_query}
Header:   Accept: application/dns-message
Method:   GET
```

For building the DNS query wire format:
- Query type: A record (type 1)
- Query class: IN (class 1)
- Set RD (recursion desired) bit to 1

Alternatively use JSON format which is easier to parse:
```
Endpoint: https://dns.nextdns.io/{profileId}?name={domain}&type=A
Header:   Accept: application/dns-json
Method:   GET
```

JSON format adds ~5ms parsing overhead vs binary but saves implementation
complexity. Use JSON unless you measure a real bottleneck.

---

## 9. NextDNS DoH — What to Check in Response

You do not need the resolved IP address. You only need to know if NextDNS
blocked the domain. Check the response for:

```
JSON format:
  response.status == "NOERROR"   → domain is clean (not blocked)
  response.status == "NXDOMAIN"  → domain is blocked by NextDNS

Binary format:
  RCODE field in DNS header == 0  → clean
  RCODE field == 3                → NXDOMAIN, blocked
```

Parse only what you need. Don't deserialize the full response object.

---

## 10. Parallel Requests with Coroutines

Never run NextDNS DoH check and meta scan fetch sequentially if you can
overlap them. Structure the checks so independent operations run concurrently:

```
Strategy:
1. URL pre-check     → runs synchronously, instant, no IO
2. Room DB check     → runs on IO dispatcher, fast
3. NextDNS DoH check → launch as coroutine job A
4. Meta scan fetch   → launch as coroutine job B

Do NOT await job A then start job B.
Launch both with async { } and use awaitAll() or select { } to act on
whichever completes first with a block result.
```

If NextDNS returns BLOCKED before meta scan finishes → cancel meta scan job,
block immediately. This saves the full meta scan fetch time.

```
val dohJob  = async { checkNextDns(domain) }
val metaJob = async { deepScanMeta(url) }

select {
    dohJob.onAwait  { result -> if (result == BLOCKED) metaJob.cancel() }
    metaJob.onAwait { result -> if (result == BLOCKED) dohJob.cancel()  }
}
```

---

## 11. Coroutine Dispatcher — Use IO Not Default

All network calls must run on `Dispatchers.IO`. Never on `Dispatchers.Default`
(CPU thread pool) or `Dispatchers.Main` (UI thread — will crash).

```
withContext(Dispatchers.IO) {
    // OkHttp call here
}
```

`Dispatchers.IO` maintains up to 64 threads by default and is optimized for
blocking IO operations. Network calls block the calling thread until response
arrives — IO dispatcher is built for this.

---

## 12. OkHttp Dispatcher — Thread Limits

OkHttp has its own internal async dispatcher for `enqueue()` calls. If you are
using OkHttp's async API (not suspend functions), tune the dispatcher:

```
dispatcher.maxRequests             = 20   (default 64, lower saves memory)
dispatcher.maxRequestsPerHost      = 10   (default 5, raise for burst checks)
```

For FocusLock, if you're using OkHttp with coroutines via `execute()` (synchronous
call inside `withContext(IO)`), this setting doesn't apply — skip it.

---

## 13. Meta Scan Fetch — Fetch Only What You Need

When fetching a page for meta scanning, you do not need the full page body.
You only need the `<head>` section. Most meta tags are in the first 4–8KB of
the HTML response. Stop reading after you have enough:

```
Read the response body as a stream.
Read only the first 8192 bytes (8KB).
Close the stream immediately after.
Parse only what was read with Jsoup.
```

This avoids downloading a 500KB page just to read 200 bytes of meta content.
Add this header to the fetch request to hint to the server:

```
Range: bytes=0-8191
```

Note: Not all servers honor the Range header for HTML. Still close the stream
early regardless — OkHttp will handle connection cleanup.

---

## 14. Jsoup Parsing — Parse Only Head

When you pass the partial HTML to Jsoup, parse only the `<head>` tag:

```
Do NOT call Jsoup.parse(fullHtml) and then query the whole document.
Call Jsoup.parseBodyFragment() or query only doc.head() after parsing.
Jsoup building a full DOM for a large page takes 10–30ms extra.
Parsing only head drops this to under 1ms.
```

---

## 15. User-Agent Header

Some servers block requests from unknown or empty User-Agents and return
403 or a redirect to a CAPTCHA page. Set a realistic User-Agent on the
meta scan fetch request to avoid wasted redirect round-trips:

```
User-Agent: Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36
```

Use a generic mobile Chrome string. Do not use your app package name — some
servers detect non-browser agents and serve different content.

---

## 16. Follow Redirects — Limit Them

By default OkHttp follows up to 20 redirects. For FocusLock, limit to 3:

```
followRedirects         = true
followSslRedirects      = true
Set a custom interceptor that counts redirect hops and throws after 3.
```

Many ad/gambling sites chain multiple redirects before landing on the real page.
Capping at 3 saves you from chasing 5–6 redirect hops that add 200–600ms total.

---

## 17. SSL/TLS — Don't Disable Certificate Validation

Never set a TrustManager that accepts all certificates to "speed things up."
It breaks security completely. Modern TLS 1.3 (used by NextDNS and most sites)
is already fast — the TLS handshake is 1 round-trip vs 2 for TLS 1.2.

OkHttp uses TLS 1.3 by default when the server supports it. Leave it alone.

---

## 18. Room DB — Index Your Blocklist

The Fast Lane Room DB check is only fast if the domain column is indexed.
Without an index, Room scans every row linearly — on 150,000 StevenBlack
entries this takes 50–150ms. With an index it's under 1ms.

```
Add @Index(value = ["domain"], unique = true) on your blocklist entity.
Add @Index(value = ["domain"]) on your domains cache table.
```

---

## 19. Room DB — Read on IO Dispatcher

Room queries are blocking by default unless you use suspend functions. Always
run Room queries on `Dispatchers.IO`:

```
withContext(Dispatchers.IO) {
    dao.isDomainBlocked(domain)
}
```

If using Flow or LiveData from Room, this is handled automatically. But for
one-shot queries (which the Fast Lane check is), always use withContext.

---

## 20. StevenBlack Initial Load — Load Once, Not Per-Check

Loading the hosts.txt file from assets and inserting into Room at first launch
is a one-time operation. Never re-read the assets file on every app start.

```
Check if the DB table is empty on first launch.
If empty → read assets/hosts.txt line by line, batch insert into Room.
If not empty → skip entirely, table is ready.

Use Room's insertAll() with a list — not individual insert() calls per domain.
Batch size of 500–1000 rows per transaction is optimal.
```

---

## 21. Prefetch on Accessibility Event

The Accessibility Service fires an event whenever the URL bar changes. Don't
wait for the user to stop typing. Start the Room DB check the moment the URL
passes basic validation (looks like a real URL, not a half-typed query).

```
On URL bar text change:
  → validate URL (quick regex check, <1ms)
  → if valid, immediately fire Room DB check in background coroutine
  → if Room returns clean, queue meta scan job (don't await yet)
  → if user navigates (URL changes again), cancel the queued job
```

This overlaps the DB check time with the user still interacting, so by the time
the page starts loading the check may already be complete.

---

## 22. Logging — Disable in Production

Every `Log.d()` or `Log.v()` call inside the URL check hot path adds 0.1–0.5ms
each. In a path that fires 10–20 times per browsing session this adds up.

```
Wrap all debug logging behind BuildConfig.DEBUG checks.
Remove any logging inside the Accessibility Service callback entirely in release.
Use Firebase Crashlytics for error reporting instead of Log.e() in production.
```

---

## Summary — Speed Budget Per Layer

Target latency for each layer when everything above is applied:

| Layer | Cold (first request) | Warm (pooled connection) |
|---|---|---|
| URL pre-check | < 1ms | < 1ms |
| Room DB check | 1–3ms | 1–2ms |
| NextDNS DoH | 80–200ms | 40–80ms |
| Meta scan fetch (8KB) | 200–500ms | 100–250ms |
| Jsoup parse (head only) | < 2ms | < 1ms |
| Keyword match | < 1ms | < 1ms |

Total worst case (all layers, cold): ~700ms
Total best case (warm, blocked at DB): ~2ms

---

