# Segredos nunca em texto plano na task definition do ECS — o container busca
# estes valores no boot via a integracao nativa do ECS com o Secrets Manager
# (secrets{} block em ecs.tf), nao via variavel de ambiente comum.

resource "aws_secretsmanager_secret" "github_client_secret" {
  name = "${var.project}/github-client-secret"
}

resource "aws_secretsmanager_secret" "anthropic_api_key" {
  name = "${var.project}/anthropic-api-key"
}

resource "aws_secretsmanager_secret" "db_credentials" {
  name = "${var.project}/db-credentials"
}

# Os valores em si (aws_secretsmanager_secret_version) nao sao definidos aqui —
# seriam preenchidos fora do Terraform (CLI/console) ou via variavel sensitive
# passada em tempo de apply, nunca commitados neste repositorio.
