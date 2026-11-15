//go:build !windows

package commandtest

// defaultLANIP is empty: the stand-in computer detects no LAN address unless a test sets one.
const defaultLANIP = ""
