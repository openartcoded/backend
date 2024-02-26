#!/bin/bash

ARCH=$(uname -m)

if [ "$ARCH" = "x86_64" ]; then
    echo "Running on amd64 architecture."
    wget https://github.com/wkhtmltopdf/packaging/releases/download/0.12.6.1-3/wkhtmltox_0.12.6.1-3.jammy_amd64.deb
    dpkg -i wkhtmltox_0.12.6.1-3.jammy_amd64.deb
elif [ "$ARCH" = "aarch64" ]; then
    echo "Running on arm64 architecture."
    wget https://github.com/wkhtmltopdf/packaging/releases/download/0.12.6.1-3/wkhtmltox_0.12.6.1-3.jammy_arm64.deb
    dpkg -i wkhtmltox_0.12.6.1-3.jammy_arm64.deb

else
    echo "Unsupported architecture: $ARCH"
    exit 1
fi
