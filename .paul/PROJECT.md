# Project: FocuseLocker

## Requirements
- Fix persistent DNS resolution failure (EAI_NODATA, UnknownHostException) for DoH endpoint.
- Ensure BootstrapDns and ProtectedSocketFactory bypass VPN rules to resolve the NextDNS hostname.
- Implement a permanent, robust solution to prevent `DNS_PROBE_POSSIBLE` recursion loops.
- Rewrite and verify unit and integration tests for all scenarios.
- Follow Fable (strict verification) and Ponytail Ultra (minimalist/lazy) guidelines.

## Constraints
- Android VpnService implementation.
- Kotlin Coroutines and OkHttp.
- Must not use patch work.
- Must follow Fable 7-phase debugging protocol.
