#!/bin/bash
# RPM pre-uninstall script for Open Integration Engine
# Stops the service before removal

set -e

# Only run on uninstall, not upgrade
# $1 will be 0 on uninstall, 1 on upgrade
if [ "$1" = "0" ]; then
    echo "Stopping Open Integration Engine service..."

    # Stop the service if it's running
    if systemctl is-active --quiet oie; then
        systemctl stop oie
        echo "Service stopped."
    fi

    # Disable the service
    if systemctl is-enabled --quiet oie 2>/dev/null; then
        systemctl disable oie
        echo "Service disabled."
    fi
fi

exit 0
