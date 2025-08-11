#!/bin/sh

while IFS= read -r line; do
  /app/strfry --config /etc/strfry.conf sync ${line} --dir up
done < /app/relay_urls.txt
