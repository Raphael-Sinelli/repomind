variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "project" {
  type    = string
  default = "repomind"
}

variable "vpc_cidr" {
  type    = string
  default = "10.0.0.0/16"
}

variable "availability_zones" {
  type    = list(string)
  default = ["us-east-1a", "us-east-1b"]
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "redis_node_type" {
  type    = string
  default = "cache.t4g.micro"
}

variable "backend_image_tag" {
  description = "Tag da imagem publicada no ECR (ex: git SHA do commit em CI)."
  type        = string
  default     = "latest"
}

variable "backend_desired_count" {
  type    = number
  default = 1
}
