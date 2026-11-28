# Toxicgram auth prototype

Independent virtual IDs, not phone numbers. No Telegram account login codes. Deploy this directory on Railway with a persistent volume /data, HTTPS, one worker, BOT_TOKEN and a random private AUTH_SECRET (at least 32 characters). Use /start or /code in a private bot chat. POST /v1/auth/verify accepts number and code; GET /v1/me and POST /v1/auth/logout use Bearer token. GET /health reports configuration, not Bot API connectivity. Android integration and messaging not implemented. Review security and proxy rate limits before production. Do not publish secrets.
