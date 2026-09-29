package com.nicsi.store.asset.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicsi.store.asset.domain.Asset;
import com.nicsi.store.asset.domain.Disposal;
import com.nicsi.store.asset.domain.RepairTicket;
import com.nicsi.store.asset.dto.AssetDto;
import com.nicsi.store.asset.repository.AssetRepository;
import com.nicsi.store.asset.repository.DisposalRepository;
import com.nicsi.store.asset.repository.RepairTicketRepository;
import com.nicsi.store.issue.domain.AssetAssignment;
import com.nicsi.store.issue.repository.AssetAssignmentRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
public class AssetLifecycleIntegrationTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Autowired private StoreSiteRepository storeSiteRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private ItemCategoryRepository itemCategoryRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private UomRepository uomRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private AssetAssignmentRepository assetAssignmentRepository;
    @Autowired private RepairTicketRepository repairTicketRepository;
    @Autowired private DisposalRepository disposalRepository;

    private String adminToken;
    private StoreSite testStore;
    private StorageLocation testLocation;
    private Item testItem;

    private UUID uuid(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String getToken(String username) throws Exception {
        String resp = mockMvc.perform(post("/api/store/dev/token?username=" + username))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("token").asText();
    }

    @BeforeEach
    void setUp() throws Exception {
        adminToken = getToken("admin");
        String tag = "LFC" + System.nanoTime() % 100000;

        Uom uom = new Uom();
        uom.setUomCode("EA-" + tag);
        uom.setUomName("Each " + tag);
        uom.setUomType("COUNT");
        uom.setCreatedBy(uuid("uom-creator"));
        uom = uomRepository.save(uom);

        ItemCategory cat = new ItemCategory();
        cat.setCategoryCode("IT-" + tag);
        cat.setCategoryName("IT Hardware " + tag);
        cat.setCreatedBy(uuid("cat-creator"));
        cat = itemCategoryRepository.save(cat);

        testStore = new StoreSite();
        testStore.setStoreCode("STR-" + tag);
        testStore.setStoreName("HQ Warehouse " + tag);
        testStore.setStoreType("IT");
        testStore.setCreatedBy(uuid("str-creator"));
        testStore = storeSiteRepository.save(testStore);

        testLocation = new StorageLocation();
        testLocation.setStore(testStore);
        testLocation.setLocationCode("BAY-" + tag);
        testLocation.setLocationName("Bay A " + tag);
        testLocation.setLocationType("SHELF");
        testLocation.setCreatedBy(uuid("loc-creator"));
        testLocation = storageLocationRepository.save(testLocation);

        testItem = new Item();
        testItem.setItemCode("SRV-" + tag);
        testItem.setItemName("Server Node " + tag);
        testItem.setItemType("NON_CONSUMABLE");
        testItem.setTrackingType("SERIAL");
        testItem.setCategory(cat);
        testItem.setBaseUom(uom);
        testItem.setCreatedBy(uuid("item-creator"));
        testItem = itemRepository.save(testItem);
    }

    private Asset createSampleAsset(String assetCode, String status, String condition) {
        Asset asset = new Asset();
        asset.setAssetCode(assetCode);
        asset.setItem(testItem);
        asset.setSerialNumber("SN-" + System.nanoTime());
        asset.setManufacturer("Lenovo");
        asset.setModelNumber("ThinkServer");
        asset.setPurchaseDate(LocalDate.now());
        asset.setPurchaseCost(new BigDecimal("75000.00"));
        asset.setStore(testStore);
        asset.setLocation(testLocation);
        asset.setAssetStatus(status);
        asset.setConditionStatus(condition);
        asset.setQrCodeValue("QR-" + UUID.randomUUID());
        asset.setCreatedBy(uuid("asset-reg"));
        return assetRepository.save(asset);
    }

    @Test
    @DisplayName("Phase 7: Transfer asset between custodians and verify assignment transitions")
    void testAssetTransferFlow() throws Exception {
        Asset asset = createSampleAsset("NICSI/IT/2026/000701", "AVAILABLE", "GOOD");
        UUID employeeId = uuid("emp-rajesh");

        // 1. Assign to employee
        AssetDto.AssignRequest assignReq = new AssetDto.AssignRequest(
                "EMPLOYEE", employeeId, "Rajesh Kumar", null, null, testLocation.getId(), "Initial deploy"
        );
        mockMvc.perform(post("/api/assets/" + asset.getId() + "/assign")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetStatus").value("ISSUED"))
                .andExpect(jsonPath("$.currentCustodianUserId").value(employeeId.toString()));

        // 2. Transfer to new custodian
        UUID newEmpId = uuid("emp-priya");
        AssetDto.TransferRequest transferReq = new AssetDto.TransferRequest(
                testStore.getId(), testLocation.getId(), "EMPLOYEE", newEmpId, "Priya Sharma",
                null, null, "Inter-department reallocation"
        );

        mockMvc.perform(post("/api/assets/" + asset.getId() + "/transfer")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(transferReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetStatus").value("ISSUED"))
                .andExpect(jsonPath("$.currentCustodianUserId").value(newEmpId.toString()));

        // Verify previous assignment is marked TRANSFERRED and new one is ACTIVE
        List<AssetAssignment> assignments = assetAssignmentRepository.findByAssetIdOrderByAssignedFromDesc(asset.getId());
        assertThat(assignments).hasSize(2);
        assertThat(assignments.get(0).getStatus()).isEqualTo("ACTIVE");
        assertThat(assignments.get(0).getAssigneeUserId()).isEqualTo(newEmpId);
        assertThat(assignments.get(1).getStatus()).isEqualTo("TRANSFERRED");
    }

    @Test
    @DisplayName("Phase 7: Return asset from custodian back to store with RESTOCK disposition")
    void testAssetReturnFlow() throws Exception {
        Asset asset = createSampleAsset("NICSI/IT/2026/000702", "AVAILABLE", "GOOD");
        UUID employeeId = uuid("emp-amit");

        // Assign first
        AssetDto.AssignRequest assignReq = new AssetDto.AssignRequest(
                "EMPLOYEE", employeeId, "Amit Verma", null, null, testLocation.getId(), "Issued for project"
        );
        mockMvc.perform(post("/api/assets/" + asset.getId() + "/assign")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk());

        // Return asset
        AssetDto.ReturnRequest returnReq = new AssetDto.ReturnRequest(
                testStore.getId(), testLocation.getId(), "GOOD", "RESTOCK", "Project completed, returned"
        );

        mockMvc.perform(post("/api/assets/" + asset.getId() + "/return")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(returnReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.conditionStatus").value("GOOD"))
                .andExpect(jsonPath("$.currentCustodianUserId").doesNotExist());

        // Verify assignment closed as RETURNED
        List<AssetAssignment> assignments = assetAssignmentRepository.findByAssetIdOrderByAssignedFromDesc(asset.getId());
        assertThat(assignments).hasSize(1);
        assertThat(assignments.get(0).getStatus()).isEqualTo("RETURNED");
    }

    @Test
    @DisplayName("Phase 7: Send asset to repair and return from repair upon completion")
    void testAssetRepairFlow() throws Exception {
        Asset asset = createSampleAsset("NICSI/IT/2026/000703", "AVAILABLE", "GOOD");
        UUID vendorId = uuid("repair-vendor-dell");

        // 1. Send to repair
        AssetDto.RepairRequest sendReq = new AssetDto.RepairRequest(
                "SEND_TO_REPAIR", vendorId, "Dell Service Center", "Motherboard power failure",
                true, null, null, null, null, "Sent under warranty"
        );

        mockMvc.perform(post("/api/assets/" + asset.getId() + "/repair")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sendReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetStatus").value("IN_REPAIR"))
                .andExpect(jsonPath("$.conditionStatus").value("REPAIR_REQUIRED"));

        List<RepairTicket> tickets = repairTicketRepository.findByAssetIdOrderByComplaintDateDesc(asset.getId());
        assertThat(tickets).hasSize(1);
        assertThat(tickets.get(0).getStatus()).isEqualTo("SENT_TO_VENDOR");
        assertThat(tickets.get(0).getWarrantyClaim()).isTrue();

        // 2. Return from repair
        AssetDto.RepairRequest returnReq = new AssetDto.RepairRequest(
                "RETURN_FROM_REPAIR", vendorId, "Dell Service Center", null,
                true, new BigDecimal("0.00"), "Motherboard replaced under warranty", "GOOD",
                "Power rail short resolved", "Repaired and tested OK"
        );

        mockMvc.perform(post("/api/assets/" + asset.getId() + "/repair")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(returnReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.conditionStatus").value("GOOD"));

        RepairTicket completedTicket = repairTicketRepository.findById(tickets.get(0).getId()).orElseThrow();
        assertThat(completedTicket.getStatus()).isEqualTo("COMPLETED");
        assertThat(completedTicket.getPartsReplaced()).isEqualTo("Motherboard replaced under warranty");
    }

    @Test
    @DisplayName("Phase 7: Dispose condemned asset and block subsequent assignments")
    void testAssetDisposalFlow() throws Exception {
        Asset asset = createSampleAsset("NICSI/IT/2026/000704", "AVAILABLE", "UNSERVICEABLE");
        UUID purchaserId = uuid("scrap-vendor");

        AssetDto.DisposeRequest disposeReq = new AssetDto.DisposeRequest(
                "AUCTION", purchaserId, "MSTC Auction Buyer", new BigDecimal("4500.00"),
                "MSTC/CERT/2026/891", "Condemned by board, auctioned via MSTC"
        );

        mockMvc.perform(post("/api/assets/" + asset.getId() + "/dispose")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(disposeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetStatus").value("DISPOSED"))
                .andExpect(jsonPath("$.conditionStatus").value("SCRAP"));

        List<Disposal> disposals = disposalRepository.findAll();
        assertThat(disposals.stream().anyMatch(d -> "MSTC/CERT/2026/891".equals(d.getCertificateNumber()))).isTrue();

        // Verify trying to assign a DISPOSED asset returns BAD_REQUEST (400)
        AssetDto.AssignRequest assignReq = new AssetDto.AssignRequest(
                "EMPLOYEE", uuid("emp-test"), "Test User", null, null, testLocation.getId(), "Should fail"
        );
        mockMvc.perform(post("/api/assets/" + asset.getId() + "/assign")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isBadRequest());
    }
}
