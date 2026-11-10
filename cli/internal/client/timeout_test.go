package client

import (
	"context"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

type timeoutError struct{}

func (timeoutError) Error() string   { return "i/o timeout" }
func (timeoutError) Timeout() bool   { return true }
func (timeoutError) Temporary() bool { return true }

func TestConnectTimeoutSaysNothingAnswersAtTheAddress(t *testing.T) {
	c := New("http://yard.invalid:8080")
	c.http.Transport.(*http.Transport).DialContext = func(context.Context, string, string) (net.Conn, error) {
		return nil, &net.OpError{Op: "dial", Net: "tcp", Err: timeoutError{}}
	}
	_, err := c.Status()
	if err == nil || !strings.Contains(err.Error(), "does not answer at http://yard.invalid:8080/") || strings.Contains(err.Error(), "within") {
		t.Fatalf("got %v", err)
	}
}

func TestSlowAnswerNamesTheAddressAndTheWait(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte(`{"overall":`))
		w.(http.Flusher).Flush()
		<-r.Context().Done()
	}))
	defer server.Close()
	c := New(server.URL)
	c.http.Timeout = 50 * time.Millisecond
	_, err := c.Status()
	if err == nil || !strings.Contains(err.Error(), server.URL) || !strings.Contains(err.Error(), "did not answer") {
		t.Fatalf("got %v", err)
	}
}
