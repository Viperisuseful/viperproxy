# **Viper Proxy**

Viper Proxy is a client-side Fabric mod that routes all of your multiplayer connections through a proxy server. It supports SOCKS5, HTTP, and HTTPS proxies.

This mod adds a button to the top right of the Multiplayer screen where you can configure your proxy and test the connection. It comes with a built-in kill switch that blocks server connections entirely if the proxy isn't healthy, and a background heartbeat checks every 15 seconds so that if your proxy drops mid-session, the mod catches it immediately. Your real IP is never exposed.

You can save multiple proxy profiles and switch between them from the config screen. Credentials are encrypted on disk and tied to your machine, so they won't be exposed in plaintext config files.

Before downloading, please note:

- This mod is client-side only — **do not install it on a server**.
- It requires **Fabric Loader 0.18.1+**, **Fabric API**, and **Java 21**.
- The kill switch is always active. If your proxy is not connected, you will not be able to join any server.


<details>
<summary>Spoiler</summary>

This project is completely vibe coded, feel free to fork it or open an issue ticket on GitHub if you have a problem.

If you find any issue/bug, open an issue ticket on GitHub contact me on discord : _viperisuseful_
</details>