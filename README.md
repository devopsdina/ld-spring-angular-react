# LaunchDarkly + Spring Boot + Quarkus + Angular + React

A small product ordering app that uses LaunchDarkly feature flags on two Java backends and in the
browser, set up with the LaunchDarkly Professional Services SDK preflight checklist:

- the SDK is initialized once, as a singleton
- every SDK has an explicit initialization timeout, so the app never waits indefinitely for LaunchDarkly
- `identify()` has an explicit timeout too
- evaluation reasons are on everywhere, and every flag is read with a `variationDetail` call

It's based on [cleon/ld-spring-angular-react](https://github.com/cleon/ld-spring-angular-react),
rebuilt on current versions of everything.

| Part | Stack | LaunchDarkly SDK |
| --- | --- | --- |
| [`launchdarkly-common/`](launchdarkly-common) | Plain Java 17, shared by both backends | [Java server SDK](https://launchdarkly.com/docs/sdk/server-side/java) 7.17 |
| [`spring-boot/`](spring-boot) | Spring Boot 4.1, Maven (port 8080) | via `launchdarkly-common` |
| [`quarkus/`](quarkus) | Quarkus 3.40 LTS, Maven (port 8081) | via `launchdarkly-common` |
| [`angular/`](angular) | Angular 22, signals, zoneless (port 4200) | [JavaScript client SDK](https://launchdarkly.com/docs/sdk/client-side/javascript) 4.13 |
| [`react/`](react) | React 19, Vite 8 (port 3000) | [React SDK](https://launchdarkly.com/docs/sdk/client-side/react/react-web) 4.1 (`@launchdarkly/react-sdk`) |

Both backends serve the same API, `GET /api/product/{id}` (ids 1–5), and take a discount off the
price with the `discount-pricing` flag. The Angular and React UIs show a random product and an order
form. The `api-backend` flag decides which backend each UI calls, and `enable-purchasing` turns the
"Place order" button on or off. Both UIs are identical, and both show each flag's value and
evaluation reason under the button, plus which backend answered.

## Flags

Create these three flags in your LaunchDarkly project before you run the app. The keys must match
exactly.

| Flag key | Type | Variations | Client-side ID | Fallback in code |
| --- | --- | --- | --- | --- |
| `enable-purchasing` | Boolean | `true`, `false` | **On**: the browser evaluates it | `false`: ordering stays off |
| `api-backend` | String | `spring-boot`, `quarkus` | **On**: the browser evaluates it | `spring-boot` |
| `discount-pricing` | Number | `0`, `0.25`, `0.5`, `0.75`, `1` | Off: only the backends evaluate it | `0`: full price |

To create them in the LaunchDarkly UI:

1. Go to **Flags** and click **Create flag**.
2. **`enable-purchasing`:** choose the **Boolean** type. Under **Advanced configuration**, turn on
   **SDKs using Client-side ID**. Without this, the browser SDKs can't see the flag and ordering
   stays off.
3. **`api-backend`:** choose the **String** type with the variations `spring-boot` and `quarkus`,
   and turn on **SDKs using Client-side ID**. Set the default rule and the off variation to
   `spring-boot`, then add a rule: `user` `plan` is one of `gold` → `quarkus`.
4. **`discount-pricing`:** choose the **Number** type and add the five variations above, as
   fractions (`0.25` is 25% off). Leave **SDKs using Client-side ID** off.
5. Turn on targeting for `enable-purchasing` and `api-backend` in the environment you're using.

Then copy the environment's keys from **Project settings > Environments**: the **SDK key** for the
backends and the **Client-side ID** for the UIs. [Run it](#run-it) shows where each one goes.

### Contexts

Every evaluation uses a **multi-context** of the user and the product, in the browser and on the
backends, so one targeting rule can use both. The backends also add `backend`, so a rule can target
the backend that's evaluating:

| Context kind | Attribute | Values |
| --- | --- | --- |
| `user` | `key`, `email` | the email, `abc123@gmail.com` by default |
| `user` | `plan` | `silver` by default |
| `user` | `framework` | `angular` or `react` (absent on calls made outside the UIs) |
| `user` | `backend` | `spring-boot` or `quarkus`: backend evaluations only |
| `product` | `key` | the product id, `1`–`5` |
| `product` | `name` | `Chair`, `Camera`, `Shoes`, `Soda`, `Utensils` |
| `product` | `type` | `furniture`, `electronics`, `clothing`, `food`, `housewares` |
| `product` | `price` | the original price, e.g. `600` |

The browsers and both backends must build these contexts the same way, or a rule matches in one
place and not another. [`contracts/contexts.json`](contracts/contexts.json) holds the expected JSON,
and the Java, Angular and React tests all check against it.

Set `email` and `plan` in the URL to try rules, e.g. `http://localhost:4200/?email=you@example.com&plan=gold`.

Some rules to try:

- `api-backend`: `quarkus` when `user` `plan` is `gold`. Open `/` (Spring Boot) and `/?plan=gold`
  (Quarkus) side by side, then turn the flag off: both go to Spring Boot.
- `discount-pricing`: 50% off when `user` `backend` is `quarkus`
- `discount-pricing`: 25% off when `user` `plan` is `gold` **and** `product` `type` is `electronics`
- `enable-purchasing`: serve `false` when `product` `type` is `food`

Target `api-backend` on `user` attributes only. The UIs add the product to the context after the
product loads, from the backend `api-backend` picked, so a rule on a product attribute would load
from one backend and then switch to the other.

## Run it

You need Java 17 or later (Quarkus 4 is planned to need Java 21), Node.js 22.22.3+ or 24.15+ (Angular 22's
minimum), and the three flags above.

### 1. The backends (ports 8080 and 8081)

```bash
cp spring-boot/.env.example spring-boot/.env   # then set LAUNCHDARKLY_SDK_KEY (secret; gitignored)
cp quarkus/.env.example quarkus/.env           # the same key
./mvnw install -DskipTests                     # builds launchdarkly-common, which both backends use

./mvnw -pl spring-boot spring-boot:run         # in one terminal
./mvnw -pl quarkus quarkus:dev                 # in another
```

Check them: http://localhost:8080/api/product/1, http://localhost:8080/actuator/health,
http://localhost:8081/api/product/1 and http://localhost:8081/q/health.

Quarkus dev mode reloads edits on the next request, including edits to `launchdarkly-common`. Spring
Boot needs a restart, and after editing `launchdarkly-common`, run
`./mvnw install -pl launchdarkly-common -DskipTests` first.

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

Both UIs proxy `/api/spring-boot/*` to `http://localhost:8080/api/*` and `/api/quarkus/*` to
`http://localhost:8081/api/*` (`SPRING_BOOT_URL` and `QUARKUS_URL` override them), so the browser
never makes a cross-origin call and the backends need no CORS settings. In production, a gateway or
ingress does this routing instead.

The client-side ID isn't secret, since it ships to every browser that loads the page. The SDK key
is secret and stays on the backends.

### Or: everything at once with Docker Compose

Create the four local files above (`spring-boot/.env`, `quarkus/.env`, `react/.env.local`,
`angular/src/environments/environment.ts`), then:

```bash
docker compose up --build --watch
```

| Service | URL | On edit |
| --- | --- | --- |
| `spring-boot` | http://localhost:8080 | `spring-boot/src` or `launchdarkly-common/src` syncs in and the container restarts (about 8s) |
| `quarkus` | http://localhost:8081 | `quarkus/src` or `launchdarkly-common/src` syncs in and dev mode reloads on the next request (about 3s) |
| `angular` | http://localhost:4200 | `angular/src` syncs in and `ng serve` live-reloads |
| `react` | http://localhost:3000 | `react/src` syncs in and Vite hot-reloads |

`--build` rebuilds images that are out of date, for example after you pull changes; without it,
Compose reuses an existing image and the UIs can run old code. Editing a `pom.xml`, `package.json`,
`angular.json` or `vite.config.ts` rebuilds that service's image. The SDK key and `react/.env.local`
are passed in with `env_file`, so they're never baked into an image. `docker compose down` stops
everything; add `-v` to also drop the Maven download caches.

Two JVMs and two Node dev servers need about 3–4 GB of memory. Check Docker Desktop's memory limit
under **Settings > Resources**.

### Or: with Podman instead of Docker

The Dockerfiles and Compose files work with [Podman](https://podman.io) unchanged. Image names
include the registry (`docker.io/...`), so Podman doesn't ask which registry to pull from.

**1. Start the Podman VM** (macOS and Windows only; Linux runs containers directly). The VM gets
2 GB of memory by default, which isn't enough for all four apps:

```bash
podman machine init --memory 6144   # once; for an existing machine:
                                    #   podman machine stop && podman machine set --memory 6144
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

**3. Run it**, with the same four local files as above:

```bash
podman compose up --build --watch
podman compose down
```

With `podman-compose`, run `podman compose up --build` without `--watch`; edits then need a restart.

**Build or run a single image** without Compose. The backend images build from the repo root,
because they include `launchdarkly-common`:

```bash
podman build -f spring-boot/Dockerfile.dev -t ld-sample-spring-boot .
podman run --rm -p 8080:8080 --env-file spring-boot/.env ld-sample-spring-boot
```

Things that differ from Docker:

- **Saving files.** Under Podman, watch can't sync a file that was replaced rather than written in
  place: it handles a replace as a delete, and the delete fails ("can only start created exec
  sessions"). VS Code and IntelliJ save in place, so their saves sync. Tools that write a new file
  and rename it over the old one, such as `sed -i`, don't; save the file again from your editor.
- **Folders the VM can't see.** On macOS the Podman VM shares `/Users`, `/private` and
  `/var/folders` by default. If the repo is elsewhere, the `certs/` mount comes up empty and the
  backends don't trust your proxy's CA. Move the repo under one of those, or recreate the machine
  with the folder added. Mounts are set only at init, and passing `--volume` replaces the defaults,
  so list them again:

  ```bash
  podman machine rm
  podman machine init --memory 6144 --volume /Users:/Users --volume /private:/private \
    --volume /var/folders:/var/folders --volume /path/to/repo:/path/to/repo
  ```
- **SELinux (Fedora, RHEL).** Bind mounts need a label. Change `./certs:/certificates:ro` to
  `./certs:/certificates:ro,z` in `compose.yaml`.
- **TLS-inspecting proxies.** The Podman VM pulls images through your corporate proxy too. If
  `podman pull` fails with a certificate error, the proxy's root CA also has to be trusted inside
  the VM. See [`certs/README.md`](certs/README.md) for exporting the root CA.
- **The TLS-intercept demo.** Podman keeps its own `mitmproxy-ca` volume, so its mitmproxy has a
  different CA from the one Docker generated. Copy it out again and restart the backends:

  ```bash
  podman compose -f compose.yaml -f compose.tls-intercept.yaml cp \
    mitmproxy:/home/mitmproxy/.mitmproxy/mitmproxy-ca-cert.pem ./certs/mitmproxy-root.crt
  podman compose -f compose.yaml -f compose.tls-intercept.yaml restart spring-boot quarkus
  ```

## Reproduce a TLS-inspecting proxy

Corporate networks often route outbound HTTPS through a proxy that inspects TLS, such as Cato,
Zscaler or Netskope. The proxy decrypts each connection and re-signs it with its own root CA. Browsers trust that CA because IT installs it in the operating system's certificate store. The JVM uses its own trust store (`cacerts`) instead, so the Java SDK rejects the connection. LaunchDarkly's own certificates are publicly trusted and aren't the problem.

[`compose.tls-intercept.yaml`](compose.tls-intercept.yaml) reproduces this locally. It runs
[mitmproxy](https://mitmproxy.org) in place of the corporate proxy and sends both backends'
LaunchDarkly traffic through it. The browsers still connect to LaunchDarkly directly, so the UIs and
the browser flags are unaffected.

### 1. See the failure

If `certs/mitmproxy-root.crt` exists from an earlier run, delete it first. Then:

```bash
docker compose -f compose.yaml -f compose.tls-intercept.yaml up --build --watch
```

Both backends' logs show the SDK failing with `PKIX path building failed` or `PKIX path validation failed`. `/actuator/health` and `/q/health` report `initialized: false`. The backends keep running on fallback values, so products show full price.

### 2. Fix it by trusting the proxy's root CA

Copy mitmproxy's root CA into [`certs/`](certs) and restart the backends:

```bash
docker compose -f compose.yaml -f compose.tls-intercept.yaml cp \
  mitmproxy:/home/mitmproxy/.mitmproxy/mitmproxy-ca-cert.pem ./certs/mitmproxy-root.crt
docker compose -f compose.yaml -f compose.tls-intercept.yaml restart spring-boot quarkus
```

Each backend's log shows `Adding certificate with alias mitmproxy/O=mitmproxy to the JVM truststore`
and then `LaunchDarkly connected. Flags are up to date.` Both health endpoints report
`"state": "VALID"` and `initialized: true`. The fix is in the JVM, so it's the same for Spring Boot
and Quarkus, with no code in either.

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

  Either way, both backends pick it up, because the setting is in the JVM.

With Podman, see [Things that differ from Docker](#or-with-podman-instead-of-docker): Podman's
mitmproxy has its own CA, so copy it out with `podman compose`.

## How the preflight rules are applied

| Rule | Backends (Java) | Angular | React |
| --- | --- | --- | --- |
| Singleton | The framework's container creates the client once: a Spring `@Bean`, a Quarkus `@Singleton` producer. [`LaunchDarklyClients`](launchdarkly-common/src/main/java/com/launchdarkly/sample/common/LaunchDarklyClients.java) builds it and keeps no copy | [`initLaunchDarkly()`](angular/src/app/launchdarkly/ld-client.ts) warns and reuses the client if called again | [`createLaunchDarklyProvider()`](react/src/launchdarkly/launchdarkly.ts) warns and reuses the provider if called again |
| Init timeout | `startWait` 2s (`launchdarkly.start-wait`; 1–5s recommended) | `start({ timeout: 0.5 })` | `startOptions: { timeout: 0.5 }`; the bootstrap request also gives up after 500ms |
| Never blocks on LaunchDarkly | The constructor returns after `startWait` either way; the backend starts and serves fallbacks | `provideLaunchDarklyClient()` holds the first render for at most 500ms | Renders with bootstrapped flags, or immediately with fallbacks |
| Identify timeout | Not applicable: server evaluations take the context directly | `identify(ctx, { timeout: 0.5 })` | `identify(ctx, { timeout: 0.5 })` |
| Evaluation reasons | Always computed on the server; read with `doubleVariationDetail` | `withReasons: true`; read with `flagDetailSignal()` | `withReasons: true`; read with `use*VariationDetail()` |
| Application metadata | `applicationInfo` from `launchdarkly.application-id` / `application-version` (version from `pom.xml`) | `applicationInfo` in [`app-info.ts`](angular/src/app/app-info.ts) (version from `package.json`) | `applicationInfo` in [`app-info.ts`](react/src/app-info.ts) (version from `package.json`) |
| Connection visible | Logs every connection change; state at `/actuator/health` (Spring Boot) and `/q/health` (Quarkus) | Status shown in the UI and logged | Status shown in the UI (`useInitializationStatus()`) |

Server-side SDKs have no option to turn evaluation reasons on; every evaluation computes one, and the
`*VariationDetail` methods return it. Client-side SDKs only receive reasons when you ask for them
with `withReasons: true`.

Each app identifies itself to LaunchDarkly with an application ID and version, sent as the
`X-LaunchDarkly-Tags` header on every SDK request, so you can see which app and version connected:
`ld-spring-angular-react-spring-boot`, `ld-spring-angular-react-quarkus`,
`ld-spring-angular-react-angular` and `ld-spring-angular-react-react`, all at `1.0.0`. The
JavaScript SDKs also send a display name.

### Backends

All the LaunchDarkly code is in [`launchdarkly-common`](launchdarkly-common), plain Java with no
Spring or Quarkus dependencies, so both backends set up the SDK, build contexts and evaluate flags the
same way. It's the same split as the Angular code: SDK code in one place, and a thin framework
layer on top.

- [`LaunchDarklyClients`](launchdarkly-common/src/main/java/com/launchdarkly/sample/common/LaunchDarklyClients.java)
  creates the client with an explicit `startWait`, and logs each connection change: `LaunchDarkly connected`,
  `connection interrupted` (still serving last known values), or `data source stopped`. If startup times
  out, it logs `did not initialize within 2.0s. Serving fallback values until it connects.` and the app
  starts anyway.
- [`Contexts`](launchdarkly-common/src/main/java/com/launchdarkly/sample/common/Contexts.java) builds
  every context. Visitors without a `userKey` share one anonymous key, so each request doesn't create
  a new context in LaunchDarkly.
- [`ProductService`](launchdarkly-common/src/main/java/com/launchdarkly/sample/common/ProductService.java)
  evaluates `discount-pricing` with `doubleVariationDetail`, logs any `ERROR` reason, and returns the
  reason with the product (`discountReason`), along with the `backend` that answered. It also logs one
  line per request, without the email:

  ```
  [quarkus] product 2 requested by react (plan=gold), routed by api-backend (RULE_MATCH) -> discount-pricing 0.25 (FALLTHROUGH)
  ```

  The frontend comes from the request's `framework` parameter, and the routing from the
  `X-Backend-Routing` header the UIs send. Both come from the browser, so only known values are
  logged. Set `com.launchdarkly.sample.common.ProductService` to DEBUG to also log each evaluation
  context, with emails masked.
- [`FlagBootstrap`](launchdarkly-common/src/main/java/com/launchdarkly/sample/common/FlagBootstrap.java)
  serves `GET /api/flags/bootstrap`: the user's context and client-side flags (`allFlagsState` with
  `CLIENT_SIDE_ONLY` and `WITH_REASONS`), for the React SDK's `bootstrap` option. Server-only flags
  such as `discount-pricing` never reach the browser. It returns 503 until the SDK has flags. Here the
  email and plan come from the URL so you can try rules; a real app builds the context from the
  logged-in session, so a user can't put someone else's email in the URL.
- [`LaunchDarklyHealth`](launchdarkly-common/src/main/java/com/launchdarkly/sample/common/LaunchDarklyHealth.java)
  gives each health endpoint the connection `state`, `initialized`, and `lastError` (for example
  `ERROR_RESPONSE(401)` for a bad SDK key). It always reports UP: the backend keeps working on
  fallback values, and restarting it wouldn't fix the connection, so it shouldn't fail a liveness or
  readiness probe.

What each framework does differently:

| | Spring Boot | Quarkus |
| --- | --- | --- |
| One client | `@Bean(destroyMethod = "close")` | `@Produces @Singleton` with `@Disposes`; `@Startup`, since Quarkus otherwise creates beans on first use |
| Settings | `@ConfigurationProperties` record, `application.yml` | `@ConfigMapping` interface, `application.properties` |
| `.env` | Read through `spring.config.import` | Read by Quarkus itself |
| Health | `/actuator/health` (Actuator) | `/q/health` (SmallRye Health) |
| Dev reload | Restart | `quarkus:dev` reloads on the next request |
| JVM options for `mvn` | `-Dspring-boot.run.jvmArguments=...` | `-Djvm.args=...` |
| JSON | Jackson 3 | Jackson 2, so the shared types have no Jackson annotations |

Each backend's tests check its response against
[`contracts/product-offer.json`](contracts/product-offer.json), so the two return the same JSON.

`launchdarkly.relay-proxy-uri` points either backend's SDK at a Relay Proxy.

### Routing between the backends

Both UIs evaluate `api-backend` and call the backend it picks:

- **Names, not URLs.** Client-side flag values are visible to anyone, so the flag serves
  `spring-boot` or `quarkus`, and the UI maps each to a path. Any other value means Spring Boot.
- **Routing only follows a successful evaluation.** The SDK briefly reports `ERROR` while
  `identify()` loads a new context's flags and while it reconnects. If that moved traffic, every
  product load would bounce to Spring Boot and back. An error keeps the current backend instead.
- **Live changes.** When targeting changes, the SDK streams the new value, and the UI fetches the same
  product from the other backend, canceling any request still in flight to the old one. Fetching the
  same product again doesn't call `identify()` again, since the context hasn't changed.
- **A 2s limit per request.** A backend that doesn't answer shows "Quarkus unavailable" (or Spring
  Boot) instead of holding the page.
- **`?backend=quarkus` or `?backend=spring-boot`** in the URL overrides the flag, for trying a
  backend without changing targeting. The `api-backend` line shows "overridden by URL".
- **What you see.** The banner, page colors and emoji follow the backend that actually answered:
  🌱 green for Spring Boot, ⚡ blue for Quarkus.

For moving a server-side workload between systems, with shadow comparisons, LaunchDarkly also has
[migration flags](https://launchdarkly.com/docs/home/flags/migration). Here the browser decides
where to route, so a string flag is the right tool.

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

Angular holds the first render until flags load (at most 500ms), so the first product request
already goes to the backend `api-backend` picks. [`App`](angular/src/app/app.ts) requests products
through `switchMap`, which cancels the old request when the backend changes.

### React

[`createLaunchDarklyProvider()`](react/src/launchdarkly/launchdarkly.ts) wraps the React SDK's
`createLDReactProvider` with the singleton guard, `withReasons: true`, and the 500ms start timeout.
[`App.tsx`](react/src/App.tsx) reads the flags with `useBoolVariationDetail` and
`useStringVariationDetail`, and the status with `useInitializationStatus()`. They re-render when a
flag or the context changes.

The React provider renders right away, so without help the first render would have fallback values:
`api-backend` would read `spring-boot`, the page would call Spring Boot, then switch to Quarkus when
flags arrived. To avoid that flicker, [`main.tsx`](react/src/main.tsx) first asks Spring Boot for a
[bootstrap](react/src/bootstrap.ts) (at most 500ms), and starts the SDK with the context and flags it
returns. The first render then has the right values. If the bootstrap fails, the SDK starts the usual
way, and `App` waits for flags before its first request.

This uses `@launchdarkly/react-sdk` 4.x, which is built on the v4 JavaScript SDK. The original repo
used `launchdarkly-react-client-sdk` 3.x with `useFlags()`. In the 4.x SDK `useFlags()` is deprecated
and returns no evaluation reasons; use the typed `use*Variation` and `use*VariationDetail` hooks instead.

In development, React's `StrictMode` runs effects twice, so each backend logs the product request
twice; the first is canceled in the browser. Production builds make one request.

## Tests

Every suite runs the real LaunchDarkly SDK, not a mock. For the outage cases, the tests point the SDK
at a local server that accepts connections and never answers, like a network that silently drops
traffic to LaunchDarkly.

```bash
./mvnw test                  # 39 tests: launchdarkly-common (startWait timeout, contexts, targeting via TestData, bootstrap,
                             #   request log), Spring Boot and Quarkus (startup with LaunchDarkly down, same JSON, health)
cd angular && npm test       # 37 tests: singleton, 500ms timeouts, reasons, flag signals, backend routing, App
cd react && npm test         # 28 tests: singleton, 500ms timeouts, reasons, bootstrap, backend routing, App
```

To see the fallback path in a browser, block `clientsdk.launchdarkly.com` and
`clientstream.launchdarkly.com` and open the Angular UI. The page settles in about 0.7s, including
page load, with "no response within 500ms", `enable-purchasing` shows
`false · ERROR (FLAG_NOT_FOUND)`, ordering stays off, and products come from Spring Boot. The React UI
starts from Spring Boot's bootstrap instead, so it shows the bootstrapped values first.
