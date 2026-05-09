# Viper Proxy (Fabric 1.21.11)

`Viper Proxy` is a **client-side Fabric mod** that enforces multiplayer connections through a configured proxy and blocks connections when the proxy is not healthy.

## Target Stack

- Minecraft: `1.21.11`
- Fabric Loader: `0.18.4`
- Fabric API: `0.141.3+1.21.11`
- Mappings: `Yarn 1.21.11+build.4`
- Java: `21` (required)

## Features

- Adds a top-right button in `MultiplayerScreen` showing proxy status:
  - `Connected` (green)
  - `ERROR` (red)
- Proxy configuration UI includes:
  - Host
  - Port
  - Username
  - Password
  - Proxy type selector (`SOCKS5`, `HTTP`, `HTTPS`)
  - `Apply` and `Close` buttons
- Writes config to `config/viperproxy.json`
- Auto-loads config on client startup and re-tests connectivity
- Enforces a fail-safe kill switch:
  - Blocks multiplayer connect attempts unless proxy is connected
  - Injects proxy handlers into client Netty pipeline for remote connections

## Build

From the project root:

- Windows: `./gradlew.bat clean build`
- Unix-like: `./gradlew clean build`

Output jar:

- `build/libs/viperproxy-1.0.0.jar`

## Notes

- This mod is **client-only** and should not be installed on servers.
- Java 21 is required for Minecraft 1.21.11.
- The proxy status line in the config UI includes the tested external IP when connected.
- This project does not use the Resource Loader API directly, so the 1.21.10/1.21.11 resource-loading changes do not require code updates here.

## Runtime Testing

After building, place the mod jar in your Fabric 1.21.11 client `mods` folder and verify:

1. Multiplayer screen shows `Viper Proxy` button at top-right with status text.
2. Open proxy config UI, enter proxy host/port/type/(optional) credentials, click `Apply`.
3. Confirm status changes to `Connected` (green) or `ERROR` (red).
4. Attempt server join while status is `ERROR`: connection must be blocked by kill-switch.
5. Verify `config/viperproxy.json` is created/updated and loaded after restarting client.
