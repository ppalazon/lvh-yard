# Generated, do not edit.
# HTTP stays local and plain; only inter-Workstation RPC requires TLS.
tls {
  defaults {
    ca_file = "/consul/tls/ca.crt"
    cert_file = "/consul/tls/consul.crt"
    key_file = "/consul/tls/consul.key"
    verify_incoming = true
    verify_outgoing = true
  }
  internal_rpc {
    verify_server_hostname = true
  }
  https {
    verify_incoming = false
  }
}
