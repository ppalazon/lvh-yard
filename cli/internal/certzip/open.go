package certzip

import (
	"archive/zip"
	"bytes"
	"crypto/aes"
	"crypto/hmac"
	"crypto/pbkdf2"
	"crypto/sha1" // #nosec G505 -- The WinZip AES format requires HMAC-SHA1 and PBKDF2-SHA1.
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"path"
	"strings"
)

// ErrPasswordRequired means that the zip file is encrypted and no password was given.
var ErrPasswordRequired = errors.New("the zip file has a password")

// ErrWrongPassword means that the password did not open the zip file.
var ErrWrongPassword = errors.New("wrong password for the zip file")

// maxFileSize keeps a damaged or hostile zip file from filling the memory. The files of a Yard
// invitation are a few kilobytes.
const maxFileSize = 1 << 20

// Open reads the files of a zip file that Build made, with the password that Build was given. It
// reads only what Build writes: stored files, plain or encrypted with AES-256. A password for a
// zip file that is not encrypted is ignored.
func Open(data []byte, password string) ([]File, error) {
	reader, err := zip.NewReader(bytes.NewReader(data), int64(len(data)))
	if err != nil {
		return nil, fmt.Errorf("not a zip file: %w", err)
	}
	var files []File
	for _, entry := range reader.File {
		if entry.FileInfo().IsDir() {
			continue
		}
		if entry.UncompressedSize64 > maxFileSize || path.IsAbs(entry.Name) || strings.Contains(entry.Name, "..") {
			return nil, fmt.Errorf("zip file entry %q is not one that LVH Yard makes", entry.Name)
		}
		content, err := openEntry(entry, password)
		if err != nil {
			return nil, fmt.Errorf("zip %s: %w", entry.Name, err)
		}
		files = append(files, File{Name: entry.Name, Data: content})
	}
	return files, nil
}

func openEntry(entry *zip.File, password string) ([]byte, error) {
	if entry.Method != methodAES {
		in, err := entry.Open()
		if err != nil {
			return nil, err
		}
		defer func() { _ = in.Close() }()
		return io.ReadAll(io.LimitReader(in, maxFileSize))
	}
	if password == "" {
		return nil, ErrPasswordRequired
	}
	if err := checkAESExtra(entry.Extra); err != nil {
		return nil, err
	}
	raw, err := entry.OpenRaw()
	if err != nil {
		return nil, err
	}
	sealed, err := io.ReadAll(io.LimitReader(raw, maxFileSize+saltSize+verifierSize+authSize))
	if err != nil {
		return nil, err
	}
	return unseal(password, sealed, entry.UncompressedSize64)
}

// checkAESExtra accepts only the AES-256, AE-2, stored form that Build writes.
func checkAESExtra(extra []byte) error {
	for len(extra) >= 4 {
		id, size := binary.LittleEndian.Uint16(extra), int(binary.LittleEndian.Uint16(extra[2:]))
		if 4+size > len(extra) {
			break
		}
		field := extra[4 : 4+size]
		if id == aesExtraID {
			if size != 7 || string(field[2:4]) != "AE" || field[4] != aesStrength256 || binary.LittleEndian.Uint16(field[5:]) != methodStoredRaw {
				return fmt.Errorf("only AES-256 encryption of stored files is supported")
			}
			return nil
		}
		extra = extra[4+size:]
	}
	return fmt.Errorf("the encryption of the zip file is not described")
}

// unseal reverses seal: it checks the password verifier and the authentication code before it
// decrypts.
func unseal(password string, sealed []byte, size uint64) ([]byte, error) {
	overhead := saltSize + verifierSize + authSize
	// size is at most maxFileSize, which Open checks, so it fits an int.
	if len(sealed) < overhead || len(sealed)-overhead != int(size) { // #nosec G115 -- size is at most maxFileSize.
		return nil, fmt.Errorf("the encrypted content has the wrong size")
	}
	salt, rest := sealed[:saltSize], sealed[saltSize:]
	verifier, rest := rest[:verifierSize], rest[verifierSize:]
	content, auth := rest[:len(rest)-authSize], rest[len(rest)-authSize:]
	derived, err := pbkdf2.Key(sha1.New, password, salt, pbkdf2Rounds, 2*keySize+verifierSize)
	if err != nil {
		return nil, err
	}
	if !bytes.Equal(derived[2*keySize:], verifier) {
		return nil, ErrWrongPassword
	}
	mac := hmac.New(sha1.New, derived[keySize:2*keySize])
	mac.Write(content)
	if !hmac.Equal(mac.Sum(nil)[:authSize], auth) {
		return nil, fmt.Errorf("the encrypted content was changed")
	}
	block, err := aes.NewCipher(derived[:keySize])
	if err != nil {
		return nil, err
	}
	plain := bytes.Clone(content)
	xorLittleEndianCTR(block, plain)
	return plain, nil
}
