package api

import (
	"net/http"
	"strconv"
	"subflow/backend/internal/middleware"
	"subflow/backend/internal/model"
	"subflow/backend/internal/service"

	"github.com/gin-gonic/gin"
)

type Handler struct {
	authService    *service.AuthService
	presetService  *service.PresetService
	rateService    *service.RateService
	syncService    *service.SyncService
	adminService   *service.AdminService
	billingService *service.BillingService
	adminToken     string
}

func NewHandler(
	authService *service.AuthService,
	presetService *service.PresetService,
	rateService *service.RateService,
	syncService *service.SyncService,
	adminService *service.AdminService,
	billingService *service.BillingService,
	adminToken string,
) *Handler {
	return &Handler{
		authService:    authService,
		presetService:  presetService,
		rateService:    rateService,
		syncService:    syncService,
		adminService:   adminService,
		billingService: billingService,
		adminToken:     adminToken,
	}
}

func (h *Handler) RegisterRoutes(r *gin.Engine) {
	api := r.Group("/api/v1")
	{
		// Health
		api.GET("/health", func(c *gin.Context) {
			c.JSON(http.StatusOK, gin.H{
				"status":  "healthy",
				"version": "1.0.0",
				"service": "subflow-api",
			})
		})

		// Auth
		auth := api.Group("/auth")
		{
			auth.POST("/google", h.loginGoogle)
			auth.POST("/guest", h.loginGuest)
			auth.GET("/me", middleware.AuthRequired(h.authService), h.getProfile)
		}

		// Presets Catalog
		api.GET("/presets", h.getPresets)

		// Currency Rates
		api.GET("/rates", h.getRates)

		// Local-First Cloud Sync
		api.POST("/sync", middleware.AuthRequired(h.authService), h.syncSubscriptions)

		// Play Billing purchase ledger
		api.POST("/billing/purchase", middleware.AuthRequired(h.authService), h.reportPurchase)

		// Admin Endpoints - token protected
		admin := api.Group("/admin", middleware.AdminAuth(h.adminToken))
		{
			admin.GET("/purchases", h.listPurchases)
			admin.GET("/revenue", h.getRevenue)
			admin.GET("/kpi", h.getAdminKPI)
			admin.GET("/users", h.listUsers)
			admin.POST("/users/:id/pro", h.setUserPro)
			admin.POST("/presets", h.savePreset)
			admin.DELETE("/presets/:id", h.deletePreset)
			admin.POST("/seed", h.seedDemoData)
		}
	}
}

// Billing Handlers

// reportPurchase records a Play purchase token against the signed-in user.
func (h *Handler) reportPurchase(c *gin.Context) {
	var req struct {
		ProductID     string `json:"product_id" binding:"required"`
		PurchaseToken string `json:"purchase_token" binding:"required"`
		OrderID       string `json:"order_id"`
		PackageName   string `json:"package_name"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "product_id and purchase_token are required"})
		return
	}

	userID := c.GetString("user_id")
	purchase, err := h.billingService.RecordPurchase(
		userID, req.ProductID, req.PurchaseToken, req.OrderID, req.PackageName,
	)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"acknowledged": true,
		"pro_tier":     string(purchase.ProTier),
	})
}

func (h *Handler) listPurchases(c *gin.Context) {
	limit, offset := pageParams(c)

	page, err := h.billingService.ListPurchasePage(limit, offset)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	c.JSON(http.StatusOK, page)
}

// pageParams reads limit/offset, rejecting junk rather than silently paging
// from zero. strconv.Atoi("abc") returns 0, and the old code discarded that
// error, so ?limit=abc quietly meant "no rows" and ?offset=-5 hit SQLite with
// a negative offset.
func pageParams(c *gin.Context) (limit, offset int) {
	limit, err := strconv.Atoi(c.DefaultQuery("limit", "50"))
	if err != nil || limit <= 0 || limit > 100 {
		limit = 50
	}
	offset, err = strconv.Atoi(c.DefaultQuery("offset", "0"))
	if err != nil || offset < 0 {
		offset = 0
	}
	return limit, offset
}

func (h *Handler) getRevenue(c *gin.Context) {
	summary, err := h.billingService.RevenueSummary()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	c.JSON(http.StatusOK, summary)
}

// Auth Handlers
func (h *Handler) loginGoogle(c *gin.Context) {
	var req struct {
		IDToken string `json:"id_token" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Missing or invalid id_token"})
		return
	}

	user, token, err := h.authService.AuthenticateWithGoogle(req.IDToken)
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Google authentication failed: " + err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"user":  user,
		"token": token,
	})
}

func (h *Handler) loginGuest(c *gin.Context) {
	var req struct {
		GuestID string `json:"guest_id"`
	}
	_ = c.ShouldBindJSON(&req)

	user, token, err := h.authService.AuthenticateAsGuest(req.GuestID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to create guest: " + err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"user":  user,
		"token": token,
	})
}

func (h *Handler) getProfile(c *gin.Context) {
	userID := c.GetString("user_id")
	user, _, err := h.authService.AuthenticateAsGuest(userID)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "User not found"})
		return
	}
	c.JSON(http.StatusOK, user)
}

// Preset Handlers
func (h *Handler) getPresets(c *gin.Context) {
	search := c.Query("search")
	category := c.Query("category")
	presets, err := h.presetService.GetPresets(search, category)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to fetch presets: " + err.Error()})
		return
	}
	c.JSON(http.StatusOK, gin.H{
		"count":   len(presets),
		"presets": presets,
	})
}

// Rate Handlers
func (h *Handler) getRates(c *gin.Context) {
	rates := h.rateService.GetRates()
	c.JSON(http.StatusOK, rates)
}

// Sync Handlers
func (h *Handler) syncSubscriptions(c *gin.Context) {
	userID := c.GetString("user_id")
	var req model.SyncRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid sync payload: " + err.Error()})
		return
	}

	resp, err := h.syncService.ProcessSync(userID, &req)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Sync processing failed: " + err.Error()})
		return
	}

	c.JSON(http.StatusOK, resp)
}

// Admin Handlers
func (h *Handler) getAdminKPI(c *gin.Context) {
	kpi, err := h.adminService.GetKPIs()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to fetch KPI: " + err.Error()})
		return
	}
	c.JSON(http.StatusOK, kpi)
}

func (h *Handler) listUsers(c *gin.Context) {
	limit, offset := pageParams(c)

	page, err := h.adminService.ListUsers(limit, offset)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to fetch users: " + err.Error()})
		return
	}
	c.JSON(http.StatusOK, page)
}

func (h *Handler) setUserPro(c *gin.Context) {
	userID := c.Param("id")
	var req struct {
		IsPro bool          `json:"is_pro"`
		Tier  model.ProTier `json:"tier"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid pro update payload"})
		return
	}

	if req.IsPro && !model.IsGrantableProTier(req.Tier) {
		c.JSON(http.StatusBadRequest, gin.H{
			"error": "Unknown pro tier: " + string(req.Tier),
		})
		return
	}

	if err := h.adminService.SetUserProStatus(userID, req.IsPro, req.Tier); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to update pro status: " + err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "User pro status updated successfully"})
}

func (h *Handler) savePreset(c *gin.Context) {
	var preset model.PresetService
	if err := c.ShouldBindJSON(&preset); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid preset payload: " + err.Error()})
		return
	}

	if err := h.presetService.SavePreset(&preset); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to save preset: " + err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "Preset saved successfully", "preset": preset})
}

func (h *Handler) deletePreset(c *gin.Context) {
	id := c.Param("id")
	deleted, err := h.presetService.DeletePreset(id)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to delete preset: " + err.Error()})
		return
	}
	if !deleted {
		c.JSON(http.StatusNotFound, gin.H{"error": "No preset with id " + id})
		return
	}
	c.JSON(http.StatusOK, gin.H{"message": "Preset deleted successfully"})
}

func (h *Handler) seedDemoData(c *gin.Context) {
	seeded, err := h.adminService.SeedDemoData()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to seed demo data: " + err.Error()})
		return
	}
	if seeded == 0 {
		c.JSON(http.StatusOK, gin.H{
			"seeded":  0,
			"message": "Skipped: this database already has real users, so no demo data was written",
		})
		return
	}
	c.JSON(http.StatusOK, gin.H{
		"seeded":  seeded,
		"message": "Demo data populated successfully",
	})
}
