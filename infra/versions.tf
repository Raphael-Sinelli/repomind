terraform {
  required_version = ">= 1.7"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # backend "s3" { ... } — intencionalmente omitido. Este projeto nunca rodou
  # `terraform apply`, entao nao ha state real para armazenar remotamente ainda.
}

provider "aws" {
  region = var.aws_region
}
