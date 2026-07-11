datacenter = "dc1"
data_dir   = "/consul/data"

ui_config {
  enabled = true
}

server           = true
bootstrap_expect = 1

bind_addr   = "0.0.0.0"
client_addr = "0.0.0.0"

log_level = "INFO"
