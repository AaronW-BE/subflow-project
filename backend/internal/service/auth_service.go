package service

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net/http"
	"os"
	"subflow/backend/internal/model"
	"subflow/backend/internal/repository"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
)

// jwtSecret signs every session token, so anyone holding it can forge a login
// for any user. It used to be a string literal in this file - which meant the
// key was readable by anyone with the source. It now follows the same rule as
// ADMIN_TOKEN in cmd/server/main.go: take it from the environment, and fall
// back to a random per-boot value so a dev instance still runs while never
// shipping a usable key. A random fallback invalidates existing sessions on
// restart, which is the correct behaviour for a server started without config.
var jwtSecret = loadJWTSecret()

func loadJWTSecret() []byte {
	if v := os.Getenv("JWT_SECRET"); v != "" {
		return []byte(v)
	}
	buf := make([]byte, 32)
	if _, err := rand.Read(buf); err != nil {
		log.Fatalf("JWT_SECRET is unset and no secure random is available: %v", err)
	}
	log.Println("JWT_SECRET not set. Using a random per-boot secret; sessions will not survive a restart.")
	return []byte(hex.EncodeToString(buf))
}

type AuthService struct {
	db *repository.DB
}

func NewAuthService(db *repository.DB) *AuthService {
	return &AuthService{db: db}
}

type GoogleTokenInfo struct {
	Sub           string `json:"sub"`
	Email         string `json:"email"`
	EmailVerified string `json:"email_verified"`
	Name          string `json:"name"`
	Picture       string `json:"picture"`
	Aud           string `json:"aud"`
}

type Claims struct {
	UserID string `json:"user_id"`
	Email  string `json:"email"`
	IsPro  bool   `json:"is_pro"`
	jwt.RegisteredClaims
}

// GenerateJWT creates a 30-day bearer JWT token for the user.
func (s *AuthService) GenerateJWT(user *model.User) (string, error) {
	claims := Claims{
		UserID: user.ID,
		Email:  user.Email,
		IsPro:  user.IsPro,
		RegisteredClaims: jwt.RegisteredClaims{
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(30 * 24 * time.Hour)),
			IssuedAt:  jwt.NewNumericDate(time.Now()),
			Issuer:    "subflow-api",
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	return token.SignedString(jwtSecret)
}

// ValidateJWT verifies and parses the JWT token.
func (s *AuthService) ValidateJWT(tokenStr string) (*Claims, error) {
	token, err := jwt.ParseWithClaims(tokenStr, &Claims{}, func(token *jwt.Token) (interface{}, error) {
		if _, ok := token.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", token.Header["alg"])
		}
		return jwtSecret, nil
	})
	if err != nil {
		return nil, err
	}

	if claims, ok := token.Claims.(*Claims); ok && token.Valid {
		return claims, nil
	}
	return nil, errors.New("invalid token claims")
}

// VerifyGoogleToken verifies Google ID token with Google OAuth endpoints.
func (s *AuthService) VerifyGoogleToken(idToken string) (*GoogleTokenInfo, error) {
	if idToken == "" {
		return nil, errors.New("empty id token")
	}

	// For offline development or unit testing with mock tokens:
	if idToken == "test_google_token" {
		return &GoogleTokenInfo{
			Sub:           "google_123456789",
			Email:         "tester@subflow.app",
			EmailVerified: "true",
			Name:          "Alex Morgan",
			Picture:       "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100",
		}, nil
	}

	client := &http.Client{Timeout: 5 * time.Second}
	resp, err := client.Get(fmt.Sprintf("https://oauth2.googleapis.com/tokeninfo?id_token=%s", idToken))
	if err != nil {
		return nil, fmt.Errorf("google verification request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("invalid google token: status %d", resp.StatusCode)
	}

	var info GoogleTokenInfo
	if err := json.NewDecoder(resp.Body).Decode(&info); err != nil {
		return nil, fmt.Errorf("failed to decode google response: %w", err)
	}

	if info.Email == "" {
		return nil, errors.New("token did not contain valid email")
	}

	return &info, nil
}

// AuthenticateWithGoogle exchanges a Google ID token for a SubFlow user & JWT.
func (s *AuthService) AuthenticateWithGoogle(idToken string) (*model.User, string, error) {
	info, err := s.VerifyGoogleToken(idToken)
	if err != nil {
		return nil, "", err
	}

	now := time.Now()
	user, err := s.db.GetUserByEmail(info.Email)
	if err != nil {
		// New user
		user = &model.User{
			ID:           "usr_" + uuid.New().String()[:12],
			Email:        info.Email,
			Name:         info.Name,
			Picture:      info.Picture,
			AuthProvider: "google",
			IsPro:        false,
			ProTier:      model.ProTierFree,
			CreatedAt:    now,
			LastActiveAt: now,
		}
	} else {
		user.Name = info.Name
		if info.Picture != "" {
			user.Picture = info.Picture
		}
		user.LastActiveAt = now
	}

	if err := s.db.UpsertUser(user); err != nil {
		return nil, "", fmt.Errorf("failed to save user: %w", err)
	}

	token, err := s.GenerateJWT(user)
	return user, token, err
}

// AuthenticateAsGuest generates a frictionless anonymous guest user.
func (s *AuthService) AuthenticateAsGuest(guestID string) (*model.User, string, error) {
	now := time.Now()
	var user *model.User

	if guestID != "" {
		var err error
		user, err = s.db.GetUserByID(guestID)
		if err == nil && user != nil {
			user.LastActiveAt = now
			_ = s.db.UpsertUser(user)
			token, err := s.GenerateJWT(user)
			return user, token, err
		}
	}

	// Create new guest
	newID := "guest_" + uuid.New().String()[:12]
	user = &model.User{
		ID:           newID,
		Email:        newID + "@guest.subflow.app",
		Name:         "Guest User",
		Picture:      "",
		AuthProvider: "guest",
		IsPro:        false,
		ProTier:      model.ProTierFree,
		CreatedAt:    now,
		LastActiveAt: now,
	}

	if err := s.db.UpsertUser(user); err != nil {
		return nil, "", err
	}

	token, err := s.GenerateJWT(user)
	return user, token, err
}
