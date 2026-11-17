// Package certzip packs certificate files in a zip file to send by email. It reads and writes no
// file, and asks nothing.
//
// archive/zip cannot encrypt, so a password turns on the WinZip AES-256 format (AE-2). 7-Zip,
// WinZip, Keka and unzip builds with AES support open it. The Windows file explorer does not.
package certzip

import (
	"archive/zip"
	"bytes"
	"crypto/aes"
	"crypto/cipher"
	"crypto/hmac"
	"crypto/pbkdf2"
	"crypto/rand"
	"crypto/sha1" // #nosec G505 -- The WinZip AES format requires HMAC-SHA1 and PBKDF2-SHA1.
	"encoding/binary"
	"fmt"
	"time"
)

// File is one file in the zip file.
type File struct {
	Name string
	Data []byte
}

const (
	methodAES       = 99
	flagEncrypted   = 0x1
	versionAES      = 51
	saltSize        = 16
	keySize         = 32
	verifierSize    = 2
	authSize        = 10
	pbkdf2Rounds    = 1000 // Fixed by the format.
	aesExtraID      = 0x9901
	aesVersionAE2   = 2
	aesStrength256  = 3
	methodStoredRaw = 0
)

// Build gives a zip file with the files. With a password, it encrypts the content of every file
// with AES-256. File names stay visible.
func Build(files []File, password string) ([]byte, error) {
	var buf bytes.Buffer
	w := zip.NewWriter(&buf)
	now := time.Now()
	for _, file := range files {
		var err error
		if password == "" {
			err = addPlain(w, file, now)
		} else {
			err = addEncrypted(w, file, password, now)
		}
		if err != nil {
			return nil, fmt.Errorf("zip %s: %w", file.Name, err)
		}
	}
	if err := w.Close(); err != nil {
		return nil, err
	}
	return buf.Bytes(), nil
}

func addPlain(w *zip.Writer, file File, now time.Time) error {
	out, err := w.CreateHeader(&zip.FileHeader{Name: file.Name, Method: zip.Deflate, Modified: now})
	if err != nil {
		return err
	}
	_, err = out.Write(file.Data)
	return err
}

func addEncrypted(w *zip.Writer, file File, password string, now time.Time) error {
	sealed, err := seal(password, file.Data)
	if err != nil {
		return err
	}
	extra := binary.LittleEndian.AppendUint16(nil, aesExtraID)
	extra = binary.LittleEndian.AppendUint16(extra, 7)
	extra = binary.LittleEndian.AppendUint16(extra, aesVersionAE2)
	extra = append(extra, 'A', 'E', aesStrength256)
	extra = binary.LittleEndian.AppendUint16(extra, methodStoredRaw)
	out, err := w.CreateRaw(&zip.FileHeader{
		Name:               file.Name,
		Method:             methodAES,
		Flags:              flagEncrypted,
		ReaderVersion:      versionAES,
		Modified:           now,
		Extra:              extra,
		CompressedSize64:   uint64(len(sealed)),
		UncompressedSize64: uint64(len(file.Data)),
		// AE-2 stores no CRC: the authentication code checks the content.
		CRC32: 0,
	})
	if err != nil {
		return err
	}
	_, err = out.Write(sealed)
	return err
}

// seal gives salt, password verifier, AES-CTR content and the first 10 bytes of its HMAC-SHA1.
func seal(password string, data []byte) ([]byte, error) {
	salt := make([]byte, saltSize)
	if _, err := rand.Read(salt); err != nil {
		return nil, err
	}
	derived, err := pbkdf2.Key(sha1.New, password, salt, pbkdf2Rounds, 2*keySize+verifierSize)
	if err != nil {
		return nil, err
	}
	block, err := aes.NewCipher(derived[:keySize])
	if err != nil {
		return nil, err
	}
	sealed := make([]byte, 0, saltSize+verifierSize+len(data)+authSize)
	sealed = append(sealed, salt...)
	sealed = append(sealed, derived[2*keySize:]...)
	start := len(sealed)
	sealed = append(sealed, data...)
	xorLittleEndianCTR(block, sealed[start:])
	mac := hmac.New(sha1.New, derived[keySize:2*keySize])
	mac.Write(sealed[start:])
	return mac.Sum(sealed)[:len(sealed)+authSize], nil
}

// xorLittleEndianCTR is AES-CTR with the counter that WinZip uses: 128 bits, little endian, from 1.
// The standard library's CTR counts big endian.
func xorLittleEndianCTR(block cipher.Block, data []byte) {
	var counter, stream [aes.BlockSize]byte
	for offset, n := 0, uint64(1); offset < len(data); offset, n = offset+aes.BlockSize, n+1 {
		binary.LittleEndian.PutUint64(counter[:8], n)
		block.Encrypt(stream[:], counter[:])
		end := min(offset+aes.BlockSize, len(data))
		for i := offset; i < end; i++ {
			data[i] ^= stream[i-offset]
		}
	}
}
