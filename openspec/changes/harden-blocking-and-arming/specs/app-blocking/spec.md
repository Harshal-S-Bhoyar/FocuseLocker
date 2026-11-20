# Spec Delta

## ADDED Requirements

### Requirement: URL verdict guard is visible across threads

The system SHALL publish the most recently observed URL such that the coroutine evaluating a URL
observes updates made by the accessibility callback thread. The published value SHALL be
observable without relying on incidental ordering.

#### Scenario: Callback thread publishes a newer URL

- **WHEN** the accessibility callback records a new URL while a previous check is running
- **THEN** the running check observes the newer URL and discards its own verdict

### Requirement: A superseded verdict applies no side effect

The system SHALL re-verify that the URL it evaluated is still the most recent URL after the last
suspension point of its check and immediately before performing any side effect for that verdict,
with no suspension between that re-verification and the side effects, and that re-verification and
those side effects SHALL be serialized with publications of the most recent URL so a newer URL
cannot be published in between. A verdict for a superseded URL SHALL NOT navigate back, launch the
blocking overlay, stop the blocking overlay, or start a smart scan.

#### Scenario: URL changes while the check is in flight

- **WHEN** the evaluated URL is replaced by a newer URL after the check begins
- **THEN** no back-navigation and no blocking overlay are performed for the superseded URL

#### Scenario: Latest blocked page is still enforced

- **WHEN** the evaluated URL is still the most recently observed URL and it is blocked
- **THEN** the system navigates away and shows the blocking overlay

#### Scenario: A newer URL cannot be published between re-verification and the effects

- **WHEN** the verdict for the evaluated URL is about to be applied
- **THEN** the re-verification and the effects it guards run on the same thread that publishes the
  most recent URL, so no newer URL can be interleaved between them

### Requirement: DNS response cache is bounded

The system SHALL bound the number of entries held in the DNS response cache. Once the bound is
reached, storing a new entry SHALL evict an existing entry rather than grow the cache without
limit.

#### Scenario: Many distinct lookups

- **WHEN** more distinct domains are resolved than the configured entry bound
- **THEN** the cache holds no more than the bound and continues to serve recent entries

#### Scenario: Expired entries are not served

- **WHEN** a cached entry's time to live has elapsed
- **THEN** the entry is not served, and a fresh resolution is performed

### Requirement: Cached responses only replay for a matching query

The system SHALL associate each cached DNS response with a fingerprint of the query that produced
it, covering the query's bytes after the DNS header so that the question section and any
additional records are both represented, and SHALL serve a cached response only to a query whose
fingerprint and query type match the stored entry.

#### Scenario: Query differing in additional records is not served

- **WHEN** a query carries different additional records than the query that populated the cache entry
- **THEN** the stored answer is not served and the query is resolved through DoH

#### Scenario: Query differing in question casing is not served

- **WHEN** a query shares a domain and query type with a cached entry but its question section differs in bytes
- **THEN** the stored answer is not served and the query is resolved through DoH

#### Scenario: Identical query is served from cache

- **WHEN** a query is identical after the DNS header to the query that populated the cache entry
- **THEN** the cached answer is served with the current transaction ID and recomputed checksums
