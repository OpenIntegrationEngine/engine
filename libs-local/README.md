# Local Dependencies Repository

This directory contains dependencies that are not available in Maven Central.
These JARs must be installed into this local Maven repository structure before building.

## Installation

Run the following script to install all local JARs into this repository:

```bash
./install-local-deps.sh
```

## Required Local JARs

The following JARs from the existing `lib/` directories need to be installed:

### Server Extensions
| JAR File | Group ID | Artifact ID | Version | Source |
|----------|----------|-------------|---------|--------|
| `mirth-vocab.jar` | `com.mirth` | `mirth-vocab` | `1.0` | `server/lib/` |
| `jai_imageio.jar` | `com.sun.media` | `jai-imageio` | `1.1` | `server/lib/extensions/dimse/` |
| `wsdl4j-1.6.2-fixed.jar` | `wsdl4j` | `wsdl4j-fixed` | `1.6.2` | `server/lib/extensions/ws/` |
| `ij.jar` | `imagej` | `ij` | `1.53` | `server/lib/extensions/dicomviewer/` |
| `PDFRenderer.jar` | `com.sun.pdfview` | `pdfrenderer` | `0.9.1` | `server/lib/extensions/pdfviewer/` |
| `webdavclient4j-core-0.92.jar` | `com.googlecode.webdavclient4j` | `webdavclient4j-core` | `0.92` | `server/lib/extensions/file/` |

### Client Libraries
| JAR File | Group ID | Artifact ID | Version | Source |
|----------|----------|-------------|---------|--------|
| `wizard.jar` | `com.mirth` | `wizard` | `1.0` | `client/lib/` |
| `language_support.jar` | `com.mirth` | `language-support` | `1.0` | `client/lib/` |
| `openjfx.jar` | `com.mirth` | `openjfx-extensions` | `1.0` | `client/lib/` |

### Third-Party Non-Standard
| JAR File | Group ID | Artifact ID | Version | Source |
|----------|----------|-------------|---------|--------|
| `not-going-to-be-commons-ssl-0.3.18.jar` | `ca.juliusdavies` | `not-yet-commons-ssl` | `0.3.18` | `server/lib/` |
| `zip4j_1.3.3.jar` | `net.lingala.zip4j` | `zip4j` | `1.3.3` | `server/lib/` |

### WebAdmin
| JAR File | Group ID | Artifact ID | Version | Source |
|----------|----------|-------------|---------|--------|
| `stripes.jar` | `net.sourceforge.stripes` | `stripes` | `1.6.0` | `webadmin/WebContent/WEB-INF/lib/` |

## Directory Structure

```
libs-local/
├── README.md                     # This file
├── install-local-deps.sh         # Installation script
├── flat/                         # Flat directory for direct JAR references
│   └── *.jar                     # JARs that can't be structured
└── com/                          # Maven repository structure
    └── mirth/
        └── mirth-vocab/
            └── 1.0/
                └── mirth-vocab-1.0.jar
```

## Adding New Local Dependencies

1. Determine the groupId, artifactId, and version for the JAR
2. Add an install command to `install-local-deps.sh`
3. Update this README
4. Add the dependency to `gradle/libs.versions.toml` and the appropriate module's `build.gradle.kts`
