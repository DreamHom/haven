# Haven EKS Deployment Guide

This guide is written for a first deployment. We are going to keep it simple:

- Run the Spring Boot app in Amazon EKS
- Use Amazon RDS for PostgreSQL
- Use Amazon MSK for Kafka
- Use Amazon ECR to store the Docker image
- Use an AWS Application Load Balancer to expose the API

We are not moving the local `docker-compose.yml` stack into Kubernetes for production.
That file is still useful for local development.

## 1. What you now have in this repo

- `Dockerfile`: builds a production image for the Spring Boot app
- `.dockerignore`: keeps the Docker build context small
- `k8s/namespace.yaml`: creates a namespace called `haven`
- `k8s/configmap.yaml`: non-secret environment variables
- `k8s/secret.example.yaml`: a template for the secret values you must provide
- `k8s/deployment.yaml`: runs two copies of the app
- `k8s/service.yaml`: gives the app an internal Kubernetes service
- `k8s/ingress.yaml`: exposes the app using an AWS ALB
- `.github/workflows/deploy-eks.yml`: builds the image, pushes it to ECR, and deploys it

## 2. Big picture

Think of the deployment like this:

1. Your code becomes a Docker image
2. The Docker image is pushed to Amazon ECR
3. EKS pulls that image and runs it as Pods
4. The Pods connect to RDS and MSK
5. The ALB sends internet traffic to the Pods

## 3. Before you touch EKS

Install these on your machine:

- AWS CLI
- `kubectl`
- `eksctl`
- Docker

Then configure AWS:

```bash
aws configure
```

Use an AWS account where you are allowed to create:

- EKS clusters
- ECR repositories
- RDS instances
- MSK clusters
- IAM roles
- VPC resources

## 4. Create the Docker image locally first

From the repo root:

```bash
docker build -t haven:local .
```

This proves the app can be packaged as a container before we involve AWS.

## 5. Create the AWS pieces

You need four main AWS resources:

### ECR

Create a repository for the app image:

```bash
aws ecr create-repository --repository-name haven
```

### RDS PostgreSQL

Create a PostgreSQL database in AWS RDS. Save these values because you will put them in `k8s/configmap.yaml` and your secret:

- database host
- database port
- database name
- database username
- database password

### MSK

Create an MSK or MSK Serverless cluster. Save the bootstrap servers value.

You will place that value in:

- `KAFKA_BOOTSTRAP_SERVERS` in `k8s/configmap.yaml`

### EKS

Create the cluster. A beginner-friendly way is `eksctl`:

```bash
eksctl create cluster \
  --name haven-cluster \
  --region us-east-1 \
  --nodes 2 \
  --node-type t3.medium
```

This takes a while. That is normal.

## 6. Connect kubectl to the cluster

After the cluster is ready:

```bash
aws eks update-kubeconfig --name haven-cluster --region us-east-1
kubectl get nodes
```

If you see nodes, `kubectl` is talking to your cluster correctly.

## 7. Install the AWS Load Balancer Controller

This is the Kubernetes component that turns `k8s/ingress.yaml` into a real AWS ALB.

Follow the official AWS guide for this step:

- https://docs.aws.amazon.com/eks/latest/userguide/aws-load-balancer-controller.html

Do not skip this. Without it, the Ingress will not create a public load balancer.

## 8. Fill in your Kubernetes config

Edit `k8s/configmap.yaml` and replace the placeholders:

- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USERNAME`
- `KAFKA_BOOTSTRAP_SERVERS`
- `CORS_ALLOWED_ORIGINS`
- `HAVEN_PHOTOS_*` values if you are using R2 in production

Then create your real secret file from the example:

```bash
cp k8s/secret.example.yaml k8s/secret.yaml
```

Now edit `k8s/secret.yaml` and replace:

- `DB_PASSWORD`
- `ADMIN_EMAIL`
- `ADMIN_PASSWORD_HASH`
- `HAVEN_JWT_PRIVATE_KEY`
- `HAVEN_JWT_PUBLIC_KEY`
- `HAVEN_PHOTOS_R2_SECRET_ACCESS_KEY` if needed

Also edit `k8s/deployment.yaml` and replace the example ECR image:

- `123456789012.dkr.ecr.us-east-1.amazonaws.com/haven:latest`

And edit `k8s/ingress.yaml`:

- replace `api.example.com` with your real API domain

## 9. Push your image to ECR manually the first time

Find your account ID:

```bash
aws sts get-caller-identity
```

Log in to ECR:

```bash
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <your-account-id>.dkr.ecr.us-east-1.amazonaws.com
```

Build and push:

```bash
docker build -t <your-account-id>.dkr.ecr.us-east-1.amazonaws.com/haven:manual .
docker push <your-account-id>.dkr.ecr.us-east-1.amazonaws.com/haven:manual
```

Then update `k8s/deployment.yaml` to use that pushed image tag.

## 10. Deploy to Kubernetes

Apply the manifests in this order:

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/ingress.yaml
```

Check the rollout:

```bash
kubectl -n haven get pods
kubectl -n haven rollout status deployment/haven-api
kubectl -n haven get svc
kubectl -n haven get ingress
```

## 11. Debugging if the Pods do not start

These commands are your best friends:

```bash
kubectl -n haven get pods
kubectl -n haven describe pod <pod-name>
kubectl -n haven logs <pod-name>
```

Common causes:

- wrong database host
- wrong database password
- JWT keys missing or malformed
- Kafka bootstrap servers wrong
- security groups blocking access from EKS to RDS or MSK

## 12. Set up GitHub Actions deployment

The workflow file is:

- `.github/workflows/deploy-eks.yml`

Before it can work, set these GitHub repository values:

Repository variables:

- `AWS_REGION`
- `ECR_REPOSITORY`
- `EKS_CLUSTER_NAME`

Repository secret:

- `AWS_DEPLOY_ROLE_ARN`

This workflow assumes GitHub will assume an AWS IAM role using OIDC.
That is the clean modern approach because you do not store long-lived AWS keys in GitHub.

## 13. Important beginner note about secrets

Do not commit `k8s/secret.yaml`.

Only commit:

- `k8s/secret.example.yaml`

Keep the real secret file local, or move later to a stronger setup like:

- AWS Secrets Manager + External Secrets Operator

## 14. What to do next after the first successful deploy

After the app is live, the next useful improvements are:

1. Add HTTPS to the ALB
2. Move secrets fully to AWS Secrets Manager
3. Add a real domain in Route 53
4. Add monitoring and alerts
5. Tune CPU, memory, and replica counts using real traffic

## 15. If you want the easiest learning path

Follow this order:

1. Build the Docker image locally
2. Create ECR
3. Create EKS
4. Install the AWS Load Balancer Controller
5. Create RDS and MSK
6. Fill in `k8s/configmap.yaml` and `k8s/secret.yaml`
7. Push one image manually
8. Deploy with `kubectl`
9. Only after that, enable the GitHub Actions workflow

That order gives you fewer moving parts at once.
