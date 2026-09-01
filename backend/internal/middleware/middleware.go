package middleware

import (
	"crypto/subtle"
	"log"
	"net/http"
	"strings"
	"subflow/backend/internal/service"
	"time"

	"github.com/gin-gonic/gin"
)

func CORS() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Writer.Header().Set("Access-Control-Allow-Origin", "*")
		c.Writer.Header().Set("Access-Control-Allow-Credentials", "true")
		c.Writer.Header().Set("Access-Control-Allow-Headers", "Content-Type, Content-Length, Accept-Encoding, X-CSRF-Token, Authorization, accept, origin, Cache-Control, X-Requested-With")
		c.Writer.Header().Set("Access-Control-Allow-Methods", "POST, OPTIONS, GET, PUT, DELETE, PATCH")

		if c.Request.Method == "OPTIONS" {
			c.AbortWithStatus(http.StatusNoContent)
			return
		}

		c.Next()
	}
}

func Logger() gin.HandlerFunc {
	return func(c *gin.Context) {
		start := time.Now()
		path := c.Request.URL.Path

		c.Next()

		latency := time.Since(start)
		statusCode := c.Writer.Status()
		log.Printf("[SubFlow] %s %s -> %d (%v)", c.Request.Method, path, statusCode, latency)
	}
}

func AuthRequired(authService *service.AuthService) gin.HandlerFunc {
	return func(c *gin.Context) {
		authHeader := c.GetHeader("Authorization")
		if authHeader == "" {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "Authorization header required"})
			c.Abort()
			return
		}

		parts := strings.SplitN(authHeader, " ", 2)
		if len(parts) != 2 || strings.ToLower(parts[0]) != "bearer" {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "Invalid token format, must be Bearer <token>"})
			c.Abort()
			return
		}

		claims, err := authService.ValidateJWT(parts[1])
		if err != nil {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "Invalid or expired token: " + err.Error()})
			c.Abort()
			return
		}

		c.Set("user_id", claims.UserID)
		c.Set("email", claims.Email)
		c.Set("is_pro", claims.IsPro)
		c.Next()
	}
}

// AdminAuth guards the /admin API surface.
//
// These endpoints can flip any user's Pro entitlement and edit the public
// preset catalogue, so leaving them open to anyone who can reach the port is
// not acceptable once the binary is deployed anywhere but localhost.
func AdminAuth(adminToken string) gin.HandlerFunc {
	return func(c *gin.Context) {
		if adminToken == "" {
			c.JSON(http.StatusServiceUnavailable, gin.H{
				"error": "Admin API disabled: ADMIN_TOKEN is not configured",
			})
			c.Abort()
			return
		}

		provided := c.GetHeader("X-Admin-Token")
		if provided == "" {
			// Also accept a bearer token so curl/scripts can use either form.
			if h := c.GetHeader("Authorization"); strings.HasPrefix(strings.ToLower(h), "bearer ") {
				provided = strings.TrimSpace(h[7:])
			}
		}

		// Constant-time compare so the token cannot be recovered by timing.
		if subtle.ConstantTimeCompare([]byte(provided), []byte(adminToken)) != 1 {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "Invalid admin token"})
			c.Abort()
			return
		}

		c.Next()
	}
}
