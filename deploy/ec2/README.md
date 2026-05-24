# EC2 Deployment

This deployment path is for the monolithic Haven backend running as one Docker
container on EC2. PostgreSQL, Kafka, and object storage should be external
managed services.

## Runtime Secrets

Store the backend runtime environment in AWS Systems Manager Parameter Store as
one encrypted `SecureString`.

Default parameter name:

```text
/haven/production/env
```

Override it with the GitHub repository variable `SSM_ENV_PARAMETER` if another
name is preferred.

Example value:

```env
PORT=8080
DB_HOST=example.rds.amazonaws.com
DB_PORT=5432
DB_NAME=dreamhomes_haven
DB_USERNAME=postgres
DB_PASSWORD=replace-me
KAFKA_BOOTSTRAP_SERVERS=replace-me
ADMIN_EMAIL=admin@dreamhomes.local
ADMIN_PASSWORD_HASH=replace-me
HAVEN_JWT_PRIVATE_KEY=replace-me
HAVEN_JWT_PUBLIC_KEY=replace-me
HAVEN_PHOTOS_STORAGE=r2
HAVEN_PHOTOS_R2_ENDPOINT=replace-me
HAVEN_PHOTOS_R2_ACCESS_KEY_ID=replace-me
HAVEN_PHOTOS_R2_SECRET_ACCESS_KEY=replace-me
HAVEN_PHOTOS_R2_BUCKET=haven
HAVEN_PHOTOS_R2_PUBLIC_BASE_URL=replace-me
CORS_ALLOWED_ORIGINS=https://your-frontend.example
```

The EC2 instance role needs permission to read and decrypt that parameter:

```json
{
  "Effect": "Allow",
  "Action": [
    "ssm:GetParameter",
    "kms:Decrypt"
  ],
  "Resource": "*"
}
```

Scope the `Resource` ARN tightly in production.

## GitHub Environment

The EC2 deploy workflow uses the `production` GitHub Environment. Configure
required reviewers in GitHub so pushes to `main` build the release but require
approval before the deployment job can touch EC2.
