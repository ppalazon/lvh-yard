package tlsca_test

import (
	"bytes"
	"errors"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/tlsca"
)

func TestACAKeyWithAPasswordOpensOnlyWithIt(t *testing.T) {
	ca := newAuthority(t, 365)
	keyPEM, err := tlsca.EncryptKey(ca.key, "s3cret")
	if err != nil {
		t.Fatal(err)
	}
	if !tlsca.KeyEncrypted(keyPEM) || tlsca.KeyEncrypted(ca.keyPEM) {
		t.Fatal("KeyEncrypted does not tell the two keys apart")
	}
	if bytes.Contains(keyPEM, []byte("EC PRIVATE KEY")) {
		t.Fatal("the key is not encrypted")
	}
	if _, signer, err := tlsca.ParseCA(ca.caPEM, keyPEM, "s3cret"); err != nil || !signer.Equal(ca.key) {
		t.Fatalf("the right password did not open the key: %v", err)
	}
	if _, _, err := tlsca.ParseCA(ca.caPEM, keyPEM, ""); !errors.Is(err, tlsca.ErrPasswordRequired) {
		t.Fatalf("no password: %v", err)
	}
	for _, wrong := range []string{"s3cre", "S3CRET", " s3cret"} {
		if _, _, err := tlsca.ParseCA(ca.caPEM, keyPEM, wrong); !errors.Is(err, tlsca.ErrWrongPassword) {
			t.Fatalf("password %q: %v", wrong, err)
		}
	}
}

func TestAKeyWithoutAPasswordIgnoresTheGivenOne(t *testing.T) {
	ca := newAuthority(t, 365)
	if _, _, err := tlsca.ParseCA(ca.caPEM, ca.keyPEM, "unused"); err != nil {
		t.Fatal(err)
	}
}
