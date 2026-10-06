# ERR-09: BFF and GraphQL error mapping

Preserve upstream code/source in GraphQL extensions and assign BFF-specific
codes only for gateway failures such as timeout, connection failure, or
malformed upstream responses. Test partial results, authorization, validation,
and non-leakage behavior.
