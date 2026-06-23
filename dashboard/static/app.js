async function load() {
  try {
    const res = await fetch('../out/dashboard_dataset.json')
    if (!res.ok) throw new Error('Failed to load dataset: ' + res.status)
    const ds = await res.json()

    document.getElementById('totalProcessed').textContent = ds.totalProcessed ?? ds.events?.length ?? '-'
    document.getElementById('totalRejected').textContent = ds.totalRejected ?? '-'
    document.getElementById('totalAlerts').textContent = ds.totalAlerts ?? ds.alerts?.length ?? '-'

    // Decisions
    const decisionCounts = ds.decisionCounts || computeDecisionCounts(ds)
    const decisionsCtx = document.getElementById('decisionsChart').getContext('2d')
    new Chart(decisionsCtx, {
      type: 'pie',
      data: {
        labels: Object.keys(decisionCounts),
        datasets: [{ data: Object.values(decisionCounts), backgroundColor: ['#10b981','#f59e0b','#ef4444'] }]
      }
    })

    // Top countries
    const countries = ds.topCountries || computeTopCountries(ds)
    const countriesCtx = document.getElementById('countriesChart').getContext('2d')
    new Chart(countriesCtx, {
      type: 'bar',
      data: { labels: Object.keys(countries), datasets: [{ label: 'count', data: Object.values(countries), backgroundColor: '#3b82f6' }] }
    })

    // Time series: aggregate by day
    const timeseries = aggregateEventsByDay(ds.events || [])
    const timesCtx = document.getElementById('timeseriesChart').getContext('2d')
    new Chart(timesCtx, { type: 'line', data: { labels: timeseries.map(p=>p.day), datasets: [{ label: 'events', data: timeseries.map(p=>p.count), fill:false, borderColor:'#6366f1' }] } })

    // Alerts list
    const alertList = document.getElementById('alertList')
    const alertCounts = ds.alertCounts || computeAlertCounts(ds)
    Object.entries(alertCounts).forEach(([k,v]) => {
      const li = document.createElement('li')
      li.textContent = `${k}: ${v}`
      alertList.appendChild(li)
    })
  } catch (e) {
    document.body.innerHTML = '<p style="color:#b91c1c">Error loading dashboard: '+e.message+'</p>'
  }
}

function computeDecisionCounts(ds){
  const counts = {}
  (ds.events||[]).forEach(e=>counts[e.finalDecision]=(counts[e.finalDecision]||0)+1)
  return counts
}

function computeTopCountries(ds){
  const counts = {}
  (ds.events||[]).forEach(e=>counts[e.transactionCountry]=(counts[e.transactionCountry]||0)+1)
  return counts
}

function computeAlertCounts(ds){
  const counts = {}
  (ds.alerts||[]).forEach(a=>counts[a.alertType]=(counts[a.alertType]||0)+1)
  return counts
}

function aggregateEventsByDay(events){
  const map = {}
  events.forEach(e=>{
    const d = new Date(e.timestamp)
    const day = d.toISOString().slice(0,10)
    map[day] = (map[day]||0)+1
  })
  return Object.keys(map).sort().map(k=>({day:k,count:map[k]}))
}

load()
