# Autopsy MCP STDIO Wrapper

This is a lightweight Node.js script that bridges Claude Desktop and Claude Code
to the Autopsy MCP HTTP server. Claude clients require a stdio-based process;
this wrapper handles that translation.

It dynamically proxies `tools/list` and `tools/call` to Autopsy's local HTTP
server — there are no hardcoded tool definitions here. When tools are added to
the Java side, this wrapper picks them up automatically with no changes needed.

## How it works

1. Autopsy starts an HTTP MCP server on `127.0.0.1:8765` when a case is opened
2. Autopsy writes an ephemeral auth token to `~/.autopsy/mcp-token`
3. This wrapper reads that token and forwards stdio MCP calls to the HTTP server
4. When the case is closed, the token file is deleted and calls will fail gracefully


## Setup

Install the single dependency (the MCP SDK):

```
cd Tools/autopsy-mcp-stdio
npm install
```

That's it. No compilation step is needed.

## Configuring Claude Desktop

Add the following to your Claude Desktop config file
(`%APPDATA%\Claude\claude_desktop_config.json` on Windows):

```json
{
  "mcpServers": {
    "autopsy": {
      "command": "C:\\PATH_TO_\\autopsy-mcp-stdio.exe"
    }
  }
}
```


## Configuring Claude Code

Add the same block to `~/.claude.json` under your project entry:


## Building the standalone .exe (no Node.js required at runtime)

The pre-built `dist/autopsy-mcp-stdio.exe` is checked into the repository and
is what ships with Autopsy. You only need to rebuild it when `autopsy-mcp-stdio.js`
or its dependencies change.

### Prerequisites

- **Node.js 18 or later** — https://nodejs.org (LTS recommended)
- **npm** — included with Node.js

### Build steps

```
cd Tools\autopsy-mcp-stdio
npm install
npm run package
```

`npm install` installs the MCP SDK and build tools into `node_modules\`.
`npm run package` uses [esbuild](https://esbuild.github.io) to bundle the script
and all dependencies into a single CJS file, then [pkg](https://github.com/vercel/pkg)
wraps that with a Node.js runtime into `dist\autopsy-mcp-stdio.exe`.

The Autopsy Ant `build-zip` target automatically copies `dist\autopsy-mcp-stdio.exe`
into the `bin\` folder of the distribution ZIP.


## Troubleshooting

**"No Autopsy case is currently open"** — Open a case in Autopsy first. The MCP
server only runs while a case is open.

**"Autopsy HTTP error 401"** — The token file is stale from a previous session.
Close and reopen the case in Autopsy to generate a fresh token.

**"Autopsy HTTP error 500"** — An error occurred on the Autopsy side. Check the
Autopsy log for details.
