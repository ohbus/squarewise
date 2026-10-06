# ERR-03: Structured security errors

Install shared servlet and reactive authentication entrypoints and access-denied
handlers. Missing, malformed, invalid, and expired bearer tokens map to
cataloged 401 problems; authorization failures map to 403 problems. Preserve
`WWW-Authenticate`, request correlation, source attribution, and safe details.
Verify all four applications, including the BFF, through live local requests.
