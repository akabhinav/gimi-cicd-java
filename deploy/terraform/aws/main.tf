# ─────────────────────────────────────────────────────────────────────────────
# GIMI CI/CD — AWS Deployment (ECS Fargate + RDS + ElastiCache)
# ─────────────────────────────────────────────────────────────────────────────

terraform {
  required_version = ">= 1.5"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.region
}

variable "region"         { default = "us-east-1" }
variable "domain"         { default = "gimi.example.com" }
variable "admin_password" { sensitive = true }
variable "jwt_secret"     { sensitive = true }
variable "worker_count"   { default = 3 }
variable "environment"    { default = "production" }

locals {
  name = "gimi-${var.environment}"
  tags = {
    Project     = "gimi-cicd"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# ── VPC ──────────────────────────────────────────────────────────────────────
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.0"

  name = local.name
  cidr = "10.0.0.0/16"

  azs             = ["${var.region}a", "${var.region}b", "${var.region}c"]
  private_subnets = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
  public_subnets  = ["10.0.101.0/24", "10.0.102.0/24", "10.0.103.0/24"]

  enable_nat_gateway   = true
  single_nat_gateway   = var.environment != "production"
  enable_dns_hostnames = true

  tags = local.tags
}

# ── RDS PostgreSQL ───────────────────────────────────────────────────────────
resource "aws_db_subnet_group" "gimi" {
  name       = local.name
  subnet_ids = module.vpc.private_subnets
  tags       = local.tags
}

resource "aws_security_group" "rds" {
  name_prefix = "${local.name}-rds-"
  vpc_id      = module.vpc.vpc_id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }

  tags = local.tags
}

resource "aws_rds_cluster" "gimi" {
  cluster_identifier     = local.name
  engine                 = "aurora-postgresql"
  engine_version         = "16.1"
  database_name          = "gimi"
  master_username        = "gimi"
  master_password        = var.admin_password
  db_subnet_group_name   = aws_db_subnet_group.gimi.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  skip_final_snapshot    = var.environment != "production"
  deletion_protection    = var.environment == "production"

  serverlessv2_scaling_configuration {
    min_capacity = 0.5
    max_capacity = 8.0
  }

  tags = local.tags
}

resource "aws_rds_cluster_instance" "gimi" {
  count              = var.environment == "production" ? 2 : 1
  identifier         = "${local.name}-${count.index}"
  cluster_identifier = aws_rds_cluster.gimi.id
  instance_class     = "db.serverless"
  engine             = aws_rds_cluster.gimi.engine
  engine_version     = aws_rds_cluster.gimi.engine_version
}

# ── ElastiCache Redis ────────────────────────────────────────────────────────
resource "aws_security_group" "redis" {
  name_prefix = "${local.name}-redis-"
  vpc_id      = module.vpc.vpc_id

  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }

  tags = local.tags
}

resource "aws_elasticache_subnet_group" "gimi" {
  name       = local.name
  subnet_ids = module.vpc.private_subnets
}

resource "aws_elasticache_replication_group" "gimi" {
  replication_group_id = local.name
  description          = "GIMI CI/CD Redis"
  engine               = "redis"
  engine_version       = "7.0"
  node_type            = "cache.t4g.medium"
  num_cache_clusters   = var.environment == "production" ? 2 : 1
  port                 = 6379
  subnet_group_name    = aws_elasticache_subnet_group.gimi.name
  security_group_ids   = [aws_security_group.redis.id]
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true

  tags = local.tags
}

# ── ECS Cluster ──────────────────────────────────────────────────────────────
resource "aws_ecs_cluster" "gimi" {
  name = local.name

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = local.tags
}

resource "aws_security_group" "ecs" {
  name_prefix = "${local.name}-ecs-"
  vpc_id      = module.vpc.vpc_id

  ingress {
    from_port   = 8080
    to_port     = 8081
    protocol    = "tcp"
    cidr_blocks = [module.vpc.vpc_cidr_block]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = local.tags
}

# ── ECS Task Definitions ────────────────────────────────────────────────────
resource "aws_ecs_task_definition" "server" {
  family                   = "${local.name}-server"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = 1024
  memory                   = 2048
  execution_role_arn       = aws_iam_role.ecs_execution.arn

  container_definitions = jsonencode([{
    name  = "gimi-server"
    image = "gimi/gimi-server:latest"
    portMappings = [{ containerPort = 8080, protocol = "tcp" }]
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "prod,distributed" },
      { name = "GIMI_DB_URL", value = "jdbc:postgresql://${aws_rds_cluster.gimi.endpoint}:5432/gimi" },
      { name = "GIMI_DB_USERNAME", value = "gimi" },
      { name = "GIMI_REDIS_URL", value = "redis://${aws_elasticache_replication_group.gimi.primary_endpoint_address}:6379" },
    ]
    secrets = [
      { name = "GIMI_DB_PASSWORD", valueFrom = aws_ssm_parameter.db_password.arn },
      { name = "GIMI_JWT_SECRET", valueFrom = aws_ssm_parameter.jwt_secret.arn },
      { name = "GIMI_ADMIN_PASSWORD", valueFrom = aws_ssm_parameter.admin_password.arn },
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.gimi.name
        "awslogs-region"        = var.region
        "awslogs-stream-prefix" = "server"
      }
    }
    healthCheck = {
      command     = ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 60
    }
  }])

  tags = local.tags
}

resource "aws_ecs_task_definition" "worker" {
  family                   = "${local.name}-worker"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = 2048
  memory                   = 4096
  execution_role_arn       = aws_iam_role.ecs_execution.arn

  container_definitions = jsonencode([{
    name  = "gimi-worker"
    image = "gimi/gimi-worker:latest"
    environment = [
      { name = "GIMI_REDIS_URL", value = "redis://${aws_elasticache_replication_group.gimi.primary_endpoint_address}:6379" },
      { name = "GIMI_SERVER_URL", value = "http://gimi-server.${local.name}:8080" },
      { name = "GIMI_WORKER_MAX_CONCURRENT", value = "200" },
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.gimi.name
        "awslogs-region"        = var.region
        "awslogs-stream-prefix" = "worker"
      }
    }
  }])

  tags = local.tags
}

# ── ECS Services ─────────────────────────────────────────────────────────────
resource "aws_ecs_service" "server" {
  name            = "${local.name}-server"
  cluster         = aws_ecs_cluster.gimi.id
  task_definition = aws_ecs_task_definition.server.arn
  desired_count   = 2
  launch_type     = "FARGATE"

  network_configuration {
    subnets         = module.vpc.private_subnets
    security_groups = [aws_security_group.ecs.id]
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.server.arn
    container_name   = "gimi-server"
    container_port   = 8080
  }

  tags = local.tags
}

resource "aws_ecs_service" "worker" {
  name            = "${local.name}-worker"
  cluster         = aws_ecs_cluster.gimi.id
  task_definition = aws_ecs_task_definition.worker.arn
  desired_count   = var.worker_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets         = module.vpc.private_subnets
    security_groups = [aws_security_group.ecs.id]
  }

  tags = local.tags
}

# ── ALB ──────────────────────────────────────────────────────────────────────
resource "aws_lb" "gimi" {
  name               = local.name
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = module.vpc.public_subnets
  tags               = local.tags
}

resource "aws_security_group" "alb" {
  name_prefix = "${local.name}-alb-"
  vpc_id      = module.vpc.vpc_id

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = local.tags
}

resource "aws_lb_target_group" "server" {
  name        = "${local.name}-server"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = module.vpc.vpc_id
  target_type = "ip"

  health_check {
    path                = "/actuator/health"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
  }

  tags = local.tags
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.gimi.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "redirect"
    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

# ── IAM ──────────────────────────────────────────────────────────────────────
resource "aws_iam_role" "ecs_execution" {
  name = "${local.name}-ecs-execution"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })

  tags = local.tags
}

resource "aws_iam_role_policy_attachment" "ecs_execution" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "ecs_ssm" {
  name = "${local.name}-ssm"
  role = aws_iam_role.ecs_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["ssm:GetParameters", "ssm:GetParameter"]
      Resource = "arn:aws:ssm:${var.region}:*:parameter/${local.name}/*"
    }]
  })
}

# ── SSM Parameters (Secrets) ────────────────────────────────────────────────
resource "aws_ssm_parameter" "db_password" {
  name  = "/${local.name}/db-password"
  type  = "SecureString"
  value = var.admin_password
  tags  = local.tags
}

resource "aws_ssm_parameter" "jwt_secret" {
  name  = "/${local.name}/jwt-secret"
  type  = "SecureString"
  value = var.jwt_secret
  tags  = local.tags
}

resource "aws_ssm_parameter" "admin_password" {
  name  = "/${local.name}/admin-password"
  type  = "SecureString"
  value = var.admin_password
  tags  = local.tags
}

# ── CloudWatch ───────────────────────────────────────────────────────────────
resource "aws_cloudwatch_log_group" "gimi" {
  name              = "/ecs/${local.name}"
  retention_in_days = 30
  tags              = local.tags
}

# ── Auto Scaling ─────────────────────────────────────────────────────────────
resource "aws_appautoscaling_target" "worker" {
  max_capacity       = 50
  min_capacity       = var.worker_count
  resource_id        = "service/${aws_ecs_cluster.gimi.name}/${aws_ecs_service.worker.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "worker_cpu" {
  name               = "${local.name}-worker-cpu"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.worker.resource_id
  scalable_dimension = aws_appautoscaling_target.worker.scalable_dimension
  service_namespace  = aws_appautoscaling_target.worker.service_namespace

  target_tracking_scaling_policy_configuration {
    target_value = 70.0
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}

# ── Outputs ──────────────────────────────────────────────────────────────────
output "alb_dns" {
  value = aws_lb.gimi.dns_name
}

output "rds_endpoint" {
  value = aws_rds_cluster.gimi.endpoint
}

output "redis_endpoint" {
  value = aws_elasticache_replication_group.gimi.primary_endpoint_address
}

output "ecs_cluster" {
  value = aws_ecs_cluster.gimi.name
}
