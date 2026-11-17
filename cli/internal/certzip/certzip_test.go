package certzip_test

import (
	"archive/zip"
	"bytes"
	"crypto/aes"
	"crypto/hmac"
	"crypto/pbkdf2"
	"crypto/sha1"
	"encoding/binary"
	"errors"
	"io"
	"testing"

	"github.com/ppalazon/lvh-yard/cli/internal/certzip"
)

var files = []certzip.File{
	{Name: "a/short.txt", Data: []byte("tiny")},
	// Longer than one AES block and not a multiple of it, to cover the partial last block.
	{Name: "b.key", Data: bytes.Repeat([]byte("-----BEGIN "+"PRIVATE KEY-----\n"), 5)},
	{Name: "empty", Data: nil},
}

func TestPlainZipOpensWithoutPassword(t *testing.T) {
	data, err := certzip.Build(files, "")
	if err != nil {
		t.Fatal(err)
	}
	reader, err := zip.NewReader(bytes.NewReader(data), int64(len(data)))
	if err != nil {
		t.Fatal(err)
	}
	for i, file := range files {
		got := readEntry(t, reader.File[i])
		if reader.File[i].Name != file.Name || !bytes.Equal(got, file.Data) {
			t.Fatalf("%s: wrong entry", file.Name)
		}
	}
}

// The test decrypts with its own reading of the WinZip AE-2 format, so the builder cannot only
// agree with itself. 7-Zip reads the same files.
func TestEncryptedZipHidesContentAndDecryptsWithPassword(t *testing.T) {
	data, err := certzip.Build(files, "correct horse")
	if err != nil {
		t.Fatal(err)
	}
	if bytes.Contains(data, []byte("PRIVATE KEY")) || bytes.Contains(data, []byte("tiny")) {
		t.Fatal("the zip file shows plaintext")
	}
	reader, err := zip.NewReader(bytes.NewReader(data), int64(len(data)))
	if err != nil {
		t.Fatal(err)
	}
	for i, file := range files {
		entry := reader.File[i]
		if entry.Name != file.Name || entry.Method != 99 || entry.Flags&1 == 0 {
			t.Fatalf("%s: not an encrypted entry: %+v", file.Name, entry.FileHeader)
		}
		raw := rawEntry(t, entry)
		got, ok := open(raw, "correct horse")
		if !ok || !bytes.Equal(got, file.Data) {
			t.Fatalf("%s: wrong content", file.Name)
		}
		if _, ok := open(raw, "wrong"); ok {
			t.Fatalf("%s: a wrong password opened the entry", file.Name)
		}
		raw[len(raw)/2+1] ^= 1
		if _, ok := open(raw, "correct horse"); ok {
			t.Fatalf("%s: a changed entry was accepted", file.Name)
		}
	}
}

func readEntry(t *testing.T, file *zip.File) []byte {
	t.Helper()
	in, err := file.Open()
	if err != nil {
		t.Fatal(err)
	}
	defer func() { _ = in.Close() }()
	data, err := io.ReadAll(in)
	if err != nil {
		t.Fatal(err)
	}
	return data
}

func rawEntry(t *testing.T, file *zip.File) []byte {
	t.Helper()
	in, err := file.OpenRaw()
	if err != nil {
		t.Fatal(err)
	}
	data, err := io.ReadAll(in)
	if err != nil {
		t.Fatal(err)
	}
	return data
}

// open reads salt(16) verifier(2) content mac(10), and checks the verifier and the mac.
func open(raw []byte, password string) ([]byte, bool) {
	salt, rest := raw[:16], raw[16:]
	verifier, rest := rest[:2], rest[2:]
	content, mac := rest[:len(rest)-10], rest[len(rest)-10:]
	derived, err := pbkdf2.Key(sha1.New, password, salt, 1000, 66)
	if err != nil || !bytes.Equal(derived[64:], verifier) {
		return nil, false
	}
	sum := hmac.New(sha1.New, derived[32:64])
	sum.Write(content)
	if !hmac.Equal(sum.Sum(nil)[:10], mac) {
		return nil, false
	}
	block, _ := aes.NewCipher(derived[:32])
	out := make([]byte, len(content))
	for i := 0; i*16 < len(content); i++ {
		var counter, stream [16]byte
		binary.LittleEndian.PutUint64(counter[:8], uint64(i+1))
		block.Encrypt(stream[:], counter[:])
		for j := i * 16; j < min(i*16+16, len(content)); j++ {
			out[j] = content[j] ^ stream[j-i*16]
		}
	}
	return out, true
}

func TestOpenReadsWhatBuildWrote(t *testing.T) {
	for _, password := range []string{"", "correct horse"} {
		data, err := certzip.Build(files, password)
		if err != nil {
			t.Fatal(err)
		}
		got, err := certzip.Open(data, password)
		if err != nil || len(got) != len(files) {
			t.Fatalf("password %q: %v, %d files", password, err, len(got))
		}
		for i, file := range files {
			if got[i].Name != file.Name || !bytes.Equal(got[i].Data, file.Data) {
				t.Fatalf("password %q: %s changed", password, file.Name)
			}
		}
	}
}

func TestOpenRefusesAMissingOrWrongPassword(t *testing.T) {
	data, err := certzip.Build(files, "correct horse")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := certzip.Open(data, ""); !errors.Is(err, certzip.ErrPasswordRequired) {
		t.Fatalf("no password: %v", err)
	}
	if _, err := certzip.Open(data, "wrong"); !errors.Is(err, certzip.ErrWrongPassword) {
		t.Fatalf("wrong password: %v", err)
	}
	if _, err := certzip.Open([]byte("not a zip"), ""); err == nil {
		t.Fatal("a file that is no zip file was opened")
	}
}

func TestOpenRefusesChangedContent(t *testing.T) {
	data, err := certzip.Build([]certzip.File{{Name: "k", Data: bytes.Repeat([]byte("x"), 40)}}, "pw")
	if err != nil {
		t.Fatal(err)
	}
	// The content of the only entry sits after its header, its name and its extra field.
	reader, _ := zip.NewReader(bytes.NewReader(data), int64(len(data)))
	offset, err := reader.File[0].DataOffset()
	if err != nil {
		t.Fatal(err)
	}
	data[offset+20] ^= 1
	if _, err := certzip.Open(data, "pw"); err == nil || errors.Is(err, certzip.ErrWrongPassword) {
		t.Fatalf("a changed entry was accepted or called a wrong password: %v", err)
	}
}
