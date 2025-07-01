# Wazuh Indexer - Email Notification Testing with Mailpit

This guide walks you through setting up and testing Wazuh Indexer's email notification system using [Mailpit](https://github.com/axllent/mailpit) as a local SMTP server.

## Prerequisites

Before you begin, set up the following environment variables in your shell:

```shellsession
PROTO="http"
INDEXER_HOST="127.0.0.1"
INDEXER_PORT="9200"
MAILPIT_HOST="127.0.0.1"
```

> The values above are meant for testing when running `./gradlew run` from the root folder of this repository.
> Adjust them as necessary for your environment.

## 1. Start Mailpit (Docker)

Start Mailpit with the following Docker command:

```bash
docker run -d \
  --name mailpit \
  --restart unless-stopped \
  -v "$(pwd)/data:/data" \
  -p 8025:8025 \
  -p 1025:1025 \
  -e MP_MAX_MESSAGES=5000 \
  -e MP_DATABASE=/data/mailpit.db \
  -e MP_SMTP_AUTH_ACCEPT_ANY=1 \
  -e MP_SMTP_AUTH_ALLOW_INSECURE=1 \
  axllent/mailpit
```

This sets up Mailpit with persistent storage and authentication disabled, making it suitable for local testing.

## 2. Configure SMTP Account in Wazuh Indexer

Create an SMTP account pointing to Mailpit:

```bash
curl -sku admin:admin -X POST "${PROTO}://${INDEXER_IP}:${INDEXER_PORT}/_plugins/_notifications/configs/" \
  -H 'Content-Type: application/json' -d '
{
  "config_id": "mailpit-id",
  "config": {
    "name": "mailpit",
    "description": "Mailpit as a destination",
    "config_type": "smtp_account",
    "is_enabled": true,
    "smtp_account": {
      "host": "'${MAILPIT_HOST}'",
      "port": 1025,
      "method": "none",
      "from_address": "wazuh@example.com"
    }
  }
}'
```

## 3. Create an Email Notification Channel

Set up an email channel that uses the SMTP account above:

```bash
curl -sku admin:admin -X POST "${PROTO}://${INDEXER_IP}:${INDEXER_PORT}/_plugins/_notifications/configs/" \
  -H 'Content-Type: application/json' -d '
{
  "config_id": "email-channel-id",
  "config": {
    "name": "mailpit-channel",
    "description": "Test output to Mailpit",
    "config_type": "email",
    "is_enabled": true,
    "email": {
      "email_account_id": "mailpit-id",
      "recipient_list": [
        { "recipient": "recipient@example.com" }
      ],
      "email_group_id_list": []
    }
  }
}'
```

## 4. Send a Test Notification

Trigger a test email through the configured channel:

```bash
curl -sku admin:admin -X GET "${PROTO}://${INDEXER_IP}:${INDEXER_PORT}/_plugins/_notifications/feature/test/email-channel-id?pretty"
```

## 5. View Email in Mailpit

Open the Mailpit web UI in your browser at http://127.0.0.1:8025.

You should see the test email message appear in the inbox.

## Troubleshooting

- **Plugin Check**: Ensure the Notifications plugin is installed and enabled.
  - Docs: [OpenSearch Notifications API](https://docs.opensearch.org/docs/latest/observing-your-data/notifications/api/)
- **Logs**: Inspect Wazuh Indexer logs for relevant errors.
- **Networking**:
  - Confirm Docker and system firewalls allow access to ports 1025 and 8025.
