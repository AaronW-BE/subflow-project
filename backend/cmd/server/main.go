package main

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"log"
	"net/http"
	"os"
	"strings"
	"subflow/backend/internal/api"
	"subflow/backend/internal/middleware"
	"subflow/backend/internal/repository"
	"subflow/backend/internal/service"
	"subflow/backend/internal/static"

	"github.com/gin-gonic/gin"
)

func main() {
	log.Println("Starting SubFlow Backend & Embedded Admin Console v1.0.0...")

	// 1. Initialize SQLite Database
	dbPath := os.Getenv("DB_PATH")
	if dbPath == "" {
		dbPath = "subflow.db"
	}
	db, err := repository.InitDB(dbPath)
	if err != nil {
		log.Fatalf("Fatal: Failed to initialize SQLite database: %v", err)
	}
	defer db.Close()
	log.Printf("SQLite database connected: %s", dbPath)

	// 2. Initialize Services
	authService := service.NewAuthService(db)
	presetService := service.NewPresetService(db)
	rateService := service.NewRateService(db)
	syncService := service.NewSyncService(db)
	billingService := service.NewBillingService(db)
	adminService := service.NewAdminService(db, billingService)

	// The admin API can change entitlements, so it is token gated. A random
	// token is generated when ADMIN_TOKEN is unset so a dev instance is still
	// usable while never being open by default.
	adminToken := os.Getenv("ADMIN_TOKEN")
	if adminToken == "" {
		adminToken = randomToken()
		log.Printf("ADMIN_TOKEN not set. Generated session token for the Admin Console: %s", adminToken)
	}

	// Seed sample demo data for admin visibility. This is a no-op once the
	// database has real users in it.
	if n, err := adminService.SeedDemoData(); err != nil {
		log.Printf("Notice: demo data seeding: %v", err)
	} else if n > 0 {
		log.Printf("Seeded %d demo users into a fresh database.", n)
	}

	// Keep the FX table current. This runs for the life of the process; every
	// failure leaves the previous rates in place, so a provider outage cannot
	// move anyone's totals.
	rateCtx, stopRates := context.WithCancel(context.Background())
	defer stopRates()
	rateService.StartRefreshing(rateCtx)

	// 3. Setup Gin Engine
	gin.SetMode(gin.ReleaseMode)
	r := gin.New()
	r.Use(gin.Recovery())
	r.Use(middleware.CORS())
	r.Use(middleware.Logger())

	// 4. Register REST API
	handler := api.NewHandler(authService, presetService, rateService, syncService, adminService, billingService, adminToken)
	handler.RegisterRoutes(r)

	// 5. Mount Embedded Admin Console (/admin)
	adminFS := static.GetFileSystem()

	// Serve static files inside /admin/assets
	r.StaticFS("/admin", adminFS)

	// Root redirects to /admin
	r.GET("/", func(c *gin.Context) {
		c.Redirect(http.StatusMovedPermanently, "/admin/")
	})

	// Fallback route for SPA client-side routing within /admin
	r.NoRoute(func(c *gin.Context) {
		path := c.Request.URL.Path
		if strings.HasPrefix(path, "/api/") {
			c.JSON(http.StatusNotFound, gin.H{"error": "Endpoint not found"})
			return
		}
		// If requesting within /admin, return index.html for SPA router
		if strings.HasPrefix(path, "/admin") {
			c.FileFromFS("index.html", adminFS)
			return
		}
		c.Redirect(http.StatusTemporaryRedirect, "/admin/")
	})

	port := os.Getenv("PORT")
	if port == "" {
		port = "8085"
	}

	log.Printf("SubFlow Server ready on http://localhost:%s", port)
	log.Printf("API Root: http://localhost:%s/api/v1/health", port)
	log.Printf("Admin Console: http://localhost:%s/admin/", port)

	if err := r.Run(":" + port); err != nil {
		log.Fatalf("Server exited with error: %v", err)
	}
}

// randomToken produces a 32-character hex admin token for dev instances.
func randomToken() string {
	buf := make([]byte, 16)
	if _, err := rand.Read(buf); err != nil {
		return "subflow-admin-fallback-token"
	}
	return hex.EncodeToString(buf)
}
