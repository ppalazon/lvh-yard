//go:build dockerintegration

package main_test

import (
 "crypto/tls"
 "crypto/x509"
 "encoding/json"
 "fmt"
 "io"
 "net"
 "net/http"
 "os"
 "os/exec"
 "path/filepath"
 "strings"
 "testing"
 "time"

 pkcs12 "software.sslmate.com/src/go-pkcs12"
)

// This Yard is also the seam for HTTPS, client certificates and Traefik-off tests.
func TestWorkstationTLSYard(t *testing.T) {
 image := os.Getenv("YARD_TEST_DASHBOARD_IMAGE")
 if image == "" { t.Fatal("set YARD_TEST_DASHBOARD_IMAGE to the dashboard image built from this checkout") }
 executable := filepath.Join(t.TempDir(),"lvh-yard")
 if out, err := exec.Command("go","build","-o",executable,".").CombinedOutput(); err != nil { t.Fatalf("build: %v\n%s",err,out) }
 run := func(args ...string) string { t.Helper(); out, err := exec.Command(executable,args...).CombinedOutput(); if err != nil { t.Fatalf("lvh-yard %v: %v\n%s",args,err,out) }; return string(out) }
 folders := []string{t.TempDir(),t.TempDir()}
 ports := make([][]int,2)
 var sockets []net.Listener
 for i := range ports { for range 10 { socket,err := net.Listen("tcp","127.0.0.1:0"); if err != nil { t.Fatal(err) }; sockets=append(sockets,socket); ports[i]=append(ports[i],socket.Addr().(*net.TCPAddr).Port) } }
 for _,socket := range sockets { socket.Close() }
 settings := []string{"consul-http-port","consul-dns-port","consul-grpc-tls-port","consul-server-port","consul-serf-lan-port","consul-serf-wan-port","dashboard-port","http-port","traefik-admin-port"}
 key := ""
 for i,folder := range folders {
  args := []string{"ws","init","--dir",folder,"--workstation",fmt.Sprintf("tls-ws-%d",i)}
  if i==1 { args=append(args,"--gossip-key",key) }
  run(args...)
  data,err := os.ReadFile(filepath.Join(folder,".env")); if err != nil { t.Fatal(err) }
  for _,line := range strings.Split(string(data),"\n") { if strings.HasPrefix(line,"YARD_GOSSIP_KEY=") { key=strings.Trim(strings.TrimPrefix(line,"YARD_GOSSIP_KEY="),"\"'") } }
  for j,name := range settings { run("ws","set","--dir",folder,name,fmt.Sprint(ports[i][j])) }
  run("ws","set","--dir",folder,"bind-address",fmt.Sprintf("127.0.0.%d",i+1))
  run("ws","set","--dir",folder,"peers",fmt.Sprintf("127.0.0.%d:%d",2-i,ports[1-i][5]))
  file,err := os.OpenFile(filepath.Join(folder,".env"),os.O_APPEND|os.O_WRONLY,0600); if err != nil { t.Fatal(err) }
  fmt.Fprintf(file,"\nYARD_DASHBOARD_IMAGE=%s\n",image); file.Close()
 }
 run("ws","tls","ca","create","--dir",folders[0])
 for i,folder := range folders {
  name:=fmt.Sprintf("tls-ws-%d",i)
  run("ws","tls","cert","create","--dir",folders[0],"--workstation",name)
  bundle:=filepath.Join(folders[0],"certificates",name)
  run("ws","tls","import","--dir",folder,"--ca",filepath.Join(bundle,"ca.crt"),"--cert",filepath.Join(bundle,"workstation.crt"),"--key",filepath.Join(bundle,"workstation.key"))
  run("ws","set","--dir",folder,"https-port",fmt.Sprint(ports[i][9]))
 }
 for i,folder := range folders {
  project:=fmt.Sprintf("yard-tls-%d-%d",os.Getpid(),i)
  t.Cleanup(func(){ cmd:=exec.Command("docker","compose","-p",project,"--project-directory",folder,"down","--volumes"); if out,err:=cmd.CombinedOutput(); err!=nil { t.Errorf("cleanup: %v\n%s",err,out) } })
  cmd:=exec.Command("docker","compose","-p",project,"--project-directory",folder,"up","-d")
  if out,err:=cmd.CombinedOutput(); err!=nil { t.Fatalf("compose: %v\n%s",err,out) }
 }
 client:=&http.Client{Timeout:5*time.Second}
 get:=func(url string) string { response,err:=client.Get(url); if err!=nil { return "" }; defer response.Body.Close(); body,_:=io.ReadAll(response.Body); if response.StatusCode!=200 { return "" }; return string(body) }
 deadline:=time.Now().Add(3*time.Minute)
 for {
  ready:=true
  for i:=range folders {
   var datacenters []string
   if json.Unmarshal([]byte(get(fmt.Sprintf("http://127.0.0.1:%d/v1/catalog/datacenters",ports[i][0]))),&datacenters)!=nil || len(datacenters)!=2 { ready=false }
   yard:=get(fmt.Sprintf("http://127.0.0.1:%d/api/yard",ports[i][6]))
   var result struct { Remotes []struct { Name string; Unreachable bool } }
   visible := false
   if json.Unmarshal([]byte(yard),&result)==nil { for _, remote := range result.Remotes { if remote.Name==fmt.Sprintf("tls-ws-%d",1-i) && !remote.Unreachable { visible=true } } }
   if !visible { ready=false }
  }
  if ready { break }; if time.Now().After(deadline) { t.Fatal("TLS Consuls did not federate or dashboards did not see each other") }; time.Sleep(time.Second)
 }
 caPEM,err:=os.ReadFile(filepath.Join(folders[0],"tls","ca.crt")); if err!=nil { t.Fatal(err) }
 roots:=x509.NewCertPool(); if !roots.AppendCertsFromPEM(caPEM) { t.Fatal("invalid Yard CA") }
 httpsClient:=&http.Client{Timeout:5*time.Second,Transport:&http.Transport{TLSClientConfig:&tls.Config{RootCAs:roots,ServerName:"yard.lvh.me"}}}
 deadline=time.Now().Add(time.Minute)
 for {
  request,_:=http.NewRequest("GET",fmt.Sprintf("https://127.0.0.1:%d/",ports[0][9]),nil); request.Host="yard.lvh.me"
  response,err:=httpsClient.Do(request)
  if err==nil {
   response.Body.Close()
   if response.StatusCode==200 && response.TLS!=nil && len(response.TLS.VerifiedChains)>0 { break }
  }
  if time.Now().After(deadline) { t.Fatalf("HTTPS Route did not answer with a Yard CA certificate: %v",err) }; time.Sleep(time.Second)
 }
 request,_:=http.NewRequest("GET",fmt.Sprintf("http://127.0.0.1:%d/",ports[0][7]),nil); request.Host="yard.lvh.me"
 noRedirect:=&http.Client{Timeout:5*time.Second,CheckRedirect:func(*http.Request,[]*http.Request)error{return http.ErrUseLastResponse}}
 response,err:=noRedirect.Do(request); if err!=nil { t.Fatal(err) }; response.Body.Close()
 if response.StatusCode!=200 { t.Fatalf("HTTP Route returned %d",response.StatusCode) }
 profileFile := filepath.Join(folders[0], "profiles.yaml")
 if err := os.WriteFile(profileFile, []byte("http:\n  middlewares:\n    certificate:\n      clientCertificate: {}\n"), 0600); err != nil { t.Fatal(err) }
 dashboard := fmt.Sprintf("http://127.0.0.1:%d", ports[0][6])
 run("--dashboard", dashboard, "profiles", "set", profileFile)
 run("--dashboard", dashboard, "service", "create", "private")
 run("--dashboard", dashboard, "register", "static", "private", "--host", "127.0.0.1", "--port", fmt.Sprint(ports[0][6]))
 run("--dashboard", dashboard, "publish", "private", "--profile", "certificate")
 run("ws", "tls", "client", "create", "alice", "--dir", folders[0], "--password", "integration-client")
 p12, err := os.ReadFile(filepath.Join(folders[0], "certificates", "clients", "alice.p12")); if err != nil { t.Fatal(err) }
 clientKey, clientCert, _, err := pkcs12.DecodeChain(p12, "integration-client"); if err != nil { t.Fatal(err) }
 certificate := tls.Certificate{Certificate: [][]byte{clientCert.Raw}, PrivateKey: clientKey}
 protectedTLS := &tls.Config{RootCAs: roots, ServerName: "private.lvh.me", Certificates: []tls.Certificate{certificate}}
 protectedClient := &http.Client{Timeout: 5*time.Second, Transport: &http.Transport{TLSClientConfig: protectedTLS}}
 protectedRequest := func(scheme string) *http.Request {
  port := ports[0][9]; if scheme == "http" { port = ports[0][7] }
  request, err := http.NewRequest("GET", fmt.Sprintf("%s://127.0.0.1:%d/", scheme, port), nil); if err != nil { t.Fatal(err) }
  request.Host = "private.lvh.me"
  return request
 }
 deadline = time.Now().Add(time.Minute)
 for {
  response, err := protectedClient.Do(protectedRequest("https"))
  if err == nil { response.Body.Close(); if response.StatusCode == 200 { break } }
  if time.Now().After(deadline) { t.Fatalf("Client Certificate Route did not accept the CLI certificate: %v", err) }; time.Sleep(time.Second)
 }
 withoutCertificate := &http.Client{Timeout: 5*time.Second, Transport: &http.Transport{TLSClientConfig: &tls.Config{RootCAs: roots, ServerName: "private.lvh.me"}}}
 response, err = withoutCertificate.Do(protectedRequest("https"))
 if response != nil { response.Body.Close() }
 if err == nil || !strings.Contains(strings.ToLower(err.Error()), "certificate") { t.Fatalf("Route did not refuse the missing Client Certificate: response=%v error=%v", response, err) }
 response, err = noRedirect.Do(protectedRequest("http")); if err != nil { t.Fatal(err) }; response.Body.Close()
 if response.StatusCode != http.StatusNotFound { t.Fatalf("Client Certificate Route exposed HTTP: %d", response.StatusCode) }
 run("ws","set","--dir",folders[1],"traefik","off")
 project:=fmt.Sprintf("yard-tls-%d-1",os.Getpid())
 compose:=func(args ...string) string { t.Helper(); base:=[]string{"compose","-p",project,"--project-directory",folders[1]}; out,err:=exec.Command("docker",append(base,args...)...).CombinedOutput(); if err!=nil { t.Fatalf("compose %v: %v\\n%s",args,err,out) }; return string(out) }
 compose("--profile","traefik","stop","traefik")
 compose("up","-d","--force-recreate")
 running:=strings.Fields(compose("ps","--services","--status","running"))
 if len(running)!=2 || !strings.Contains(strings.Join(running," "),"consul") || !strings.Contains(strings.Join(running," "),"dashboard") { t.Fatalf("Traefik-off services: %v",running) }
 deadline=time.Now().Add(3*time.Minute)
 for {
  var datacenters []string
  if json.Unmarshal([]byte(get(fmt.Sprintf("http://127.0.0.1:%d/v1/catalog/datacenters",ports[1][0]))),&datacenters)==nil && len(datacenters)==2 && get(fmt.Sprintf("http://127.0.0.1:%d/actuator/health",ports[1][6]))!="" { break }
  if time.Now().After(deadline) { t.Fatal("Traefik-off Workstation lost federation or dashboard health") }; time.Sleep(time.Second)
 }
}
