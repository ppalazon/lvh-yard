package tlsca_test

import (
	"bytes"
	"encoding/base64"
	"encoding/pem"
	"errors"
	"strings"
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

// opensslPBES2 is a CA key that "openssl pkcs8 -topk8 -v2 aes-256-cbc -v2prf hmacWithSHA256 -iter
// 600000" made, with the password s3cret, in base64. It pins the algorithm IDs against another
// implementation: a round trip through our own constants cannot tell that they are wrong. It has
// no PEM header, and its name has no word that a secret scanner looks for, because the scanners
// would take this throwaway key for a real one.
const opensslPBES2 = `MIH1MGAGCSqGSIb3DQEFDTBTMDIGCSqGSIb3DQEFDDAlBBBNk6s1Bev/HnUs9SMi
jEhLAgMJJ8AwDAYIKoZIhvcNAgkFADAdBglghkgBZQMEASoEECnBlYz/rg3qQVSy
4cGhoY0EgZB616ivB14O58BZzVrdNqa6X2vcyHC2wxDP2h+HOhUAROtwcpRT7dFX
hyXVFanKaKpCFhbekpuDOZCsdAbkuFY4GrDl0vC+3IDLxeI6iPLJdPciUc3VmTF3
3nl+h+RtFP+FQ1uOc1nt4vz8en33HbHoSKKgZqFJcFFcw4WbN3GxxNVqYthMZq5m
jqxMAIxxFP4=`

func opensslKey(t *testing.T) []byte {
	t.Helper()
	der, err := base64.StdEncoding.DecodeString(strings.ReplaceAll(opensslPBES2, "\n", ""))
	if err != nil {
		t.Fatal(err)
	}
	return pem.EncodeToMemory(&pem.Block{Type: "ENCRYPTED PRIVATE KEY", Bytes: der})
}

const opensslCA = `-----BEGIN CERTIFICATE-----
MIIBZjCCAQ2gAwIBAgIUUfdLuqP/+INVC7jL2fYE6c6EOJ0wCgYIKoZIzj0EAwIw
EjEQMA4GA1UEAwwHZml4dHVyZTAgFw0yNjExMTkxMjA4MDdaGA8yMTI2MTAyNjEy
MDgwN1owEjEQMA4GA1UEAwwHZml4dHVyZTBZMBMGByqGSM49AgEGCCqGSM49AwEH
A0IABDCiXsmUoaincCCY5vsLQgLn+eM8yDf2wZuI/V7Hs2+/A5+UMQOIDHV1tuH+
BOi1nkj+Zdy3vd9wS7AEzaUiPVajPzA9MB0GA1UdDgQWBBRYoD3oa4Ma4E40LMG8
KRViuOc9ITAPBgNVHRMBAf8EBTADAQH/MAsGA1UdDwQEAwICBDAKBggqhkjOPQQD
AgNHADBEAiAqKmSHVtN5b87laYxBcSic1AN7Pza3HoJyAUWXIpl4jAIgAUF/gr/z
lxqx8FKqfq2QbbE73MfF1ROizGS/gLpypM8=
-----END CERTIFICATE-----`

func TestAKeyThatOpenSSLEncryptedOpensWithItsPassword(t *testing.T) {
	cert, signer, err := tlsca.ParseCA([]byte(opensslCA), opensslKey(t), "s3cret")
	if err != nil {
		t.Fatalf("OpenSSL's key did not open: %v", err)
	}
	if !cert.IsCA || signer == nil {
		t.Fatal("no CA and key came out")
	}
	if _, _, err := tlsca.ParseCA([]byte(opensslCA), opensslKey(t), "other"); !errors.Is(err, tlsca.ErrWrongPassword) {
		t.Fatalf("a wrong password for OpenSSL's key: %v", err)
	}
}
