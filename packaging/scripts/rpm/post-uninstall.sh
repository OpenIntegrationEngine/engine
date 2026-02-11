#!/bin/bash
# RPM post-uninstall script for Open Integration Engine
# Cleans up after removal

set -e

# Only run on uninstall, not upgrade
# $1 will be 0 on uninstall, 1 on upgrade
if [ "$1" = "0" ]; then
    # Reload systemd to remove the service
    systemctl daemon-reload

    echo ""
    echo "========================================"
    echo "Open Integration Engine has been removed."
    echo ""
    echo "The following directories have been preserved:"
    echo "  /var/log/oie  - Log files"
    echo "  /var/lib/oie  - Application data"
    echo "  /etc/oie      - Configuration files"
    echo ""
    echo "To completely remove all data, run:"
    echo "  sudo rm -rf /var/log/oie /var/lib/oie /etc/oie"
    echo ""
    echo "The 'oie' user and group have been preserved."
    echo "To remove them, run:"
    echo "  sudo userdel oie"
    echo "  sudo groupdel oie"
    echo "========================================"
fi

exit 0
