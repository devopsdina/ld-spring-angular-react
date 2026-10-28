# LaunchDarkly + Spring Boot + Angular + React

A small product ordering app that uses LaunchDarkly feature flags on the server and in the browser,
set up with the LaunchDarkly Professional Services SDK preflight checklist:

- the SDK is initialized once, as a singleton
- every SDK has an explicit initialization timeout, so the app never waits indefinitely for LaunchDarkly
- `identify()` has an explicit timeout too
- evaluation reasons are on everywhere, and every flag is read with a `variationDetail` call

It's based on [cleon/ld-spring-angular-react](https://github.com/cleon/ld-spring-angular-react),
rebuilt on current versions of everything.

| Part | Stack | LaunchDarkly SDK |
| --- | --- | --- |
| [`server/`](server) | Spring Boot 4.1, Java 17+, Maven | [Java server SDK](https://launchdarkly.com/docs/sdk/server-side/java) 7.17 |
| [`angular/`](angular) | Angular 22, signals, zoneless | [JavaScript client SDK](https://launchdarkly.com/docs/sdk/client-side/javascript) 4.13 |
| [`react/`](react) | React 19, Vite 8 | [React SDK](https://launchdarkly.com/docs/sdk/client-side/react/react-web) 4.1 (`@launchdarkly/react-sdk`) |

The server returns products from `GET /api/product/{id}` (ids 1–5) and takes a discount off the price
using the `discount-pricing` flag. The Angular and React UIs show a random product and an order form;
the `enable-purchasing` flag turns the "Place order" button on or off. Both UIs are identical, and both
show each flag's value and evaluation reason under the button.

## Flags

Create these two flags in your LaunchDarkly project before you run the app. The keys must match
exactly.

| Flag key | Type | Variations | Client-side ID | Fallback in code |
| --- | --- | --- | --- | --- |
| `enable-purchasing` | Boolean | `true`, `false` | **On**: the browser evaluates it | `false`: ordering stays off |
| `discount-pricing` | Number | `0`, `0.25`, `0.5`, `0.75`, `1` | Off: only the server evaluates it | `0`: full price |

To create them in the LaunchDarkly UI:

1. Go to **Flags** and click **Create flag**.
2. **`enable-purchasing`:** choose the **Boolean** type. Under **Advanced configuration**, turn on
   **SDKs using Client-side ID**. Without this, the browser SDKs can't see the flag and ordering
   stays off.
3. **`discount-pricing`:** choose the **Number** type and add the five variations above, as
   fractions (`0.25` is 25% off). Leave **SDKs using Client-side ID** off.
4. Turn on targeting for `enable-purchasing` in the environment you're using, so it serves `true`.

Then copy the environment's keys from **Project settings > Environments**: the **SDK key** for the
server and the **Client-side ID** for the UIs. [Run it](#run-it) shows where each one goes.

Every evaluation uses a **multi-context** of the user and the product, in the browser and on the
server, so one targeting rule can use both:

| Context kind | Attribute | Values |
| --- | --- | --- |
| `user` | `key`, `email` | the email, `abc123@gmail.com` by default |
| `user` | `plan` | `gold` by default |
| `user` | `framework` | `angular` or `react` (absent on server calls made outside the UIs) |
| `product` | `key` | the product id, `1`–`5` |
| `product` | `name` | `Chair`, `Camera`, `Shoes`, `Soda`, `Utensils` |
| `product` | `type` | `furniture`, `electronics`, `clothing`, `food`, `housewares` |
| `product` | `price` | the original price, e.g. `600` |

Set `email` and `plan` in the URL to try rules, e.g. `http://localhost:4200/?email=you@example.com&plan=silver`.

Some rules to try:

- `discount-pricing`: 50% off when `product` `price` is greater than `100`
- `discount-pricing`: 25% off when `user` `plan` is `gold` **and** `product` `type` is `electronics`
- `enable-purchasing`: serve `false` when `product` `type` is `food`

## Run it

You need Java 17 or later, Node.js 22.22.3+ or 24.15+ (Angular 22's minimum), and the
`enable-purchasing` flag marked available to **SDKs using Client-side ID**.

### 1. Spring Boot server (port 8080)

```bash
cd server
cp .env.example .env          # then set LAUNCHDARKLY_SDK_KEY (secret; .env is gitignored)
./mvnw spring-boot:run
```

Check it: http://localhost:8080/api/product/1 and http://localhost:8080/actuator/health.

### 2. Angular UI (port 4200)

```bash
cd angular
cp src/environments/environment.example.ts src/environments/environment.ts   # then set clientSideID
npm install
npm start
```

`environment.ts` is gitignored. If it's missing, `npm start`, `npm run build` and `npm test` create it
from `environment.example.ts` with a placeholder ID, so a fresh clone still builds.

### 3. React UI (port 3000)

```bash
cd react
cp .env.example .env.local    # then set VITE_LD_CLIENT_SIDE_ID
npm install
npm run dev
```

### Or: everything at once with Docker Compose

Create the three local files above (`server/.env`, `react/.env.local`,
`angular/src/environments/environment.ts`), then:

```bash
docker compose up --watch
```

| Service | URL | On edit |
| --- | --- | --- |
| `server` | http://localhost:8080 | `server/src` syncs in and the container restarts (about 6s) |
| `angular` | http://localhost:4200 | `angular/src` syncs in and `ng serve` live-reloads |
| `react` | http://localhost:3000 | `react/src` syncs in and Vite hot-reloads |

Editing `package.json`, `pom.xml`, `angular.json` or `vite.config.ts` rebuilds that service's image.
The SDK key and `react/.env.local` are passed in with `env_file`, so they're never baked into an image.
`docker compose down` stops everything; add `-v` to also drop the Maven download cache.

Both UIs proxy `/api` to the server on port 8080 (`API_PROXY_TARGET` overrides it; Compose sets
`http://server:8080`), so the browser never makes a cross-origin call and the server needs no CORS
settings.

The client-side ID isn't secret, since it ships to every browser that loads the page. The SDK key
is secret and stays on the server.

### Or: with Podman instead of Docker

The Dockerfiles and Compose files work with [Podman](https://podman.io) unchanged. Image names
include the registry (`docker.io/...`), so Podman doesn't ask which registry to pull from.

**1. Start the Podman VM** (macOS and Windows only; Linux runs containers directly):

```bash
podman machine init      # once
podman machine start
```

`podman machine start` may suggest installing `podman-mac-helper` or exporting `DOCKER_HOST`.
`podman compose` needs neither, because it points `docker-compose` at the Podman VM itself. Plain
`docker compose` still talks to Docker Desktop. If both are running this stack, stop one first
(`docker compose down` or `podman compose down`), since they use the same ports.

**2. Install a Compose provider.** `podman compose` hands the work to another program:

| Provider | Hot reload (`--watch`) | Install |
| --- | --- | --- |
| `docker-compose` (Docker's Compose v2) | Yes | Podman Desktop offers it during setup, or install the standalone `docker-compose` binary |
| `podman-compose` | No: it doesn't support `develop.watch` | `pip install podman-compose` |

Use `docker-compose` for hot reload. If both are installed, `podman compose` picks
`docker-compose`.

**3. Run it**, with the same three local files as above:

```bash
podman compose up --watch
podman compose down
```

With `podman-compose`, run `podman compose up` without `--watch`; edits then need a restart
(`podman compose up --build`).

**Build or run a single image** without Compose:

```bash
podman build -f server/Dockerfile.dev -t ld-sample-server server
podman run --rm -p 8080:8080 --env-file server/.env ld-sample-server
```

Things that differ from Docker:

- **Folders the VM can't see.** On macOS the Podman VM shares `/Users`, `/private` and
  `/var/folders` by default. If the repo is elsewhere, the `certs/` mount comes up empty and the
  server doesn't trust your proxy's CA. Move the repo under one of those, or recreate the machine
  with the folder added. Mounts are set only at init, and passing `--volume` replaces the defaults,
  so list them again:

  ```bash
  podman machine rm
  podman machine init --volume /Users:/Users --volume /private:/private \
    --volume /var/folders:/var/folders --volume /path/to/repo:/path/to/repo
  ```
- **SELinux (Fedora, RHEL).** Bind mounts need a label. Change `./certs:/certificates:ro` to
  `./certs:/certificates:ro,z` in `compose.yaml`.
- **TLS-inspecting proxies.** The Podman VM pulls images through your corporate proxy too. If
  `podman pull` fails with a certificate error, the proxy's root CA also has to be trusted inside
  the VM. See [`certs/README.md`](certs/README.md) for exporting the root CA.
- **The TLS-intercept demo.** Podman keeps its own `mitmproxy-ca` volume, so its mitmproxy has a
  different CA from the one Docker generated. Copy it out again and restart the server:

  ```bash
  podman compose -f compose.yaml -f compose.tls-intercept.yaml cp \
    mitmproxy:/home/mitmproxy/.mitmproxy/mitmproxy-ca-cert.pem ./certs/mitmproxy-root.crt
  podman compose -f compose.yaml -f compose.tls-intercept.yaml restart server
  ```

## Reproduce a TLS-inspecting proxy

Corporate networks often route outbound HTTPS through a proxy that inspects TLS, such as Cato,
Zscaler or Netskope. The proxy decrypts each connection and re-signs it with its own root CA. Browsers trust that CA because IT installs it in the operating system's certificate store. The JVM uses its own trust store (`cacerts`) instead, so the Java SDK rejects the connection. LaunchDarkly's own certificates are publicly trusted and aren't the problem.

[`compose.tls-intercept.yaml`](compose.tls-intercept.yaml) reproduces this locally. It runs
[mitmproxy](https://mitmproxy.org) in place of the corporate proxy and sends only the server's
LaunchDarkly traffic through it. The browsers still connect to LaunchDarkly directly, so the UIs and
`enable-purchasing` are unaffected.

### 1. See the failure

If `certs/mitmproxy-root.crt` exists from an earlier run, delete it first. Then:

```bash
docker compose -f compose.yaml -f compose.tls-intercept.yaml up --watch
```

The server log shows the SDK failing with `PKIX path building failed` or `PKIX path validation failed`. `/actuator/health` reports `initialized: false`. The app keeps running on fallback values, so products show full price.

### 2. Fix it by trusting the proxy's root CA

Copy mitmproxy's root CA into [`certs/`](certs) and restart the server:

```bash
docker compose -f compose.yaml -f compose.tls-intercept.yaml cp \
  mitmproxy:/home/mitmproxy/.mitmproxy/mitmproxy-ca-cert.pem ./certs/mitmproxy-root.crt
docker compose -f compose.yaml -f compose.tls-intercept.yaml restart server
```

The server log shows `Adding certificate with alias mitmproxy/O=mitmproxy to the JVM truststore`
and then `LaunchDarkly connected. Flags are up to date.` `/actuator/health` reports
`"state": "VALID"` and `initialized: true`.

Trust the proxy's **root** CA, not the certificate a browser shows for `stream.launchdarkly.com`.
The proxy generates that per-host certificate itself and replaces it regularly; the root stays the
same. mitmproxy keeps its CA in the `mitmproxy-ca` volume, so the same file keeps working across
restarts.

### 3. The second failure: a proxy that holds the stream

Some proxies wait for a response to finish before passing it on. The SDK's streaming connection
never finishes, so flags never arrive. The TLS handshake succeeds, so there's no PKIX error; the SDK
just times out after `startWait`. To reproduce it, remove `--set stream_large_bodies=1` from the
`mitmproxy` command in `compose.tls-intercept.yaml` and restart. If you see this behind a real proxy,
ask for streaming connections to LaunchDarkly to be exempt from buffering, or switch the SDK to
polling with `Components.pollingDataSource()`.

### For a real corporate proxy (not in docker)

- **In a container:** export the corporate root CA into `certs/` instead of mitmproxy's. See
  [`certs/README.md`](certs/README.md) for the export scripts (macOS and Windows).
- **Running Maven directly on Windows:** tell the JVM to use the Windows certificate store, where IT
  has already installed the root CA, then open a new terminal:

  ```powershell
  setx JAVA_TOOL_OPTIONS "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
  ```

  Or import the root CA into the JDK's trust store. Run `mvn -v` to see which JDK Maven uses:

  ```powershell
  keytool -importcert -cacerts -alias corporate-root -file corporate-root.crt
  ```

With Podman, see [Things that differ from Docker](#or-with-podman-instead-of-docker): Podman's
mitmproxy has its own CA, so copy it out with `podman compose`.

## How the preflight rules are applied

| Rule | Server (Java) | Angular | React |
| --- | --- | --- | --- |
| Singleton | `@Bean` in [`LaunchDarklyConfig`](server/src/main/java/com/launchdarkly/sample/launchdarkly/LaunchDarklyConfig.java); Spring creates it once | [`initLaunchDarkly()`](angular/src/app/launchdarkly/ld-client.ts) warns and reuses the client if called again | [`createLaunchDarklyProvider()`](react/src/launchdarkly/launchdarkly.ts) warns and reuses the provider if called again |
| Init timeout | `startWait` 2s (`launchdarkly.start-wait`; 1–5s recommended) | `start({ timeout: 0.5 })` | `startOptions: { timeout: 0.5 }` |
| Never blocks on LaunchDarkly | The constructor returns after `startWait` either way; the app starts and serves fallbacks | `provideLaunchDarklyClient()` holds the first render for at most 500ms | The provider renders immediately; hooks return fallbacks until flags load |
| Identify timeout | Not applicable: server evaluations take the context directly | `identify(ctx, { timeout: 0.5 })` | `identify(ctx, { timeout: 0.5 })` |
| Evaluation reasons | Always computed on the server; read with `doubleVariationDetail` | `withReasons: true`; read with `flagDetailSignal()` | `withReasons: true`; read with `useBoolVariationDetail()` |
| Application metadata | `applicationInfo` from `launchdarkly.application-id` / `application-version` (version from `pom.xml`) | `applicationInfo` in [`app-info.ts`](angular/src/app/app-info.ts) (version from `package.json`) | `applicationInfo` in [`app-info.ts`](react/src/app-info.ts) (version from `package.json`) |
| Connection visible | Logs every connection change; state at `/actuator/health` | Status shown in the UI and logged | Status shown in the UI (`useInitializationStatus()`) |

Server-side SDKs have no option to turn evaluation reasons on; every evaluation computes one, and the
`*VariationDetail` methods return it. Client-side SDKs only receive reasons when you ask for them
with `withReasons: true`.

Each app identifies itself to LaunchDarkly with an application ID and version, sent as the
`X-LaunchDarkly-Tags` header on every SDK request, so you can see which app and version connected:
`ld-spring-angular-react-server`, `ld-spring-angular-react-angular` and `ld-spring-angular-react-react`,
all at `1.0.0`. The JavaScript SDKs also send a display name.

### Server

- [`LaunchDarklyConfig`](server/src/main/java/com/launchdarkly/sample/launchdarkly/LaunchDarklyConfig.java)
  creates the client with an explicit `startWait`, and logs each connection change: `LaunchDarkly connected`,
  `connection interrupted` (still serving last known values), or `data source stopped`. If startup times
  out, it logs `did not initialize within 2.0s. Serving fallback values until it connects.` and the app
  starts anyway.
- [`LaunchDarklyHealthIndicator`](server/src/main/java/com/launchdarkly/sample/launchdarkly/LaunchDarklyHealthIndicator.java)
  adds a `launchdarkly` component to `/actuator/health` with the connection `state`, `initialized`, and
  `lastError` (for example `ERROR_RESPONSE(401)` for a bad SDK key). It always reports UP: the app keeps
  working on fallback values, and restarting it wouldn't fix the connection, so it shouldn't fail a
  liveness or readiness probe.
- [`ProductController`](server/src/main/java/com/launchdarkly/sample/product/ProductController.java)
  evaluates `discount-pricing` with `doubleVariationDetail`, logs any `ERROR` reason, and returns the
  reason with the product (`discountReason`). Visitors without a `userKey` share one anonymous key, so
  each request doesn't create a new context in LaunchDarkly.
- If outbound HTTPS goes through a TLS-inspecting proxy, add the proxy's CA to the JVM trust store;
  LaunchDarkly's own certificates are publicly trusted. See
  [Reproduce a TLS-inspecting proxy](#reproduce-a-tls-inspecting-proxy). `launchdarkly.relay-proxy-uri` points the SDK
  at a Relay Proxy.

### Angular

The LaunchDarkly code is in [`angular/src/app/launchdarkly/`](angular/src/app/launchdarkly), the same
layer as [hello-angular](https://github.com/devopsdina/hello-angular):

- [`ld-client.ts`](angular/src/app/launchdarkly/ld-client.ts) has no Angular code. It owns the one client, both
  500ms timeouts, and `withReasons: true`.
- [`flag-signal.ts`](angular/src/app/launchdarkly/flag-signal.ts) connects it to Angular:
  - `provideLaunchDarklyClient()` starts the client during app startup
  - `flagDetailSignal(key, fallback)` returns the value and reason as a signal, which Angular redraws
    when the flag changes, and removes its listener when the component is destroyed
  - `launchDarklyStatus` is the startup result

The app starts with the user context, then calls `identifyContext()` with the user + product
multi-context once the product loads. Flag signals also re-read after an identify completes, because a
new context can change a flag's reason without changing its value, and the SDK only fires `change`
when the value changes.

### React

[`createLaunchDarklyProvider()`](react/src/launchdarkly/launchdarkly.ts) wraps the React SDK's
`createLDReactProvider` with the singleton guard, `withReasons: true`, and the 500ms start timeout.
[`App.tsx`](react/src/App.tsx) reads the flag with `useBoolVariationDetail('enable-purchasing', false)`
and the status with `useInitializationStatus()`. Both re-render when the flag or the context changes.

This uses `@launchdarkly/react-sdk` 4.x, which is built on the v4 JavaScript SDK. The original repo
used `launchdarkly-react-client-sdk` 3.x with `useFlags()`. In the 4.x SDK `useFlags()` is deprecated
and returns no evaluation reasons; use the typed `use*Variation` and `use*VariationDetail` hooks instead.

## Tests

Every suite runs the real LaunchDarkly SDK, not a mock. For the outage cases, the tests point the SDK
at a local server that accepts connections and never answers, like a network that silently drops
traffic to LaunchDarkly.

```bash
cd server && ./mvnw test     # 16 tests: startWait timeout, startup with LaunchDarkly down, application metadata, targeting rules via TestData
cd angular && npm test       # 24 tests: singleton, 500ms init and identify timeouts, reasons, flag signals, App
cd react && npm test         # 10 tests: singleton, 500ms init and identify timeouts, reasons, App
```

To see the fallback path in a browser, block `clientsdk.launchdarkly.com` and
`clientstream.launchdarkly.com`. The page settles in about 0.7s, including page load, with "no response within 500ms",
`enable-purchasing` shows `false · ERROR (FLAG_NOT_FOUND)`, and ordering stays off.
