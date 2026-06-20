# FocusLocker Threat Model

## 1. System Overview
FocusLocker is an Android parental control and productivity application. It uses a combination of Accessibility Services, VPN TUN interface, and Device Administrator APIs to enforce domain, keyword, and category blocking.

## 2. Trust Boundaries & Entry Points
- **VPN Packet Interface (Low Trust):** Receives raw IP packets (IPv4/IPv6) from local applications.
- **Accessibility Service (Low Trust):** Receives dynamic UI events and text from active applications and browsers.
- **DoH Network Interface (Medium Trust):** Queries NextDNS over HTTPS. Input is locally extracted domains, output is DNS resolution status.
- **Local SQLite Database (High Trust):** Room database storing blocked domains, keywords, and schedules.

## 3. Threat Vectors
1. **Packet Injection/Corruption:** Malicious local apps sending malformed IPv4/IPv6 packets to the TUN interface to crash the VPN service.
2. **DNS Evasion:** Apps using private encrypted DoH/DoT resolvers to bypass the TUN interface capture.
3. **SSRF via DoH Queries:** Specially crafted domain names bypassing `DomainExtractor` and manipulating the NextDNS query.
4. **Denial of Service (DoS):** Flooding the DoH endpoint or VPN loop with excessive queries, causing battery drain or OOM.

## 4. Key Security Controls
- Protocol validation in `VpnPacketLoop` dropping non-UDP traffic.
- Checksum validation and rewriting in `DnsResponseBuilder`.
- Asynchronous execution in `DoHQueryUseCase` to prevent VPN blocking.
- `DomainExtractor` using `java.net.URI` for host parsing.
