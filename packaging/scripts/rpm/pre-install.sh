#!/bin/bash
# RPM pre-install script for Open Integration Engine
# Creates the oie user and group if they don't exist

set -e

OIE_USER="oie"
OIE_GROUP="oie"

# Create group if it doesn't exist
if ! getent group "$OIE_GROUP" > /dev/null 2>&1; then
    groupadd --system "$OIE_GROUP"
    echo "Created system group: $OIE_GROUP"
fi

# Create user if it doesn't exist
if ! getent passwd "$OIE_USER" > /dev/null 2>&1; then
    useradd --system \
        --gid "$OIE_GROUP" \
        --home-dir /opt/oie \
        --no-create-home \
        --shell /sbin/nologin \
        --comment "Open Integration Engine service account" \
        "$OIE_USER"
    echo "Created system user: $OIE_USER"
fi

# Create required directories
mkdir -p /var/log/oie
mkdir -p /var/lib/oie
mkdir -p /etc/oie

exit 0
