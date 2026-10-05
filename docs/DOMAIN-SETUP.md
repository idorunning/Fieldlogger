# Field Logger domain

The existing Site is branded **Field Logger**. Its registered custom hostname is `fieldlogger.co.uk`, purchased through Cloudflare. Custom domain ID: `appgdom_6ac2f249c00c8191b30017dba2c5e841`. Keep the existing Sites project and its D1/R2 storage.

In Cloudflare's DNS records for `fieldlogger.co.uk`, add:

| Type | Name | Value | Proxy |
| --- | --- | --- | --- |
| A | @ | 162.159.143.30 | DNS only |
| A | @ | 172.66.3.26 | DNS only |
| TXT | _openai-site-verification | openai-site-verification=Ts0AIm0eMJ8e2Tlwsi48_pGwK9RbGjaJDCcS1wGj5N0 | — |
| TXT | _cf-custom-hostname | 00a31060-db08-49b5-bda5-cb78047ebcbb | — |

These are public DNS validation values returned by the hosting provider, not API secrets. At registration, the domain's Cloudflare nameservers were active but no apex A record existed. Preserve unrelated records, especially email records. The user subsequently approved a publicly reachable app entry point with Field Logger password login, so Android domain verification can work. Journal records and stored keys remain account-protected.

The domain and SSL status were verified active on 5 October 2026. On future changes, refresh this custom domain's status with Sites until both are active. Registration alone does not prove HTTPS is ready. The original hosted URL continues to work while DNS propagates.

The new domain has separate browser storage and login cookies. Sign into the same Field Logger account to download backed-up discoveries. Export/upload discoveries that exist only in offline storage at the old origin before changing origins. No provider key is transferred through browser storage.
