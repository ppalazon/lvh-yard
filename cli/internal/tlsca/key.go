package tlsca

import (
	"bytes"
	"crypto/aes"
	"crypto/cipher"
	"crypto/ecdsa"
	"crypto/pbkdf2"
	"crypto/rand"
	"crypto/sha256"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/asn1"
	"encoding/pem"
	"errors"
	"fmt"
)

// A key with a password is PKCS#8 with PBES2 (PBKDF2-HMAC-SHA256 and AES-256-CBC), in a PEM block of
// type ENCRYPTED PRIVATE KEY. OpenSSL reads it, for example with openssl pkey. The standard
// library can only encrypt the old PEM format, which uses MD5 and has no integrity check.
const encryptedKeyType = "ENCRYPTED PRIVATE KEY"

// 600000 rounds is the OWASP advice for PBKDF2-HMAC-SHA256. The CA key signs rarely.
const (
	keyRounds    = 600000
	maxKeyRounds = 10_000_000
)

var (
	oidPBES2      = asn1.ObjectIdentifier{1, 2, 840, 113549, 1, 5, 13}
	oidPBKDF2     = asn1.ObjectIdentifier{1, 2, 840, 113549, 1, 5, 12}
	oidHMACSHA256 = asn1.ObjectIdentifier{1, 2, 840, 113549, 2, 9}
	oidAES256CBC  = asn1.ObjectIdentifier{2, 16, 840, 1, 101, 3, 4, 1, 42}
)

// ErrPasswordRequired means that the key has a password and none was given.
var ErrPasswordRequired = errors.New("the CA key has a password")

// ErrWrongPassword means that the key did not open with the password.
var ErrWrongPassword = errors.New("wrong password for the CA key")

type encryptedKeyInfo struct {
	Algorithm pkix.AlgorithmIdentifier
	Data      []byte
}

type pbes2Params struct {
	KDF    pkix.AlgorithmIdentifier
	Scheme pkix.AlgorithmIdentifier
}

type pbkdf2Params struct {
	Salt       []byte
	Iterations int
	KeyLength  int `asn1:"optional"`
	PRF        pkix.AlgorithmIdentifier
}

// KeyEncrypted reports whether the PEM of a key has a password.
func KeyEncrypted(keyPEM []byte) bool {
	block, _ := pem.Decode(keyPEM)
	return block != nil && block.Type == encryptedKeyType
}

// EncryptKey gives the PEM of a key that opens only with the password.
func EncryptKey(key *ecdsa.PrivateKey, password string) ([]byte, error) {
	plain, err := x509.MarshalPKCS8PrivateKey(key)
	if err != nil {
		return nil, err
	}
	salt, iv := make([]byte, 16), make([]byte, aes.BlockSize)
	if _, err := rand.Read(salt); err != nil {
		return nil, err
	}
	if _, err := rand.Read(iv); err != nil {
		return nil, err
	}
	derived, err := pbkdf2.Key(sha256.New, password, salt, keyRounds, 32)
	if err != nil {
		return nil, err
	}
	block, err := aes.NewCipher(derived)
	if err != nil {
		return nil, err
	}
	padding := aes.BlockSize - len(plain)%aes.BlockSize
	padded := append(plain, bytes.Repeat([]byte{byte(padding)}, padding)...)
	cipher.NewCBCEncrypter(block, iv).CryptBlocks(padded, padded)

	kdf, err := asn1.Marshal(pbkdf2Params{Salt: salt, Iterations: keyRounds, KeyLength: 32, PRF: pkix.AlgorithmIdentifier{Algorithm: oidHMACSHA256, Parameters: asn1.NullRawValue}})
	if err != nil {
		return nil, err
	}
	scheme, err := asn1.Marshal(iv)
	if err != nil {
		return nil, err
	}
	params, err := asn1.Marshal(pbes2Params{
		KDF:    pkix.AlgorithmIdentifier{Algorithm: oidPBKDF2, Parameters: asn1.RawValue{FullBytes: kdf}},
		Scheme: pkix.AlgorithmIdentifier{Algorithm: oidAES256CBC, Parameters: asn1.RawValue{FullBytes: scheme}},
	})
	if err != nil {
		return nil, err
	}
	der, err := asn1.Marshal(encryptedKeyInfo{pkix.AlgorithmIdentifier{Algorithm: oidPBES2, Parameters: asn1.RawValue{FullBytes: params}}, padded})
	if err != nil {
		return nil, err
	}
	return pem.EncodeToMemory(&pem.Block{Type: encryptedKeyType, Bytes: der}), nil
}

// readEncryptedKey reads the parameters of a key that EncryptKey made.
func readEncryptedKey(der []byte) (kdf pbkdf2Params, iv, data []byte, err error) {
	var info encryptedKeyInfo
	if rest, err := asn1.Unmarshal(der, &info); err != nil || len(rest) != 0 || !info.Algorithm.Algorithm.Equal(oidPBES2) {
		return kdf, nil, nil, fmt.Errorf("invalid CA key: not a PKCS#8 key with a password")
	}
	var params pbes2Params
	if _, err := asn1.Unmarshal(info.Algorithm.Parameters.FullBytes, &params); err != nil || !params.KDF.Algorithm.Equal(oidPBKDF2) || !params.Scheme.Algorithm.Equal(oidAES256CBC) {
		return kdf, nil, nil, fmt.Errorf("invalid CA key: only PBKDF2 with AES-256-CBC is supported")
	}
	if _, err := asn1.Unmarshal(params.KDF.Parameters.FullBytes, &kdf); err != nil || !kdf.PRF.Algorithm.Equal(oidHMACSHA256) ||
		kdf.Iterations < 1 || kdf.Iterations > maxKeyRounds || (kdf.KeyLength != 0 && kdf.KeyLength != 32) {
		return kdf, nil, nil, fmt.Errorf("invalid CA key: only PBKDF2 with HMAC-SHA256 is supported")
	}
	if _, err := asn1.Unmarshal(params.Scheme.Parameters.FullBytes, &iv); err != nil || len(iv) != aes.BlockSize ||
		len(info.Data) == 0 || len(info.Data)%aes.BlockSize != 0 {
		return kdf, nil, nil, fmt.Errorf("invalid CA key")
	}
	return kdf, iv, info.Data, nil
}

// decryptKey reads a key that EncryptKey made.
func decryptKey(der []byte, password string) (*ecdsa.PrivateKey, error) {
	kdf, iv, data, err := readEncryptedKey(der)
	if err != nil {
		return nil, err
	}
	derived, err := pbkdf2.Key(sha256.New, password, kdf.Salt, kdf.Iterations, 32)
	if err != nil {
		return nil, err
	}
	block, err := aes.NewCipher(derived)
	if err != nil {
		return nil, err
	}
	plain := make([]byte, len(data))
	cipher.NewCBCDecrypter(block, iv).CryptBlocks(plain, data)
	padding := int(plain[len(plain)-1])
	if padding < 1 || padding > aes.BlockSize || padding > len(plain) {
		return nil, ErrWrongPassword
	}
	parsed, err := x509.ParsePKCS8PrivateKey(plain[:len(plain)-padding])
	if err != nil {
		return nil, ErrWrongPassword
	}
	key, ok := parsed.(*ecdsa.PrivateKey)
	if !ok {
		return nil, fmt.Errorf("invalid CA key: not an ECDSA key")
	}
	return key, nil
}
