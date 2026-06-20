# Payment Event Dashboard (Scala.js + Laminar)

An interactive, static dashboard for the payment-event pipeline. It reads the dataset the backend
exports to `out/dashboard_dataset.json` and lets you filter results, inspect KPIs, charts and a
transaction table entirely in the browser. There is **no HTTP API** and **no Node build step** —
the page is plain static files plus a single compiled `main.js`.

## Tech

- [Scala.js](https://www.scala-js.org/) + [Laminar](https://laminar.dev/) for the UI.
- Shared dataset contract (`com.team.pipeline.dashboard.contract`) is cross-compiled to JVM
  (backend exporter) and JS (this dashboard), so the BE/FE JSON shape is defined once.
- Charts are hand-drawn SVG (no chart library / no CDN required).

## Build

The dashboard is compiled by sbt into `dashboard/assets/main.js`:

```bash
sbt dashboard/fastLinkJS
```

(Use `sbt dashboard/fullLinkJS` for an optimized build.)

## Run locally

1. Process data and export the dataset (backend side):
   - `sbt run` (processes events into Mongo)
   - generate `out/dashboard_dataset.json` (dashboard dataset exporter)
2. Compile the dashboard: `sbt dashboard/fastLinkJS`
3. Serve the repo root with any static server, e.g.:
   - `python3 -m http.server 5173`
4. Open: `http://localhost:5173/dashboard/`

The page fetches `../out/dashboard_dataset.json` relative to `dashboard/index.html`, i.e.
`out/dashboard_dataset.json` from the repo root.

## Dataset contract

The dashboard expects the shape defined in
`com.team.pipeline.dashboard.contract.DashboardDataset`:

```json
{
  "generatedAt": "2026-04-28T09:30:00Z",
  "events": [{ "eventId": 1, "timestamp": "...", "customerId": 1, "amount": "150.00", "currency": "PLN", "status": "SUCCESS", "paymentMethod": "BLIK", "transactionCountry": "PL", "merchantCategory": "GROCERY", "channel": "MOBILE", "deviceId": "device-1-1", "riskScore": 0, "riskDecision": "Approve", "finalDecision": "Accepted" }],
  "alerts": [{ "eventId": 2, "customerId": 2, "alertType": "AmountOutlier", "riskScore": 35 }],
  "violations": [{ "eventId": 5, "customerId": 5, "violationType": "InactiveCustomer" }]
}
```

The dataset never contains raw email or `hashedCustomerEmail`.
