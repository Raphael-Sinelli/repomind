resource "aws_ecs_cluster" "main" {
  name = "${var.project}-cluster"
}

resource "aws_cloudwatch_log_group" "backend" {
  name              = "/ecs/${var.project}-backend"
  retention_in_days = 14
}

resource "aws_iam_role" "ecs_execution" {
  name = "${var.project}-ecs-execution"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_execution" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# Permissao explicita para ler os 3 segredos (secrets.tf) no boot do container —
# a policy gerenciada acima cobre ECR/CloudWatch, mas nao Secrets Manager.
resource "aws_iam_role_policy" "ecs_secrets" {
  name = "${var.project}-ecs-secrets"
  role = aws_iam_role.ecs_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = ["secretsmanager:GetSecretValue"]
      Resource = [
        aws_secretsmanager_secret.github_client_secret.arn,
        aws_secretsmanager_secret.anthropic_api_key.arn,
        aws_secretsmanager_secret.db_credentials.arn,
        aws_db_instance.main.master_user_secret[0].secret_arn,
      ]
    }]
  })
}

resource "aws_ecs_task_definition" "backend" {
  family                   = "${var.project}-backend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = aws_iam_role.ecs_execution.arn

  container_definitions = jsonencode([{
    name  = "backend"
    image = "${aws_ecr_repository.backend.repository_url}:${var.backend_image_tag}"

    portMappings = [{ containerPort = 8080, protocol = "tcp" }]

    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
      { name = "SPRING_DATASOURCE_URL", value = "jdbc:postgresql://${aws_db_instance.main.address}:5432/repomind" },
      { name = "REDIS_HOST", value = aws_elasticache_cluster.main.cache_nodes[0].address },
    ]

    secrets = [
      { name = "GITHUB_CLIENT_SECRET", valueFrom = aws_secretsmanager_secret.github_client_secret.arn },
      { name = "ANTHROPIC_API_KEY", valueFrom = aws_secretsmanager_secret.anthropic_api_key.arn },
      { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = aws_db_instance.main.master_user_secret[0].secret_arn },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.backend.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "backend"
      }
    }

    healthCheck = {
      command  = ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
      interval = 30
      timeout  = 5
      retries  = 3
    }
  }])
}

resource "aws_ecs_service" "backend" {
  name            = "${var.project}-backend"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.backend.arn
  desired_count   = var.backend_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets         = aws_subnet.private[*].id
    security_groups = [aws_security_group.backend.id]
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.backend.arn
    container_name    = "backend"
    container_port    = 8080
  }

  depends_on = [aws_lb_listener.https]
}
