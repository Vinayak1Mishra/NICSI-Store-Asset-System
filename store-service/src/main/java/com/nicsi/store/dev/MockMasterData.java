package com.nicsi.store.dev;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Profile(DevProfile.DEV)
public class MockMasterData {

    public record Employee(UUID id, String code, String name, String email, UUID departmentId) {}
    public record Department(UUID id, String code, String name, UUID divisionId) {}
    public record Division(UUID id, String code, String name) {}
    public record Project(UUID id, String code, String name) {}
    public record Vendor(UUID id, String code, String name, String gstNo) {}

    private final Map<UUID, Employee> employees = new HashMap<>();
    private final Map<UUID, Department> departments = new HashMap<>();
    private final Map<UUID, Division> divisions = new HashMap<>();
    private final Map<UUID, Project> projects = new HashMap<>();
    private final Map<UUID, Vendor> vendors = new HashMap<>();

    public MockMasterData() {
        initData();
    }

    private void initData() {
        UUID div1 = uuid("div-hq");
        UUID div2 = uuid("div-regional");
        divisions.put(div1, new Division(div1, "DIV-HQ", "Headquarters"));
        divisions.put(div2, new Division(div2, "DIV-REG", "Regional Office"));

        UUID deptIt = uuid("dept-it");
        UUID deptAdmin = uuid("dept-admin");
        UUID deptFinance = uuid("dept-finance");
        departments.put(deptIt, new Department(deptIt, "DEPT-IT", "IT Department", div1));
        departments.put(deptAdmin, new Department(deptAdmin, "DEPT-ADM", "Admin Department", div1));
        departments.put(deptFinance, new Department(deptFinance, "DEPT-FIN", "Finance Department", div1));

        for (int i = 1; i <= 10; i++) {
            UUID empId = uuid("emp-" + i);
            UUID dId = (i % 3 == 0) ? deptFinance : ((i % 2 == 0) ? deptAdmin : deptIt);
            employees.put(empId, new Employee(empId, "EMP" + String.format("%03d", i), "Employee " + i, "emp" + i + "@nicsi.in", dId));
        }

        for (int i = 1; i <= 3; i++) {
            UUID projId = uuid("proj-" + i);
            projects.put(projId, new Project(projId, "PRJ" + String.format("%03d", i), "Project " + i));
        }

        for (int i = 1; i <= 5; i++) {
            UUID venId = uuid("vendor-" + i);
            vendors.put(venId, new Vendor(venId, "VEN" + String.format("%03d", i), "Vendor " + i, "22AAAAA0000A1Z" + i));
        }
    }

    private UUID uuid(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes());
    }

    public Employee getEmployee(UUID id) { return employees.get(id); }
    public Department getDepartment(UUID id) { return departments.get(id); }
    public Project getProject(UUID id) { return projects.get(id); }
    public Vendor getVendor(UUID id) { return vendors.get(id); }

    public Collection<Employee> listEmployees() { return employees.values(); }
    public Collection<Department> listDepartments() { return departments.values(); }
    public Collection<Division> listDivisions() { return divisions.values(); }
    public Collection<Project> listProjects() { return projects.values(); }
    public Collection<Vendor> listVendors() { return vendors.values(); }
}
