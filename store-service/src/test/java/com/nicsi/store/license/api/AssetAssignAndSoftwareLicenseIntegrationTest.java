package com.nicsi.store.license.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicsi.store.asset.domain.Asset;
import com.nicsi.store.asset.dto.AssetDto;
import com.nicsi.store.asset.repository.AssetRepository;
import com.nicsi.store.issue.domain.AssetAssignment;
import com.nicsi.store.issue.repository.AssetAssignmentRepository;
import com.nicsi.store.license.dto.SoftwareLicenseDto;
import com.nicsi.store.license.repository.SoftwareLicenseAllocationRepository;
import com.nicsi.store.license.repository.SoftwareLicenseRepository;
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
import com.nicsi.store.testutil.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
public class AssetAssignAndSoftwareLicenseIntegrationTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Autowired private StoreSiteRepository storeSiteRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private ItemCategoryRepository itemCategoryRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private UomRepository uomRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private AssetAssignmentRepository assetAssignmentRepository;
    @Autowired private SoftwareLicenseRepository licenseRepository;
    @Autowired private SoftwareLicenseAllocationRepository allocationRepository;

    private String adminToken;
    private String tag;

    private StoreSite testStore;
    private StorageLocation testLocation;
    private Item hardwareItem;
    private Item softwareItem;

    private UUID uuid(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes());
    }

    private String getToken(String username) throws Exception {
        String resp = mockMvc.perform(post("/api/store/dev/token?username=" + username))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("token").asText();
    }

    @BeforeEach
    void setUp() throws Exception {
        tag = "P6-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        adminToken = getToken("admin");

        StoreSite s = new StoreSite();
        s.setStoreCode("STORE-" + tag);
        s.setStoreName("Test Store " + tag);
        s.setStoreType("GENERAL");
        testStore = storeSiteRepository.save(s);

        StorageLocation loc = new StorageLocation();
        loc.setStore(testStore);
        loc.setLocationCode("RACK-" + tag);
        loc.setLocationName("Rack " + tag);
        loc.setLocationType("RACK");
        testLocation = storageLocationRepository.save(loc);

        Uom nos = uomRepository.findByUomCodeIgnoreCase("NOS").orElseThrow();
        ItemCategory cat = itemCategoryRepository.findAll().stream().findFirst().orElseThrow();

        Item hw = new Item();
        hw.setItemCode("HW-" + tag);
        hw.setItemName("Laptop " + tag);
        hw.setItemType("NON_CONSUMABLE");
        hw.setTrackingType("SERIAL");
        hw.setAssetRequired(true);
        hw.setCategory(cat);
        hw.setBaseUom(nos);
        hw.setStandardRate(new BigDecimal("60000.00"));
        hardwareItem = itemRepository.save(hw);

        Item sw = new Item();
        sw.setItemCode("SW-" + tag);
        sw.setItemName("Operating System " + tag);
        sw.setItemType("SOFTWARE");
        sw.setTrackingType("LICENSE");
        sw.setAssetRequired(false);
        sw.setCategory(cat);
        sw.setBaseUom(nos);
        sw.setStandardRate(new BigDecimal("15000.00"));
        softwareItem = itemRepository.save(sw);
    }

    @Test
    @DisplayName("Phase 6: Standalone Asset Assignment via POST /api/assets/{id}/assign")
    void testStandaloneAssetAssignment() throws Exception {
        // Create an available asset
        Asset asset = new Asset();
        asset.setAssetCode("NICSI/HW/" + tag + "/000001");
        asset.setItem(hardwareItem);
        asset.setSerialNumber("SN-" + tag + "-01");
        asset.setStore(testStore);
        asset.setLocation(testLocation);
        asset.setAssetStatus("AVAILABLE");
        asset.setConditionStatus("GOOD");
        asset.setQrCodeValue("QR-" + tag + "-01");
        asset.setBarcodeValue("BC-" + tag + "-01");
        asset.setCreatedBy(uuid("admin"));
        Asset savedAsset = assetRepository.save(asset);

        UUID employeeId = uuid("employee.john");
        UUID deptId = uuid("dept-it");

        AssetDto.AssignRequest assignReq = new AssetDto.AssignRequest(
                "EMPLOYEE",
                employeeId,
                "John Doe",
                deptId,
                null,
                testLocation.getId(),
                "Assigned for project development"
        );

        // 1. Assign asset via /api/assets/{id}/assign (contract alias)
        mockMvc.perform(post("/api/assets/" + savedAsset.getId() + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetStatus").value("ISSUED"))
                .andExpect(jsonPath("$.currentCustodianUserId").value(employeeId.toString()))
                .andExpect(jsonPath("$.currentDepartmentId").value(deptId.toString()));

        // Verify assignment record
        AssetAssignment activeAssignment = assetAssignmentRepository.findFirstByAssetIdAndStatus(savedAsset.getId(), "ACTIVE").orElseThrow();
        assertThat(activeAssignment.getAssigneeUserId()).isEqualTo(employeeId);
        assertThat(activeAssignment.getAssigneeNameSnapshot()).isEqualTo("John Doe");
        assertThat(activeAssignment.getStatus()).isEqualTo("ACTIVE");

        // 2. Re-assign to a different custodian via /api/store/assets/{id}/assign
        UUID newEmployeeId = uuid("store.manager");
        AssetDto.AssignRequest reassignReq = new AssetDto.AssignRequest(
                "EMPLOYEE",
                newEmployeeId,
                "Store Manager",
                deptId,
                null,
                testLocation.getId(),
                "Transferred to new manager"
        );

        mockMvc.perform(post("/api/store/assets/" + savedAsset.getId() + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reassignReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetStatus").value("ISSUED"))
                .andExpect(jsonPath("$.currentCustodianUserId").value(newEmployeeId.toString()));

        // Old assignment should be TRANSFERRED, new assignment ACTIVE
        List<AssetAssignment> assignments = assetAssignmentRepository.findAll().stream()
                .filter(a -> a.getAssetId().equals(savedAsset.getId())).toList();
        assertThat(assignments).hasSize(2);
        assertThat(assignments.stream().filter(a -> "TRANSFERRED".equals(a.getStatus())).count()).isEqualTo(1);
        assertThat(assignments.stream().filter(a -> "ACTIVE".equals(a.getStatus())).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Phase 6: Software License Intake, Allocation, and Release Lifecycle")
    void testSoftwareLicenseLifecycle() throws Exception {
        String licenseCode = "LIC-" + tag + "-001";

        SoftwareLicenseDto.CreateRequest createReq = new SoftwareLicenseDto.CreateRequest(
                softwareItem.getId(),
                licenseCode,
                null,
                "USER",
                new BigDecimal("10.000"),
                "SEC-REF-" + tag,
                LocalDate.now(),
                LocalDate.now(),
                LocalDate.now().plusYears(1),
                "PO-" + tag
        );

        // 1. Create license via /api/store/licenses
        String createResp = mockMvc.perform(post("/api/store/licenses")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.licenseCode").value(licenseCode))
                .andExpect(jsonPath("$.entitlementQty").value(10.0))
                .andExpect(jsonPath("$.allocatedQty").value(0.0))
                .andExpect(jsonPath("$.availableQty").value(10.0))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();

        UUID licenseId = UUID.fromString(objectMapper.readTree(createResp).get("id").asText());

        // 2. Allocate 3 seats via /api/licenses/{id}/allocate (alias)
        SoftwareLicenseDto.AllocateRequest allocReq = new SoftwareLicenseDto.AllocateRequest(
                "USER",
                uuid("employee.john"),
                null,
                null,
                new BigDecimal("3.000"),
                "Allocated 3 user seats"
        );

        String allocResp = mockMvc.perform(post("/api/licenses/" + licenseId + "/allocate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(allocReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocatedQty").value(3.0))
                .andExpect(jsonPath("$.availableQty").value(7.0))
                .andExpect(jsonPath("$.allocations.length()").value(1))
                .andReturn().getResponse().getContentAsString();

        UUID allocationId = UUID.fromString(objectMapper.readTree(allocResp).get("allocations").get(0).get("id").asText());

        // 3. Exceed available entitlement -> expect 400 BAD_REQUEST
        SoftwareLicenseDto.AllocateRequest exceedReq = new SoftwareLicenseDto.AllocateRequest(
                "USER",
                uuid("store.operator"),
                null,
                null,
                new BigDecimal("8.000"), // only 7 available
                "Exceeds available"
        );

        mockMvc.perform(post("/api/licenses/" + licenseId + "/allocate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exceedReq)))
                .andExpect(status().isBadRequest());

        // 4. Release allocation via /api/store/licenses/{id}/allocations/{allocationId}/release
        mockMvc.perform(post("/api/store/licenses/" + licenseId + "/allocations/" + allocationId + "/release")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocatedQty").value(0.0))
                .andExpect(jsonPath("$.availableQty").value(10.0));

        // 5. Query license by ID
        mockMvc.perform(get("/api/licenses/" + licenseId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licenseCode").value(licenseCode))
                .andExpect(jsonPath("$.availableQty").value(10.0));
    }
}
