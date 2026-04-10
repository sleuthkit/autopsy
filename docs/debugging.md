# Debugging

## Getting a thread dump from a running Autopsy instance

Autopsy launched from NetBeans runs as `nbexec64.exe` — the NetBeans platform JVM launcher. It does **not** appear as `java.exe`. A separate `java.exe` process is the embedded Solr server (port 23232); do not confuse the two.

### Find the PID

```powershell
# 1. Find the NetBeans launcher PID
Get-Process | Where-Object { $_.ProcessName -match "netbeans" } | Select-Object Id, ProcessName

# 2. Find its nbexec64.exe child — this is the actual JVM
#    Replace 1234 with the netbeans64 PID from step 1
Get-CimInstance Win32_Process | Where-Object { $_.ParentProcessId -eq 1234 } | Select-Object ProcessId, Name
```

### Take the dump

```bash
"C:/Program Files/Java/jdk-21.0.10/bin/jstack.exe" <PID> > autopsy_dump.txt
```

### Key ingest thread names

| Thread name pattern | What it does |
|---|---|
| `IM-data-source-ingest-*` | Data source module pipeline (iLeap, Drones, etc.) |
| `IM-file-ingest-*` | File-level ingest workers |
| `IM-data-artifact-ingest-*` | Artifact ingest |
| `IM-ingest-events-*` | Ingest event bus |
| `tika-reader-*` | Tika parsing threads (per-file executor in TikaTextExtractor) |

### Interpreting the dump

- **BLOCKED** — thread is waiting for a Java monitor lock. May resolve on its own if no cycle.
- **WAITING at `IngestTasksScheduler.getNextTask`** — thread is idle, no tasks queued. Normal between modules.
- To tell if a thread is truly stuck vs. just slow, take two dumps ~60 seconds apart and compare CPU time. A RUNNABLE thread with increasing CPU time is making progress.
- `IM-data-source-ingest-*` CPU time not increasing = data source phase is done (waiting for next job or all modules finished).

### Note on `jps`

`jps` will not find `nbexec64.exe`. Use PowerShell `Get-Process` as shown above.
