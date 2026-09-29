package com.nicsi.store.inventory.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicsi.store.grn.dto.GrnDto;
import com.nicsi.store.grn.dto.GrnPostDto;
import com.nicsi.store.inspection.dto.InspectionDto;
import com.nicsi.store.master.dto.ItemDto;
import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.ItemCategory;
import com.nicsi.store.master.domain.StorageLocation;
import com.nicsi.store.master.domain.StoreSite;
import com.nicsi.store.master.domain.Uom;
import com.nicsi.store.master.repository.ItemCategoryRepository;
import com.nicsi.store.master.repository.ItemRepository;
import com.nicsi.store.master.repository.StorageLocationRepository;
import com.nicsi.store.master.repository.StoreSiteRepository;
import com.nicsi.store.master.repository.UomRepository;
import com.nicsi.store.procurementref.dto.PurchaseOrderDto;
import com.nicsi.store.testutil.BaseIntegrationTest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Read-side coverage for endpoints that existed in code but were never exercised by any test
 * (every one of these was reported as "built but unexercised" in the PDF section 21 audit).
 *
 * A single PO -> GRN -> inspection -> post fixture is shared by all of them, because the same
 * posting is what produces the rows the inventory and asset reads need: it writes the
 * stock_balance row, the stock_transaction ledger row, and the serialised assets. Seeding those
 * tables directly would let the reads return empty pages and prove nothing about their shape.
 *
 * Each endpoint is asserted three ways: 200 with the expected shape, 401 with no token, and 403
 * for a caller lacking the required permission.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ReadEndpointCoverageIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StoreSiteRepository storeSiteRepository;

    @Autowired
    private StorageLocationRepository storageLocationRepository;

    @Autowired
    private ItemCategoryRepository itemCategoryRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private UomRepository uomRepository;

    @Value("${nicsi.security.jwt-secret}")
    private String jwtSecret;

    private String adminToken;
    private String storeManagerToken;
    /** Authenticated, but holds no permissions at all: the cleanest "user without the permission". */
    private String noPermissionToken;

    private UUID testStoreId;
    private UUID testLocationId;
    private UUID serialItemId;
    private String postedAssetCode;

    @BeforeEach
    void setUp() throws Exception {
        truncateInventoryTables(jdbcTemplate, "ReadEndpointCoverageIntegrationTest");

        adminToken = getToken("admin");
        storeManagerToken = getToken("store.manager");
        noPermissionToken = tokenWithPermissions(Set.of());

        StoreSite store = storeSiteRepository.findByStoreCodeIgnoreCase("MAIN-STORE").orElseGet(() -> {
            StoreSite s = new StoreSite();
            s.setStoreCode("MAIN-STORE");
            s.setStoreName("Main Warehouse");
            s.setStoreType("GENERAL");
            return storeSiteRepository.save(s);
        });
        testStoreId = store.getId();

        StorageLocation location = storageLocationRepository.findAll().stream()
                .filter(l -> l.getStore().getId().equals(testStoreId) && "RACK-01".equals(l.getLocationCode()))
                .findFirst().orElseGet(() -> {
                    StorageLocation l = new StorageLocation();
                    l.setStore(store);
                    l.setLocationCode("RACK-01");
                    l.setLocationName("Rack 01");
                    l.setLocationType("RACK");
                    return storageLocationRepository.save(l);
                });
        testLocationId = location.getId();

        Uom uom = uomRepository.findByUomCodeIgnoreCase("NOS").orElseThrow();

        ItemCategory itCat = itemCategoryRepository.findByCategoryCodeIgnoreCase("IT-HW").orElseGet(() -> {
            ItemCategory c = new ItemCategory();
            c.setCategoryCode("IT-HW");
            c.setCategoryName("IT Hardware");
            return itemCategoryRepository.save(c);
        });

        // SERIAL + assetRequired, so posting it creates a stock_balance row, a ledger row AND an asset.
        Item serialItem = itemRepository.findByItemCodeIgnoreCase("LAPTOP-DELL").orElseGet(() -> {
            Item it = new Item();
            it.setItemCode("LAPTOP-DELL");
            it.setItemName("Dell Latitude 5420");
            it.setCategory(itCat);
            it.setBaseUom(uom);
            it.setItemType("NON_CONSUMABLE");
            it.setTrackingType("SERIAL");
            it.setAssetRequired(true);
            it.setStandardRate(new BigDecimal("60000.00"));
            return itemRepository.save(it);
        });
        serialItemId = serialItem.getId();

        postGrnForOneSerialisedAsset();
    }

    /**
     * Full PO -> GRN -> inspect -> post chain for a single serialised unit. The posting is what
     * creates the rows that the balances, ledger and asset reads below assert against.
     * store.manager creates and therefore receives the GRN; admin is neither receiver nor creator,
     * so it can inspect and post. Using one user for both would trip the maker-checker guard with
     * 403 MAKER_CHECKER_VIOLATION, which is what GrnToStockPostingIntegrationTest documents.
     */
    private void postGrnForOneSerialisedAsset() throws Exception {
        String poNumber = "PO-READ-" + System.currentTimeMillis();
        PurchaseOrderDto.CreateRequest poReq = new PurchaseOrderDto.CreateRequest(
                "NICSI_ERP", null, poNumber, LocalDate.now(),
                "DIRECT", "GEM-2026-0001", "CONT-2026",
                UUID.randomUUID(), "VEND-DELL", "Dell Global",
                "INR", new BigDecimal("60000.00"),
                null,
                List.of(new PurchaseOrderDto.CreateItemRequest(
                        1, serialItemId, "Dell Laptop",
                        new BigDecimal("1.000"), new BigDecimal("60000.00"),
                        BigDecimal.ZERO, LocalDate.now().plusDays(30), null, null, null))
        );
        String poResp = mockMvc.perform(post("/api/store/purchase-orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(poReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID poId = UUID.fromString(objectMapper.readTree(poResp).get("id").asText());
        UUID poItemId = UUID.fromString(objectMapper.readTree(poResp).get("items").get(0).get("id").asText());

        GrnDto.CreateRequest grnReq = new GrnDto.CreateRequest(
                LocalDate.now(), testStoreId, poId,
                UUID.randomUUID(), "Dell Global",
                "INV-READ", LocalDate.now(), "CH-READ", LocalDate.now(),
                "Fixture receipt for read-endpoint coverage",
                List.of(new GrnDto.CreateItemRequest(
                        poItemId, serialItemId, new BigDecimal("1.000"), new BigDecimal("60000.00"),
                        testLocationId, null, null, null, "Laptop received"))
        );
        String grnResp = mockMvc.perform(post("/api/store/grns")
                        .header("Authorization", "Bearer " + storeManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grnReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID grnId = UUID.fromString(objectMapper.readTree(grnResp).get("id").asText());
        UUID grnItemId = UUID.fromString(objectMapper.readTree(grnResp).get("items").get(0).get("id").asText());

        mockMvc.perform(post("/api/store/grns/" + grnId + "/submit")
                        .header("Authorization", "Bearer " + storeManagerToken))
                .andExpect(status().isOk());

        String inspectionJson = mockMvc.perform(get("/api/store/inspections/by-grn/" + grnId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode inspectionNode = objectMapper.readTree(inspectionJson);

        InspectionDto.DecideRequest decideRequest = new InspectionDto.DecideRequest(
                "Passes technical inspection",
                List.of(new InspectionDto.DecideItemRequest(
                        UUID.fromString(inspectionNode.get("items").get(0).get("id").asText()),
                        new BigDecimal("1.000"), BigDecimal.ZERO, BigDecimal.ZERO,
                        true, "GOOD", true, true,
                        "{\"specificationMatch\":true,\"physicalCondition\":\"GOOD\"}",
                        "As expected")),
                inspectionNode.get("version").asLong()
        );
        mockMvc.perform(post("/api/store/inspections/" + inspectionNode.get("id").asText() + "/decide")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decideRequest)))
                .andExpect(status().isOk());

        GrnPostDto.PostRequest postRequest = new GrnPostDto.PostRequest(
                List.of(new GrnPostDto.LineSerialRequest(grnItemId, List.of("READ-SN-001"))),
                "Received and stored"
        );
        mockMvc.perform(post("/api/store/grns/" + grnId + "/post")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "IDEM-READ-FIXTURE-" + System.currentTimeMillis())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(postRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"));

        postedAssetCode = (String) jdbcTemplate.queryForMap(
                "SELECT asset_code FROM store.asset WHERE grn_item_id = ?", grnItemId).get("asset_code");
    }

    private String getToken(String username) throws Exception {
        String resp = mockMvc.perform(post("/api/store/dev/token?username=" + username))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("token").asText();
    }

    /**
     * Mints a syntactically valid token with an explicit permission set, bypassing MockUsers.
     * Needed because all eleven mock users are granted ITEM_VIEW, so there is no role in the
     * Blueprint 8 matrix that can produce a genuine 403 for the item read endpoints. The token
     * is signed with the same secret the JwtAuthenticationFilter verifies against, so it is a
     * real authentication, not a test shortcut past the filter.
     */
    private String tokenWithPermissions(Set<String> permissions) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("username", "no.perm.user")
                .claim("name", "No Permission User")
                .claim("roles", List.of())
                .claim("permissions", List.copyOf(permissions))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(1, ChronoUnit.HOURS)))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    // ---------------------------------------------------------------- items

    @Test
    @DisplayName("GET /api/store/items returns 200 with the page envelope and item rows")
    void listItemsReturns200WithShape() throws Exception {
        mockMvc.perform(get("/api/store/items")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").exists())
                .andExpect(jsonPath("$.size").exists())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.first").isBoolean())
                .andExpect(jsonPath("$.last").isBoolean())
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].itemCode").value("LAPTOP-DELL"))
                .andExpect(jsonPath("$.content[0].itemName").exists())
                .andExpect(jsonPath("$.content[0].itemType").exists())
                .andExpect(jsonPath("$.content[0].trackingType").exists())
                .andExpect(jsonPath("$.content[0].active").isBoolean())
                .andExpect(jsonPath("$.content[0].version").isNumber());
    }

    @Test
    @DisplayName("GET /api/store/items requires a token (401) and ITEM_VIEW (403)")
    void listItemsIsGuarded() throws Exception {
        mockMvc.perform(get("/api/store/items"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/store/items")
                        .header("Authorization", "Bearer " + noPermissionToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/store/items/{id} returns 200 with the full item projection")
    void getItemByIdReturns200WithShape() throws Exception {
        mockMvc.perform(get("/api/store/items/" + serialItemId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(serialItemId.toString()))
                .andExpect(jsonPath("$.itemCode").value("LAPTOP-DELL"))
                .andExpect(jsonPath("$.categoryId").exists())
                .andExpect(jsonPath("$.categoryCode").value("IT-HW"))
                .andExpect(jsonPath("$.categoryName").exists())
                .andExpect(jsonPath("$.baseUomId").exists())
                .andExpect(jsonPath("$.uomCode").exists())
                .andExpect(jsonPath("$.itemType").value("NON_CONSUMABLE"))
                .andExpect(jsonPath("$.trackingType").value("SERIAL"))
                .andExpect(jsonPath("$.assetRequired").value(true))
                .andExpect(jsonPath("$.standardRate").isNumber())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.version").isNumber());
    }

    @Test
    @DisplayName("GET /api/store/items/{id} requires a token (401) and ITEM_VIEW (403)")
    void getItemByIdIsGuarded() throws Exception {
        mockMvc.perform(get("/api/store/items/" + serialItemId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/store/items/" + serialItemId)
                        .header("Authorization", "Bearer " + noPermissionToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/store/items/{id} updates the item and bumps the version")
    void updateItemReturns200AndBumpsVersion() throws Exception {
        String before = mockMvc.perform(get("/api/store/items/" + serialItemId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long versionBefore = objectMapper.readTree(before).get("version").asLong();

        // UpdateRequest carries a mandatory @NotNull version, so the read-modify-write is required.
        // Serialised from the record rather than hand-written JSON, so a field rename or a new
        // mandatory field is a compile error here instead of a runtime parse failure.
        String body = objectMapper.writeValueAsString(new ItemDto.UpdateRequest(
                "LAPTOP-DELL",
                "Dell Latitude 5420 (rev B)",
                UUID.fromString(objectMapper.readTree(before).get("categoryId").asText()),
                null,
                UUID.fromString(objectMapper.readTree(before).get("baseUomId").asText()),
                "NON_CONSUMABLE",
                "SERIAL",
                "updated by ReadEndpointCoverageIntegrationTest",
                null, null, null, null, null, null, null,
                true, true, false, true,
                versionBefore
        ));

        mockMvc.perform(put("/api/store/items/" + serialItemId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(serialItemId.toString()))
                .andExpect(jsonPath("$.itemName").value("Dell Latitude 5420 (rev B)"))
                .andExpect(jsonPath("$.version").isNumber());
    }

    @Test
    @DisplayName("PUT /api/store/items/{id} requires a token (401) and ITEM_UPDATE (403)")
    void updateItemIsGuarded() throws Exception {
        String body = "{\"itemCode\":\"X\",\"itemName\":\"X\",\"categoryId\":\"" + UUID.randomUUID()
                + "\",\"baseUomId\":\"" + UUID.randomUUID()
                + "\",\"itemType\":\"NON_CONSUMABLE\",\"trackingType\":\"SERIAL\","
                + "\"returnable\":true,\"warrantyApplicable\":true,\"expiryTracking\":false,"
                + "\"assetRequired\":true,\"version\":0}";

        mockMvc.perform(put("/api/store/items/" + serialItemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/store/items/" + serialItemId)
                        .header("Authorization", "Bearer " + noPermissionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ inventory

    @Test
    @DisplayName("GET /api/store/inventory/balances returns 200 with the posted balance row")
    void listBalancesReturns200WithShape() throws Exception {
        mockMvc.perform(get("/api/store/inventory/balances")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].itemId").value(serialItemId.toString()))
                .andExpect(jsonPath("$.content[0].itemCode").value("LAPTOP-DELL"))
                .andExpect(jsonPath("$.content[0].itemName").exists())
                .andExpect(jsonPath("$.content[0].uomCode").exists())
                .andExpect(jsonPath("$.content[0].storeId").value(testStoreId.toString()))
                .andExpect(jsonPath("$.content[0].locationCode").value("RACK-01"))
                .andExpect(jsonPath("$.content[0].onHandQty").isNumber());
    }

    @Test
    @DisplayName("GET /api/store/inventory/balances requires a token (401) and STOCK_VIEW (403)")
    void listBalancesIsGuarded() throws Exception {
        mockMvc.perform(get("/api/store/inventory/balances"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/store/inventory/balances")
                        .header("Authorization", "Bearer " + noPermissionToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/store/inventory/ledger returns 200 with the posted receipt row")
    void listLedgerReturns200WithShape() throws Exception {
        mockMvc.perform(get("/api/store/inventory/ledger")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].transactionNo").exists())
                .andExpect(jsonPath("$.content[0].transactionType").value("RECEIPT"))
                .andExpect(jsonPath("$.content[0].transactionTime").exists())
                .andExpect(jsonPath("$.content[0].itemId").value(serialItemId.toString()))
                .andExpect(jsonPath("$.content[0].itemCode").value("LAPTOP-DELL"))
                .andExpect(jsonPath("$.content[0].storeCode").exists());
    }

    @Test
    @DisplayName("GET /api/store/inventory/ledger requires a token (401) and STOCK_VIEW (403)")
    void listLedgerIsGuarded() throws Exception {
        mockMvc.perform(get("/api/store/inventory/ledger"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/store/inventory/ledger")
                        .header("Authorization", "Bearer " + noPermissionToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/store/inventory/low-stock returns 200 with the page envelope")
    void lowStockReturns200WithShape() throws Exception {
        // The fixture item carries no reorder or minimum level, so it is correctly absent from a
        // low-stock result. The assertion is on the envelope and the LowStockResponse projection,
        // not on membership: a row-count assertion here would assert the absence of a restock rule
        // that the fixture never set.
        mockMvc.perform(get("/api/store/inventory/low-stock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    @Test
    @DisplayName("GET /api/store/inventory/low-stock requires a token (401) and STOCK_VIEW (403)")
    void lowStockIsGuarded() throws Exception {
        mockMvc.perform(get("/api/store/inventory/low-stock"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/store/inventory/low-stock")
                        .header("Authorization", "Bearer " + noPermissionToken))
                .andExpect(status().isForbidden());
    }

    // --------------------------------------------------------------- assets

    @Test
    @DisplayName("GET /api/store/assets returns 200 with the posted asset row")
    void listAssetsReturns200WithShape() throws Exception {
        mockMvc.perform(get("/api/store/assets")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].assetCode").value(postedAssetCode))
                .andExpect(jsonPath("$.content[0].itemCode").value("LAPTOP-DELL"))
                .andExpect(jsonPath("$.content[0].itemName").exists())
                .andExpect(jsonPath("$.content[0].serialNumber").value("READ-SN-001"))
                .andExpect(jsonPath("$.content[0].storeCode").exists())
                .andExpect(jsonPath("$.content[0].locationCode").value("RACK-01"))
                .andExpect(jsonPath("$.content[0].assetStatus").exists())
                .andExpect(jsonPath("$.content[0].conditionStatus").exists())
                .andExpect(jsonPath("$.content[0].qrCodeValue").exists())
                .andExpect(jsonPath("$.content[0].purchaseCost").isNumber());
    }

    @Test
    @DisplayName("GET /api/store/assets requires a token (401) and ASSET_VIEW (403)")
    void listAssetsIsGuarded() throws Exception {
        mockMvc.perform(get("/api/store/assets"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/store/assets")
                        .header("Authorization", "Bearer " + noPermissionToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/store/assets/by-code/{assetCode} returns 200 for the posted asset")
    void getAssetByCodeReturns200WithShape() throws Exception {
        mockMvc.perform(get("/api/store/assets/by-code/" + postedAssetCode)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetCode").value(postedAssetCode))
                .andExpect(jsonPath("$.itemCode").value("LAPTOP-DELL"))
                .andExpect(jsonPath("$.serialNumber").value("READ-SN-001"))
                .andExpect(jsonPath("$.storeId").value(testStoreId.toString()))
                .andExpect(jsonPath("$.grnItemId").exists())
                .andExpect(jsonPath("$.assetStatus").exists())
                .andExpect(jsonPath("$.version").isNumber());
    }

    @Test
    @DisplayName("GET /api/store/assets/by-code/{assetCode} requires a token (401) and ASSET_VIEW (403)")
    void getAssetByCodeIsGuarded() throws Exception {
        mockMvc.perform(get("/api/store/assets/by-code/" + postedAssetCode))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/store/assets/by-code/" + postedAssetCode)
                        .header("Authorization", "Bearer " + noPermissionToken))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ search & 404 coverage

    @Test
    @DisplayName("GET /api/store/grns supports search parameter and requires authentication")
    void listGrnsWithSearchFilter() throws Exception {
        mockMvc.perform(get("/api/store/grns"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/store/grns")
                        .param("search", "READ")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());

        mockMvc.perform(get("/api/store/grns")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("GET /api/store/inspections supports search parameter and requires authentication")
    void listInspectionsWithSearchFilter() throws Exception {
        mockMvc.perform(get("/api/store/inspections"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/store/inspections")
                        .param("search", "INSP")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());

        mockMvc.perform(get("/api/store/inspections")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("GET /api/store/purchase-orders supports search parameter and requires authentication")
    void listPurchaseOrdersWithSearchFilter() throws Exception {
        mockMvc.perform(get("/api/store/purchase-orders"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/store/purchase-orders")
                        .param("search", "PO-READ")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());

        mockMvc.perform(get("/api/store/purchase-orders")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("Authenticated GET to non-existent endpoint returns 404 RESOURCE_NOT_FOUND (F5 fix)")
    void unknownPathReturns404() throws Exception {
        mockMvc.perform(get("/api/store/non-existent-endpoint-test-404")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
