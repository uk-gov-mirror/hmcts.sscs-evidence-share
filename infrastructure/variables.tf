variable "product" {
  type    = "string"
}

variable "component" {
  type = "string"
}

variable "location_app" {
  type    = "string"
  default = "UK South"
}

variable "env" {
  type = "string"
}

variable "ilbIp" {}

variable "subscription" {}

variable "capacity" {
  default = "1"
}

variable "common_tags" {
  type = "map"
}

variable "ccd_idam_s2s_auth_microservice" {
  default = "sscs"
}

variable "idam_oauth2_client_id" {
  default = "sscs"
}

variable "idam_redirect_url" {
  default = "https://sscs-ccd-callback-orchestrator-sandbox.service.core-compute-sandbox.internal"
}

variable "trust_all_certs" {
  default = false
}

variable "logback_require_alert_level" {
  default = false
}

variable "logback_require_error_code" {
  default = false
}

variable "send_letter_service_base_url" {
  default = ""
}

variable "send_letter_service_enabled" {
  default = true
}