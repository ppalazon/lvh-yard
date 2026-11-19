package tlsca

import (
	"crypto/aes"
	"crypto/x509/pkix"
	"encoding/asn1"
	"encoding/pem"
	"strings"
	"testing"
)

// keyParts are the parts of an encrypted key. A test changes one and checks that readEncryptedKey refuses it.
type keyParts struct {
	outer      asn1.ObjectIdentifier
	kdfOID     asn1.ObjectIdentifier
	schemeOID  asn1.ObjectIdentifier
	prf        asn1.ObjectIdentifier
	iterations int
	keyLength  int
	iv         []byte
	data       []byte
}

func goodParts() keyParts {
	return keyParts{
		outer: oidPBES2, kdfOID: oidPBKDF2, schemeOID: oidAES256CBC, prf: oidHMACSHA256,
		iterations: 1000, keyLength: 32,
		iv: make([]byte, aes.BlockSize), data: make([]byte, 2*aes.BlockSize),
	}
}

func (p keyParts) der(t *testing.T) []byte {
	t.Helper()
	mustMarshal := func(v any) []byte {
		out, err := asn1.Marshal(v)
		if err != nil {
			t.Fatal(err)
		}
		return out
	}
	kdf := mustMarshal(pbkdf2Params{
		Salt: []byte("salt"), Iterations: p.iterations, KeyLength: p.keyLength,
		PRF: pkix.AlgorithmIdentifier{Algorithm: p.prf, Parameters: asn1.NullRawValue},
	})
	scheme := mustMarshal(p.iv)
	params := mustMarshal(pbes2Params{
		KDF:    pkix.AlgorithmIdentifier{Algorithm: p.kdfOID, Parameters: asn1.RawValue{FullBytes: kdf}},
		Scheme: pkix.AlgorithmIdentifier{Algorithm: p.schemeOID, Parameters: asn1.RawValue{FullBytes: scheme}},
	})
	return mustMarshal(encryptedKeyInfo{pkix.AlgorithmIdentifier{Algorithm: p.outer, Parameters: asn1.RawValue{FullBytes: params}}, p.data})
}

func TestReadEncryptedKeyRefusesWhatEncryptKeyDoesNotMake(t *testing.T) {
	if _, _, _, err := readEncryptedKey(goodParts().der(t)); err != nil {
		t.Fatalf("the good key: %v", err)
	}
	otherOID := asn1.ObjectIdentifier{1, 2, 3, 4}
	cases := []struct {
		name   string
		change func(*keyParts)
		want   string
	}{
		{"not PBES2", func(p *keyParts) { p.outer = otherOID }, "not a PKCS#8 key with a password"},
		{"another KDF", func(p *keyParts) { p.kdfOID = otherOID }, "only PBKDF2 with AES-256-CBC"},
		{"another cipher", func(p *keyParts) { p.schemeOID = otherOID }, "only PBKDF2 with AES-256-CBC"},
		{"another PRF", func(p *keyParts) { p.prf = otherOID }, "only PBKDF2 with HMAC-SHA256"},
		{"no rounds", func(p *keyParts) { p.iterations = 0 }, "only PBKDF2 with HMAC-SHA256"},
		{"too many rounds", func(p *keyParts) { p.iterations = maxKeyRounds + 1 }, "only PBKDF2 with HMAC-SHA256"},
		{"another key length", func(p *keyParts) { p.keyLength = 16 }, "only PBKDF2 with HMAC-SHA256"},
		{"short IV", func(p *keyParts) { p.iv = make([]byte, 8) }, "invalid CA key"},
		{"no data", func(p *keyParts) { p.data = nil }, "invalid CA key"},
		{"data off the block size", func(p *keyParts) { p.data = make([]byte, aes.BlockSize+1) }, "invalid CA key"},
	}
	for _, c := range cases {
		parts := goodParts()
		c.change(&parts)
		if _, _, _, err := readEncryptedKey(parts.der(t)); err == nil || !strings.Contains(err.Error(), c.want) {
			t.Errorf("%s: %v", c.name, err)
		}
	}
	if _, _, _, err := readEncryptedKey(append(goodParts().der(t), 0)); err == nil {
		t.Error("trailing bytes after the key were accepted")
	}
	if _, _, _, err := readEncryptedKey([]byte("garbage")); err == nil {
		t.Error("garbage was accepted")
	}
}

func TestAKeyWithAnEmptyKeyLengthUsesTheDefault(t *testing.T) {
	parts := goodParts()
	parts.keyLength = 0
	if _, _, _, err := readEncryptedKey(parts.der(t)); err != nil {
		t.Errorf("PBKDF2 allows no key length: %v", err)
	}
}

func TestEncryptKeyUsesTheRoundsThatTheDocumentationNames(t *testing.T) {
	_, key, err := NewCA("rounds", 30)
	if err != nil {
		t.Fatal(err)
	}
	keyPEM, err := EncryptKey(key, "password")
	if err != nil {
		t.Fatal(err)
	}
	block, _ := pem.Decode(keyPEM)
	kdf, iv, data, err := readEncryptedKey(block.Bytes)
	if err != nil {
		t.Fatal(err)
	}
	if kdf.Iterations != 600000 || kdf.KeyLength != 32 || len(kdf.Salt) != 16 || len(iv) != aes.BlockSize || len(data)%aes.BlockSize != 0 {
		t.Errorf("rounds %d, key length %d, salt %d bytes, IV %d bytes, data %d bytes", kdf.Iterations, kdf.KeyLength, len(kdf.Salt), len(iv), len(data))
	}
}
