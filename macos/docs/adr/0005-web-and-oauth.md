# ADR-0005: System browser, loopback OAuth, and targeted Chrome CDP

- Status: Accepted
- Date: 2026-08-02

## Context

Bundling a general Chromium/JCEF view would materially increase package size
and attack surface. OAuth needs a secure desktop redirect, while some source
providers require a real browser to complete JavaScript challenges.

## Decision

Open user-facing web pages and authorization requests in the system browser.
Receive OAuth responses on a random loopback port with state validation; Google
Drive additionally uses PKCE-S256 and treats desktop clients as public clients.
Use a separately managed Chrome DevTools Protocol session only for source WAF/
JavaScript challenge work, then return cookies/results to normal OkHttp flows.

## Consequences

There is no embedded general-purpose browser. OAuth follows desktop browser
expectations and source challenge handling remains isolated, bounded, optional,
and dependent on Chrome being installed.
