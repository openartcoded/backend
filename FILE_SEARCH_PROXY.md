# file-search read-only proxy

The backend exposes the following **authenticated** endpoints, using the existing JWT resource-server security:

- `GET /api/file-search/search?q=invoice&page=1&per_page=20&query_type=queryParser`
- `GET /api/file-search/find-all?page=1&per_page=20`
- `GET /api/file-search/stats`

`query_type` is one of `termQuery`, `regexQuery`, `fuzzySearch`, or `queryParser`.
Authentication is required for all three endpoints; the caller's Bearer token is **not** forwarded to file-search.
Index mutation endpoints (`/reset`, `/reindex`) are intentionally **not proxied**.

Configure `FILE_SEARCH_BASE_URL` (default: `http://file-search:8080`). Example Docker Compose environment:

```yaml
services:
  backend:
    environment:
      FILE_SEARCH_BASE_URL: http://file-search:8080
  file-search:
    # your existing image/configuration
    expose:
      - "8080"
```

Keep both containers on the same Compose network. The upstream service need not publish a port to the host.
Example request:

```sh
curl -iG http://localhost:8080/api/file-search/search \
  -H "Authorization: Bearer $TOKEN" \
  --data-urlencode 'q=invoice' \
  --data-urlencode 'query_type=queryParser'
```

Without a valid JWT, Spring Security denies the request before contacting file-search.
