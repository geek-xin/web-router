# Route Log Panel Design

## Goal

Each route card gets its own log entry point. Opening it shows only that route's proxy requests and keeps streaming new entries while the panel is open.

## Architecture

The existing Gateway global filter remains the single request recorder. `ProxyRequestLogService` keeps the in-memory rolling buffer and adds route-scoped snapshot and stream methods. `ProxyRequestLogController` exposes both the existing global endpoints and new route-scoped endpoints.

The admin page removes the standalone global log section. Route cards add a `日志` action that opens a modal with the selected route name, connection state, route-scoped IP totals, and a compact real-time log table.

## Data Flow

1. `ProxyRequestLogFilter` records every proxied request with `routeId`.
2. `ProxyRequestLogService.snapshot(routeId)` filters the rolling buffer and computes totals for that route.
3. `ProxyRequestLogService.stream(routeId)` filters the live sink by route.
4. `app.js` fetches the route snapshot when the modal opens, then connects to the route SSE stream.
5. Closing the modal closes the active `EventSource`.

## Error Handling

The frontend shows a toast and marks the modal status when snapshot loading fails. If EventSource is unavailable or reconnecting, the status pill reflects that without blocking route management actions.

## Testing

Service tests cover route-scoped totals, IP stats, and filtered recent logs. Controller tests cover the route-scoped snapshot endpoint response. Full `mvn test` remains the final verification command.
