package static

import (
	"embed"
	"io/fs"
	"net/http"
)

//go:embed all:dist/*
var distFS embed.FS

func GetFileSystem() http.FileSystem {
	sub, err := fs.Sub(distFS, "dist")
	if err != nil {
		panic(err)
	}
	return http.FS(sub)
}
