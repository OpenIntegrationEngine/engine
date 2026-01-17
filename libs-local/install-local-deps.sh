#!/bin/bash
# Script to install non-Maven-Central JARs into local repository structure
# Run from the project root directory

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
LIBS_LOCAL="$SCRIPT_DIR"

# Function to install a JAR into local Maven repository
install_jar() {
    local src_jar="$1"
    local group_id="$2"
    local artifact_id="$3"
    local version="$4"

    if [ ! -f "$src_jar" ]; then
        echo "WARNING: Source JAR not found: $src_jar"
        return 1
    fi

    # Create directory structure: groupId/artifactId/version/
    local group_path="${group_id//./\/}"
    local target_dir="$LIBS_LOCAL/$group_path/$artifact_id/$version"
    local target_jar="$target_dir/${artifact_id}-${version}.jar"

    mkdir -p "$target_dir"

    # Copy JAR
    cp "$src_jar" "$target_jar"

    # Create minimal POM
    cat > "$target_dir/${artifact_id}-${version}.pom" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"
         xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <modelVersion>4.0.0</modelVersion>
    <groupId>$group_id</groupId>
    <artifactId>$artifact_id</artifactId>
    <version>$version</version>
    <packaging>jar</packaging>
</project>
EOF

    echo "Installed: $group_id:$artifact_id:$version"
}

echo "Installing local dependencies into $LIBS_LOCAL"
echo "================================================"

# Server libraries
install_jar "$PROJECT_ROOT/server/lib/mirth-vocab.jar" \
    "com.mirth" "mirth-vocab" "1.0"

install_jar "$PROJECT_ROOT/server/lib/not-going-to-be-commons-ssl-0.3.18.jar" \
    "ca.juliusdavies" "not-yet-commons-ssl" "0.3.18"

install_jar "$PROJECT_ROOT/server/lib/zip4j_1.3.3.jar" \
    "net.lingala.zip4j" "zip4j" "1.3.3"

install_jar "$PROJECT_ROOT/server/lib/backport-util-concurrent-Java60-3.1.jar" \
    "backport-util-concurrent" "backport-util-concurrent-java60" "3.1"

# Server extension libraries - DICOM/DCM4CHE
install_jar "$PROJECT_ROOT/server/lib/extensions/dimse/jai_imageio.jar" \
    "com.sun.media" "jai-imageio" "1.1"

install_jar "$PROJECT_ROOT/server/lib/extensions/dimse/dcm4che-core-2.0.29.jar" \
    "dcm4che" "dcm4che-core" "2.0.29"

install_jar "$PROJECT_ROOT/server/lib/extensions/dimse/dcm4che-filecache-2.0.29.jar" \
    "dcm4che" "dcm4che-filecache" "2.0.29"

install_jar "$PROJECT_ROOT/server/lib/extensions/dimse/dcm4che-net-2.0.29.jar" \
    "dcm4che" "dcm4che-net" "2.0.29"

install_jar "$PROJECT_ROOT/server/lib/extensions/dimse/dcm4che-tool-dcmrcv-2.0.29.jar" \
    "dcm4che" "dcm4che-tool-dcmrcv" "2.0.29"

install_jar "$PROJECT_ROOT/server/lib/extensions/dimse/dcm4che-tool-dcmsnd-2.0.29.jar" \
    "dcm4che" "dcm4che-tool-dcmsnd" "2.0.29"

install_jar "$PROJECT_ROOT/server/lib/extensions/ws/wsdl4j-1.6.2-fixed.jar" \
    "wsdl4j" "wsdl4j-fixed" "1.6.2"

install_jar "$PROJECT_ROOT/server/lib/extensions/dicomviewer/ij.jar" \
    "imagej" "ij" "1.53"

install_jar "$PROJECT_ROOT/server/lib/extensions/pdfviewer/PDFRenderer.jar" \
    "com.sun.pdfview" "pdfrenderer" "0.9.1"

install_jar "$PROJECT_ROOT/server/lib/extensions/file/webdavclient4j-core-0.92.jar" \
    "com.googlecode.webdavclient4j" "webdavclient4j-core" "0.92"

# Client libraries
install_jar "$PROJECT_ROOT/client/lib/wizard.jar" \
    "com.mirth" "wizard" "1.0"

install_jar "$PROJECT_ROOT/client/lib/language_support.jar" \
    "com.mirth" "language-support" "1.0"

install_jar "$PROJECT_ROOT/client/lib/openjfx.jar" \
    "com.mirth" "openjfx-extensions" "1.0"

install_jar "$PROJECT_ROOT/client/lib/jai_imageio.jar" \
    "com.sun.media" "jai-imageio-client" "1.1"

# Client GUI libraries with problematic Maven Central POMs
install_jar "$PROJECT_ROOT/client/lib/swingx-core-1.6.2.jar" \
    "org.swinglabs" "swingx-core" "1.6.2"

install_jar "$PROJECT_ROOT/client/lib/looks-2.3.1.jar" \
    "com.jgoodies" "looks" "2.3.1"

# javax/JAXB/JAXWS extension libraries
install_jar "$PROJECT_ROOT/server/lib/javax/jaxb/ext/istack-commons-runtime-3.0.6.jar" \
    "com.sun.istack" "istack-commons-runtime" "3.0.6"

install_jar "$PROJECT_ROOT/server/lib/javax/jaxws/ext/FastInfoset-1.2.13.jar" \
    "com.sun.xml.fastinfoset" "FastInfoset" "1.2.13"

install_jar "$PROJECT_ROOT/server/lib/javax/jaxws/ext/jsr181-api-1.0.jar" \
    "javax.jws" "jsr181-api" "1.0"

install_jar "$PROJECT_ROOT/server/lib/javax/jaxws/ext/management-api-3.2.1.b001.jar" \
    "org.glassfish.external" "management-api" "3.2.1.b001"

install_jar "$PROJECT_ROOT/server/lib/javax/jaxws/ext/gmbal-api-only-3.1.0.b001.jar" \
    "org.glassfish.gmbal" "gmbal-api-only" "3.1.0.b001"

install_jar "$PROJECT_ROOT/server/lib/javax/jaxws/ext/ha-api-3.1.9.jar" \
    "org.glassfish.ha" "ha-api" "3.1.9"

install_jar "$PROJECT_ROOT/server/lib/javax/jaxws/ext/policy-2.7.2.jar" \
    "com.sun.xml.ws" "policy" "2.7.2"

install_jar "$PROJECT_ROOT/server/lib/javax/jaxws/ext/mimepull-1.9.7.jar" \
    "org.jvnet.mimepull" "mimepull" "1.9.7"

install_jar "$PROJECT_ROOT/server/lib/javax/jaxws/ext/saaj-impl-1.0.jar" \
    "com.sun.xml.messaging.saaj" "saaj-impl" "1.0"

install_jar "$PROJECT_ROOT/server/lib/javax/jaxws/ext/stax-ex-1.8.jar" \
    "org.jvnet.staxex" "stax-ex" "1.8"

install_jar "$PROJECT_ROOT/server/lib/javax/jaxws/ext/streambuffer-1.5.4.jar" \
    "com.sun.xml.stream.buffer" "streambuffer" "1.5.4"

# WebAdmin libraries
install_jar "$PROJECT_ROOT/webadmin/WebContent/WEB-INF/lib/stripes.jar" \
    "net.sourceforge.stripes" "stripes" "1.6.0"

echo ""
echo "================================================"
echo "Local dependencies installation complete!"
echo ""
echo "You can now build with: ./gradlew build"
