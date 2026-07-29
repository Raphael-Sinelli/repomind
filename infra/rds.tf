resource "aws_db_subnet_group" "main" {
  name       = "${var.project}-db-subnets"
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_db_instance" "main" {
  identifier     = "${var.project}-db"
  engine         = "postgres"
  engine_version = "16"
  instance_class = var.db_instance_class

  allocated_storage = 20
  storage_encrypted = true

  db_name  = "repomind"
  username = "repomind"
  # Senha gerenciada pelo RDS + Secrets Manager, nao fixada aqui.
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  multi_az            = false # custo — em producao real, true
  skip_final_snapshot = true  # demonstracao, nunca em producao real
  publicly_accessible = false

  tags = { Name = "${var.project}-db" }
}
