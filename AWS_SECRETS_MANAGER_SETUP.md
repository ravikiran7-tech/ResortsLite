# AWS Secrets Manager Configuration Guide

## Overview
This application has been updated to use AWS Secrets Manager for storing and retrieving database credentials and other sensitive configuration values. This eliminates hard-coded credentials from the source code and enables secure credential rotation without redeployment.

## AWS Secrets Manager Setup

### 1. Create Secret in AWS Secrets Manager

Create a secret named `resortslite/database/credentials` (or use the name specified in `AWS_SECRET_NAME` environment variable) with the following JSON structure:

```json
{
  "db.host": "db-prod.resorts-internal.com",
  "db.username": "admin",
  "db.password": "YourSecurePassword",
  "payment.api.url": "http://payment-service.internal:9090/payments/charge"
}
```

### 2. AWS CLI Command to Create Secret

```bash
aws secretsmanager create-secret \
    --name resortslite/database/credentials \
    --description "Database credentials for ResortsLite application" \
    --secret-string '{
      "db.host": "db-prod.resorts-internal.com",
      "db.username": "admin",
      "db.password": "YourSecurePassword",
      "payment.api.url": "http://payment-service.internal:9090/payments/charge"
    }' \
    --region us-east-1
```

### 3. IAM Permissions Required

The application's IAM role (EC2 instance role, ECS task role, or Lambda execution role) must have the following permissions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue",
        "secretsmanager:DescribeSecret"
      ],
      "Resource": "arn:aws:secretsmanager:us-east-1:ACCOUNT_ID:secret:resortslite/database/credentials-*"
    }
  ]
}
```

### 4. Environment Variables

Set the following environment variables:

- `AWS_SECRET_NAME`: Name of the secret in AWS Secrets Manager (default: `resortslite/database/credentials`)
- `AWS_REGION`: AWS region where the secret is stored (default: `us-east-1`)

### 5. Local Development

For local development without AWS credentials, the application will fall back to environment variables:

- `DB_HOST`: Database host (default: `localhost`)
- `DB_USERNAME`: Database username (default: `sa`)
- `DB_PASSWORD`: Database password (default: empty)
- `PAYMENT_API_URL`: Payment API URL (default: `http://localhost:9090/payments/charge`)

## Benefits

1. **Security**: Credentials are encrypted at rest and in transit
2. **Rotation**: Credentials can be rotated without redeploying the application
3. **Audit**: All secret access is logged in AWS CloudTrail
4. **Compliance**: Meets cloud security compliance requirements
5. **Version Control Safety**: No credentials in source code or git history

## Code Changes

### Files Modified:
1. `pom.xml` - Added AWS Secrets Manager SDK dependency
2. `BookingService.java` - Removed hard-coded credentials (lines 22-23)
3. `config/AwsSecretsManagerConfig.java` - New configuration class for retrieving secrets
4. `application.properties` - Added AWS Secrets Manager configuration

### Hard-coded Credentials Removed:
- Line 22: `private static final String DB_USER = "admin";`
- Line 23: `private static final String DB_PASS = "Resort$Pass#2019!";`

These have been replaced with runtime retrieval from AWS Secrets Manager via the `AwsSecretsManagerConfig` class.

## Testing

To test the application:

1. Ensure AWS credentials are configured (via IAM role or AWS CLI)
2. Create the secret in AWS Secrets Manager
3. Set environment variables if needed
4. Run the application: `mvn spring-boot:run`
5. The application will automatically retrieve credentials from AWS Secrets Manager on startup

## Troubleshooting

If the application fails to retrieve secrets:
- Check IAM permissions
- Verify the secret name matches the configuration
- Check AWS region configuration
- Review CloudWatch logs for error messages
- Ensure the secret exists in the specified region
