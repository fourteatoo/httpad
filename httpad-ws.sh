#!/bin/sh

# Usage: ./watch-window.sh [PORT]
# Default port: 8080

PORT="${1:-8080}"
API_URL="http://localhost:${PORT}/api/focus"

echo "Listening for active window changes -> sending EDN to ${API_URL}"

xprop -root -spy _NET_ACTIVE_WINDOW | while read -r line; do
  # Extract the window ID from xprop output
  WIN_ID=$(echo "$line" | awk '{print $NF}')
  
  if [ "$WIN_ID" != "0x0" ] && [ -n "$WIN_ID" ]; then
    # Fetch WM_CLASS for the active window and clean it
    WIN_CLASS=$(xprop -id "$WIN_ID" WM_CLASS 2>/dev/null | awk -F '"' '{print $(NF-1)}' | tr '[:upper:]' '[:lower:]')
    
    if [ -n "$WIN_CLASS" ]; then
      # Post EDN payload to HTTPAD backend
      curl -s -X POST "$API_URL" \
           -H "Content-Type: application/edn" \
           -d "{:window \"$WIN_CLASS\"}" > /dev/null
    fi
  fi
done
