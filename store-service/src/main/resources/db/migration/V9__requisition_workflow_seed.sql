-- V9__requisition_workflow_seed.sql
-- Baseline 2-step approval workflow for Material Requisitions

INSERT INTO workflow.definition (
    id, workflow_code, workflow_name, entity_type, version_no, active, created_at, created_by
) VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'REQ_STANDARD_V1',
    'Standard Requisition Approval Workflow',
    'REQUISITION',
    1,
    true,
    now(),
    '00000000-0000-0000-0000-000000000000'
) ON CONFLICT (workflow_code, version_no) DO NOTHING;

INSERT INTO workflow.step_definition (
    id, workflow_definition_id, step_no, step_code, step_name, approver_type, approver_role_code, sla_hours, allow_reject, allow_return
) VALUES (
    'a0000000-0000-0000-0000-000000000002',
    'a0000000-0000-0000-0000-000000000001',
    1,
    'HOD_APPROVAL',
    'Department Head / HOD Approval',
    'HOD',
    'ROLE_HOD',
    48,
    true,
    true
) ON CONFLICT (workflow_definition_id, step_no) DO NOTHING;

INSERT INTO workflow.step_definition (
    id, workflow_definition_id, step_no, step_code, step_name, approver_type, approver_role_code, sla_hours, allow_reject, allow_return
) VALUES (
    'a0000000-0000-0000-0000-000000000003',
    'a0000000-0000-0000-0000-000000000001',
    2,
    'STORE_VERIFICATION',
    'Store Officer Stock Verification',
    'STORE_OFFICER',
    'ROLE_STORE_OFFICER',
    24,
    true,
    true
) ON CONFLICT (workflow_definition_id, step_no) DO NOTHING;
