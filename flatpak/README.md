# Autopsy Flatpak

Packages Autopsy as a distributable Flatpak bundle (`.flatpak` file) for direct installation on
Linux systems. The bundle is self-contained and can be installed and run fully offline once built.

## Distribution

This package targets direct distribution (not Flathub). The `.flatpak` bundle is attached as a
release asset to GitHub Releases, similar to how the Snap is distributed.

## Prerequisites (build machine)

- `flatpak` and `flatpak-builder` installed
- Flathub remote added (provides the runtime and SDK):
  ```sh
  flatpak remote-add --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo
  ```
- `org.freedesktop.Platform//25.08`, `org.freedesktop.Sdk//25.08`, and
  `org.freedesktop.Sdk.Extension.openjdk17//25.08` installed:
  ```sh
  flatpak install flathub org.freedesktop.Platform//25.08 org.freedesktop.Sdk//25.08 \
    org.freedesktop.Sdk.Extension.openjdk17//25.08
  ```
- Internet access during the build (needed for downloading the NetBeans platform and Autopsy
  Maven dependencies; the resulting bundle installs and runs offline)
- **≥ 20 GB of free disk space** on the build machine — the NetBeans platform download, the
  Autopsy compilation, and the intermediate flatpak overlay together require significant space

## Build

From the repository root:

```sh
flatpak-builder --force-clean build-dir org.sleuthkit.Autopsy.yaml
```

Build time is typically 15–30 minutes (dominated by the NetBeans platform download and Autopsy
compilation). All Sleuth Kit Maven dependencies are pre-declared in the manifest with verified
SHA256 checksums and need no network access. The Autopsy `ant build-zip` step does require
network to download the NetBeans platform (~100 MB) and Autopsy's own dependency tree.

## Bundle and install

```sh
# Create a single-file distributable bundle
flatpak build-bundle ~/.local/share/flatpak/repo autopsy.flatpak org.sleuthkit.Autopsy

# Install from the bundle (no network needed)
flatpak install --user autopsy.flatpak

# Run
flatpak run org.sleuthkit.Autopsy
```

## CI / GitHub Actions

`.github/workflows/build-flatpak.yml` builds the bundle automatically on tag pushes
(`autopsy-*`) and on manual dispatch. The resulting `autopsy.flatpak` is attached to the
GitHub Release when triggered by a tag.

## Design decisions

### Runtime
`org.freedesktop.Platform//25.08` with `org.freedesktop.Sdk.Extension.openjdk17` — avoids
bundling a full JRE by using the SDK extension mechanism. JDK 17 is required by Autopsy.

### Network during build
The manifest sets `build-args: [--share=network]` globally. This is necessary because:
- Autopsy's `ant build-zip` downloads the Apache NetBeans platform at build time
- Pre-bundling all NetBeans modules individually would be impractical (hundreds of JARs)

All Sleuth Kit Maven dependencies (13 JARs) ARE pre-bundled in the manifest with SHA256
checksums, so the Sleuth Kit module builds fully offline.

### Permissions
`--device=all` is required for forensics work (raw block device access). Users may additionally
need to run Autopsy with appropriate OS group membership (e.g., `disk` group) to access local
disks in the `/dev/` directory.

### Hugepages (Solr)
Flatpak cannot grant access to `/sys/kernel/mm/hugepages`. Solr runs without hugepages, which
is a performance trade-off only (not a functional blocker).

## Known limitations

Inherited from Autopsy's Linux support:
- Recent Activity module is non-functional
- LEAPP processors are non-functional
- HEIF image processing is unavailable
- Video thumbnails are unavailable

## Module build order

1. `openjdk` — installs JDK 17 from the SDK extension into `/app/jdk`
2. `ant` — installs Apache Ant 1.10.15 into `/app/ant`
3. `libewf` (legacy) — E01 forensics image support
4. `libafflib` — AFF forensics format support
5. `libvmdk` — VMware disk image support
6. `libvhdi` — VHD disk image support
7. `libvslvm` — LVM volume support
8. `testdisk` — provides `photorec` (required by `unix_setup.sh`)
9. `sleuthkit` — native TSK libraries + Java bindings (offline Maven build)
10. `autopsy` — Autopsy itself, built from local source via `ant build-zip`
