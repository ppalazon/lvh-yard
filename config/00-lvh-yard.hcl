# Generated, do not edit.
server = true
bootstrap_expect = 1
ui_config {
  enabled = true
}
data_dir = "/consul/data"
# Otherwise Consul keeps its first Gossip Key and ignores a new one.
disable_keyring_file = true
client_addr = "127.0.0.1"
# Publish new check output at once, even when its status has not changed.
check_update_interval = "0s"
log_level = "error"
